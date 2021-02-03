/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle.configuration;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GradleRuntimeConfigurationTest {
    private GradleRuntimeConfiguration runtime;

    @Before
    public void setUp() {
        runtime = new GradleRuntimeConfiguration();
    }

    @Test
    public void testGetUsername() throws Exception {
        runtime.setUsername("username");
        assertEquals("username", runtime.getUsername());
    }

    @Test
    public void testGetPassword() throws Exception {
        runtime.setPassword("password");
        assertEquals("password", runtime.getPassword());
    }
}
