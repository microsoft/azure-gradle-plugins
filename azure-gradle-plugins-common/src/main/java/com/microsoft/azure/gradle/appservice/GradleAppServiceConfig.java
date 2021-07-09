/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.appservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

@Getter
@Setter
@Accessors(fluent = true)
public class GradleAppServiceConfig {
    @JsonProperty
    private String subscriptionId;
    @JsonProperty
    private String resourceGroup;
    @JsonProperty
    private String region;
    @JsonProperty
    private String pricingTier;
    @JsonProperty
    private String appName;
    @JsonProperty
    private String servicePlanResourceGroup;
    @JsonProperty
    private String servicePlanName;
    @JsonProperty
    private GradleRuntimeConfig runtime;
    @JsonProperty
    private Map<String, String> appSettings;
    @JsonProperty
    private String deploymentSlotName;
    @JsonProperty
    private String deploymentSlotConfigurationSource;
}
