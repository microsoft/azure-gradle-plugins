/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.util;

import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessage;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;
import lombok.AllArgsConstructor;
import org.gradle.api.logging.Logger;

@AllArgsConstructor
public class GradleAzureMessager implements IAzureMessager {
    private Logger log;

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
                log.lifecycle(message.getMessage());
                return true;
        }
    }

    @Override
    public String value(String val) {
        return TextUtils.cyan(val);
    }
}
