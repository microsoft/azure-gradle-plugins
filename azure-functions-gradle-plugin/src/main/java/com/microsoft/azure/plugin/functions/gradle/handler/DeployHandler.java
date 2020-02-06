/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.handler;

import com.google.common.base.Preconditions;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.common.Utils;
import com.microsoft.azure.common.appservice.DeployTargetType;
import com.microsoft.azure.common.appservice.DeploymentType;
import com.microsoft.azure.common.appservice.OperatingSystemEnum;
import com.microsoft.azure.common.deploytarget.DeployTarget;
import com.microsoft.azure.common.exceptions.AzureExecutionException;
import com.microsoft.azure.common.function.configurations.ElasticPremiumPricingTier;
import com.microsoft.azure.common.function.configurations.FunctionExtensionVersion;
import com.microsoft.azure.common.function.configurations.RuntimeConfiguration;
import com.microsoft.azure.common.function.handlers.artifact.DockerArtifactHandler;
import com.microsoft.azure.common.function.handlers.artifact.MSDeployArtifactHandlerImpl;
import com.microsoft.azure.common.function.handlers.artifact.RunFromBlobArtifactHandlerImpl;
import com.microsoft.azure.common.function.handlers.artifact.RunFromZipArtifactHandlerImpl;
import com.microsoft.azure.common.function.handlers.runtime.DockerFunctionRuntimeHandler;
import com.microsoft.azure.common.function.handlers.runtime.FunctionRuntimeHandler;
import com.microsoft.azure.common.function.handlers.runtime.LinuxFunctionRuntimeHandler;
import com.microsoft.azure.common.function.handlers.runtime.WindowsFunctionRuntimeHandler;
import com.microsoft.azure.common.function.utils.FunctionUtils;
import com.microsoft.azure.common.handlers.ArtifactHandler;
import com.microsoft.azure.common.handlers.artifact.ArtifactHandlerBase;
import com.microsoft.azure.common.handlers.artifact.FTPArtifactHandlerImpl;
import com.microsoft.azure.common.handlers.artifact.ZIPArtifactHandlerImpl;
import com.microsoft.azure.common.logging.Log;
import com.microsoft.azure.common.utils.AppServiceUtils;
import com.microsoft.azure.management.appservice.FunctionApp;
import com.microsoft.azure.management.appservice.FunctionApp.DefinitionStages.WithCreate;
import com.microsoft.azure.management.appservice.FunctionApp.Update;
import com.microsoft.azure.management.appservice.JavaVersion;
import com.microsoft.azure.management.appservice.PricingTier;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.plugin.functions.gradle.GradleDockerCredentialProvider;
import com.microsoft.azure.plugin.functions.gradle.IAppServiceContext;
import com.microsoft.azure.plugin.functions.gradle.configuration.GradleRuntimeConfiguration;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.Consumer;

import static com.microsoft.azure.common.appservice.DeploymentType.DOCKER;
import static com.microsoft.azure.common.appservice.DeploymentType.EMPTY;
import static com.microsoft.azure.common.appservice.DeploymentType.RUN_FROM_BLOB;
import static com.microsoft.azure.common.appservice.DeploymentType.RUN_FROM_ZIP;

/**
 * Deploy artifacts to target Azure Functions in Azure. If target Azure
 * Functions doesn't exist, it will be created.
 */
public class DeployHandler {
    private static final String JDK_VERSION_ERROR = "Azure Functions only support JDK 8, which is lower than local " +
        "JDK version %s";
    private static final String FUNCTIONS_WORKER_RUNTIME_NAME = "FUNCTIONS_WORKER_RUNTIME";
    private static final String FUNCTIONS_WORKER_RUNTIME_VALUE = "java";
    private static final String SET_FUNCTIONS_WORKER_RUNTIME = "Set function worker runtime to java";
    private static final String CHANGE_FUNCTIONS_WORKER_RUNTIME = "Function worker runtime doesn't " +
        "meet the requirement, change it from %s to java";
    private static final String FUNCTIONS_EXTENSION_VERSION_NAME = "FUNCTIONS_EXTENSION_VERSION";
    private static final String FUNCTIONS_EXTENSION_VERSION_VALUE = "~3";
    private static final String SET_FUNCTIONS_EXTENSION_VERSION = "Functions extension version " +
            "isn't configured, setting up the default value";
    public static final JavaVersion DEFAULT_JAVA_VERSION = JavaVersion.JAVA_8_NEWEST;
    public static final String VALID_JAVA_VERSION_PATTERN = "^1\\.8.*"; // For now we only support function with java 8

