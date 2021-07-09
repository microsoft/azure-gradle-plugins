/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.handler;


import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.exception.ManagementException;
import com.google.common.base.Preconditions;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.IAppServiceContext;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.applicationinsights.ApplicationInsights;
import com.microsoft.azure.toolkit.lib.applicationinsights.ApplicationInsightsEntity;
import com.microsoft.azure.toolkit.lib.appservice.entity.FunctionEntity;
import com.microsoft.azure.toolkit.lib.appservice.model.DockerConfiguration;
import com.microsoft.azure.toolkit.lib.appservice.model.FunctionDeployType;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.appservice.model.Runtime;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppServicePlan;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppServiceUpdater;
import com.microsoft.azure.toolkit.lib.appservice.service.IFunctionApp;
import com.microsoft.azure.toolkit.lib.appservice.service.IFunctionAppBase;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.ResourceGroup;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import com.microsoft.azure.toolkit.lib.legacy.function.configurations.FunctionExtensionVersion;
import com.microsoft.azure.toolkit.lib.legacy.function.utils.FunctionUtils;
import com.microsoft.azure.toolkit.lib.resource.AzureGroup;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Deploy artifacts to target Azure Functions in Azure. If target Azure
 * Functions doesn't exist, it will be created.
 */
public class DeployHandler {
    private static final String PORTAL_URL_PATTERN = "%s/#@/resource%s";
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
    private static final String DEPLOY_FINISH =
        "Deployment done, you may access your resource through %s";
    private static final String FUNCTION_APP_CREATE_START = "The specified function app does not exist. " +
        "Creating a new function app...";
    private static final String CREATE_FUNCTION_APP = "Creating function app %s...";
    private static final String CREATE_FUNCTION_APP_DONE = "Successfully created function app %s.";
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
        "for this Function App. You can visit %s/#@/resource%s/overview to view your " +
        "Application Insights component.";
    private static final String APPLICATION_INSIGHTS_CREATE_FAILED = "Unable to create the Application Insights " +
        "for the Function App due to error %s. Please use the Azure Portal to manually create and configure the " +
        "Application Insights if needed.";
    private static final String INSTRUMENTATION_KEY_IS_NOT_VALID = "Instrumentation key is not valid, " +
        "please update the application insights configuration";
    private static final String CREATE_RESOURCE_GROUP = "Creating resource group %s in region %s...";
    private static final String CREATE_RESOURCE_GROUP_DONE = "Successfully created resource group %s.";

