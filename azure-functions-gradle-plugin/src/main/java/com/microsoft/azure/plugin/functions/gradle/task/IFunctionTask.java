/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.common.logging.Log;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;

public interface IFunctionTask {
    static final String JDK_VERSION_ERROR = "Azure Functions only support JDK 8, which is lower than local " +
            "JDK version %s";
    IFunctionTask setFunctionsExtension(AzureFunctionsExtension functionsExtension);

    AzureFunctionsExtension getFunctionsExtension();

    public default void checkJavaVersion() {
        final String javaVersion = System.getProperty("java.version");
        if (!javaVersion.startsWith("1.8")) {
            Log.error(String.format(JDK_VERSION_ERROR, javaVersion));
        }
    }
}