    public static final String DEPLOY_START = "Trying to deploy the function app...";
    public static final String DEPLOY_FINISH = "Successfully deployed the function app at https://%s.azurewebsites.net";
    public static final String FUNCTION_APP_CREATE_START = "The specified function app does not exist. " +
            "Creating a new function app...";
    public static final String FUNCTION_APP_CREATED = "Successfully created the function app: %s";
    public static final String FUNCTION_APP_UPDATE = "Updating the specified function app...";
    public static final String FUNCTION_APP_UPDATE_DONE = "Successfully updated the function app.";
    public static final String DEPLOYMENT_TYPE_KEY = "deploymentType";

    public static final String HOST_JAVA_VERSION = "Java version of function host : %s";
    public static final String HOST_JAVA_VERSION_OFF = "Java version of function host is not initiated," +
            " set it to Java 8";
    public static final String HOST_JAVA_VERSION_INCORRECT = "Java version of function host %s does not" +
            " meet the requirement of Azure Functions, set it to Java 8";
    public static final String UNKNOWN_DEPLOYMENT_TYPE = "The value of <deploymentType> is unknown, supported values are: " +
            "ftp, zip, msdeploy, run_from_blob and run_from_zip.";

    public static final OperatingSystemEnum DEFAULT_OS = OperatingSystemEnum.Windows;
    private IAppServiceContext ctx;

    public DeployHandler(IAppServiceContext ctx) {
        Preconditions.checkNotNull(ctx);
        this.ctx = ctx;
    }

    public void execute() throws AzureExecutionException {
        checkJavaVersion();
        createOrUpdateFunctionApp();
        final FunctionApp app = getFunctionApp();
        if (app == null) {
            throw new AzureExecutionException(
                    String.format("Failed to get the function app with name: %s", ctx.getAppName()));
        }

        final DeployTarget deployTarget = new DeployTarget(app, DeployTargetType.FUNCTION);
        Log.prompt(DEPLOY_START);
        getArtifactHandler().publish(deployTarget);
        Log.prompt(String.format(DEPLOY_FINISH, ctx.getAppName()));
    }

    private static void checkJavaVersion() {
        final String javaVersion = System.getProperty("java.version");
        if (!javaVersion.startsWith("1.8")) {
            Log.error(String.format(JDK_VERSION_ERROR, javaVersion));
        }
    }

    private void createOrUpdateFunctionApp() throws AzureExecutionException {
        final FunctionApp app = getFunctionApp();
        if (app == null) {
            createFunctionApp();
        } else {
            updateFunctionApp(app);
        }
    }

    private void createFunctionApp() throws AzureExecutionException {
        Log.prompt(FUNCTION_APP_CREATE_START);
        final FunctionRuntimeHandler runtimeHandler = getFunctionRuntimeHandler();
        final WithCreate withCreate = runtimeHandler.defineAppWithRuntime();
        configureAppSettings(withCreate::withAppSettings, getAppSettingsWithDefaultValue());
        withCreate.withJavaVersion(DEFAULT_JAVA_VERSION).withWebContainer(null).create();
        Log.prompt(String.format(FUNCTION_APP_CREATED, ctx.getAppName()));
    }

