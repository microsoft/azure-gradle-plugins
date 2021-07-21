/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.gradle.auth.GradleAuthHelper;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.gradle.configuration.GradleWebAppConfig;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.util.GradleProxyUtils;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.appservice.model.WebAppArtifact;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebApp;
import com.microsoft.azure.toolkit.lib.appservice.task.CreateOrUpdateWebAppTask;
import com.microsoft.azure.toolkit.lib.appservice.task.DeployWebAppTask;
import com.microsoft.azure.toolkit.lib.appservice.utils.Utils;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_CREATORS;
import static com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_GETTERS;
import static com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_IS_GETTERS;

public class DeployTask extends DefaultTask {
    @Setter
    private AzureWebappPluginExtension azureWebappExtension;

    @Setter
    private String artifactFile;

    @TaskAction
    public void deploy() throws GradleException {
        GradleProxyUtils.configureProxy();
        initTask();
        final GradleWebAppConfig config = parseConfiguration();
        normalizeConfigValue(config);
        validate(config);
        config.subscriptionId(GradleAuthHelper.login(azureWebappExtension.getAuth(), config.subscriptionId()));
        validateOnline(config);
        final IWebApp target = createOrUpdateWebapp(config);
        deployArtifact(target, config);
    }

    private JsonSchema getConfigurationSchema() {
        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try (InputStream inputStream = this.getClass().getResourceAsStream("/schema/WebAppConfiguration.json")) {
            return factory.getSchema(inputStream);
        } catch (IOException e) {
            throw new AzureToolkitRuntimeException("Failed to load configuration schema");
        }
    }

    protected void validateConfiguration(Consumer<ValidationMessage> validationMessageConsumer, Object rawConfig) {
        final JsonSchema schema = getConfigurationSchema();
        final ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .disable(AUTO_DETECT_CREATORS, AUTO_DETECT_GETTERS, AUTO_DETECT_IS_GETTERS);
        final JsonNode jsonConfig = objectMapper.convertValue(rawConfig, JsonNode.class);
        final Set<ValidationMessage> validate = schema.validate(jsonConfig, jsonConfig, "azurewebapp");
        validate.forEach(validationMessageConsumer);
        if (CollectionUtils.isNotEmpty(validate)) {
            throw new AzureToolkitRuntimeException("Invalid values found in azurewebapp configuration, please correct the value with messages above");
        }
    }

    private void deployArtifact(IWebApp target, GradleWebAppConfig config) {
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

    private IWebApp createOrUpdateWebapp(GradleWebAppConfig config) {
        return new CreateOrUpdateWebAppTask(convert(config)).execute();
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
        ProxyManager.getInstance().init();
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