    private static final String FUNCTION_JAVA_VERSION_KEY = "functionJavaVersion";
    private static final String DISABLE_APP_INSIGHTS_KEY = "disableAppInsights";
    private static final String JVM_UP_TIME = "jvmUpTime";
    private static final String CREATE_NEW_APP_SERVICE_PLAN = "createNewAppServicePlan";
    private static final String CREATE_NEW_FUNCTION_APP = "isCreateNewFunctionApp";
    private static final String CREATE_APP_SERVICE_PLAN = "Creating app service plan...";
    private static final String CREATE_APP_SERVICE_PLAN_DONE = "Successfully created app service plan %s.";
    private static final String CREATE_NEW_RESOURCE_GROUP = "createNewResourceGroup";
    private static final String UPDATE_FUNCTION_APP = "Updating target Function App %s...";
    private static final String UPDATE_FUNCTION_DONE = "Successfully updated Function App %s.";
    private static final String SKIP_DEPLOYMENT_FOR_DOCKER_APP_SERVICE = "Skip deployment for docker app service";
    private static final String LOCAL_SETTINGS_FILE = "local.settings.json";
    private static final String DEPLOY = "deploy";
    private static final String RUNNING = "Running";
    private static final String APP_NAME_PATTERN = "[a-zA-Z0-9\\-]{2,60}";
    private static final String RESOURCE_GROUP_PATTERN = "[a-zA-Z0-9._\\-()]{1,90}";
    private static final String APP_SERVICE_PLAN_NAME_PATTERN = "[a-zA-Z0-9\\-]{1,40}";
    private static final String EMPTY_APP_NAME = "Please config the 'appName' in build.gradle.";
    private static final String INVALID_APP_NAME = "The 'appName' only allow alphanumeric characters, hyphens and cannot start or end in a hyphen.";
    private static final String EMPTY_RESOURCE_GROUP = "Please config the 'resourceGroup' in build.gradle.";
    private static final String INVALID_RESOURCE_GROUP_NAME = "The 'resourceGroup' only allow alphanumeric characters, periods, underscores, " +
        "hyphens and parenthesis and cannot end in a period.";
    private static final String INVALID_SERVICE_PLAN_NAME = "Invalid value for 'appServicePlanName', it need to match the pattern %s";
    private static final String INVALID_SERVICE_PLAN_RESOURCE_GROUP_NAME = "Invalid value for 'appServicePlanResourceGroup', " +
        "it only allow alphanumeric characters, periods, underscores, hyphens and parenthesis and cannot end in a period.";
    private static final String INVALID_REGION = "The value of 'region' is not supported, please correct it in build.gradle.";
    private static final String EMPTY_IMAGE_NAME = "Please specify the 'image' under 'runtime' section in build.gradle.";
    private static final String INVALID_OS = "The value of 'os' is not correct, supported values are: 'windows', 'linux' and 'docker'.";
    private static final String INVALID_JAVA_VERSION = "Unsupported value %s for 'javaVersion' in build.gradle";
    private static final String INVALID_PRICING_TIER = "Unsupported value %s for 'pricingTier' in build.gradle";
    private static final String FAILED_TO_LIST_TRIGGERS = "Deployment succeeded, but failed to list http trigger urls.";
    private static final int LIST_TRIGGERS_MAX_RETRY = 3;
    private static final String ARTIFACT_INCOMPATIBLE = "Your function app artifact compile version is higher than the java version in function host, " +
        "please downgrade the project compile version and try again.";
    private static final String HTTP_TRIGGER_URLS = "HTTP Trigger Urls:";
    private static final String NO_ANONYMOUS_HTTP_TRIGGER = "No anonymous HTTP Triggers found in deployed function app, skip list triggers.";
    private static final String AUTH_LEVEL = "authLevel";
    private static final String HTTP_TRIGGER = "httpTrigger";
    private static final String UNABLE_TO_LIST_NONE_ANONYMOUS_HTTP_TRIGGERS = "Some http trigger urls cannot be displayed " +
        "because they are non-anonymous. To access the non-anonymous triggers, please refer https://aka.ms/azure-functions-key.";
    private static final String SYNCING_TRIGGERS_AND_FETCH_FUNCTION_INFORMATION = "Syncing triggers and fetching function information (Attempt %d/%d)...";
    private static final int LIST_TRIGGERS_RETRY_PERIOD_IN_SECONDS = 10;
    private static final String NO_TRIGGERS_FOUNDED = "No triggers found in deployed function app, " +
        "please try to deploy the project again.";
    private final IAppServiceContext ctx;

    public DeployHandler(IAppServiceContext ctx) {
        Preconditions.checkNotNull(ctx);
        this.ctx = ctx;
    }

    public void execute() throws AzureExecutionException {

        TelemetryAgent.getInstance().addDefaultProperty(FUNCTION_JAVA_VERSION_KEY, String.valueOf(javaVersion()));
        TelemetryAgent.getInstance().addDefaultProperty(DISABLE_APP_INSIGHTS_KEY, String.valueOf(ctx.isDisableAppInsights()));
        doValidate();
        final IFunctionApp app = createOrUpdateFunctionApp();
        if (app == null) {
            throw new AzureExecutionException(
                String.format("Failed to get the function app with name: %s", ctx.getAppName()));
        }
        deployArtifact(app);
        listHTTPTriggerUrls(app);
    }

