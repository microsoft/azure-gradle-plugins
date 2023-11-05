/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.microsoft.azure.gradle.auth.GradleAuthConfig;
import com.microsoft.azure.gradle.configuration.GradleDeploymentSlotConfig;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.legacy.appservice.DockerImageType;
import groovy.lang.Closure;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public class AzureWebappPluginExtension {
    public static final String JAVA_VERSION_KEY = "javaVersion";
    public static final String JAVA_WEB_CONTAINER_KEY = "javaWebContainer";
    public static final String DOCKER_IMAGE_TYPE_KEY = "dockerImageType";
    public static final String PRICING_TIER_KEY = "pricingTier";
    public static final String REGION_KEY = "region";
    public static final String OS_KEY = "os";
    public static final String SKIP_CREATE_RESOURCE_KEY = "skipCreateResource";
    public static final String DEPLOY_TO_SLOT_KEY = "isDeployToSlot";

    private Boolean allowTelemetry;

    private String subscription;

    private String resourceGroup;

    private String appName;

    private String region;

    private String pricingTier;

    private String appServicePlanResourceGroup;

    private String appServicePlanName;

    private GradleAuthConfig auth;

    private GradleRuntimeConfig runtime;

    private GradleDeploymentSlotConfig deploymentSlot;

    private Map<String, String> appSettings;

    private String appInsightsInstance;

    private String appInsightsKey;
    private Project project;

    @Input
    @Optional
    public String getResourceGroup() {
        return resourceGroup;
    }

    @Input
    public String getAppName() {
        return appName;
    }

    @Input
    @Optional
    public String getRegion() {
        return region;
    }

    @Input
    @Optional
    public String getSubscription() {
        return subscription;
    }

    @Input
    @Optional
    public String getPricingTier() {
        return pricingTier;
    }

    @Input
    @Optional
    public String getAppServicePlanName() {
        return appServicePlanName;
    }

    @Input
    @Optional
    public String getAppServicePlanResourceGroup() {
        return appServicePlanResourceGroup;
    }

    @Input
    @Optional
    @Deprecated
    public GradleAuthConfig getAuthentication() {
        return auth;
    }

    @Input
    @Optional
    public GradleAuthConfig getAuth() {
        return auth;
    }

    @Input
    @Optional
    public Map<String, String> getAppSettings() {
        return appSettings;
    }

    @Input
    @Optional
    public GradleRuntimeConfig getRuntime() {
        return runtime;
    }

    @Input
    @Optional
    public Boolean getAllowTelemetry() {
        return allowTelemetry;
    }

    @Input
    @Optional
    public String getAppInsightsInstance() {
        return appInsightsInstance;
    }

    @Input
    @Optional
    public String getAppInsightsKey() {
        return appInsightsKey;
    }

    @Input
    @Optional
    public GradleDeploymentSlotConfig getDeploymentSlot() {
        return deploymentSlot;
    }

    public void setAllowTelemetry(Boolean allowTelemetry) {
        this.allowTelemetry = allowTelemetry;
    }

    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setSubscription(String subscription) {
        this.subscription = subscription;
    }

    public void setPricingTier(String pricingTier) {
        this.pricingTier = pricingTier;
    }

    @Deprecated
    public void setAuthentication(Closure closure) {
        this.auth = new GradleAuthConfig();
        project.configure(auth, closure);
    }

    public void setAuth(Closure closure) {
        this.auth = new GradleAuthConfig();
        project.configure(auth, closure);
    }

    public void setRuntime(Closure closure) {
        runtime = new GradleRuntimeConfig();
        project.configure(runtime, closure);
    }

    public void setDeploymentSlot(Closure closure) {
        deploymentSlot = new GradleDeploymentSlotConfig();
        project.configure(deploymentSlot, closure);
    }

    public void setAppServicePlanName(String appServicePlanName) {
        this.appServicePlanName = appServicePlanName;
    }

    public void setAppServicePlanResourceGroup(String appServicePlanResourceGroup) {
        this.appServicePlanResourceGroup = appServicePlanResourceGroup;
    }

    public void setAppSettings(Closure closure) {
        this.appSettings = new HashMap<>();
        project.configure(appSettings, closure);
    }

    public void setAppInsightsInstance(@Nullable String appInsightsInstance) {
        this.appInsightsInstance = appInsightsInstance;
    }

    public void setAppInsightsKey(@Nullable String appInsightsKey) {
        this.appInsightsKey = appInsightsKey;
    }

    @JsonSetter
    public void setAuth(GradleAuthConfig auth) {
        this.auth = auth;
    }

    @JsonSetter
    public void setRuntime(GradleRuntimeConfig runtime) {
        this.runtime = runtime;
    }

    @JsonSetter
    public void setDeploymentSlot(GradleDeploymentSlotConfig deploymentSlot) {
        this.deploymentSlot = deploymentSlot;
    }

    @JsonSetter
    public void setAppSettings(Map<String, String> appSettings) {
        this.appSettings = appSettings;
    }

    public AzureWebappPluginExtension(@Nonnull Project project) {
        this.project = project;
    }

    public Map<String, String> getTelemetryProperties() {
        final Map<String, String> result = new HashMap<>();
        final GradleRuntimeConfig runtime = getRuntime();
        final String os = java.util.Optional.ofNullable(runtime).map(GradleRuntimeConfig::os).orElse(null);
        result.put(OS_KEY, os);
        result.put(JAVA_VERSION_KEY, java.util.Optional.ofNullable(runtime).map(GradleRuntimeConfig::javaVersion).orElse(null));
        result.put(JAVA_WEB_CONTAINER_KEY, java.util.Optional.ofNullable(runtime).map(GradleRuntimeConfig::webContainer).orElse(null));
        result.put(PRICING_TIER_KEY, pricingTier);
        result.put(REGION_KEY, region);
        if (runtime != null && StringUtils.equalsIgnoreCase(os, OperatingSystem.DOCKER.getValue())) {
            final boolean isCustomRegistry = StringUtils.isNotEmpty(runtime.registryUrl());
            final DockerImageType imageType;
            if (isCustomRegistry) {
                imageType = StringUtils.isEmpty(runtime.password()) ? DockerImageType.PRIVATE_REGISTRY : DockerImageType.UNKNOWN;
            } else {
                imageType = StringUtils.isEmpty(runtime.password()) ? DockerImageType.PRIVATE_DOCKER_HUB : DockerImageType.PUBLIC_DOCKER_HUB;
            }
            result.put(DOCKER_IMAGE_TYPE_KEY, imageType.name());
        } else {
            result.put(DOCKER_IMAGE_TYPE_KEY, DockerImageType.NONE.toString());
        }
        final boolean isDeployToSlot = java.util.Optional.ofNullable(getDeploymentSlot()).map(GradleDeploymentSlotConfig::name)
                .map(StringUtils::isNotEmpty).orElse(false);
        result.put(DEPLOY_TO_SLOT_KEY, String.valueOf(isDeployToSlot));
        result.put(SKIP_CREATE_RESOURCE_KEY, System.getProperty("azure.resource.create.skip", "false"));
        return result;
    }
}