    private void updateFunctionApp(final FunctionApp app) throws AzureExecutionException {
        Log.prompt(FUNCTION_APP_UPDATE);
        // Work around of https://github.com/Azure/azure-sdk-for-java/issues/1755
        app.inner().withTags(null);
        final FunctionRuntimeHandler runtimeHandler = getFunctionRuntimeHandler();
        runtimeHandler.updateAppServicePlan(app);
        final Update update = runtimeHandler.updateAppRuntime(app);
        checkHostJavaVersion(app, update); // Check Java Version of Server
        configureAppSettings(update::withAppSettings, getAppSettingsWithDefaultValue());
        update.apply();
        Log.prompt(FUNCTION_APP_UPDATE_DONE + ctx.getAppName());
    }

    private void checkHostJavaVersion(final FunctionApp app, final Update update) {
        final JavaVersion serverJavaVersion = app.javaVersion();
        if (serverJavaVersion.toString().matches(VALID_JAVA_VERSION_PATTERN)) {
            Log.prompt(String.format(HOST_JAVA_VERSION, serverJavaVersion));
        } else if (serverJavaVersion.equals(JavaVersion.OFF)) {
            Log.prompt(HOST_JAVA_VERSION_OFF);
            update.withJavaVersion(DEFAULT_JAVA_VERSION);
        } else {
            Log.prompt(HOST_JAVA_VERSION_INCORRECT);
            update.withJavaVersion(DEFAULT_JAVA_VERSION);
        }
    }

    private void configureAppSettings(final Consumer<Map<String, String>> withAppSettings, final Map<String, String> appSettings) {
        if (appSettings != null && !appSettings.isEmpty()) {
            withAppSettings.accept(appSettings);
        }
    }

    private FunctionRuntimeHandler getFunctionRuntimeHandler() throws AzureExecutionException {
        final FunctionRuntimeHandler.Builder<?> builder;
        final OperatingSystemEnum os = getOsEnum();
        switch (os) {
            case Windows:
                builder = new WindowsFunctionRuntimeHandler.Builder();
                break;
            case Linux:
                builder = new LinuxFunctionRuntimeHandler.Builder();
                break;
            case Docker:
                // TODO: refact RuntimeConfiguration to allow set plain username/password
                final GradleRuntimeConfiguration runtime = (GradleRuntimeConfiguration) this.ctx.getRuntime();
                builder = new DockerFunctionRuntimeHandler.Builder().image(runtime.getImage())
                        .dockerCredentialProvider(StringUtils.isNotBlank(runtime.getUsername()) ?
                                new GradleDockerCredentialProvider(runtime.getUsername(), runtime.getPassword())
                                : null)
                        .registryUrl(runtime.getRegistryUrl());
                break;
            default:
                throw new AzureExecutionException(String.format("Unsupported runtime %s", os));
        }
        return builder.appName(ctx.getAppName()).resourceGroup(ctx.getResourceGroup()).runtime(ctx.getRuntime())
                .region(Region.fromName(ctx.getRegion())).pricingTier(getPricingTier())
                .servicePlanName(ctx.getAppServicePlanName())
                .servicePlanResourceGroup(ctx.getAppServicePlanResourceGroup())
                .functionExtensionVersion(getFunctionExtensionVersion()).azure(this.ctx.getAzureClient()).build();
    }

    private OperatingSystemEnum getOsEnum() throws AzureExecutionException {
        final RuntimeConfiguration runtime = ctx.getRuntime();
        if (runtime != null && StringUtils.isNotBlank(runtime.getOs())) {
            return Utils.parseOperationSystem(runtime.getOs());
        }
        return DEFAULT_OS;
    }

    public DeploymentType getDeploymentType() throws AzureExecutionException {
        final DeploymentType deploymentType = DeploymentType.fromString(ctx.getDeploymentType());
        return deploymentType == EMPTY ? getDeploymentTypeByRuntime() : deploymentType;
    }

    public DeploymentType getDeploymentTypeByRuntime() throws AzureExecutionException {
        final OperatingSystemEnum operatingSystemEnum = getOsEnum();
        switch (operatingSystemEnum) {
            case Docker:
                return DOCKER;
            case Linux:
                return isDedicatedPricingTier() ? RUN_FROM_ZIP : RUN_FROM_BLOB;
            default:
                return RUN_FROM_ZIP;
        }
    }

