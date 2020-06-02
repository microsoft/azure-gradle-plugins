/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.auth.AzureTokenWrapper;
import com.microsoft.azure.auth.configuration.AuthConfiguration;
import com.microsoft.azure.common.exceptions.AzureExecutionException;
import com.microsoft.azure.common.function.configurations.RuntimeConfiguration;
import com.microsoft.azure.common.project.IProject;
import com.microsoft.azure.management.Azure;

import java.util.Map;

public interface IAppServiceContext {
    String getDeploymentStagingDirectoryPath();

    String getSubscription();

    String getAppName();

    String getResourceGroup();

    RuntimeConfiguration getRuntime();

    String getRegion();

    String getPricingTier();

    String getAppServicePlanResourceGroup();

    String getAppServicePlanName();

    Map<String, String> getAppSettings();

    AuthConfiguration getAuth();

    String getDeploymentType();

    String getAppInsightsInstance();

    String getAppInsightsKey();

    boolean isDisableAppInsights();

    Azure getAzureClient() throws AzureExecutionException;

    AzureTokenWrapper getAzureTokenWrapper() throws AzureExecutionException;

    IProject getProject();
}
