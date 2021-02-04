/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.common.docker.IDockerCredentialProvider;
import com.microsoft.azure.common.exceptions.AzureExecutionException;

import org.apache.commons.lang3.StringUtils;

public class GradleDockerCredentialProvider implements IDockerCredentialProvider {
    private String username;
    private String password;

    public GradleDockerCredentialProvider(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String getUsername() throws AzureExecutionException {
        return username;
    }

    @Override
    public String getPassword() throws AzureExecutionException {
        return password;
    }

    @Override
    public void validate() throws AzureExecutionException {
        if (StringUtils.isNotBlank(this.username) && StringUtils.isBlank(this.password)) {
            throw new AzureExecutionException("Missing password for private docker image.");
        }
        if (StringUtils.isBlank(this.username) && StringUtils.isNotBlank(this.password)) {
            throw new AzureExecutionException("Missing username for private docker image.");
        }
    }
}
