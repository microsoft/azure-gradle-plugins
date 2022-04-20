/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.auth;

import com.azure.core.management.AzureEnvironment;
import com.azure.identity.DeviceCodeInfo;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.temeletry.TelemetryConstants;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.auth.AzureCloud;
import com.microsoft.azure.toolkit.lib.auth.core.devicecode.DeviceCodeAccount;
import com.microsoft.azure.toolkit.lib.auth.exception.AzureToolkitAuthenticationException;
import com.microsoft.azure.toolkit.lib.auth.exception.InvalidConfigurationException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthConfiguration;
import com.microsoft.azure.toolkit.lib.auth.model.AuthType;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

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
            Azure.az(AzureAccount.class).account().selectSubscription(Collections.singletonList(targetSubscriptionId));
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
        promptAzureEnvironment(auth.getEnvironment());
        final Account account = accountLogin(auth);
        final boolean isInteractiveLogin = account.getAuthType() == AuthType.OAUTH2 || account.getAuthType() == AuthType.DEVICE_CODE;
        final AzureEnvironment env = account.getEnvironment();
        final String environmentName = AzureEnvironmentUtils.azureEnvironmentToString(env);
        if (env != AzureEnvironment.AZURE && env != auth.getEnvironment()) {
            AzureMessager.getMessager().info(AzureString.format(USING_AZURE_ENVIRONMENT, environmentName));
        }
        printCredentialDescription(account, isInteractiveLogin);
        TelemetryAgent.getInstance().addDefaultProperty(TelemetryConstants.AUTH_TYPE_KEY, Objects.toString(auth.getType()));
        TelemetryAgent.getInstance().addDefaultProperty(TelemetryConstants.AUTH_METHOD_KEY, Objects.toString(account.getAuthType()));
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
            if (StringUtils.isNotEmpty(account.getEntity().getEmail())) {
                AzureMessager.getMessager().info(AzureString.format("Username: %s", account.getEntity().getEmail()));
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
        if (auth.getEnvironment() != null) {
            Azure.az(AzureCloud.class).set(auth.getEnvironment());
        }
        if (auth.getType() == null || auth.getType() == AuthType.AUTO) {
            if (StringUtils.isAllBlank(auth.getCertificate(), auth.getCertificatePassword(), auth.getKey())) {
                final Account account = findFirstAvailableAccount().block();
                if (account == null) {
                    throw new AzureToolkitAuthenticationException("There are no accounts available.");
                }
                promptForOAuthOrDeviceCodeLogin(account.getAuthType());
                return handleDeviceCodeAccount(Azure.az(AzureAccount.class).loginAsync(account, false).block());
            } else {
                return doServicePrincipalLogin(auth);
            }
        } else {
            promptForOAuthOrDeviceCodeLogin(auth.getType());
            return handleDeviceCodeAccount(Azure.az(AzureAccount.class).loginAsync(auth, false).block());
        }
    }

    private static Account doServicePrincipalLogin(AuthConfiguration auth) {
        auth.setType(AuthType.SERVICE_PRINCIPAL);
        return Azure.az(AzureAccount.class).login(auth).account();
    }

    private static Account handleDeviceCodeAccount(Account account) {
        if (account instanceof DeviceCodeAccount) {
            final DeviceCodeAccount deviceCodeAccount = (DeviceCodeAccount) account;
            final DeviceCodeInfo challenge = deviceCodeAccount.getDeviceCode();
            AzureMessager.getMessager().info(
                AzureString.format(StringUtils.replace(challenge.getMessage(), challenge.getUserCode(), "%s"), challenge.getUserCode()));
        }
        return account.continueLogin().block();
    }

    private static void promptForOAuthOrDeviceCodeLogin(AuthType authType) {
        if (authType == AuthType.OAUTH2 || authType == AuthType.DEVICE_CODE) {
            AzureMessager.getMessager().info(AzureString.format("Auth type: %s", authType.toString()));
        }
    }

    private static Mono<Account> findFirstAvailableAccount() {
        final List<Account> accounts = Azure.az(AzureAccount.class).accounts();
        if (accounts.isEmpty()) {
            return Mono.error(new AzureToolkitAuthenticationException("There are no accounts available."));
        }
        Mono<Account> current = checkAccountAvailable(accounts.get(0));
        for (int i = 1; i < accounts.size(); i++) {
            final Account ac = accounts.get(i);
            current = current.onErrorResume(e -> checkAccountAvailable(ac));
        }
        return current;
    }

    private static Mono<Account> checkAccountAvailable(Account account) {
        return account.checkAvailable().map(avail -> {
            if (avail) {
                return account;
            }
            throw new AzureToolkitAuthenticationException(String.format("Cannot login with auth type: %s", account.getAuthType()));
        });
    }

    private static AuthConfiguration toAuthConfiguration(GradleAuthConfig gradleAuthConfig) throws InvalidConfigurationException {
        final AuthConfiguration authConfiguration = new AuthConfiguration();
        authConfiguration.setClient(gradleAuthConfig.getClient());
        authConfiguration.setTenant(gradleAuthConfig.getTenant());
        authConfiguration.setCertificate(gradleAuthConfig.getCertificate());
        authConfiguration.setCertificatePassword(gradleAuthConfig.getCertificatePassword());
        authConfiguration.setKey(gradleAuthConfig.getKey());
        final String authTypeStr = gradleAuthConfig.getType();
        authConfiguration.setType(AuthType.parseAuthType(authTypeStr));
        authConfiguration.setEnvironment(AzureEnvironmentUtils.stringToAzureEnvironment(gradleAuthConfig.getEnvironment()));
        if (StringUtils.isNotBlank(gradleAuthConfig.getEnvironment()) && Objects.isNull(authConfiguration.getEnvironment())) {
            throw new InvalidConfigurationException(String.format(INVALID_AZURE_ENVIRONMENT, gradleAuthConfig.getEnvironment()));
        }
        return authConfiguration;
    }
}
