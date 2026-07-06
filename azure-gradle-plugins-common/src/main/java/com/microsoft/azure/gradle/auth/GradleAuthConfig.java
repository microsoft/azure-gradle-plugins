/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.auth;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class GradleAuthConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private String type;
    private String environment;
    private String client;
    private String tenant;
    private String key;
    private String certificate;
    private String certificatePassword;
}
