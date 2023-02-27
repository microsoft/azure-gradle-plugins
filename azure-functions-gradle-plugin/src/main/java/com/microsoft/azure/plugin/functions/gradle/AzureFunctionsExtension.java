/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.microsoft.azure.gradle.auth.GradleAuthConfig;
import com.microsoft.azure.gradle.configuration.GradleDeploymentSlotConfig;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.plugin.functions.gradle.configuration.deploy.Deployment;
import groovy.lang.Closure;
import lombok.NoArgsConstructor;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public class AzureFunctionsExtension {

    @Nullable
    private Boolean allowTelemetry;

    @Nullable
    private String localDebug;

    @Nullable
    private String subscription;

    @Nullable
    private String resourceGroup;

    private String appName;

    @Nullable
    private String region;

    @Nullable
    private String pricingTier;

    @Nullable
    private String appServicePlanResourceGroup;

    @Nullable
    private String appServicePlanName;

    @Nullable
    private GradleAuthConfig auth;

    @Nullable
    private Deployment deployment;

    @Nullable
    private GradleRuntimeConfig runtime;

    @Nullable
    private GradleDeploymentSlotConfig deploymentSlot;

    private Map<String, String> appSettings;

    @JsonIgnore
    private Project project;

    @Nullable
    private String appInsightsInstance;

    @Nullable
    private String appInsightsKey;

    @Nullable
    private Boolean disableAppInsights;

    public AzureFunctionsExtension(Project project) {
        this.project = project;
    }

    @Input
    @Optional
    public String getLocalDebug() {
        return this.localDebug;
    }

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
    public GradleAuthConfig getAuth() {
        return auth;
    }

    @Input
    @Optional
    public Deployment getDeployment() {
        return deployment;
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

    public void setAuth(Closure closure) {
        this.auth = new GradleAuthConfig();
        project.configure(auth, closure);
    }

    @Deprecated
    public void setAuthentication(Closure closure) {
        this.auth = new GradleAuthConfig();
        project.configure(auth, closure);
    }

    public void setDeployment(Closure closure) {
        deployment = new Deployment();
        project.configure(deployment, closure);
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

    public void setLocalDebug(String localDebug) {
        this.localDebug = localDebug;
    }

    public void setAppInsightsInstance(@Nullable String appInsightsInstance) {
        this.appInsightsInstance = appInsightsInstance;
    }

    public void setAppInsightsKey(@Nullable String appInsightsKey) {
        this.appInsightsKey = appInsightsKey;
    }

    public void setDisableAppInsights(@Nullable Boolean disableAppInsights) {
        this.disableAppInsights = disableAppInsights;
    }

    @JsonSetter
    public void setAuth(@Nullable GradleAuthConfig auth) {
        this.auth = auth;
    }

    @JsonSetter
    public void setDeployment(@Nullable Deployment deployment) {
        this.deployment = deployment;
    }

    @JsonSetter
    public void setRuntime(@Nullable GradleRuntimeConfig runtime) {
        this.runtime = runtime;
    }

    @JsonSetter
    public void setDeploymentSlot(@Nullable GradleDeploymentSlotConfig deploymentSlot) {
        this.deploymentSlot = deploymentSlot;
    }

    @JsonSetter
    public void setAppSettings(Map<String, String> appSettings) {
        this.appSettings = appSettings;
    }
}
