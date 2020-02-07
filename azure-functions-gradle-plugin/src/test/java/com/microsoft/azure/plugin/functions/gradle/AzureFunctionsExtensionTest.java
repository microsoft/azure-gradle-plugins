/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
}
