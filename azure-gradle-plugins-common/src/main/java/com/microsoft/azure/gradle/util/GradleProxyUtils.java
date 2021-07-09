/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.util;

import com.azure.core.util.Configuration;
import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;

import java.util.Objects;

public class GradleProxyUtils {
    @SuppressWarnings("HttpUrlsUsage")
    public static void configureProxy() {
        final ProxyManager proxyManager = ProxyManager.getInstance();
        if (Objects.nonNull(proxyManager.getProxy())) {
            System.out.printf("Use system proxy: %s:%s%n", TextUtils.cyan(proxyManager.getHttpProxyHost()),
                TextUtils.cyan(Integer.toString(proxyManager.getHttpProxyPort())));

            Configuration.getGlobalConfiguration().put(Configuration.PROPERTY_HTTP_PROXY,
                String.format("http://%s:%s", proxyManager.getHttpProxyHost(), proxyManager.getHttpProxyPort()));
        }
    }
}
