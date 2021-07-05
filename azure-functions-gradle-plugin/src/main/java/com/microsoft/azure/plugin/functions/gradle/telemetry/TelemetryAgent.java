/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle.telemetry;

import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemeter;
import com.microsoft.azure.toolkit.lib.common.utils.InstallationIdUtils;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static com.microsoft.azure.plugin.functions.gradle.telemetry.TelemetryConstants.*;

public class TelemetryAgent implements TelemetryConfiguration {

    private static final String FAILURE_REASON = "failureReason";

    private static final String CONFIGURATION_PATH = Paths.get(System.getProperty("user.home"),
        ".azure", "gradleplugins.properties").toString();
    private static final String FIRST_RUN_KEY = "first.run";
    private static final String PRIVACY_STATEMENT = "\nData/Telemetry\n" +
        "---------\n" +
        "This project collects usage data and sends it to Microsoft to help improve our products and services.\n" +
        "Read Microsoft's privacy statement to learn more: https://privacy.microsoft.com/en-us/privacystatement." +
        "\n\nYou can change your telemetry configuration through 'allowTelemetry' property.\n" +
        "For more information, please go to https://aka.ms/azure-gradle-config.\n";

    private String pluginName;
    private String pluginVersion;
    private boolean allowTelemetry;

    @Setter
    private String subscriptionId;
    @Setter
    private String authType;
    @Setter
    private String authMethod;
    private AppInsightsProxy telemetryProxy;
    private final String sessionId = UUID.randomUUID().toString();
    private final String installationId = InstallationIdUtils.getHashMac();

    private static TelemetryAgent instance = new TelemetryAgent();

    public static TelemetryAgent getInstance() {
        return instance;
    }

    public String getUserAgent() {
        return this.allowTelemetry ?
            String.format("%s/%s %s:%s %s:%s", pluginName, pluginVersion,
                INSTALLATION_ID_KEY, installationId, SESSION_ID_KEY, sessionId)
            : String.format("%s/%s", pluginName, pluginVersion);
    }

    public void initTelemetry(@Nonnull String pluginName, @Nonnull String pluginVersion, boolean allowTelemetry) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.allowTelemetry = allowTelemetry;
        telemetryProxy = new AppInsightsProxy(this);
        if (!allowTelemetry) {
            telemetryProxy.trackEvent(TELEMETRY_NOT_ALLOWED);
            telemetryProxy.disable();
        } else {
            AzureTelemeter.setClient(telemetryProxy.getClient());
            AzureTelemeter.setCommonProperties(this.getTelemetryProperties());
            AzureTelemeter.setEventNamePrefix("AzurePlugin.Gradle");
        }
    }

    public void addDefaultProperty(String key, String value) {
        this.telemetryProxy.addDefaultProperty(key, value);
    }

    public void addDefaultProperties(Map<String, String> properties) {
        Optional.ofNullable(properties).ifPresent(values -> values.forEach(this::addDefaultProperty));
    }

    @Override
    public Map<String, String> getTelemetryProperties() {
        final Map<String, String> map = new HashMap<>();
        map.put(INSTALLATION_ID_KEY, installationId);
        map.put(PLUGIN_NAME_KEY, pluginName);
        map.put(PLUGIN_VERSION_KEY, pluginVersion);
        map.put(TelemetryConstants.SUBSCRIPTION_ID_KEY, subscriptionId);
        map.put(SESSION_ID_KEY, sessionId);
        map.put(AUTH_TYPE_KEY, authType);
        map.put(AUTH_METHOD_KEY, authMethod);
        return map;
    }

    public void trackEvent(String event) {
        if (this.allowTelemetry) {
            telemetryProxy.trackEvent(event);
        }
    }

    public void trackEvent(final String eventName, final Map<String, String> customProperties) {
        if (this.allowTelemetry) {
            telemetryProxy.trackEvent(eventName, customProperties);
        }
    }

    public void showPrivacyStatement() {
        final Properties prop = new Properties();
        if (isFirstRun(prop)) {
            AzureMessager.getMessager().confirm(PRIVACY_STATEMENT);
            updateConfigurationFile(prop);
        }
    }

    public void trackTaskSkip(Class taskClass) {
        trackEvent(taskClass.getSimpleName() + ".skip");
    }

    public void trackTaskStart(Class taskClass) {
        trackEvent(taskClass.getSimpleName() + ".start");
    }

    public void trackTaskSuccess(Class taskClass) {
        trackEvent(taskClass.getSimpleName() + ".success");
    }

    public void trackTaskFailure(Class taskClass, final String message) {
        final HashMap<String, String> failureReason = new HashMap<>();
        failureReason.put(FAILURE_REASON, message);
        trackEvent(taskClass.getSimpleName() + ".failure", failureReason);
    }

    public void traceException(Class taskClass, final Exception exception) {
        String message = exception.getMessage();
        if (StringUtils.isEmpty(message)) {
            message = exception.toString();
        }
        trackTaskFailure(taskClass, message);
    }

    private boolean isFirstRun(Properties prop) {
        try {
            final File configurationFile = new File(CONFIGURATION_PATH);
            if (configurationFile.exists()) {
                try (InputStream input = new FileInputStream(CONFIGURATION_PATH)) {
                    prop.load(input);
                    final String firstRunValue = prop.getProperty(FIRST_RUN_KEY);
                    if (firstRunValue != null && !firstRunValue.isEmpty() && firstRunValue.equalsIgnoreCase("false")) {
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
        try (OutputStream output = new FileOutputStream(CONFIGURATION_PATH)) {
            prop.setProperty(FIRST_RUN_KEY, "false");
            prop.store(output, "Azure Gradle Plugin configurations");
        } catch (Exception e) {
            // catch exceptions here to avoid blocking mojo execution.
            AzureMessager.getMessager().warning(e.getMessage());
        }
    }
}
