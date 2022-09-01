/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.auth;

import com.azure.core.management.AzureEnvironment;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.temeletry.TelemetryConstants;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AuthConfiguration;
import com.microsoft.azure.toolkit.lib.auth.AuthType;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.auth.AzureCloud;
import com.microsoft.azure.toolkit.lib.auth.AzureEnvironmentUtils;
import com.microsoft.azure.toolkit.lib.auth.AzureToolkitAuthenticationException;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.InvalidConfigurationException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GradleAuthHelper {
    protected static final String SUBSCRIPTION_TEMPLATE = "Subscription: %s(%s)";
    private static final String USING_AZURE_ENVIRONMENT = "Using Azure environment: %s.";
    private static final String SUBSCRIPTION_NOT_FOUND = "Subscription %s was not found in current account.";
    private static final String INVALID_AZURE_ENVIRONMENT = "Invalid environment string '%s', please replace it with one of " +
        AzureEnvironment.knownEnvironments().stream().map(AzureEnvironmentUtils::getCloudName).collect(Collectors.joining(",")) + ".";

    public static String login(GradleAuthConfig auth, String subscriptionId) {
        try {
            Account account = login(toAuthConfiguration(ObjectUtils.firstNonNull(auth, new GradleAuthConfig())));
            final List<Subscription> subscriptions = account.getSubscriptions();
            final String targetSubscriptionId = getTargetSubscriptionId(subscriptionId, subscriptions, account.getSelectedSubscriptions());
            checkSubscription(subscriptions, targetSubscriptionId);
            Azure.az(AzureAccount.class).account().setSelectedSubscriptions(Collections.singletonList(targetSubscriptionId));
            printCurrentSubscription(account);
            return targetSubscriptionId;
        } catch (InvalidConfigurationException e) {
            throw new AzureToolkitAuthenticationException("Failed to authenticate with Azure due to error: " + e.getMessage());
        }
    }

    private static void printCurrentSubscription(Account account) {
        if (account == null || account.getSelectedSubscriptions().isEmpty()) {
            return;
        }
        final Subscription subscription = account.getSelectedSubscriptions().get(0);
        if (subscription != null) {
            AzureMessager.getMessager().info(String.format(SUBSCRIPTION_TEMPLATE, subscription.getName(), subscription.getId()));
        }
    }

    private static void checkSubscription(List<Subscription> subscriptions, String targetSubscriptionId) {
        if (StringUtils.isEmpty(targetSubscriptionId)) {
            return;
        }
        final Optional<Subscription> optionalSubscription = subscriptions.stream()
            .filter(subscription -> StringUtils.equals(subscription.getId(), targetSubscriptionId))
            .findAny();
        if (!optionalSubscription.isPresent()) {
            throw new AzureToolkitAuthenticationException(String.format(SUBSCRIPTION_NOT_FOUND, targetSubscriptionId));
        }
    }

    private static String getTargetSubscriptionId(String defaultSubscriptionId,
                                                  List<Subscription> subscriptions,
                                                  List<Subscription> selectedSubscriptions) {
        String targetSubscriptionId = defaultSubscriptionId;
        if (StringUtils.isBlank(targetSubscriptionId) && !selectedSubscriptions.isEmpty()) {
            targetSubscriptionId = selectedSubscriptions.stream().filter(Subscription::isSelected).map(Subscription::getId).findFirst().orElse(null);
        }
        if (StringUtils.isBlank(targetSubscriptionId) && !subscriptions.isEmpty()) {
            targetSubscriptionId = subscriptions.stream().map(Subscription::getId).findFirst().orElse(null);
        }
        TelemetryAgent.getInstance().addDefaultProperty(TelemetryConstants.SUBSCRIPTION_ID_KEY, targetSubscriptionId);
        return targetSubscriptionId;
    }

    private static Account login(@Nonnull AuthConfiguration auth) {
        final AzureEnvironment azureEnvironment = AzureEnvironmentUtils.stringToAzureEnvironment(auth.getEnvironment());
        promptAzureEnvironment(azureEnvironment);
        final Account account = accountLogin(auth);
        final boolean isInteractiveLogin = account.getType() == AuthType.OAUTH2 || account.getType() == AuthType.DEVICE_CODE;
        final AzureEnvironment env = account.getEnvironment();
        final String environmentName = AzureEnvironmentUtils.azureEnvironmentToString(env);
        if (env != AzureEnvironment.AZURE && env != azureEnvironment) {
            AzureMessager.getMessager().info(AzureString.format(USING_AZURE_ENVIRONMENT, environmentName));
        }
        printCredentialDescription(account, isInteractiveLogin);
        TelemetryAgent.getInstance().addDefaultProperty(TelemetryConstants.AUTH_TYPE_KEY, Objects.toString(auth.getType()));
        TelemetryAgent.getInstance().addDefaultProperty(TelemetryConstants.AUTH_METHOD_KEY, Objects.toString(account.getType()));
        TelemetryAgent.getInstance().addDefaultProperty(TelemetryConstants.AZURE_ENVIRONMENT_KEY, environmentName);
        return account;
    }

    private static void printCredentialDescription(Account account, boolean skipType) {
        if (skipType) {
            if (CollectionUtils.isNotEmpty(account.getSubscriptions())) {
                final List<Subscription> selectedSubscriptions = account.getSelectedSubscriptions();
                if (selectedSubscriptions != null && selectedSubscriptions.size() == 1) {
                    AzureMessager.getMessager().info(AzureString.format("Default subscription: %s(%s)",
                        selectedSubscriptions.get(0).getName(),
                        selectedSubscriptions.get(0).getId()));
                }
            }
            if (StringUtils.isNotEmpty(account.getUsername())) {
                AzureMessager.getMessager().info(AzureString.format("Username: %s", account.getUsername()));
            }
        } else {
            AzureMessager.getMessager().info(account.toString());
        }
    }

    private static void promptAzureEnvironment(AzureEnvironment env) {
        if (env != null && env != AzureEnvironment.AZURE) {
            AzureMessager.getMessager().info(AzureString.format("Auth environment: %s", AzureEnvironmentUtils.azureEnvironmentToString(env)));
        }
    }

    private static Account accountLogin(AuthConfiguration auth) {
        final AzureAccount azureAccount = Azure.az(AzureAccount.class);
        if (azureAccount.isLoggedIn() || azureAccount.isLoggingIn()) {
            azureAccount.logout();
        }
        if (auth.getEnvironment() != null) {
            Azure.az(AzureCloud.class).set(AzureEnvironmentUtils.stringToAzureEnvironment(auth.getEnvironment()));
        }
        if (auth.getType() == AuthType.DEVICE_CODE) {
            auth.setDeviceCodeConsumer(info -> {
                final String message = StringUtils.replace(info.getMessage(), info.getUserCode(), TextUtils.cyan(info.getUserCode()));
                AzureMessager.getMessager().info(message);
            });
        }
        if (auth.getType() == AuthType.AUTO) {
            if (StringUtils.isAllBlank(auth.getCertificate(), auth.getCertificatePassword(), auth.getKey())) {
                return azureAccount.login(auth, false);
            } else {
                auth.setType(AuthType.SERVICE_PRINCIPAL);
                return azureAccount.login(auth);
            }
        } else {
            return azureAccount.login(auth, false);
        }
    }

    private static AuthConfiguration toAuthConfiguration(GradleAuthConfig gradleAuthConfig) throws InvalidConfigurationException {
        final AuthConfiguration authConfiguration = new AuthConfiguration(AuthType.parseAuthType(gradleAuthConfig.getType()));
        authConfiguration.setClient(gradleAuthConfig.getClient());
        authConfiguration.setTenant(gradleAuthConfig.getTenant());
        authConfiguration.setCertificate(gradleAuthConfig.getCertificate());
        authConfiguration.setCertificatePassword(gradleAuthConfig.getCertificatePassword());
        authConfiguration.setKey(gradleAuthConfig.getKey());
        authConfiguration.setEnvironment(gradleAuthConfig.getEnvironment());
        if (StringUtils.isNotBlank(gradleAuthConfig.getEnvironment()) && Objects.isNull(authConfiguration.getEnvironment())) {
            throw new InvalidConfigurationException(String.format(INVALID_AZURE_ENVIRONMENT, gradleAuthConfig.getEnvironment()));
        }
        return authConfiguration;
    }
}
