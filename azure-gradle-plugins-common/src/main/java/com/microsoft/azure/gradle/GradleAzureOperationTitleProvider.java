/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.gradle;

import com.microsoft.azure.toolkit.lib.common.operation.AzureOperationBundle;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

@Slf4j
public class GradleAzureOperationTitleProvider implements AzureOperationBundle.Provider {
    private static final Map<String, Optional<ResourceBundle>> libBundles = new ConcurrentHashMap<>();
    private static final Map<String, Optional<ResourceBundle>> intellijBundles = new ConcurrentHashMap<>();
    private static final GradleAzureOperationTitleProvider INSTANCE = new GradleAzureOperationTitleProvider();
    public static final String ALL = "<ALL>";
    private static boolean assertOnMissedKeys;

    public static void assertOnMissedKeys(boolean doAssert) {
        assertOnMissedKeys = doAssert;
    }

    @Override
    @Nonnull
    public String getMessage(@Nonnull final String key, final Object... params) {
        final String notFound = String.format("!%s!", key);
        final String subGroup = key.split("\\.")[0].replaceAll("\\|", "_");
        final String supGroup = key.split("[|.]")[0];
        final ArrayList<Supplier<String>> suppliers = new ArrayList<>();
        suppliers.add(() -> this.getLibOperationTitle(subGroup, key, params));
        suppliers.add(() -> this.getLibOperationTitle(supGroup, key, params));
        suppliers.add(() -> this.getLibOperationTitle(ALL, key, params));
        for (final Supplier<String> supplier : suppliers) {
            final String title = supplier.get();
            if (Objects.nonNull(title)) {
                return title;
            }
        }
        return notFound;
    }

    public String getLibOperationTitle(@Nonnull final String group, @Nonnull final String key, final Object... params) {
        return libBundles.computeIfAbsent(group, k -> {
            final String bundleName = ALL.equals(group) ?
                "com.microsoft.azure.toolkit.operation.titles" :
                String.format("com.microsoft.azure.toolkit.operation.titles_%s", group);
            return Optional.ofNullable(getBundle(bundleName));
        }).map(b -> messageOrNull(b, key, params)).orElse(null);
    }

    @Nullable
    private ResourceBundle getBundle(String bundleName) {
        try {
            return ResourceBundle.getBundle(bundleName);
        } catch (final Exception e) {
            return null;
        }
    }

    public static void register() {
        AzureOperationBundle.register(INSTANCE);
    }

    @Nullable
    public static String messageOrNull(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
        String value = messageOrDefault(bundle, key, key, params);
        if (key.equals(value)) return null;
        return value;
    }

    public static String messageOrDefault(@Nullable ResourceBundle bundle,
                                          @NotNull String key,
                                          @Nullable String defaultValue,
                                          Object @NotNull ... params) {
        if (bundle == null) return defaultValue;

        String value;
        try {
            value = bundle.getString(key);
        }
        catch (MissingResourceException e) {
            value = useDefaultValue(bundle, key, defaultValue);
        }

        String result = postprocessValue(bundle, value, params);
        return result;
    }

    @NotNull
    static String useDefaultValue(@Nullable ResourceBundle bundle, @NotNull String key, @Nullable String defaultValue) {
        if (defaultValue != null) {
            return defaultValue;
        }

        if (assertOnMissedKeys) {
            log.error("'" + key + "' is not found in " + bundle);
        }
        return "!" + key + "!";
    }

    @NotNull
    static String postprocessValue(@NotNull ResourceBundle bundle, @NotNull String value, Object @NotNull ... params) {
        return String.format(value, params);
    }
}