    /**
     * List anonymous HTTP Triggers url after deployment
     * @param target the target function
     */
    protected void listHTTPTriggerUrls(IFunctionApp target) {
        try {
            final List<FunctionEntity> triggers = listFunctions(target);
            final List<FunctionEntity> httpFunction = triggers.stream()
                .filter(function -> function.getTrigger() != null &&
                    StringUtils.equalsIgnoreCase(function.getTrigger().getType(), HTTP_TRIGGER))
                .collect(Collectors.toList());
            final List<FunctionEntity> anonymousTriggers = httpFunction.stream()
                .filter(bindingResource -> bindingResource.getTrigger() != null &&
                    StringUtils.equalsIgnoreCase(bindingResource.getTrigger().getProperty(AUTH_LEVEL), AuthorizationLevel.ANONYMOUS.toString()))
                .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(httpFunction) || CollectionUtils.isEmpty(anonymousTriggers)) {
                AzureMessager.getMessager().info(NO_ANONYMOUS_HTTP_TRIGGER);
                return;
            }
            AzureMessager.getMessager().info(HTTP_TRIGGER_URLS);
            anonymousTriggers.forEach(trigger -> AzureMessager.getMessager().info(String.format("\t %s : %s", trigger.getName(), trigger.getTriggerUrl())));
            if (anonymousTriggers.size() < httpFunction.size()) {
                AzureMessager.getMessager().info(UNABLE_TO_LIST_NONE_ANONYMOUS_HTTP_TRIGGERS);
            }
        } catch (RuntimeException e) {
            // show warning instead of exception for list triggers
            AzureMessager.getMessager().warning(FAILED_TO_LIST_TRIGGERS);
        }
    }

    private List<FunctionEntity> listFunctions(final IFunctionApp functionApp) {
        for (int i = 0; i < LIST_TRIGGERS_MAX_RETRY; i++) {
            try {
                AzureMessager.getMessager().info(String.format(SYNCING_TRIGGERS_AND_FETCH_FUNCTION_INFORMATION, i + 1, LIST_TRIGGERS_MAX_RETRY));
                functionApp.syncTriggers();
                final List<FunctionEntity> triggers = functionApp.listFunctions();
                if (CollectionUtils.isNotEmpty(triggers)) {
                    return triggers;
                }
            } catch (RuntimeException e) {
                // swallow service exception while list triggers
            }
            try {
                Thread.sleep(LIST_TRIGGERS_RETRY_PERIOD_IN_SECONDS * 1000);
            } catch (InterruptedException e) {
                // swallow interrupted exception
            }
        }
        throw new AzureToolkitRuntimeException(NO_TRIGGERS_FOUNDED);
    }

    protected void doValidate() throws AzureExecutionException {
        validateParameters();
        validateApplicationInsightsConfiguration();
        validateArtifactCompileVersion();
    }

    protected void validateParameters() {
        // app name
        final String appName = ctx.getAppName();
        if (StringUtils.isBlank(appName)) {
            throw new AzureToolkitRuntimeException(EMPTY_APP_NAME);
        }
        if (appName.startsWith("-") || !appName.matches(APP_NAME_PATTERN)) {
            throw new AzureToolkitRuntimeException(INVALID_APP_NAME);
        }

        final String resourceGroup = ctx.getResourceGroup();
        // resource group
        if (StringUtils.isBlank(resourceGroup)) {
            throw new AzureToolkitRuntimeException(EMPTY_RESOURCE_GROUP);
        }
        if (resourceGroup.endsWith(".") || !resourceGroup.matches(RESOURCE_GROUP_PATTERN)) {
            throw new AzureToolkitRuntimeException(INVALID_RESOURCE_GROUP_NAME);
        }

        final String appServicePlanName = ctx.getAppServicePlanName();
        // asp name & resource group
        if (StringUtils.isNotEmpty(appServicePlanName) && !appServicePlanName.matches(APP_SERVICE_PLAN_NAME_PATTERN)) {
            throw new AzureToolkitRuntimeException(String.format(INVALID_SERVICE_PLAN_NAME, APP_SERVICE_PLAN_NAME_PATTERN));
        }

        final String appServicePlanResourceGroup = ctx.getAppServicePlanResourceGroup();
        if (StringUtils.isNotEmpty(appServicePlanResourceGroup) &&
            (appServicePlanResourceGroup.endsWith(".") || !appServicePlanResourceGroup.matches(RESOURCE_GROUP_PATTERN))) {
            throw new AzureToolkitRuntimeException(INVALID_SERVICE_PLAN_RESOURCE_GROUP_NAME);
        }

        final String region = ctx.getRegion();
        if (StringUtils.isNotEmpty(region) && Region.fromName(region) == null) {
            // allow arbitrary region since the region can be changed
            AzureMessager.getMessager().warning(INVALID_REGION);
        }

        final GradleRuntimeConfig runtime = ctx.getRuntime();
        // os
        if (StringUtils.isNotEmpty(runtime.os()) && OperatingSystem.fromString(runtime.os()) == null) {
            throw new AzureToolkitRuntimeException(INVALID_OS);
        }
        // java version
        if (StringUtils.isNotEmpty(runtime.javaVersion()) && JavaVersion.fromString(runtime.javaVersion()) == JavaVersion.OFF) {
            throw new AzureToolkitRuntimeException(String.format(INVALID_JAVA_VERSION, runtime.javaVersion()));
        }

        final String pricingTier = ctx.getPricingTier();
        // pricing tier
        if (StringUtils.isNotEmpty(pricingTier) && PricingTier.fromString(pricingTier) == null) {
            throw new AzureToolkitRuntimeException(String.format(INVALID_PRICING_TIER, pricingTier));
        }
        // docker image
        if (OperatingSystem.fromString(runtime.os()) == OperatingSystem.DOCKER && StringUtils.isEmpty(runtime.image())) {
            throw new AzureToolkitRuntimeException(EMPTY_IMAGE_NAME);
        }

        validateApplicationInsightsConfiguration();
    }

    private IFunctionApp createOrUpdateFunctionApp() throws AzureExecutionException {
        final IFunctionApp app = getFunctionApp();
        if (!app.exists()) {
            return createFunctionApp(app);
        } else {
            return updateFunctionApp(app);
        }
    }

    private void deployArtifact(IFunctionAppBase target) throws AzureExecutionException {
        if (target.getRuntime().isDocker()) {
            AzureMessager.getMessager().info(SKIP_DEPLOYMENT_FOR_DOCKER_APP_SERVICE);
            return;
        }
        AzureMessager.getMessager().info(DEPLOY_START);
        String deploymentType = ctx.getDeploymentType();
        FunctionDeployType deployType;
        try {
            deployType = StringUtils.isEmpty(deploymentType) ? null : FunctionDeployType.fromString(deploymentType);
        } catch (AzureToolkitRuntimeException ex) {
            throw new AzureExecutionException(UNKNOWN_DEPLOYMENT_TYPE);
        }

        // For ftp deploy, we need to upload entire staging directory not the zipped package
        final File file = deployType == FunctionDeployType.FTP ? new File(ctx.getDeploymentStagingDirectoryPath()) : packageStagingDirectory();
        final RunnableWithException deployRunnable = deployType == null ? () -> target.deploy(file) : () -> target.deploy(file, deployType);
        executeWithTimeRecorder(deployRunnable, DEPLOY);
        // todo: check function status after deployment
        if (!StringUtils.equalsIgnoreCase(target.state(), RUNNING)) {
            target.start();
        }
        AzureMessager.getMessager().info(String.format(DEPLOY_FINISH, getResourcePortalUrl(target.id())));
    }

    private interface RunnableWithException {
        void run() throws Exception;
    }

    private void executeWithTimeRecorder(RunnableWithException operation, String name) throws AzureExecutionException {
        final long startTime = System.currentTimeMillis();
        try {
            operation.run();
        } catch (Exception e) {
            throw new AzureExecutionException(e.getMessage(), e);
        } finally {
            final long endTime = System.currentTimeMillis();
            TelemetryAgent.getInstance().addDefaultProperty(String.format("%s-cost", name), String.valueOf(endTime - startTime));
        }
    }

    private File packageStagingDirectory() {
        final File zipFile = new File(ctx.getDeploymentStagingDirectoryPath() + ".zip");
        final File stagingDirectory = new File(ctx.getDeploymentStagingDirectoryPath());

        ZipUtil.pack(stagingDirectory, zipFile);
        ZipUtil.removeEntry(zipFile, LOCAL_SETTINGS_FILE);
        return zipFile;
    }

    public String getResourcePortalUrl(String id) {
        final AzureEnvironment environment = Azure.az(AzureAccount.class).account().getEnvironment();
        return String.format(PORTAL_URL_PATTERN, getPortalUrl(environment), id);
    }

    private IFunctionApp createFunctionApp(IFunctionApp functionApp) throws AzureExecutionException {
        AzureMessager.getMessager().info(FUNCTION_APP_CREATE_START);
        TelemetryAgent.getInstance().addDefaultProperty(CREATE_NEW_FUNCTION_APP, String.valueOf(true));

        final ResourceGroup resourceGroup = getOrCreateResourceGroup(ctx.getResourceGroup(), ctx.getRegion());
        final IAppServicePlan appServicePlan = getOrCreateAppServicePlan();
        AzureMessager.getMessager().info(String.format(CREATE_FUNCTION_APP, ctx.getAppName()));
        final Runtime runtime = getRuntimeOrDefault();
        final Map appSettings = getAppSettingsWithDefaultValue();
        // get/create ai instances only if user didn't specify ai connection string in app settings
        bindApplicationInsights(appSettings, true);
        final IFunctionApp result = (IFunctionApp) functionApp.create().withName(ctx.getAppName())
            .withResourceGroup(resourceGroup.getName())
            .withPlan(appServicePlan.id())
            .withRuntime(runtime)
            .withDockerConfiguration(getDockerConfiguration())
            .withAppSettings(appSettings)
            .commit();
        AzureMessager.getMessager().info(String.format(CREATE_FUNCTION_APP_DONE, result.name()));
        return result;
    }

    private DockerConfiguration getDockerConfiguration() {
        GradleRuntimeConfig runtime = ctx.getRuntime();
        final OperatingSystem os = Optional.ofNullable(runtime.os()).map(OperatingSystem::fromString).orElse(null);
        if (os != OperatingSystem.DOCKER) {
            return null;
        }
        return DockerConfiguration.builder()
            .image(runtime.image())
            .registryUrl(runtime.registryUrl())
            .userName(runtime.username())
            .password(runtime.password())
            .build();
    }

    private IAppServicePlan getOrCreateAppServicePlan() {
        final String servicePlanName = StringUtils.isEmpty(ctx.getAppServicePlanName()) ?
            String.format("asp-%s", ctx.getAppName()) : ctx.getAppServicePlanName();
        final String servicePlanGroup = StringUtils.firstNonBlank(ctx.getAppServicePlanResourceGroup(), ctx.getResourceGroup());
        getOrCreateResourceGroup(servicePlanGroup, ctx.getRegion());
        final IAppServicePlan appServicePlan = ctx.getOrCreateAzureAppServiceClient().appServicePlan(servicePlanGroup, servicePlanName);
        if (!appServicePlan.exists()) {
            AzureMessager.getMessager().info(CREATE_APP_SERVICE_PLAN);
            TelemetryAgent.getInstance().addDefaultProperty(CREATE_NEW_APP_SERVICE_PLAN, String.valueOf(true));
            appServicePlan.create()
                .withName(servicePlanName)
                .withResourceGroup(servicePlanGroup)
                .withRegion(getParsedRegion())
                .withPricingTier(getParsedPricingTier())
                .withOperatingSystem(getRuntimeOrDefault().getOperatingSystem())
                .commit();
            AzureMessager.getMessager().info(String.format(CREATE_APP_SERVICE_PLAN_DONE, appServicePlan.name()));
        }
        return appServicePlan;
    }

    private Runtime getRuntimeOrDefault() {
        final OperatingSystem os = Optional.ofNullable(ctx.getRuntime().os()).map(OperatingSystem::fromString).orElse(OperatingSystem.WINDOWS);
        final JavaVersion javaVersion = Optional.ofNullable(ctx.getRuntime().javaVersion())
            .map(JavaVersion::fromString).orElse(JavaVersion.JAVA_8);
        return Runtime.getRuntime(os, WebContainer.JAVA_OFF, javaVersion);
    }

    private Region getParsedRegion() {
        return Optional.ofNullable(ctx.getRegion()).map(Region::fromName).orElse(Region.US_WEST);
    }

    private PricingTier getParsedPricingTier() {
        String pricingTier = ctx.getPricingTier();
        if (StringUtils.isEmpty(pricingTier)) {
            return PricingTier.CONSUMPTION;
        }
        return Optional.ofNullable(PricingTier.fromString(pricingTier))
            .orElseThrow(() -> new AzureToolkitRuntimeException(String.format("Invalid pricing tier %s", pricingTier)));
    }

    private static ResourceGroup getOrCreateResourceGroup(String resourceGroupName, String region) {
        try {
            return Azure.az(AzureGroup.class).getByName(resourceGroupName);
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() != 404) {
                throw e;
            }
            AzureMessager.getMessager().info(String.format(CREATE_RESOURCE_GROUP, resourceGroupName, region));
            TelemetryAgent.getInstance().addDefaultProperty(CREATE_NEW_RESOURCE_GROUP, String.valueOf(true));
            final ResourceGroup result = Azure.az(AzureGroup.class).create(resourceGroupName, region);
            AzureMessager.getMessager().info(String.format(CREATE_RESOURCE_GROUP_DONE, result.getName()));
            return result;
        }
    }

    private Map<String, String> recordJvmUpTime(Map<String, String> properties) {
        final long jvmUpTime = ManagementFactory.getRuntimeMXBean().getUptime();
        properties.put(JVM_UP_TIME, String.valueOf(jvmUpTime));
        return properties;
    }

    private IFunctionApp updateFunctionApp(final IFunctionApp functionApp) throws AzureExecutionException {
        // Work around of https://github.com/Azure/azure-sdk-for-java/issues/1755
        // update app service plan
        AzureMessager.getMessager().info(String.format(UPDATE_FUNCTION_APP, functionApp.name()));
        final IAppServicePlan currentPlan = functionApp.plan();
        IAppServicePlan targetServicePlan = StringUtils.isEmpty(ctx.getAppServicePlanName()) ? currentPlan :
            ctx.getOrCreateAzureAppServiceClient().appServicePlan(
                StringUtils.firstNonBlank(ctx.getAppServicePlanResourceGroup(), ctx.getResourceGroup())
                , ctx.getAppServicePlanName());
        if (!targetServicePlan.exists()) {
            targetServicePlan = getOrCreateAppServicePlan();
        } else if (StringUtils.isNotEmpty(ctx.getPricingTier())) {
            targetServicePlan.update().withPricingTier(getParsedPricingTier()).commit();
        }

        final Map<String, String> appSettings = getAppSettingsWithDefaultValue();

        final IAppServiceUpdater<? extends IFunctionApp> update = functionApp.update();
        if (ctx.isDisableAppInsights()) {
            // Remove App Insights connection when `disableAppInsights` set to true
            // Need to call `withoutAppSetting` as withAppSettings will only not remove parameters
            update.withoutAppSettings(APPINSIGHTS_INSTRUMENTATION_KEY);
        } else {
            bindApplicationInsights(appSettings, false);
        }

        final IFunctionApp result = update.withPlan(targetServicePlan.id())
            .withRuntime(getRuntime())
            .withDockerConfiguration(getDockerConfiguration())
            .withAppSettings(appSettings)
            .commit();
        AzureMessager.getMessager().info(String.format(UPDATE_FUNCTION_DONE, functionApp.name()));
        return result;
    }

    private Runtime getRuntime() {
        final GradleRuntimeConfig runtime = ctx.getRuntime();
        if (StringUtils.isEmpty(runtime.os()) && StringUtils.isEmpty(runtime.javaVersion())) {
            return null;
        }
        final OperatingSystem os = OperatingSystem.fromString(runtime.os());
        final JavaVersion javaVersion = JavaVersion.fromString(runtime.javaVersion());
        return Runtime.getRuntime(os, WebContainer.JAVA_OFF, javaVersion);
    }

    private void configureAppSettings(final Consumer<Map<String, String>> withAppSettings, final Map<String, String> appSettings) {
        if (appSettings != null && !appSettings.isEmpty()) {
            withAppSettings.accept(appSettings);
        }
    }

    protected void validateArtifactCompileVersion() throws AzureExecutionException {
        final Runtime runtime = getRuntimeOrDefault();
        if (runtime.isDocker()) {
            return;
        }
        final ComparableVersion runtimeVersion = new ComparableVersion(runtime.getJavaVersion().getValue());
        final File file = this.ctx.getProject().getArtifactFile().toFile();
        final ComparableVersion artifactVersion = new ComparableVersion(Utils.getArtifactCompileVersion(file));
        if (runtimeVersion.compareTo(artifactVersion) < 0) {
            throw new AzureExecutionException(ARTIFACT_INCOMPATIBLE);
        }
    }

    private JavaVersion javaVersion() {
        return Objects.isNull(ctx.getRuntime()) ? null : JavaVersion.fromString(ctx.getRuntime().javaVersion());
    }

    public IFunctionApp getFunctionApp() {
        return ctx.getOrCreateAzureAppServiceClient().functionApp(ctx.getResourceGroup(), ctx.getAppName());
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
            AzureMessager.getMessager().info(settingIsEmptyMessage);
            result.put(settingName, settingValue);
        }
    }

    private void overrideDefaultAppSetting(Map<String, String> result, String settingName, String settingIsEmptyMessage,
                                           String settingValue, String changeSettingMessage) {
        final String setting = result.get(settingName);
        if (StringUtils.isEmpty(setting)) {
            AzureMessager.getMessager().info(settingIsEmptyMessage);
        } else if (!setting.equals(settingValue)) {
            AzureMessager.getMessager().warning(String.format(changeSettingMessage, setting));
        }
        result.put(settingName, settingValue);
    }

    /**
     * Binding Function App with Application Insights
     * Will follow the below sequence appInsightsKey -&gt; appInsightsInstance -&gt; Create New AI Instance (Function creation only)
     *
     * @param appSettings App settings map
     * @param isCreation  Define the stage of function app, as we only create ai instance by default when create new function apps
     * @throws AzureExecutionException When there are conflicts in configuration or meet errors while finding/creating application insights instance
     */
    private void bindApplicationInsights(Map<String, String> appSettings, boolean isCreation) throws AzureExecutionException {
        // Skip app insights creation when user specify ai connection string in app settings
        if (appSettings.containsKey(APPINSIGHTS_INSTRUMENTATION_KEY)) {
            return;
        }
        final String instrumentationKey;
        if (StringUtils.isNotEmpty(ctx.getAppInsightsKey())) {
            instrumentationKey = ctx.getAppInsightsKey();
            if (!Utils.isGUID(instrumentationKey)) {
                throw new AzureExecutionException(INSTRUMENTATION_KEY_IS_NOT_VALID);
            }
        } else {
            final ApplicationInsightsEntity applicationInsightsComponent = getOrCreateApplicationInsights(isCreation);
            instrumentationKey = applicationInsightsComponent == null ? null : applicationInsightsComponent.getInstrumentationKey();
        }
        if (StringUtils.isNotEmpty(instrumentationKey)) {
            appSettings.put(APPINSIGHTS_INSTRUMENTATION_KEY, instrumentationKey);
        }
    }

    private void validateApplicationInsightsConfiguration() {
        if (ctx.isDisableAppInsights() && (StringUtils.isNotEmpty(ctx.getAppInsightsKey()) || StringUtils.isNotEmpty(ctx.getAppInsightsInstance()))) {
            throw new AzureToolkitRuntimeException(APPLICATION_INSIGHTS_CONFIGURATION_CONFLICT);
        }
    }

    private ApplicationInsightsEntity getOrCreateApplicationInsights(boolean enableCreation) {
        return StringUtils.isNotEmpty(ctx.getAppInsightsInstance()) ? getApplicationInsights(ctx.getAppInsightsInstance()) :
            enableCreation ? createApplicationInsights(ctx.getAppName()) : null;
    }

    private ApplicationInsightsEntity getApplicationInsights(String appInsightsInstance) {
        ApplicationInsightsEntity resource;
        try {
            resource = Azure.az(ApplicationInsights.class).get(ctx.getResourceGroup(), appInsightsInstance);
        } catch (ManagementException e) {
            resource = null;
        }
        if (resource == null) {
            AzureMessager.getMessager().warning(String.format(FAILED_TO_GET_APPLICATION_INSIGHTS, appInsightsInstance, ctx.getResourceGroup()));
            return createApplicationInsights(appInsightsInstance);
        }
        return resource;
    }

    private ApplicationInsightsEntity createApplicationInsights(String name) {
        if (ctx.isDisableAppInsights()) {
            AzureMessager.getMessager().info(SKIP_CREATING_APPLICATION_INSIGHTS);
            return null;
        }
        try {
            AzureMessager.getMessager().info(APPLICATION_INSIGHTS_CREATE_START);
            final AzureEnvironment environment = Azure.az(AzureAccount.class).account().getEnvironment();
            final ApplicationInsightsEntity resource = Azure.az(ApplicationInsights.class)
                .create(ctx.getResourceGroup(), Region.fromName(ctx.getRegion()), name);
            AzureMessager.getMessager().info(String.format(APPLICATION_INSIGHTS_CREATED, resource.getName(), getPortalUrl(environment), resource.getId()));
            return resource;
        } catch (Exception e) {
            AzureMessager.getMessager().warning(String.format(APPLICATION_INSIGHTS_CREATE_FAILED, e.getMessage()));
            return null;
        }
    }

    private static String getPortalUrl(AzureEnvironment azureEnvironment) {
        if (azureEnvironment == null || azureEnvironment == AzureEnvironment.AZURE) {
            return "https://ms.portal.azure.com";
        }
        if (azureEnvironment == AzureEnvironment.AZURE_CHINA) {
            return "https://portal.azure.cn";
        }
        return azureEnvironment.getPortal();
    }
}
