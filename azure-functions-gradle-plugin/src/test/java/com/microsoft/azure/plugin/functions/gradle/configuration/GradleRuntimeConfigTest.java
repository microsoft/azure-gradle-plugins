/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle.configuration;

import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GradleRuntimeConfigTest {
    private GradleRuntimeConfig runtime;

    @Before
    public void setUp() {
        runtime = new GradleRuntimeConfig();
    }

    @Test
    public void testGetUsername() throws Exception {
        runtime.username("username");
        assertEquals("username", runtime.username());
    }

    @Test
    public void testGetPassword() throws Exception {
        runtime.password("password");
        assertEquals("password", runtime.password());
    }
}
