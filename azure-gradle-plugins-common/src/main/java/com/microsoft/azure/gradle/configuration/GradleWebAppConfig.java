/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.microsoft.azure.gradle.appservice.GradleAppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.WebAppArtifact;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(fluent = true)
public class GradleWebAppConfig extends GradleAppServiceConfig {
    @JsonIgnore
    private List<WebAppArtifact> webAppArtifacts;
}
