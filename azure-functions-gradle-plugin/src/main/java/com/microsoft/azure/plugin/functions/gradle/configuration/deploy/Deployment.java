/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.configuration.deploy;

import org.gradle.api.tasks.Input;

import java.io.Serializable;

public class Deployment implements Serializable {
    private static final long serialVersionUID = 1L;
    private String type;

    @Input
    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
