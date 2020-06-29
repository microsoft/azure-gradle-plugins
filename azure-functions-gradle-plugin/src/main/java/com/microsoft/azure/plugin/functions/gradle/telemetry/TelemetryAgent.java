/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.plugin.functions.gradle.telemetry;

import com.microsoft.azure.common.logging.Log;
import com.microsoft.azure.common.utils.GetHashMac;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsPlugin;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class TelemetryAgent implements TelemetryConfiguration {
    public static final String AUTH_INIT_FAILURE = "AuthInitFailure";
    private static final String PLUGIN_NAME_KEY = "pluginName";
    private static final String PLUGIN_VERSION_KEY = "pluginVersion";
    private static final String INSTALLATION_ID_KEY = "installationId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String SUBSCRIPTION_ID_KEY = "subscriptionId";
    private static final String TELEMETRY_NOT_ALLOWED = "TelemetryNotAllowed";
    private static final String AUTH_TYPE_KEY = "authType";
    private static final String AUTH_METHOD_KEY = "authMethod";
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

    private boolean allowTelemetry = true;
    private String sessionId = UUID.randomUUID().toString();
    private String installationId = GetHashMac.getHashMac();
    private String pluginVersion;
    private String subscriptionId;
    private String authType;
    private String authMethod;
    private TelemetryProxy telemetryProxy;

    public static TelemetryAgent instance = new TelemetryAgent();

    private TelemetryAgent() {
        try {
            this.pluginVersion = IOUtils.toString(TelemetryAgent.class.getResource("/version.txt"), Charset.defaultCharset()).trim();
        } catch (IOException e) {
            this.pluginVersion = "unknown";
            Log.error(e);
        }
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getUserAgent() {
        return this.allowTelemetry ?
            String.format("%s/%s %s:%s %s:%s", AzureFunctionsPlugin.GRADLE_PLUGIN_NAME, pluginVersion,
                        INSTALLATION_ID_KEY, installationId, SESSION_ID_KEY, sessionId)
                : String.format("%s/%s", AzureFunctionsPlugin.GRADLE_PLUGIN_NAME, pluginVersion);
    }

    public void initTelemetry() {
        telemetryProxy = new AppInsightsProxy(this);
        if (!allowTelemetry) {
            telemetryProxy.trackEvent(TELEMETRY_NOT_ALLOWED);
            telemetryProxy.disable();
        }
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public void addDefaultProperties(String key, String value) {
        if (telemetryProxy == null) {
            initTelemetry();
        }
        this.telemetryProxy.addDefaultProperty(key, value);
    }

    @Override
    public Map<String, String> getTelemetryProperties() {
        final Map<String, String> map = new HashMap<>();
        map.put(INSTALLATION_ID_KEY, installationId);
        map.put(PLUGIN_NAME_KEY, AzureFunctionsPlugin.GRADLE_PLUGIN_NAME);
        map.put(PLUGIN_VERSION_KEY, getPluginVersion());
        map.put(SUBSCRIPTION_ID_KEY, subscriptionId);
        map.put(SESSION_ID_KEY, sessionId);
        map.put(AUTH_TYPE_KEY, authType);
        map.put(AUTH_METHOD_KEY, authMethod);
        return map;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public void setAuthMethod(String method) {
        this.authMethod = method;
    }

    public void setAllowTelemetry(boolean allowTelemetry) {
        this.allowTelemetry = allowTelemetry;
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
            Log.prompt(PRIVACY_STATEMENT);
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
            Log.debug(e.getMessage());
        }
        return true;
    }

    private void updateConfigurationFile(Properties prop) {
        try (OutputStream output = new FileOutputStream(CONFIGURATION_PATH)) {
            prop.setProperty(FIRST_RUN_KEY, "false");
            prop.store(output, "Azure Maven Plugin configurations");
        } catch (Exception e) {
            // catch exceptions here to avoid blocking mojo execution.
            Log.debug(e.getMessage());
        }
    }
}
