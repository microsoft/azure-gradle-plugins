/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle.configuration.auth;

import com.microsoft.azure.auth.configuration.AuthConfiguration;

public class GradleAuthConfiguration extends AuthConfiguration {
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    private String type;

}
