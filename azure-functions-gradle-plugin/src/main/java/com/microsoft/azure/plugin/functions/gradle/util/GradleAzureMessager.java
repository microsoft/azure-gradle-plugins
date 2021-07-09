/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessage;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GradleAzureMessager implements IAzureMessager {

    @Override
    public boolean show(IAzureMessage message) {
        switch (message.getType()) {
            case ALERT:
            case CONFIRM:
            case WARNING:
                log.warn(message.getMessage());
                return true;
            case ERROR:
                log.error(message.getMessage(), ((Throwable) message.getPayload()));
                return true;
            case INFO:
            case SUCCESS:
            default:
                prompt(message.getMessage());
                return true;
        }
    }

    public static void prompt(String message) {
        // legacy code for prompt, will be replaced by new method: Notifier.noticeUser later
        if (log.isInfoEnabled()) {
            log.info(message);
        } else {
            System.out.println(message);
        }
    }

    @Override
    public String value(String val) {
        return TextUtils.cyan(val);
    }
}
