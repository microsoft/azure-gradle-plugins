/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.handler;


import com.azure.core.management.AzureEnvironment;
import com.google.common.base.Preconditions;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.FunctionAppConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.entity.FunctionEntity;
import com.microsoft.azure.toolkit.lib.appservice.function.AzureFunctions;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionApp;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionAppBase;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionsServiceSubscription;
import com.microsoft.azure.toolkit.lib.appservice.function.core.AzureFunctionsAnnotationConstants;
import com.microsoft.azure.toolkit.lib.appservice.model.Runtime;
import com.microsoft.azure.toolkit.lib.appservice.model.*;
import com.microsoft.azure.toolkit.lib.appservice.plan.AppServicePlan;
import com.microsoft.azure.toolkit.lib.appservice.task.CreateOrUpdateFunctionAppTask;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.operation.OperationContext;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.microsoft.azure.toolkit.lib.appservice.model.FunctionAppLinuxRuntime.*;
import static com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils.fromFunctionApp;
import static com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils.mergeAppServiceConfig;

/**
 * Deploy artifacts to target Azure Functions in Azure. If target Azure
 * Functions doesn't exist, it will be created.
 */
public class DeployHandler {
    private static final String PORTAL_URL_PATTERN = "%s/#@/resource%s";
    private static final String DEPLOY_START = "Trying to deploy the function app...";
    private static final String DEPLOY_FINISH =
        "Deployment done, you may access your resource through %s";
    private static final String UNKNOWN_DEPLOYMENT_TYPE = "The value of <deploymentType> is unknown, supported values are: " +
        "ftp, zip, msdeploy, run_from_blob and run_from_zip.";
    private static final String APPLICATION_INSIGHTS_CONFIGURATION_CONFLICT = "Contradictory configurations for application insights," +
        " specify 'appInsightsKey' or 'appInsightsInstance' if you want to enable it, and specify " +
        "'disableAppInsights=true' if you want to disable it.";

    private static final String FUNCTION_JAVA_VERSION_KEY = "functionJavaVersion";
    private static final String DISABLE_APP_INSIGHTS_KEY = "disableAppInsights";
    private static final String JVM_UP_TIME = "jvmUpTime";
    private static final String SKIP_DEPLOYMENT_FOR_DOCKER_APP_SERVICE = "Skip deployment for docker app service";
    private static final String LOCAL_SETTINGS_FILE = "local.settings.json";
    private static final String DEPLOY = "deploy";
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
    private static final String INVALID_OS = "The value of 'os' is not correct, supported values are: 'windows', 'linux' and 'docker'.";
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
    private static final String EXPANDABLE_PRICING_TIER_WARNING = "'%s' may not be a valid pricing tier, " +
        "please refer to https://aka.ms/maven_function_configuration#supported-pricing-tiers for valid values";
    private static final String EXPANDABLE_REGION_WARNING = "'%s' may not be a valid region, " +
        "please refer to https://aka.ms/maven_function_configuration#supported-regions for valid values";
    private static final String CV2_INVALID_CONTAINER_SIZE = "Invalid container size for flex consumption plan, valid values are: %s";
    private static final List<Integer> VALID_CONTAINER_SIZE = Arrays.asList(512, 2048, 4096);
    public static final int MAX_MAX_INSTANCES = 1000;
    public static final int MIN_MAX_INSTANCES = 40;
    public static final int MIN_HTTP_INSTANCE_CONCURRENCY = 1;
    public static final int MAX_HTTP_INSTANCE_CONCURRENCY = 1000;
    private final GradleFunctionContext ctx;

    public DeployHandler(final GradleFunctionContext ctx) {
        Preconditions.checkNotNull(ctx);
        this.ctx = ctx;
    }

    public void execute() {
        OperationContext.current().setTelemetryProperty(FUNCTION_JAVA_VERSION_KEY, StringUtils.firstNonBlank(getJavaVersion(), "N/A"));
        OperationContext.current().setTelemetryProperty(DISABLE_APP_INSIGHTS_KEY, String.valueOf(ctx.isDisableAppInsights()));
        ((FunctionsServiceSubscription) ctx.getOrCreateAzureAppServiceClient().getParent()).loadRuntimes();
        doValidate();
        final FunctionAppBase<?, ?, ?> app = createOrUpdateFunctionApp();
        deployArtifact(app);
        if (app instanceof FunctionApp) {
            listHTTPTriggerUrls((FunctionApp) app);
        }
    }

