/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.handler;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.microsoft.azure.common.Utils;
import com.microsoft.azure.common.applicationinsights.ApplicationInsightsManager;
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
import com.microsoft.azure.management.applicationinsights.v2015_05_01.ApplicationInsightsComponent;
import com.microsoft.azure.management.appservice.FunctionApp;
import com.microsoft.azure.management.appservice.FunctionApp.DefinitionStages.WithCreate;
import com.microsoft.azure.management.appservice.FunctionApp.Update;
import com.microsoft.azure.management.appservice.JavaVersion;
import com.microsoft.azure.management.appservice.PricingTier;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.plugin.functions.gradle.GradleDockerCredentialProvider;
import com.microsoft.azure.plugin.functions.gradle.IAppServiceContext;
import com.microsoft.azure.plugin.functions.gradle.configuration.GradleRuntimeConfiguration;
import com.microsoft.azure.plugin.functions.gradle.telemetry.TelemetryAgent;
import com.microsoft.azure.tools.auth.model.AzureCredentialWrapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
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
    private static final String FUNCTIONS_WORKER_RUNTIME_NAME = "FUNCTIONS_WORKER_RUNTIME";
    private static final String FUNCTIONS_WORKER_RUNTIME_VALUE = "java";
    private static final String SET_FUNCTIONS_WORKER_RUNTIME = "Set function worker runtime to java";
    private static final String CHANGE_FUNCTIONS_WORKER_RUNTIME = "Function worker runtime doesn't " +
        "meet the requirement, change it from %s to java";
    private static final String FUNCTIONS_EXTENSION_VERSION_NAME = "FUNCTIONS_EXTENSION_VERSION";
    private static final String FUNCTIONS_EXTENSION_VERSION_VALUE = "~3";
    private static final String SET_FUNCTIONS_EXTENSION_VERSION = "Functions extension version " +
            "isn't configured, setting up the default value";
    private static final String DEPLOY_START = "Trying to deploy the function app...";
    private static final String DEPLOY_FINISH = "Successfully deployed the function app at https://%s.azurewebsites.net";
    private static final String FUNCTION_APP_CREATE_START = "The specified function app does not exist. " +
            "Creating a new function app...";
    private static final String FUNCTION_APP_CREATED = "Successfully created the function app: %s";
    private static final String FUNCTION_APP_UPDATE = "Updating the specified function app...";
    private static final String FUNCTION_APP_UPDATE_DONE = "Successfully updated the function app %s.";
    private static final String UNKNOWN_DEPLOYMENT_TYPE = "The value of <deploymentType> is unknown, supported values are: " +
            "ftp, zip, msdeploy, run_from_blob and run_from_zip.";
    private static final String APPINSIGHTS_INSTRUMENTATION_KEY = "APPINSIGHTS_INSTRUMENTATIONKEY";
    private static final String APPLICATION_INSIGHTS_CONFIGURATION_CONFLICT = "Contradictory configurations for application insights," +
            " specify 'appInsightsKey' or 'appInsightsInstance' if you want to enable it, and specify " +
            "'disableAppInsights=true' if you want to disable it.";
    private static final String FAILED_TO_GET_APPLICATION_INSIGHTS = "The application insights %s cannot be found, " +
            "will create it in resource group %s.";
    private static final String SKIP_CREATING_APPLICATION_INSIGHTS = "Skip creating application insights";
    private static final String APPLICATION_INSIGHTS_CREATE_START = "Creating application insights...";
    private static final String APPLICATION_INSIGHTS_CREATED = "Successfully created the application insights %s " +
            "for this Function App. You can visit https://portal.azure.com/#resource%s/overview to view your " +
            "Application Insights component.";
    private static final String APPLICATION_INSIGHTS_CREATE_FAILED = "Unable to create the Application Insights " +
            "for the Function App due to error %s. Please use the Azure Portal to manually create and configure the " +
            "Application Insights if needed.";
    private static final String INSTRUMENTATION_KEY_IS_NOT_VALID = "Instrumentation key is not valid, " +
            "please update the application insights configuration";
    private static final OperatingSystemEnum DEFAULT_OS = OperatingSystemEnum.Windows;
    private static final String FUNCTION_JAVA_VERSION_KEY = "functionJavaVersion";
    private static final String DISABLE_APP_INSIGHTS_KEY = "disableAppInsights";

    private IAppServiceContext ctx;

    public DeployHandler(IAppServiceContext ctx) {
        Preconditions.checkNotNull(ctx);
        this.ctx = ctx;
    }

    public void execute() throws AzureExecutionException {

        TelemetryAgent.instance.addDefaultProperties(FUNCTION_JAVA_VERSION_KEY, String.valueOf(getJavaVersion()));
        TelemetryAgent.instance.addDefaultProperties(DISABLE_APP_INSIGHTS_KEY, String.valueOf(ctx.isDisableAppInsights()));
        final FunctionApp app = createOrUpdateFunctionApp();
        if (app == null) {
            throw new AzureExecutionException(
                    String.format("Failed to get the function app with name: %s", ctx.getAppName()));
        }

        final DeployTarget deployTarget = new DeployTarget(app, DeployTargetType.FUNCTION);
        Log.prompt(DEPLOY_START);
        getArtifactHandler().publish(deployTarget);
        Log.prompt(String.format(DEPLOY_FINISH, ctx.getAppName()));
    }

    private FunctionApp createOrUpdateFunctionApp() throws AzureExecutionException {
        final FunctionApp app = getFunctionApp();
        if (app == null) {
            return createFunctionApp();
        } else {
            return updateFunctionApp(app);
        }
    }

    private FunctionApp createFunctionApp() throws AzureExecutionException {
        Log.prompt(FUNCTION_APP_CREATE_START);
        validateApplicationInsightsConfiguration();
        final Map appSettings = getAppSettingsWithDefaultValue();
        // get/create ai instances only if user didn't specify ai connection string in app settings
        bindApplicationInsights(appSettings, true);
        final FunctionRuntimeHandler runtimeHandler = getFunctionRuntimeHandler();
        final WithCreate withCreate = runtimeHandler.defineAppWithRuntime();
        configureAppSettings(withCreate::withAppSettings, appSettings);
        final FunctionApp appCreated = withCreate.create();
        Log.prompt(String.format(FUNCTION_APP_CREATED, ctx.getAppName()));
        return appCreated;
    }

    private FunctionApp updateFunctionApp(final FunctionApp app) throws AzureExecutionException {
        Log.prompt(FUNCTION_APP_UPDATE);
        // Work around of https://github.com/Azure/azure-sdk-for-java/issues/1755
        app.inner().withTags(null);
        final FunctionRuntimeHandler runtimeHandler = getFunctionRuntimeHandler();
        runtimeHandler.updateAppServicePlan(app);
        final Update update = runtimeHandler.updateAppRuntime(app);
        validateApplicationInsightsConfiguration();
        final Map appSettings = getAppSettingsWithDefaultValue();
        if (ctx.isDisableAppInsights()) {
            // Remove App Insights connection when `disableAppInsights` set to true
            // Need to call `withoutAppSetting` as withAppSettings will only not remove parameters
            update.withoutAppSetting(APPINSIGHTS_INSTRUMENTATION_KEY);
        } else {
            bindApplicationInsights(appSettings, false);
        }
        configureAppSettings(update::withAppSettings, appSettings);
        final FunctionApp appUpdated = update.apply();
        Log.prompt(String.format(FUNCTION_APP_UPDATE_DONE, ctx.getAppName()));
        return appUpdated;
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
                // TODO: refactor RuntimeConfiguration to allow set plain username/password
                final GradleRuntimeConfiguration runtime =
                    (GradleRuntimeConfiguration) (Optional.fromNullable(ctx.getRuntime()).or(new GradleRuntimeConfiguration()));
                builder = new DockerFunctionRuntimeHandler.Builder().image(runtime.getImage())
                        .dockerCredentialProvider(StringUtils.isNotBlank(runtime.getUsername()) ?
                                new GradleDockerCredentialProvider(runtime.getUsername(), runtime.getPassword())
                                : null)
                        .registryUrl(runtime.getRegistryUrl());
                break;
            default:
                throw new AzureExecutionException(String.format("Unsupported runtime %s", os));
        }
        return builder.appName(ctx.getAppName()).resourceGroup(ctx.getResourceGroup())
                .runtime(Optional.fromNullable(ctx.getRuntime()).or(new RuntimeConfiguration()))
                .region(Region.fromName(ctx.getRegion())).pricingTier(getPricingTier())
                .servicePlanName(ctx.getAppServicePlanName())
                .servicePlanResourceGroup(ctx.getAppServicePlanResourceGroup())
                // since PR https://github.com/microsoft/azure-maven-plugins/pull/1116, java version is required
                .javaVersion(getJavaVersion())
                .functionExtensionVersion(getFunctionExtensionVersion()).azure(this.ctx.getAzureClient()).build();
    }

    private OperatingSystemEnum getOsEnum() throws AzureExecutionException {
        final RuntimeConfiguration runtime = ctx.getRuntime();
        if (runtime != null && StringUtils.isNotBlank(runtime.getOs())) {
            return Utils.parseOperationSystem(runtime.getOs());
        }
        return DEFAULT_OS;
    }

    private JavaVersion getJavaVersion() {
        return FunctionUtils.parseJavaVersion(Objects.isNull(ctx.getRuntime()) ? null : ctx.getRuntime().getJavaVersion());
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
        return ctx.getAzureClient().appServices().functionApps().getByResourceGroup(ctx.getResourceGroup(), ctx.getAppName());
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

    /**
     * Binding Function App with Application Insights
     * Will follow the below sequence appInsightsKey -> appInsightsInstance -> Create New AI Instance (Function creation only)
     * @param appSettings App settings map
     * @param isCreation Define the stage of function app, as we only create ai instance by default when create new function apps
     * @throws AzureExecutionException When there are conflicts in configuration or meet errors while finding/creating application insights instance
     */
    private void bindApplicationInsights(Map appSettings, boolean isCreation) throws AzureExecutionException {
        // Skip app insights creation when user specify ai connection string in app settings
        if (appSettings.containsKey(APPINSIGHTS_INSTRUMENTATION_KEY)) {
            return;
        }
        String instrumentationKey = null;
        if (StringUtils.isNotEmpty(ctx.getAppInsightsKey())) {
            instrumentationKey = ctx.getAppInsightsKey();
            if (!Utils.isGUID(instrumentationKey)) {
                throw new AzureExecutionException(INSTRUMENTATION_KEY_IS_NOT_VALID);
            }
        } else {
            final ApplicationInsightsComponent applicationInsightsComponent = getOrCreateApplicationInsights(isCreation);
            instrumentationKey = applicationInsightsComponent == null ? null : applicationInsightsComponent.instrumentationKey();
        }
        if (StringUtils.isNotEmpty(instrumentationKey)) {
            appSettings.put(APPINSIGHTS_INSTRUMENTATION_KEY, instrumentationKey);
        }
    }

    private void validateApplicationInsightsConfiguration() throws AzureExecutionException {
        if (ctx.isDisableAppInsights() && (StringUtils.isNotEmpty(ctx.getAppInsightsKey()) || StringUtils.isNotEmpty(ctx.getAppInsightsInstance()))) {
            throw new AzureExecutionException(APPLICATION_INSIGHTS_CONFIGURATION_CONFLICT);
        }
    }

    private ApplicationInsightsComponent getOrCreateApplicationInsights(boolean enableCreation) throws AzureExecutionException {
        final String subscriptionId = ctx.getAzureClient().subscriptionId();
        final AzureCredentialWrapper credentials = ctx.getAzureCredentialWrapper();
        final ApplicationInsightsManager applicationInsightsManager = new ApplicationInsightsManager(credentials.getAzureTokenCredentials(),
                subscriptionId, TelemetryAgent.instance.getUserAgent());
        final String appInsightsInstance = ctx.getAppInsightsInstance();
        return StringUtils.isNotEmpty(appInsightsInstance) ?
                getApplicationInsights(applicationInsightsManager, appInsightsInstance) :
                enableCreation ? createApplicationInsights(applicationInsightsManager, ctx.getAppName()) : null;
    }

    private ApplicationInsightsComponent getApplicationInsights(ApplicationInsightsManager applicationInsightsManager,
                                                                String appInsightsInstance) {
        final ApplicationInsightsComponent resource = applicationInsightsManager.getApplicationInsightsInstance(ctx.getResourceGroup(),
                appInsightsInstance);
        if (resource == null) {
            Log.prompt(String.format(FAILED_TO_GET_APPLICATION_INSIGHTS, appInsightsInstance, ctx.getResourceGroup()));
            return createApplicationInsights(applicationInsightsManager, appInsightsInstance);
        }
        return resource;
    }

    private ApplicationInsightsComponent createApplicationInsights(ApplicationInsightsManager applicationInsightsManager, String name) {
        if (ctx.isDisableAppInsights()) {
            Log.prompt(SKIP_CREATING_APPLICATION_INSIGHTS);
            return null;
        }
        try {
            Log.prompt(APPLICATION_INSIGHTS_CREATE_START);
            final ApplicationInsightsComponent resource = applicationInsightsManager.createApplicationInsights(ctx.getResourceGroup(), name, ctx.getRegion());
            Log.prompt(String.format(APPLICATION_INSIGHTS_CREATED, resource.name(), resource.id()));
            return resource;
        } catch (Exception e) {
            Log.prompt(String.format(APPLICATION_INSIGHTS_CREATE_FAILED, e.getMessage()));
            return null;
        }
    }

}
