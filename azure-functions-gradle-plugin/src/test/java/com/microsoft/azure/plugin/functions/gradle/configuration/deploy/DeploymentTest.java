/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle.configuration.deploy;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DeploymentTest {
    private Deployment deploy;

    @Before
    public void setUp() {
        deploy = new Deployment();
    }

    @Test
    public void testType() throws Exception {
        deploy.setType("type1");
        assertEquals("type1", deploy.getType());
    }
}