    private RuntimeConfig getRuntimeConfig() {
        final GradleRuntimeConfig config = ctx.getRuntime();
        if (config == null) {
            return null;
        }
        final FunctionsServiceSubscription serviceSubscription = (FunctionsServiceSubscription) Azure.az(AzureFunctions.class)
                .forSubscription(ctx.getOrCreateAzureAppServiceClient().getSubscriptionId());
        serviceSubscription.loadRuntimes();
        final OperatingSystem os = Optional.ofNullable(config.os()).map(OperatingSystem::fromString)
                .orElseGet(() -> Optional.ofNullable(getFunctionApp()).map(FunctionApp::getAppServicePlan).map(AppServicePlan::getOperatingSystem).orElse(OperatingSystem.WINDOWS));
        final String javaVersion = getJavaVersion();
        return new RuntimeConfig().os(os).javaVersion(javaVersion)
                .image(config.image()).registryUrl(config.registryUrl())
                .username(config.username()).password(config.password());
    }

    /**
     * List anonymous HTTP Triggers url after deployment
     * @param target the target function
     */
    protected void listHTTPTriggerUrls(FunctionApp target) {
        try {
            final List<FunctionEntity> triggers = listFunctionsWithRetry(target);
            final List<FunctionEntity> httpFunction = triggers.stream()
                .filter(function -> function.getTrigger() != null &&
                    StringUtils.equalsIgnoreCase(function.getTrigger().getType(), HTTP_TRIGGER))
                .collect(Collectors.toList());
            final List<FunctionEntity> anonymousTriggers = httpFunction.stream()
                .filter(bindingResource -> bindingResource.getTrigger() != null &&
                    StringUtils.equalsIgnoreCase(bindingResource.getTrigger().getProperty(AUTH_LEVEL), AzureFunctionsAnnotationConstants.ANONYMOUS))
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

    private List<FunctionEntity> listFunctionsWithRetry(final FunctionApp functionApp) {
        final int[] count = {0};
        return Mono.fromCallable(() -> listFunctions(functionApp, count[0]++)).subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.fixedDelay(LIST_TRIGGERS_MAX_RETRY - 1, Duration.ofSeconds(LIST_TRIGGERS_RETRY_PERIOD_IN_SECONDS))).block();
    }

    @AzureOperation(name = "user/functionapp.list_function.app", params = {"functionApp.getName()"})
    private List<FunctionEntity> listFunctions(final FunctionApp functionApp, int count) {
        final AzureString message = count == 0 ?
                AzureString.fromString(SYNCING_TRIGGERS) : AzureString.format(SYNCING_TRIGGERS_WITH_RETRY, count, LIST_TRIGGERS_MAX_RETRY);
        AzureMessager.getDefaultMessager().info(message);
        return Optional.of(functionApp.listFunctions(true))
                .filter(CollectionUtils::isNotEmpty)
                .orElseThrow(() -> new AzureToolkitRuntimeException(NO_TRIGGERS_FOUNDED));
    }

    protected void doValidate() {
        validateParameters();
        validateApplicationInsightsConfiguration();
        if (Objects.equals(PricingTier.fromString(ctx.getPricingTier()), PricingTier.FLEX_CONSUMPTION)) {
            validateFlexConsumptionConfiguration();
        }
    }

    private void validateFlexConsumptionConfiguration() {
        // regions
        final String subsId = ctx.getOrCreateAzureAppServiceClient().getSubscriptionId();
        final List<Region> regions = Azure.az(AzureAppService.class).forSubscription(subsId)
                .functionApps().listRegions(PricingTier.FLEX_CONSUMPTION);
        final Region region = Optional.ofNullable(ctx.getRegion()).filter(StringUtils::isNotBlank).map(Region::fromName).orElse(null);
        final String supportedRegionsValue = regions.stream().map(Region::getName).collect(Collectors.joining(","));
        if (Objects.nonNull(region) && !regions.contains(region)) {
            throw new AzureToolkitRuntimeException(String.format("`%s` is not a valid region for flex consumption app, supported values are %s", region.getName(), supportedRegionsValue));
        }
        // runtime
        final List<? extends FunctionAppRuntime> validFlexRuntimes = Objects.isNull(region) ? Collections.emptyList() :
                Azure.az(AzureAppService.class).forSubscription(subsId).functionApps().listFlexConsumptionRuntimes(region);
        final GradleRuntimeConfig runtime = ctx.getRuntime();
        final OperatingSystem os = Optional.ofNullable(runtime).map(GradleRuntimeConfig::os).map(OperatingSystem::fromString).orElse(OperatingSystem.WINDOWS);
        final String javaVersion = Optional.ofNullable(runtime).map(GradleRuntimeConfig::javaVersion).orElse(FunctionAppRuntime.DEFAULT_JAVA.toString());
        final FunctionAppRuntime functionAppRuntime = os == OperatingSystem.DOCKER ? FunctionAppDockerRuntime.INSTANCE :
                os == OperatingSystem.LINUX ? FunctionAppLinuxRuntime.fromJavaVersionUserText(javaVersion) : FunctionAppWindowsRuntime.fromJavaVersionUserText(javaVersion);
        if (Objects.nonNull(region) && !validFlexRuntimes.contains(functionAppRuntime)) {
            final String validValues = validFlexRuntimes.stream().map(FunctionAppRuntime::getDisplayName).collect(Collectors.joining(","));
            throw new AzureToolkitRuntimeException(String.format("Invalid runtime configuration, valid flex consumption runtimes are %s in region %s", validValues, region.getLabel()));
        }
        // storage authentication method
        final StorageAuthenticationMethod authenticationMethod = Optional.ofNullable(ctx.getStorageAuthenticationMethod())
                .map(StorageAuthenticationMethod::fromString)
                .orElse(null);
        if (Objects.nonNull(authenticationMethod)) {
            if (StringUtils.isNotBlank(ctx.getStorageAccountConnectionString()) &&
                    authenticationMethod != StorageAuthenticationMethod.StorageAccountConnectionString) {
                AzureMessager.getMessager().warning("The value of 'storageAccountConnectionString' will be ignored because the value of 'storageAuthenticationMethod' is not StorageAccountConnectionString");
            }
            if (StringUtils.isNotBlank(ctx.getUserAssignedIdentityResourceId()) &&
                    authenticationMethod != StorageAuthenticationMethod.UserAssignedIdentity) {
                AzureMessager.getMessager().warning("The value of 'userAssignedIdentityResourceId' will be ignored because the value of 'storageAuthenticationMethod' is not UserAssignedIdentity");
            }
            if (StringUtils.isBlank(ctx.getUserAssignedIdentityResourceId()) && authenticationMethod == StorageAuthenticationMethod.UserAssignedIdentity) {
                throw new AzureToolkitRuntimeException("Please specify the value of 'userAssignedIdentityResourceId' when the value of 'storageAuthenticationMethod' is UserAssignedIdentity");
            }
        }
        // scale configuration
        if (Objects.nonNull(ctx.getInstanceMemory()) && !VALID_CONTAINER_SIZE.contains(ctx.getInstanceMemory())) {
            throw new AzureToolkitRuntimeException(String.format(CV2_INVALID_CONTAINER_SIZE, VALID_CONTAINER_SIZE.stream().map(String::valueOf).collect(Collectors.joining(","))));
        }
        if (Objects.nonNull(ctx.getMaximumInstances()) && (ctx.getMaximumInstances() > MAX_MAX_INSTANCES || ctx.getMaximumInstances() < MIN_MAX_INSTANCES)) {
            throw new AzureToolkitRuntimeException("Invalid value for 'maximumInstances', it should be in range [40, 1000]");
        }
        if (Objects.nonNull(ctx.getHttpInstanceConcurrency()) && (ctx.getHttpInstanceConcurrency() < MIN_HTTP_INSTANCE_CONCURRENCY || ctx.getHttpInstanceConcurrency() > MAX_HTTP_INSTANCE_CONCURRENCY)) {
            throw new AzureToolkitRuntimeException("Invalid value for 'httpInstanceConcurrency', it should be in range [1, 1000]");
        }
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
        if (StringUtils.isNotEmpty(region) && Region.fromName(region).isExpandedValue()) {
            // allow arbitrary region since the region can be changed
            AzureMessager.getMessager().warning(AzureString.format(EXPANDABLE_REGION_WARNING, region));
        }

        final GradleRuntimeConfig runtime = ctx.getRuntime();
        if (runtime != null) {
            // os
            if (StringUtils.isNotEmpty(runtime.os()) && OperatingSystem.fromString(runtime.os()) == null) {
                throw new AzureToolkitRuntimeException(INVALID_OS);
            }
        }

        final String pricingTier = ctx.getPricingTier();
        // pricing tier
        if (StringUtils.isNotEmpty(pricingTier) && PricingTier.fromString(pricingTier) == null) {
            throw new AzureToolkitRuntimeException(String.format(EXPANDABLE_PRICING_TIER_WARNING, pricingTier));
        }
        Optional.ofNullable(getRuntimeConfig()).ifPresent(this::validateArtifactCompileVersion);
        validateApplicationInsightsConfiguration();
    }

    @Nonnull
    private FunctionAppBase<?, ?, ?> createOrUpdateFunctionApp() {
        final FunctionApp app = getFunctionApp();
        final FunctionAppConfig functionConfig = (FunctionAppConfig) new FunctionAppConfig()
            .flexConsumptionConfiguration(ctx.getFlexConsumptionConfiguration())
            .disableAppInsights(ctx.isDisableAppInsights())
            .appInsightsKey(ctx.getAppInsightsKey())
            .appInsightsInstance(ctx.getAppInsightsInstance())
            .subscriptionId(ctx.getOrCreateAzureAppServiceClient().getSubscriptionId())
            .resourceGroup(ctx.getResourceGroup())
            .appName(ctx.getAppName())
            .servicePlanName(ctx.getAppServicePlanName())
            .servicePlanResourceGroup(ctx.getAppServicePlanResourceGroup())
            .deploymentSlotName(ctx.getDeploymentSlotName()) // gradle function plugin doesn't support deploy slot now
            .deploymentSlotConfigurationSource(ctx.getDeploymentSlotConfigurationSource())
            .pricingTier(getParsedPricingTier())
            .region(getParsedRegion())
            .runtime(getRuntimeConfig())
            .appSettings(ctx.getAppSettings());

        final boolean createFunctionApp = Optional.ofNullable(app).map(function -> !function.exists()).orElse(true);
        final AppServiceConfig defaultConfig = createFunctionApp ? buildDefaultConfig(functionConfig.subscriptionId(),
            functionConfig.resourceGroup(), functionConfig.appName()) : fromFunctionApp(app);
        mergeAppServiceConfig(functionConfig, defaultConfig);
        if (!createFunctionApp && !functionConfig.disableAppInsights() && StringUtils.isBlank(functionConfig.appInsightsKey())) {
            // fill ai key from existing app settings
            final String aiKey = Optional.ofNullable(app.getAppSettings())
                    .map(map -> map.get(CreateOrUpdateFunctionAppTask.APPINSIGHTS_INSTRUMENTATION_KEY)).orElse(null);
            functionConfig.appInsightsKey(aiKey);
        }
        return new CreateOrUpdateFunctionAppTask(functionConfig).execute();
    }

    private void deployArtifact(@Nonnull final FunctionAppBase<?, ?, ?> target) {
        final boolean isDockerRuntime = Optional.ofNullable(target.getRuntime()).map(Runtime::isDocker).orElse(false);
        if (isDockerRuntime) {
            AzureMessager.getMessager().info(SKIP_DEPLOYMENT_FOR_DOCKER_APP_SERVICE);
            return;
        }
        AzureMessager.getMessager().info(DEPLOY_START);
        String deploymentType = ctx.getDeploymentType();
        FunctionDeployType deployType;
        try {
            deployType = StringUtils.isEmpty(deploymentType) ? null : FunctionDeployType.fromString(deploymentType);
        } catch (AzureToolkitRuntimeException ex) {
            throw new AzureToolkitRuntimeException(UNKNOWN_DEPLOYMENT_TYPE, ex);
        }

        // For ftp deploy, we need to upload entire staging directory not the zipped package
        final File file = deployType == FunctionDeployType.FTP ? new File(ctx.getDeploymentStagingDirectoryPath()) : packageStagingDirectory();
        final RunnableWithException deployRunnable = deployType == null ? () -> target.deploy(file) : () -> target.deploy(file, deployType);
        executeWithTimeRecorder(deployRunnable, DEPLOY);
        // todo: check function status after deployment
        if (!target.getFormalStatus().isRunning()) {
            target.start();
        }
        AzureMessager.getMessager().info(String.format(DEPLOY_FINISH, getResourcePortalUrl(target.getId())));
    }

    private interface RunnableWithException {
        void run() throws Exception;
    }

    private void executeWithTimeRecorder(RunnableWithException operation, String name) {
        final long startTime = System.currentTimeMillis();
        try {
            operation.run();
        } catch (Exception e) {
            throw new AzureToolkitRuntimeException(e.getMessage(), e);
        } finally {
            final long endTime = System.currentTimeMillis();
            OperationContext.current().setTelemetryProperty(String.format("%s-cost", name), String.valueOf(endTime - startTime));
            OperationContext.current().setTelemetryProperty(JVM_UP_TIME, String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime()));
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

    private AppServiceConfig buildDefaultConfig(String subscriptionId, String resourceGroup, String appName) {
        // get java version according to project java version
        final FunctionAppConfig result = AppServiceConfig.buildDefaultFunctionConfig(resourceGroup, appName);
        final org.gradle.api.JavaVersion localRuntime = org.gradle.api.JavaVersion.current();
        final Runtime runtime = localRuntime.isCompatibleWith(org.gradle.api.JavaVersion.VERSION_17) ? FUNCTION_JAVA17 :
                localRuntime.isJava11Compatible() ? FUNCTION_JAVA11 : FUNCTION_JAVA8;
        result.runtime(new RuntimeConfig().os(runtime.getOperatingSystem()).javaVersion(runtime.getJavaVersionUserText()));
        result.subscriptionId(subscriptionId);
        return result;
    }

    private Region getParsedRegion() {
        return Optional.ofNullable(ctx.getRegion()).map(Region::fromName).orElse(null);
    }

    private PricingTier getParsedPricingTier() {
        String pricingTier = ctx.getPricingTier();
        if (StringUtils.isEmpty(pricingTier)) {
            return null;
        }
        return Optional.ofNullable(PricingTier.fromString(pricingTier))
            .orElseThrow(() -> new AzureToolkitRuntimeException(String.format("Invalid pricing tier %s", pricingTier)));
    }

    protected void validateArtifactCompileVersion(@Nonnull RuntimeConfig runtime) {
        if (runtime.os() == OperatingSystem.DOCKER) {
            return;
        }
        final String javaVersion = Optional.ofNullable(getJavaVersion()).orElse(StringUtils.EMPTY);
        final File file = this.ctx.getProject().getArtifactFile().toFile();
        final int runtimeVersion;
        final int artifactCompileVersion;
        try {
            runtimeVersion = Utils.getJavaMajorVersion(javaVersion);
            artifactCompileVersion = Utils.getArtifactCompileVersion(file);
        } catch (RuntimeException e) {
            AzureMessager.getMessager().info("Failed to get version of your artifact, skip artifact compatibility test");
            return;
        }
        if (runtimeVersion < artifactCompileVersion) {
            throw new AzureToolkitRuntimeException(ARTIFACT_INCOMPATIBLE);
        }
    }

    @Nullable
    private String getJavaVersion() {
        return Optional.ofNullable(ctx.getRuntime()).map(GradleRuntimeConfig::javaVersion).orElse(null);
    }

    public FunctionApp getFunctionApp() {
        return ctx.getOrCreateAzureAppServiceClient().get(ctx.getAppName(), ctx.getResourceGroup());
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