    private boolean isDedicatedPricingTier() {
        return AppServiceUtils.getPricingTierFromString(ctx.getPricingTier()) != null;
    }

    public FunctionApp getFunctionApp() throws AzureExecutionException {
        final PagedList<FunctionApp> functionList = ctx.getAzureClient().appServices().functionApps().list();
        return AppServiceUtils.findAppServiceInPagedList(functionList, ctx.getResourceGroup(), ctx.getAppName());
    }

    public FunctionExtensionVersion getFunctionExtensionVersion() throws AzureExecutionException {
        final String extensionVersion = getAppSettingsWithDefaultValue().get(FUNCTIONS_EXTENSION_VERSION_NAME);
        return FunctionUtils.parseFunctionExtensionVersion(extensionVersion);
    }

    public Map<String, String> getAppSettingsWithDefaultValue() {
        final Map<String, String> settings = ctx.getAppSettings();
        overrideDefaultAppSetting(settings, FUNCTIONS_WORKER_RUNTIME_NAME, SET_FUNCTIONS_WORKER_RUNTIME,
                FUNCTIONS_WORKER_RUNTIME_VALUE, CHANGE_FUNCTIONS_WORKER_RUNTIME);
        setDefaultAppSetting(settings, FUNCTIONS_EXTENSION_VERSION_NAME, SET_FUNCTIONS_EXTENSION_VERSION,
                FUNCTIONS_EXTENSION_VERSION_VALUE);
        return settings;
    }

    private void setDefaultAppSetting(Map<String, String> result, String settingName, String settingIsEmptyMessage,
            String settingValue) {
        final String setting = result.get(settingName);
        if (StringUtils.isEmpty(setting)) {
            Log.prompt(settingIsEmptyMessage);
            result.put(settingName, settingValue);
        }
    }

    private void overrideDefaultAppSetting(Map<String, String> result, String settingName, String settingIsEmptyMessage,
            String settingValue, String changeSettingMessage) {
        final String setting = result.get(settingName);
        if (StringUtils.isEmpty(setting)) {
            Log.prompt(settingIsEmptyMessage);
        } else if (!setting.equals(settingValue)) {
            Log.warn(String.format(changeSettingMessage, setting));
        }
        result.put(settingName, settingValue);
    }

    public PricingTier getPricingTier() throws AzureExecutionException {
        if (StringUtils.isEmpty(ctx.getPricingTier())) {
            return null;
        }
        final String pricingTier = ctx.getPricingTier();
        final ElasticPremiumPricingTier elasticPremiumPricingTier = ElasticPremiumPricingTier.fromString(pricingTier);
        return elasticPremiumPricingTier != null ? elasticPremiumPricingTier.toPricingTier()
                : AppServiceUtils.getPricingTierFromString(pricingTier);
    }

    private ArtifactHandler getArtifactHandler() throws AzureExecutionException {
        final ArtifactHandlerBase.Builder builder;

        final DeploymentType deploymentType = getDeploymentType();
        switch (deploymentType) {
            case MSDEPLOY:
                builder = new MSDeployArtifactHandlerImpl.Builder().functionAppName(this.ctx.getAppName());
                break;
            case FTP:
                builder = new FTPArtifactHandlerImpl.Builder();
                break;
            case ZIP:
                builder = new ZIPArtifactHandlerImpl.Builder();
                break;
            case RUN_FROM_BLOB:
                builder = new RunFromBlobArtifactHandlerImpl.Builder();
                break;
            case DOCKER:
                builder = new DockerArtifactHandler.Builder();
                break;
            case EMPTY:
            case RUN_FROM_ZIP:
                builder = new RunFromZipArtifactHandlerImpl.Builder();
                break;
            default:
                throw new AzureExecutionException(UNKNOWN_DEPLOYMENT_TYPE);
        }
        return builder.stagingDirectoryPath(this.ctx.getDeploymentStagingDirectoryPath()).build();
    }
}
