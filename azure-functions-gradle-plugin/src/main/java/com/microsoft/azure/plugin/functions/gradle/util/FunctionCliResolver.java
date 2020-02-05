/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.common.function.utils.CommandUtils;
import com.microsoft.azure.common.logging.Log;
import com.microsoft.azure.maven.common.utils.TextUtils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FunctionCliResolver {
    private static final String RUNTIME_NOT_FOUND = "Azure Functions Core Tools not found. " +
            "Please go to https://aka.ms/azfunc-install to install Azure Functions Core Tools first.";

    public static String resolveFunc() throws IOException, InterruptedException {
        final boolean isWindows = CommandUtils.isWindows();
        final List<File> funCmdFiles = resolvePathForCommand("func", isWindows);
        File result = null;
        for (final File file : funCmdFiles) {
            final File canonicalFile = file.getCanonicalFile();
            if (!canonicalFile.exists()) {
                continue;
            }
            // when `func core tools` is manually installed and func is available at PATH
            // use canonical path to locate the real installation path
            result = findFuncExecInFolder(canonicalFile.getParentFile(), isWindows);
            if (result == null) {
                if (isWindows) {
                    result = resolveFuncForWindows(canonicalFile);
                } else {
                    // in linux/mac, when the way of `npm install azure-functions-core-tools`, the canonicalFile will point to `main.js`
                    if (canonicalFile.getName().equals("main.js")) {
                        result = findFuncExecInFolder(Paths.get(canonicalFile.getParent(), "..", "bin").normalize().toFile(),
                                isWindows);
                    }
                }
            }

            if (result != null) {
                return  result.getAbsolutePath();
            }
        }
        Log.warn(TextUtils.red(RUNTIME_NOT_FOUND));
        return null;
    }

    private static File resolveFuncForWindows(final File canonicalFile) {
        if (canonicalFile.getName().equalsIgnoreCase("func.cmd")) {
            return findFuncExecInFolder(
                    Paths.get(canonicalFile.getParent(), "node_modules", "azure-functions-core-tools", "bin")
                            .toFile(),
                    true);
        } else {
            // check chocolate install
            final File libFolder = Paths
                    .get(canonicalFile.getParent(), "..", "lib", "azure-functions-core-tools", "tools")
                    .normalize().toFile();
            return findFuncExecInFolder(libFolder, true);
        }
    }

    private static File findFuncExecInFolder(final File folder, final boolean windows) {
        if (new File(folder, "func.dll").exists()) {
            final File func = new File(folder, windows ? "func.exe" : "func");
            if (func.exists()) {
                return func;
            }
        }
        return null;
    }

    private static List<File> resolvePathForCommand(final String command, final boolean isWindows)
            throws IOException, InterruptedException {
        return extractFileFromOutput(executeMultipleLineOutput((isWindows ? "where " : "which ") + command, isWindows));
    }

    private static List<File> extractFileFromOutput(final String[] outputStrings) {
        final List<File> list = new ArrayList<>();
        for (final String outputLine : outputStrings) {
            if (StringUtils.isBlank(outputLine)) {
                continue;
            }

            final File file = new File(outputLine.replaceAll("\\r|\\n", ""));
            if (!file.exists() || !file.isFile()) {
                continue;
            }

            list.add(file);
        }
        return list;
    }

    private static String[] executeMultipleLineOutput(final String cmd, final boolean isWindows)
            throws IOException, InterruptedException {
        final String[] cmds = isWindows ? new String[] { "cmd.exe", "/c", cmd } : new String[] { "bash", "-c", cmd };
        final Process p = Runtime.getRuntime().exec(cmds);
        final int exitCode = p.waitFor();
        if (exitCode != 0) {
            return new String[0];
        }
        return StringUtils.split(IOUtils.toString(p.getInputStream(), "utf8"));
    }
}
