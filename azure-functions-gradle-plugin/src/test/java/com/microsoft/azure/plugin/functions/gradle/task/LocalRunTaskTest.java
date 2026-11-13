/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import org.gradle.process.ExecSpec;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
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
    public void serializeSystemPropertiesPreservesSimpleSpecialCharacters() {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("empty", "");
        properties.put("equals", "a=b");
        properties.put("singleQuote", "it's-fine");
        properties.put("path", "C:\\temp\\file");

        Assert.assertEquals("-Dempty= -Dequals=a=b -DsingleQuote=it's-fine -Dpath=C:\\temp\\file",
                LocalRunTask.serializeSystemProperties(properties));
    }

    @Test
    public void serializeSystemPropertiesEscapesTrailingBackslashesInQuotedArguments() {
        Assert.assertEquals("\"-Dpath=C:\\path with spaces\\\\\"",
                LocalRunTask.serializeSystemProperties(Collections.singletonMap("path", "C:\\path with spaces\\")));
    }

    @Test
    public void serializeSystemPropertiesEscapesBackslashesBeforeQuotes() {
        Assert.assertEquals("\"-Dquoted=backslash\\\\\\\"quote\"",
                LocalRunTask.serializeSystemProperties(Collections.singletonMap("quoted", "backslash\\\"quote")));
    }

    @Test
    public void serializeSystemPropertiesQuotesTabsAndNewlines() {
        Assert.assertEquals("\"-Dmessage=hello\tworld\nagain\"",
                LocalRunTask.serializeSystemProperties(Collections.singletonMap("message", "hello\tworld\nagain")));
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

        final Map<String, Object> result = mergeEnvironment(Collections.unmodifiableMap(inheritedEnvironment),
                Collections.unmodifiableMap(configuredEnvironment), Collections.unmodifiableMap(systemProperties), false, null);

        Assert.assertEquals("value", result.get("INHERITED"));
        Assert.assertEquals("value", result.get("CONFIGURED"));
        Assert.assertEquals("-Xmx512m \"-Dmessage=hello world\"", result.get("JAVA_OPTS"));
        Assert.assertEquals(2, inheritedEnvironment.size());
        Assert.assertEquals("-Xmx512m", inheritedEnvironment.get("JAVA_OPTS"));
        Assert.assertEquals(1, configuredEnvironment.size());
        Assert.assertEquals("hello world", systemProperties.get("message"));
    }

    @Test
    public void mergeEnvVarsOverridesInheritedValuesBeforeAppendingSystemProperties() {
        final Map<String, Object> inheritedEnvironment = new HashMap<>();
        inheritedEnvironment.put("JAVA_OPTS", "-Xmx512m -Dmessage=inherited");
        inheritedEnvironment.put("SETTING", "inherited");
        final Map<String, Object> configuredEnvironment = new HashMap<>();
        configuredEnvironment.put("JAVA_OPTS", "-Xmx256m -Dmessage=configured");
        configuredEnvironment.put("SETTING", 42);

        final Map<String, Object> result = mergeEnvironment(inheritedEnvironment, configuredEnvironment,
                Collections.singletonMap("message", "property"), false, null);

        Assert.assertEquals(42, result.get("SETTING"));
        Assert.assertEquals("-Xmx256m -Dmessage=configured -Dmessage=property", result.get("JAVA_OPTS"));
    }

    @Test
    public void mergeEnvVarsPreservesInheritedOptionsWhenConfigurationIsAbsent() {
        final Map<String, Object> inheritedEnvironment = Collections.singletonMap("JAVA_OPTS", "-Xmx512m");
        final Map<String, Object> result = mergeEnvironment(inheritedEnvironment, null, null, null, null);

        Assert.assertEquals(inheritedEnvironment, result);
        Assert.assertNotSame(inheritedEnvironment, result);
    }

    @Test
    public void mergeEnvVarsDoesNotAddJavaOptsWhenOptionsAreAbsent() {
        final Map<String, Object> result = mergeEnvironment(Collections.singletonMap("INHERITED", "value"),
                Collections.singletonMap("SETTING", "value"), null, false, null);

        Assert.assertEquals(2, result.size());
        Assert.assertFalse(result.containsKey("JAVA_OPTS"));
    }

    @Test
    public void mergeEnvVarsHandlesNullAndEmptyMaps() {
        Assert.assertTrue(mergeEnvironment(null, null, null, null, null).isEmpty());
        Assert.assertTrue(mergeEnvironment(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                false, null).isEmpty());
    }

    @Test
    public void mergeEnvVarsIgnoresBlankOptionsWhenAppendingProperties() {
        final Map<String, Object> result = mergeEnvironment(Collections.singletonMap("JAVA_OPTS", "  "), null,
                Collections.singletonMap("message", "value"), false, null);

        Assert.assertEquals("-Dmessage=value", result.get("JAVA_OPTS"));
    }

    @Test
    public void mergeEnvVarsAppendsDefaultDebugOptionsWhenEnabled() {
        final Map<String, Object> result = mergeEnvironment(Collections.singletonMap("JAVA_OPTS", "-Xmx512m"),
                null, Collections.singletonMap("message", "value"), true, null);

        Assert.assertEquals("-Xmx512m -Dmessage=value -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
                result.get("JAVA_OPTS"));
    }

    @Test
    public void mergeEnvVarsEnablesConfiguredDebugWithoutFlag() {
        final Map<String, Object> result = mergeEnvironment(null, null, null, false,
                "transport=dt_socket,server=y,suspend=n,address=5006");

        Assert.assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006", result.get("JAVA_OPTS"));
    }

    @Test
    public void mergeEnvVarsDoesNotDuplicateDebugPrefix() {
        final String debug = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006";
        final Map<String, Object> result = mergeEnvironment(null, null, Collections.singletonMap("message", "value"),
                true, debug);

        Assert.assertEquals("-Dmessage=value " + debug, result.get("JAVA_OPTS"));
    }

    @Test
    public void mergeEnvVarsOnWindowsAppendsToMixedCaseInheritedOptions() {
        final Map<String, Object> inherited = Collections.singletonMap("java_opts", "-Xmx512m");
        final Map<String, Object> result = mergeEnvironment(inherited, null,
                Collections.singletonMap("message", "value"), true, null, true);

        Assert.assertEquals("-Xmx512m -Dmessage=value -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
                result.get("JAVA_OPTS"));
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("-Xmx512m", inherited.get("java_opts"));
    }

    @Test
    public void mergeEnvVarsOnWindowsOverridesNamesIgnoringCase() {
        final Map<String, Object> inherited = new HashMap<>();
        inherited.put("JAVA_OPTS", "-Xmx512m");
        inherited.put("Setting", "inherited");
        final Map<String, Object> configured = new HashMap<>();
        configured.put("Java_Opts", "-Xmx256m");
        configured.put("SETTING", "configured");

        final Map<String, Object> result = mergeEnvironment(inherited, configured,
                Collections.singletonMap("message", "value"), false, null, true);

        Assert.assertEquals("-Xmx256m -Dmessage=value", result.get("JAVA_OPTS"));
        Assert.assertEquals("configured", result.get("Setting"));
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("-Xmx512m", inherited.get("JAVA_OPTS"));
        Assert.assertEquals("-Xmx256m", configured.get("Java_Opts"));
    }

    @Test
    public void mergeEnvVarsOnUnixKeepsDifferentlyCasedNamesSeparate() {
        final Map<String, Object> inherited = new HashMap<>();
        inherited.put("java_opts", "-Xmx512m");
        inherited.put("Setting", "inherited");
        final Map<String, Object> configured = new HashMap<>();
        configured.put("Java_Opts", "-Xmx256m");
        configured.put("SETTING", "configured");

        final Map<String, Object> result = mergeEnvironment(inherited, configured,
                Collections.singletonMap("message", "value"), false, null, false);

        Assert.assertEquals("-Dmessage=value", result.get("JAVA_OPTS"));
        Assert.assertEquals("-Xmx512m", result.get("java_opts"));
        Assert.assertEquals("-Xmx256m", result.get("Java_Opts"));
        Assert.assertEquals("inherited", result.get("Setting"));
        Assert.assertEquals("configured", result.get("SETTING"));
        Assert.assertEquals(5, result.size());
    }

    private static Map<String, Object> mergeEnvironment(Map<String, Object> inheritedEnvironment,
                                                       Map<String, Object> configuredEnvironment,
                                                       Map<String, String> systemProperties,
                                                       Boolean enableDebug, String debugConfig) {
        return mergeEnvironment(inheritedEnvironment, configuredEnvironment, systemProperties, enableDebug, debugConfig,
                java.io.File.separatorChar == '\\');
    }

    private static Map<String, Object> mergeEnvironment(Map<String, Object> inheritedEnvironment,
                                                       Map<String, Object> configuredEnvironment,
                                                       Map<String, String> systemProperties,
                                                       Boolean enableDebug, String debugConfig, boolean isWindows) {
        final ExecSpec spec = mock(ExecSpec.class);
        when(spec.getEnvironment()).thenReturn(inheritedEnvironment);
        final GradleFunctionContext context = mock(GradleFunctionContext.class);
        when(context.getEnvVars()).thenReturn(configuredEnvironment);
        when(context.getSysProps()).thenReturn(systemProperties);
        when(context.getLocalDebugConfig()).thenReturn(debugConfig);
        return LocalRunTask.mergeEnvVars(spec, context, enableDebug, isWindows);
    }
}
