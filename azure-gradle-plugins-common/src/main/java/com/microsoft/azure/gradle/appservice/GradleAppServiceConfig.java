/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.appservice;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

@Getter
@Setter
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class GradleAppServiceConfig {

    private String subscriptionId;

    private String resourceGroup;

    private String region;

    private String pricingTier;

    private String appName;

    private String servicePlanResourceGroup;

    private String servicePlanName;

    private GradleRuntimeConfig runtime;

    private Map<String, String> appSettings;

    private String deploymentSlotName;

    private String deploymentSlotConfigurationSource;
}
