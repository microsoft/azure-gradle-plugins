/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.configuration;

import com.microsoft.azure.gradle.appservice.GradleAppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.WebAppArtifact;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class GradleWebAppConfig extends GradleAppServiceConfig {
    // resources
    @Getter
    @Setter
    private List<WebAppArtifact> webAppArtifacts;
}
