/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.util;

import org.junit.Test;

public class FunctionCliResolverTest {
    @Test
    public void testResolveFunc() throws Exception {
        final String funcCoreToolPath = FunctionCliResolver.resolveFunc();
        if (funcCoreToolPath != null) {
            System.out.println("Your function core tool is found at: " + funcCoreToolPath);
        } else {
            System.err.println("Azure Functions Core Tools not found. ");
        }
    }
}
