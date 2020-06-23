/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;

public interface IFunctionTask {
    IFunctionTask setFunctionsExtension(AzureFunctionsExtension functionsExtension);

    AzureFunctionsExtension getFunctionsExtension();
}
