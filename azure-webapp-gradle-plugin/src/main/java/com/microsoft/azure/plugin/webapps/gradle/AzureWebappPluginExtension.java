/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.microsoft.azure.gradle.auth.GradleAuthConfig;
import com.microsoft.azure.gradle.configuration.GradleDeploymentSlotConfig;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class AzureWebappPluginExtension {
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

    private Boolean disableAppInsights;

    @Nonnull
    private final Project project;

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
    public Boolean isDisableAppInsights() {
        return disableAppInsights;
    }

    @Input
    @Optional
    public GradleDeploymentSlotConfig getDeploymentSlot() {
        return deploymentSlot;
    }

    public void setDeploymentSlot(GradleDeploymentSlotConfig deploymentSlot) {
        this.deploymentSlot = deploymentSlot;
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

    public void setDisableAppInsights(Boolean disableAppInsights) {
        this.disableAppInsights = disableAppInsights;
    }

    public AzureWebappPluginExtension(@Nonnull Project project) {
        this.project = project;
    }
}
