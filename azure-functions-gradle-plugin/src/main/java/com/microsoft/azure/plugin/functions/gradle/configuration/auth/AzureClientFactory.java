/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.plugin.functions.gradle.configuration.auth;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.auth.AzureAuthHelper;
import com.microsoft.azure.auth.AzureTokenWrapper;
import com.microsoft.azure.auth.configuration.AuthConfiguration;
import com.microsoft.azure.auth.configuration.AuthType;
import com.microsoft.azure.auth.exception.AzureLoginFailureException;
import com.microsoft.azure.common.logging.Log;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.Azure.Authenticated;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.plugin.functions.gradle.telemetry.TelemetryAgent;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

public class AzureClientFactory {
    private static final String CLOUD_SHELL_ENV_KEY = "ACC_CLOUD";
    private static final String AZURE_FOLDER = ".azure";
    private static final String AZURE_PROFILE_NAME = "azureProfile.json";
    private static final String INVALID_AUTH_TYPE = "'%s' is not a valid auth type for Azure Gradle plugins, " +
            "supported values are %s. Will use 'auto' by default.";
    private static final String SUBSCRIPTION_TEMPLATE = "Subscription : %s(%s)";
    private static final String SUBSCRIPTION_NOT_FOUND = "Subscription %s was not found in current account.";
    private static final String NO_AVAILABLE_SUBSCRIPTION = "No available subscription found in current account.";
    // TODO: we need to change this link when wiki for gradle plugin is ready
    private static final String SUBSCRIPTION_NOT_SPECIFIED = "Subscription ID was not specified, using the first subscription in current account," +
            " please refer https://github.com/microsoft/azure-maven-plugins/wiki/Authentication#subscription for more information.";
    private static final String UNSUPPORTED_AZURE_ENVIRONMENT = "Unsupported Azure environment %s, using Azure by default.";
    private static final String AZURE_ENVIRONMENT = "azureEnvironment";
    private static final String USING_AZURE_ENVIRONMENT = "Using Azure environment : %s.";

    public static AzureTokenWrapper getAzureTokenWrapper(String type, AuthConfiguration auth) throws AzureLoginFailureException {
        TelemetryAgent.instance.setAuthType(type);
        final String environmentParameter = auth == null ? null : auth.getEnvironment();
        final AzureEnvironment environment;
        if (!AzureAuthHelper.validateEnvironment(environmentParameter)) {
            Log.prompt(String.format(UNSUPPORTED_AZURE_ENVIRONMENT, environmentParameter));
            environment = AzureEnvironment.AZURE;
        } else {
            environment = AzureAuthHelper.getAzureEnvironment(environmentParameter);
        }
        final String environmentName = AzureAuthHelper.getAzureEnvironmentDisplayName(environment);
        if (environment != AzureEnvironment.AZURE) {
            Log.prompt(String.format(USING_AZURE_ENVIRONMENT, environmentName));
        }
        TelemetryAgent.instance.addDefaultProperties(AZURE_ENVIRONMENT, environmentName);
        return getAuthTypeEnum(type).getAzureToken(auth, environment);
    }

    public static Azure getAzureClient(AzureTokenWrapper azureTokenWrapper, String subscriptionId) throws AzureLoginFailureException {
        try {
            if (azureTokenWrapper != null) {
                TelemetryAgent.instance.setAuthMethod(azureTokenWrapper.getAuthMethod().name());
            }
            return azureTokenWrapper == null ? null : getAzureClientInner(azureTokenWrapper, subscriptionId);
        } catch (IOException e) {
            TelemetryAgent.instance.trackEvent(TelemetryAgent.AUTH_INIT_FAILURE);
            throw new AzureLoginFailureException(e.getMessage());
        }
    }

