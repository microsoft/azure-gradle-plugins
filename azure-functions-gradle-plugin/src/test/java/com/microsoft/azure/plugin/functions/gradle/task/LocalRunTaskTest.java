/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import org.gradle.process.ExecSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalRunTaskTest {

    @Test
    public void serializeSystemPropertiesQuotesComplexValues() {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("simple", "value");
        properties.put("message", "hello world");
        properties.put("quoted", "say \"hello\"");

        Assert.assertEquals(
                "-Dsimple=value \"-Dmessage=hello world\" \"-Dquoted=say \\\"hello\\\"\"",
                LocalRunTask.serializeSystemProperties(properties));
    }

    @Test
    public void mergeEnvVarsDoesNotMutateConfiguredOrInheritedEnvironment() {
        final Map<String, Object> inheritedEnvironment = new HashMap<>();
        inheritedEnvironment.put("JAVA_OPTS", "-Xmx512m");
        inheritedEnvironment.put("INHERITED", "value");
        final Map<String, Object> configuredEnvironment = new HashMap<>();
        configuredEnvironment.put("CONFIGURED", "value");
        final Map<String, String> systemProperties = new HashMap<>();
        systemProperties.put("message", "hello world");

        final ExecSpec spec = mock(ExecSpec.class);
        when(spec.getEnvironment()).thenReturn(inheritedEnvironment);
        final GradleFunctionContext context = mock(GradleFunctionContext.class);
        when(context.getEnvVars()).thenReturn(configuredEnvironment);
        when(context.getSysProps()).thenReturn(systemProperties);
        when(context.getLocalDebugConfig()).thenReturn(null);

        final Map<String, Object> result = LocalRunTask.mergeEnvVars(spec, context, false);

        Assert.assertEquals("value", result.get("INHERITED"));
        Assert.assertEquals("value", result.get("CONFIGURED"));
        Assert.assertEquals("-Xmx512m \"-Dmessage=hello world\"", result.get("JAVA_OPTS"));
        Assert.assertEquals(2, inheritedEnvironment.size());
        Assert.assertEquals(1, configuredEnvironment.size());
    }
}
