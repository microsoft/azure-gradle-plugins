/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.plugin.functions.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.plugin.functions.gradle.configuration.auth.GradleAuthConfig;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.common.IProject;

import java.util.Map;

public interface IAppServiceContext {
    String getDeploymentStagingDirectoryPath();

    String getSubscription();

    String getAppName();

    String getResourceGroup();

    GradleRuntimeConfig getRuntime();

    String getRegion();

    String getPricingTier();

    String getAppServicePlanResourceGroup();

    String getAppServicePlanName();

    Map<String, String> getAppSettings();

    GradleAuthConfig getAuth();

    String getDeploymentType();

    String getAppInsightsInstance();

    String getAppInsightsKey();

    boolean isDisableAppInsights();

    IProject getProject();

    AzureAppService getOrCreateAzureAppServiceClient();
}
