/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.plugin.functions.gradle.handler.PackageHandler;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;

import java.io.File;

public class FunctionUtils {
    private static final String STAGE_DIR_FOUND = "Azure Function App's staging directory found at: ";
    private static final String STAGE_DIR_NOT_FOUND =
            "Stage directory not found. Please run 'gradle azureFunctionsPackage first.";
    private static final String HOST_JSON_NOT_FOUND = "File 'host.json' cannot be found at staging directory.";
    private static final String LOCAL_SETTINGS_JSON_NOT_FOUND = "File 'local.settings.json' cannot be found at staging directory.";

    public static void checkStagingDirectory(String stagingFolder) throws AzureExecutionException {
        final File file = new File(stagingFolder);
        if (!file.exists() || !file.isDirectory()) {
            throw new AzureExecutionException(STAGE_DIR_NOT_FOUND);
        }
        AzureMessager.getMessager().info(STAGE_DIR_FOUND + stagingFolder);
        final File hostJson = new File(file, PackageHandler.HOST_JSON);
        if (!hostJson.exists() || !hostJson.isFile()) {
            throw new AzureExecutionException(HOST_JSON_NOT_FOUND);
        }

        final File localSettingsJson = new File(file, PackageHandler.LOCAL_SETTINGS_JSON);
        if (!localSettingsJson.exists() || !localSettingsJson.isFile()) {
            throw new AzureExecutionException(LOCAL_SETTINGS_JSON_NOT_FOUND);
        }
    }
}
