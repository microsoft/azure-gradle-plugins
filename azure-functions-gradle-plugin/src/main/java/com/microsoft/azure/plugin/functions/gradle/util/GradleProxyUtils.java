/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import com.microsoft.azure.tools.auth.exception.InvalidConfigurationException;
import com.microsoft.azure.tools.auth.util.ValidationUtil;
import com.microsoft.azure.tools.common.util.TextUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class GradleProxyUtils {

    public static void initProxyManager(String httpProxyHost, String httpProxyPort) throws InvalidConfigurationException {
        final ProxyManager proxyManager = ProxyManager.getInstance();
        if (!StringUtils.isAllBlank(httpProxyHost, httpProxyPort)) {
            ValidationUtil.validateHttpProxy(httpProxyHost, httpProxyPort);
            proxyManager.configure("user", httpProxyHost, NumberUtils.toInt(httpProxyPort));
        }

        final String source = proxyManager.getSource();
        if (source != null) {
            System.out.println(String.format("Use %s proxy: %s:%s", source, TextUtils.cyan(proxyManager.getHttpProxyHost()),
                    TextUtils.cyan(Integer.toString(proxyManager.getHttpProxyPort()))));
        }
    }
}
