/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.microsoft.azure.gradle.auth.GradleAuthHelper;
import com.microsoft.azure.gradle.configuration.GradleDeploymentSlotConfig;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.gradle.configuration.GradleWebAppConfig;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.appservice.model.WebAppArtifact;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.task.CreateOrUpdateWebAppTask;
import com.microsoft.azure.toolkit.lib.appservice.task.DeployWebAppTask;
import com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils;
import com.microsoft.azure.toolkit.lib.appservice.utils.Utils;
import com.microsoft.azure.toolkit.lib.appservice.webapp.AzureWebApp;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebApp;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebAppBase;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import com.microsoft.azure.toolkit.lib.common.validator.SchemaValidator;
import com.microsoft.azure.toolkit.lib.common.validator.ValidationMessage;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils.fromAppService;
import static com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceConfigUtils.mergeAppServiceConfig;

public class DeployTask extends DefaultTask {
    private static final String PROXY = "proxy";
    private static final String INVALID_PARAMETER_ERROR_MESSAGE = "Invalid values found in configuration, please correct the value with messages below:";

    @Setter
    private AzureWebappPluginExtension azureWebappExtension;

    @Setter
    private String artifactFile;

    @TaskAction
    @AzureOperation(name = "webapp.deploy_app", type = AzureOperation.Type.ACTION)
    public void deploy() throws GradleException {
        try {
            ProxyManager.getInstance().applyProxy();
            TelemetryAgent.getInstance().addDefaultProperty(PROXY, String.valueOf(ProxyManager.getInstance().isProxyEnabled()));
            TelemetryAgent.getInstance().addDefaultProperties(azureWebappExtension.getTelemetryProperties());
            initTask();
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleWebAppConfig config = parseConfiguration();
            normalizeConfigValue(config);
            validate(config);
            config.subscriptionId(GradleAuthHelper.login(azureWebappExtension.getAuth(), config.subscriptionId()));
            validateOnline(config);
            final WebAppBase<?, ?, ?> target = createOrUpdateWebapp(config);
            deployArtifact(target, config);
            TelemetryAgent.getInstance().trackTaskSuccess(this.getClass());
        } catch (Exception e) {
            AzureMessager.getMessager().error(e);
            TelemetryAgent.getInstance().traceException(this.getClass(), e);
            throw new GradleException("Cannot deploy web app due to error: " + e.getMessage(), e);
        }
    }

    protected void validateConfiguration(Consumer<ValidationMessage> validationMessageConsumer, Object rawConfig) {
        final List<ValidationMessage> validate = SchemaValidator.getInstance().validate("GradleWebAppConfiguration",
            rawConfig, "azurewebapp");
        validate.forEach(validationMessageConsumer);
        if (CollectionUtils.isNotEmpty(validate)) {
            final String errorDetails = validate.stream().map(message -> message.getMessage().toString()).collect(Collectors.joining(StringUtils.LF));
            throw new AzureToolkitRuntimeException(String.join(StringUtils.LF, INVALID_PARAMETER_ERROR_MESSAGE, errorDetails));
        }
    }

    private void deployArtifact(WebAppBase<?, ?, ?> target, GradleWebAppConfig config) {
        new DeployWebAppTask(target, config.webAppArtifacts()).execute();
    }

    private void validateOnline(GradleWebAppConfig config) {
        // check online regions
        final List<String> validRegions =
            Azure.az(AzureAccount.class).listRegions(config.subscriptionId()).stream()
                .map(Region::getName).map(StringUtils::lowerCase).collect(Collectors.toList());
        if (StringUtils.isNotBlank(config.region()) && !validRegions.contains(config.region())) {
            throw new AzureToolkitRuntimeException(String.format("Unsupported region '%s' in current subscription, valid values are: %s.", config.region(),
                String.join(",", validRegions)));
        }
    }

    private void validate(GradleWebAppConfig config) {
        validateConfiguration(message -> AzureMessager.getMessager().error(message.getMessage()), config);
    }

    private WebAppBase<?, ?, ?> createOrUpdateWebapp(GradleWebAppConfig config) {
        final AppServiceConfig appServiceConfig = convert(config);
        final WebApp app = Azure.az(AzureWebApp.class).webApps(appServiceConfig.subscriptionId())
                .get(appServiceConfig.resourceGroup(), appServiceConfig.appName());
        boolean skipCreate = BooleanUtils.toBoolean(System.getProperty("azure.resource.create.skip", "false"));
        final AppServiceConfig defaultConfig = app != null && app.exists() ? fromAppService(app, Objects.requireNonNull(app.getAppServicePlan())) :
                buildDefaultConfig(appServiceConfig.subscriptionId(), appServiceConfig.resourceGroup(), appServiceConfig.appName());
        mergeAppServiceConfig(appServiceConfig, defaultConfig);
        if (appServiceConfig.pricingTier() == null) {
            appServiceConfig.pricingTier(
                StringUtils.containsIgnoreCase(Objects.toString(appServiceConfig.runtime().webContainer()), "jboss") ? PricingTier.PREMIUM_P1V3 : PricingTier.PREMIUM_P1V2);
        }
        CreateOrUpdateWebAppTask task = new CreateOrUpdateWebAppTask(appServiceConfig);
        task.setSkipCreateAzureResource(skipCreate);
        return task.execute();
    }

