/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class AzureFunctionsExtensionTest {
    private AzureFunctionsExtension testExtension;
    private Project fakeProject;

    @Before
    public void setUp() {
        fakeProject = ProjectBuilder.builder().build();
        testExtension = fakeProject.getExtensions().create("azurefunctions", AzureFunctionsExtension.class,
                fakeProject);

    }

    @Test
    public void testAppName() {
        testExtension.setAppName("app1");
        Assert.assertEquals("app1", testExtension.getAppName());
    }

    @Test
    public void testLocalConfigurationIsOptional() {
        Assert.assertNull(testExtension.getEnvVars());
        Assert.assertNull(testExtension.getSysProps());
    }

    @Test
    public void testLocalConfigurationIsAvailableToContext() {
        fakeProject.getPluginManager().apply("java");
        final Map<String, Object> environment = Collections.singletonMap("SETTING", 42);
        final Map<String, String> properties = Collections.singletonMap("message", "hello world");
        testExtension.setEnvVars(environment);
        testExtension.setSysProps(properties);
        final GradleFunctionContext context = new GradleFunctionContext(fakeProject, testExtension);

        Assert.assertSame(environment, testExtension.getEnvVars());
        Assert.assertSame(properties, testExtension.getSysProps());
        Assert.assertSame(environment, context.getEnvVars());
        Assert.assertSame(properties, context.getSysProps());
    }
}