    private static Azure getAzureClientInner(AzureTokenWrapper azureTokenCredentials, String subscriptionId) throws IOException, AzureLoginFailureException {
        Preconditions.checkNotNull(azureTokenCredentials, "The parameter 'azureTokenCredentials' cannot be null.");
        Log.prompt(azureTokenCredentials.getCredentialDescription());
        final Authenticated authenticated = Azure.configure().withUserAgent(TelemetryAgent.instance.getUserAgent()).authenticate(azureTokenCredentials);
        // For cloud shell, use subscription in profile as the default subscription.
        if (StringUtils.isEmpty(subscriptionId) && isInCloudShell()) {
            subscriptionId = getSubscriptionOfCloudShell();
        }
        subscriptionId = StringUtils.isEmpty(subscriptionId) ? azureTokenCredentials.defaultSubscriptionId() : subscriptionId;
        final Azure azureClient = StringUtils.isEmpty(subscriptionId) ? authenticated.withDefaultSubscription() :
                authenticated.withSubscription(subscriptionId);
        checkSubscription(azureClient, subscriptionId);
        final Subscription subscription = azureClient.getCurrentSubscription();
        Log.prompt(String.format(SUBSCRIPTION_TEMPLATE, subscription.displayName(), subscription.subscriptionId()));
        return azureClient;
    }

    private static boolean isInCloudShell() {
        return System.getenv(CLOUD_SHELL_ENV_KEY) != null;
    }

    private static void checkSubscription(Azure azure, String targetSubscription) throws AzureLoginFailureException {
        final PagedList<Subscription> subscriptions = azure.subscriptions().list();
        subscriptions.loadAll();
        if (subscriptions.size() == 0) {
            throw new AzureLoginFailureException(NO_AVAILABLE_SUBSCRIPTION);
        }
        if (StringUtils.isEmpty(targetSubscription)) {
            Log.prompt(SUBSCRIPTION_NOT_SPECIFIED);
            return;
        }
        final Optional<Subscription> optionalSubscription = subscriptions.stream()
                .filter(subscription -> StringUtils.equals(subscription.subscriptionId(), targetSubscription))
                .findAny();
        if (!optionalSubscription.isPresent()) {
            throw new AzureLoginFailureException(String.format(SUBSCRIPTION_NOT_FOUND, targetSubscription));
        }
    }

    private static AuthType getAuthTypeEnum(String authType) {
        if (StringUtils.isEmpty(authType)) {
            return AuthType.AUTO;
        }
        AuthType result = Arrays.stream(AuthType.getValidAuthTypes())
                .filter(authTypeEnum -> StringUtils.equalsAnyIgnoreCase(authTypeEnum.name(), authType))
                .findFirst().orElse(null);
        if (result == null) {
            final String validAuthTypes = Arrays.stream(AuthType.getValidAuthTypes())
                    .map(authTypeEnum -> String.format("'%s'", StringUtils.lowerCase(authTypeEnum.name())))
                    .collect(Collectors.joining(", "));
            Log.prompt(String.format(INVALID_AUTH_TYPE, authType, validAuthTypes));
            result = AuthType.AUTO;
        }
        return result;
    }

    private static String getSubscriptionOfCloudShell() throws IOException {
        final JsonObject subscription = getDefaultSubscriptionObject();
        return subscription == null ? null : subscription.getAsJsonPrimitive("id").getAsString();
    }

    private static JsonObject getDefaultSubscriptionObject() throws IOException {
        final File azureProfile = Paths.get(System.getProperty("user.home"),
                AZURE_FOLDER, AZURE_PROFILE_NAME).toFile();
        try (final FileInputStream fis = new FileInputStream(azureProfile);
             final Scanner scanner = new Scanner(new BOMInputStream(fis))) {
            final String jsonProfile = scanner.useDelimiter("\\Z").next();
            final JsonArray subscriptionList = (new Gson()).fromJson(jsonProfile, JsonObject.class)
                    .getAsJsonArray("subscriptions");
            for (final JsonElement child : subscriptionList) {
                final JsonObject subscription = (JsonObject) child;
                if (subscription.getAsJsonPrimitive("isDefault").getAsBoolean()) {
                    return subscription;
                }
            }
        }
        return null;
    }
}