    private AppServiceConfig buildDefaultConfig(String subscriptionId, String resourceGroup, String appName) {
        final String packaging = FilenameUtils.getExtension(StringUtils.firstNonBlank(this.artifactFile, ""));
        // get java version according to project java version
        JavaVersion javaVersion = org.gradle.api.JavaVersion.current().isJava8() ? JavaVersion.JAVA_8 : JavaVersion.JAVA_11;
        return AppServiceConfigUtils.buildDefaultWebAppConfig(subscriptionId, resourceGroup, appName, packaging, javaVersion);
    }

    private AppServiceConfig convert(GradleWebAppConfig config) {
        return new AppServiceConfig()
            .subscriptionId(config.subscriptionId())
            .resourceGroup(config.resourceGroup())
            .appName(config.appName())
            .servicePlanResourceGroup(config.servicePlanResourceGroup())
            .deploymentSlotName(config.deploymentSlotName())
            .deploymentSlotConfigurationSource(config.deploymentSlotConfigurationSource())
            .pricingTier(Optional.ofNullable(config.pricingTier()).map(PricingTier::fromString).orElse(null))
            .region(Optional.ofNullable(config.region()).map(Region::fromName).orElse(null))
            .runtime(convert(config.runtime()))
            .servicePlanName(config.servicePlanName())
            .appSettings(config.appSettings());
    }

    private RuntimeConfig convert(@Nullable GradleRuntimeConfig configNullable) {
        return Optional.ofNullable(configNullable).map(config -> new RuntimeConfig()
            .os(Optional.ofNullable(config.os()).map(OperatingSystem::fromString).orElse(null))
            .webContainer(Optional.ofNullable(config.webContainer()).map(WebContainer::fromString).orElse(null))
            .javaVersion(Optional.ofNullable(config.javaVersion()).map(JavaVersion::fromString).orElse(null))
            .registryUrl(config.registryUrl())
            .image(config.image())
            .username(config.username())
            .password(config.password())
            .startUpCommand(config.startUpCommand())).orElse(null);
    }

    private void initTask() {
        Azure.az().config().setLogLevel(HttpLogDetailLevel.NONE.name());
        Azure.az().config().setUserAgent(TelemetryAgent.getInstance().getUserAgent());
    }

    private GradleWebAppConfig parseConfiguration() {
        final AzureWebappPluginExtension ctx = this.azureWebappExtension;
        GradleWebAppConfig config = new GradleWebAppConfig();
        config.subscriptionId(ctx.getSubscription());
        config.resourceGroup(ctx.getResourceGroup());
        config.appName(ctx.getAppName());
        config.pricingTier(ctx.getPricingTier());
        config.region(ctx.getRegion());
        config.runtime(ctx.getRuntime());
        config.appSettings(ctx.getAppSettings());
        config.servicePlanName(ctx.getAppServicePlanName());
        config.servicePlanResourceGroup(ctx.getAppServicePlanResourceGroup());
        config.deploymentSlotName(Optional.ofNullable(ctx.getDeploymentSlot()).map(GradleDeploymentSlotConfig::name).orElse(null));
        config.deploymentSlotConfigurationSource(Optional.ofNullable(ctx.getDeploymentSlot())
                .map(GradleDeploymentSlotConfig::configurationSource).orElse(null));
        if (StringUtils.isNotBlank(this.artifactFile)) {
            File file = new File(this.artifactFile);
            if (!file.exists()) {
                throw new AzureToolkitRuntimeException(String.format("artifact file(%s) cannot be found.", file.getAbsolutePath()));
            }
            final WebAppArtifact webAppArtifact = WebAppArtifact.builder()
                .deployType(Utils.getDeployTypeByFileExtension(file))
                .file(file).build();

            config.webAppArtifacts(Collections.singletonList(webAppArtifact));
        }
        return config;
    }

    private void normalizeConfigValue(GradleWebAppConfig config) {
        if (StringUtils.isNotBlank(config.region()) && Region.fromName(config.region()) != null) {
            config.region(Region.fromName(config.region()).getName());
        }
    }
}
