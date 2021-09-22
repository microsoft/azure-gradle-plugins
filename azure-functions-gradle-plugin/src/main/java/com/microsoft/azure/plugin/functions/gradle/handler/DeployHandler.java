/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.handler;


import com.azure.core.management.AzureEnvironment;
import com.google.common.base.Preconditions;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.IAppServiceContext;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.FunctionAppConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.entity.FunctionEntity;
import com.microsoft.azure.toolkit.lib.appservice.model.FunctionDeployType;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.service.IFunctionApp;
import com.microsoft.azure.toolkit.lib.appservice.service.IFunctionAppBase;
import com.microsoft.azure.toolkit.lib.appservice.task.CreateOrUpdateFunctionAppTask;
import com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils.fromAppService;
import static com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils.mergeAppServiceConfig;

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
    private static final String CREATE_NEW_RESOURCE_GROUP = "createNewResourceGroup";
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
    private static final int LIST_TRIGGERS_MAX_RETRY = 5;
    private static final String ARTIFACT_INCOMPATIBLE = "Your function app artifact compile version is higher than the java version in function host, " +
        "please downgrade the project compile version and try again.";
    private static final String HTTP_TRIGGER_URLS = "HTTP Trigger Urls:";
    private static final String NO_ANONYMOUS_HTTP_TRIGGER = "No anonymous HTTP Triggers found in deployed function app, skip list triggers.";
    private static final String AUTH_LEVEL = "authLevel";
    private static final String HTTP_TRIGGER = "httpTrigger";
    private static final String UNABLE_TO_LIST_NONE_ANONYMOUS_HTTP_TRIGGERS = "Some http trigger urls cannot be displayed " +
        "because they are non-anonymous. To access the non-anonymous triggers, please refer https://aka.ms/azure-functions-key.";
    private static final String SYNCING_TRIGGERS = "Syncing triggers and fetching function information";
    private static final String SYNCING_TRIGGERS_WITH_RETRY = "Syncing triggers and fetching function information (Attempt {0}/{1})...";
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
        final IFunctionApp app = (IFunctionApp) createOrUpdateFunctionApp();
        if (app == null) {
            throw new AzureExecutionException(
                String.format("Failed to get the function app with name: %s", ctx.getAppName()));
        }
        deployArtifact(app);
        listHTTPTriggerUrls(app);
    }

    private RuntimeConfig getRuntimeConfig() {
        final GradleRuntimeConfig runtime = ctx.getRuntime();
        if (runtime == null) {
            return null;
        }
        final OperatingSystem os = Optional.ofNullable(runtime.os()).map(OperatingSystem::fromString).orElse(null);
        final JavaVersion javaVersion = Optional.ofNullable(runtime.javaVersion()).map(JavaVersion::fromString).orElse(null);
        final RuntimeConfig result = new RuntimeConfig().os(os).javaVersion(javaVersion).webContainer(WebContainer.JAVA_OFF)
            .image(runtime.image()).registryUrl(runtime.registryUrl());
        result.username(runtime.username()).password(runtime.password());
        return result;
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
        final int[] count = {0};
        return Mono.fromCallable(() -> {
            final AzureString message = count[0]++ == 0 ?
                    AzureString.fromString(SYNCING_TRIGGERS) : AzureString.format(SYNCING_TRIGGERS_WITH_RETRY, count[0], LIST_TRIGGERS_MAX_RETRY);
            AzureMessager.getMessager().info(message);
            return Optional.ofNullable(functionApp.listFunctions(true))
                    .filter(CollectionUtils::isNotEmpty)
                    .orElseThrow(() -> new AzureToolkitRuntimeException(NO_TRIGGERS_FOUNDED));
        }).subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.fixedDelay(LIST_TRIGGERS_MAX_RETRY - 1, Duration.ofSeconds(LIST_TRIGGERS_RETRY_PERIOD_IN_SECONDS))).block();
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

    private IFunctionAppBase<?> createOrUpdateFunctionApp() throws AzureExecutionException {
        final IFunctionApp app = getFunctionApp();
        final FunctionAppConfig functionConfig = (FunctionAppConfig) new FunctionAppConfig()
            .disableAppInsights(ctx.isDisableAppInsights())
            .appInsightsKey(ctx.getAppInsightsKey())
            .appInsightsInstance(ctx.getAppInsightsInstance())
            .subscriptionId(ctx.getSubscription())
            .resourceGroup(ctx.getResourceGroup())
            .appName(ctx.getAppName())
            .servicePlanName(ctx.getAppServicePlanName())
            .servicePlanResourceGroup(ctx.getAppServicePlanResourceGroup())
            .deploymentSlotName(null) // gradle function plugin doesn't support deploy slot now
            .deploymentSlotConfigurationSource(null)
            .pricingTier(getParsedPricingTier())
            .region(getParsedRegion())
            .runtime(getRuntimeConfig())
            .appSettings(ctx.getAppSettings());

        boolean createFunctionApp = app.exists();
        final AppServiceConfig defaultConfig = createFunctionApp ? buildDefaultConfig(functionConfig.subscriptionId(),
            functionConfig.resourceGroup(), functionConfig.appName()) : fromAppService(app, app.plan());
        mergeAppServiceConfig(functionConfig, defaultConfig);
        if (!createFunctionApp && !functionConfig.disableAppInsights()) {
            // fill ai key from existing app settings
            functionConfig.appInsightsKey(app.entity().getAppSettings().get(CreateOrUpdateFunctionAppTask.APPINSIGHTS_INSTRUMENTATION_KEY));
        }
        if (!createFunctionApp) {
            return createFunctionApp(functionConfig);
        } else {
            return updateFunctionApp(functionConfig);
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
            TelemetryAgent.getInstance().addDefaultProperty(JVM_UP_TIME, String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime()));
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

    private IFunctionAppBase<?> createFunctionApp(FunctionAppConfig functionConfig) {
        AzureMessager.getMessager().info(FUNCTION_APP_CREATE_START);
        return new CreateOrUpdateFunctionAppTask(functionConfig).execute();
    }

    private AppServiceConfig buildDefaultConfig(String subscriptionId, String resourceGroup, String appName) {
        // get java version according to project java version
        JavaVersion javaVersion = org.gradle.api.JavaVersion.current().isJava8() ? JavaVersion.JAVA_8 : JavaVersion.JAVA_11;
        return AppServiceConfigUtils.buildDefaultFunctionConfig(subscriptionId, resourceGroup, appName, javaVersion);
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

    private IFunctionApp updateFunctionApp(FunctionAppConfig functionAppConfig) {
        return (IFunctionApp) new CreateOrUpdateFunctionAppTask(functionAppConfig).execute();
    }

    protected void validateArtifactCompileVersion() throws AzureExecutionException {
        final RuntimeConfig runtime = getRuntimeConfig();
        if (runtime.os() == OperatingSystem.DOCKER) {
            return;
        }
        final ComparableVersion runtimeVersion = new ComparableVersion(runtime.javaVersion().getValue());
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

    private void validateApplicationInsightsConfiguration() {
        if (ctx.isDisableAppInsights() && (StringUtils.isNotEmpty(ctx.getAppInsightsKey()) || StringUtils.isNotEmpty(ctx.getAppInsightsInstance()))) {
            throw new AzureToolkitRuntimeException(APPLICATION_INSIGHTS_CONFIGURATION_CONFLICT);
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
