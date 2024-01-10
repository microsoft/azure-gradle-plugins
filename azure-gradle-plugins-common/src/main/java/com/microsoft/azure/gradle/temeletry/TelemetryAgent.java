/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.temeletry;

import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.AzureConfiguration;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemeter;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetryClient;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetryConfigProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static com.microsoft.azure.gradle.temeletry.TelemetryConstants.*;

public class TelemetryAgent {

    private static final String CONFIGURATION_PATH = Paths.get(System.getProperty("user.home"),
        ".azure", "gradleplugins.properties").toString();
    private static final String FIRST_RUN_KEY = "first.run";
    private static final String PRIVACY_STATEMENT = "\nData/Telemetry\n" +
        "---------\n" +
        "This project collects usage data and sends it to Microsoft to help improve our products and services.\n" +
        "Read Microsoft's privacy statement to learn more: https://privacy.microsoft.com/en-us/privacystatement." +
        "\n\nYou can change your telemetry configuration through 'allowTelemetry' property.\n" +
        "For more information, please go to https://aka.ms/azure-gradle-config.\n";
    private static final String ERROR_MESSAGE = "error.message";
    private static final String ERROR_STACK = "error.stack";
    private static final String ERROR_CLASSNAME = "error.class_name";
    private final AzureTelemetryClient telemetryProxy = AzureTelemeter.getClient();

    private static final TelemetryAgent instance = new TelemetryAgent();

    public static TelemetryAgent getInstance() {
        return instance;
    }

    @Deprecated
    public void addDefaultProperty(String key, String value) {
        AzureTelemeter.addCommonProperty(key, value);
    }

    @Deprecated
    public void addDefaultProperties(Map<String, String> properties) {
        Optional.ofNullable(properties).ifPresent(values -> values.forEach(this::addDefaultProperty));
    }

    @Deprecated
    public void trackEvent(String event) {
        telemetryProxy.trackEvent(event);
    }

    @Deprecated
    public void trackEvent(final String eventName, final Map<String, String> customProperties) {
        telemetryProxy.trackEvent(eventName, customProperties);
    }

    public void showPrivacyStatement() {
        final Properties prop = new Properties();
        if (isFirstRun(prop)) {
            AzureMessager.getMessager().confirm(PRIVACY_STATEMENT);
            updateConfigurationFile(prop);
        }
    }

    @Deprecated
    public void trackTaskSkip(@Nonnull final Class<?> taskClass) {
        trackEvent(taskClass.getSimpleName() + ".skip");
    }

    @Deprecated
    public void trackTaskStart(@Nonnull final Class<?> taskClass) {
        trackEvent(taskClass.getSimpleName() + ".start");
    }

    @Deprecated
    public void trackTaskSuccess(@Nonnull final Class<?> taskClass) {
        trackEvent(taskClass.getSimpleName() + ".success");
    }

    @Deprecated
    public void traceException(@Nonnull final Class<?> taskClass, final Exception exception) {
        final HashMap<String, String> failureReason = new HashMap<>();
        final String errorMessage = Optional.ofNullable(exception.getMessage())
                .filter(StringUtils::isNotEmpty).orElseGet(exception::toString);
        failureReason.put(ERROR_MESSAGE, errorMessage);
        failureReason.put(ERROR_STACK, ExceptionUtils.getStackTrace(exception));
        failureReason.put(ERROR_CLASSNAME, exception.getClass().getName());
        trackEvent(taskClass.getSimpleName() + ".failure", failureReason);
    }

    private boolean isFirstRun(Properties prop) {
        try {
            final File configurationFile = new File(CONFIGURATION_PATH);
            if (configurationFile.exists()) {
                try (InputStream input = Files.newInputStream(Paths.get(CONFIGURATION_PATH))) {
                    prop.load(input);
                    final String firstRunValue = prop.getProperty(FIRST_RUN_KEY);
                    if (firstRunValue != null && firstRunValue.equalsIgnoreCase("false")) {
                        return false;
                    }
                }
            } else {
                configurationFile.getParentFile().mkdirs();
                configurationFile.createNewFile();
            }
        } catch (Exception e) {
            // catch exceptions here to avoid blocking mojo execution.
            AzureMessager.getMessager().warning(e.getMessage());
        }
        return true;
    }

    private void updateConfigurationFile(Properties prop) {
        try (OutputStream output = Files.newOutputStream(Paths.get(CONFIGURATION_PATH))) {
            prop.setProperty(FIRST_RUN_KEY, "false");
            prop.store(output, "Azure Gradle Plugin configurations");
        } catch (Exception e) {
            // catch exceptions here to avoid blocking mojo execution.
            AzureMessager.getMessager().warning(e.getMessage());
        }
    }

    public static class GradleAzureTelemetryCommonPropertiesProvider implements AzureTelemetryConfigProvider {
        @Override
        public Map<String, String> getCommonProperties() {
            final Map<String, String> map = new HashMap<>();
            final AzureConfiguration config = Azure.az().config();
            map.put(PLUGIN_NAME_KEY, config.getProduct());
            map.put(PLUGIN_VERSION_KEY, config.getVersion());
            map.put(INSTALLATION_ID_KEY, config.getMachineId());
            map.put(SESSION_ID_KEY, config.getSessionId());
            return map;
        }

        @Override
        public String getEventNamePrefix() {
            return "AzurePlugin.Gradle";
        }
    }
}
