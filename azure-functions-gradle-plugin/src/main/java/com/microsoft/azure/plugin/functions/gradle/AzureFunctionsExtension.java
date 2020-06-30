/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.plugin.functions.gradle.configuration.GradleRuntimeConfiguration;
import com.microsoft.azure.plugin.functions.gradle.configuration.auth.GradleAuthConfiguration;
import com.microsoft.azure.plugin.functions.gradle.configuration.deploy.Deployment;

import groovy.lang.Closure;

import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;


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
    private GradleAuthConfiguration authentication;

    @Nullable
    private Deployment deployment;

    @Nullable
    private GradleRuntimeConfiguration runtime;

    private Map<String, Object> appSettings;

    private final Project project;

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
    public GradleAuthConfiguration getAuthentication() {
        return authentication;
    }

    @Input
    @Optional
    public Deployment getDeployment() {
        return deployment;
    }

    @Input
    @Optional
    public Map<String, Object> getAppSettings() {
        return appSettings;
    }

    @Input
    @Optional
    public GradleRuntimeConfiguration getRuntime() {
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

    public void setAuthentication(Closure closure) {
        this.authentication = new GradleAuthConfiguration();
        project.configure(authentication, closure);
    }

    public void setDeployment(Closure closure) {
        deployment = new Deployment();
        project.configure(deployment, closure);
    }

    public void setRuntime(Closure closure) {
        runtime = new GradleRuntimeConfiguration();
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
}
