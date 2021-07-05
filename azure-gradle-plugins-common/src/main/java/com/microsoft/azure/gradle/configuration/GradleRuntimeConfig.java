/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.configuration;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
public class GradleRuntimeConfig {
    private String os;
    private String javaVersion;
    private String webContainer;
    private String image;
    private String registryUrl;
    private String username;
    private String password;
    private String startUpCommand;

    public boolean isPublicImage() {
        return StringUtils.isAllEmpty(username, password);
    }
}
