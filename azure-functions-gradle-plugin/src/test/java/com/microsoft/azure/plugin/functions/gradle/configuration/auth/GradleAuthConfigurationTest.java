/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.plugin.functions.gradle.configuration.auth;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class GradleAuthConfigurationTest {
    private GradleAuthConfiguration config;

    @Before
    public void setUp() {
        config = new GradleAuthConfiguration();
    }

    @Test
    public void testType() throws Exception {
        config.setType("type1");
        assertEquals("type1", config.getType());
    }
}
