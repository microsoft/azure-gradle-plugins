/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.common.exceptions.AzureExecutionException;
import com.microsoft.azure.common.function.utils.CommandUtils;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.util.FunctionCliResolver;
import com.microsoft.azure.plugin.functions.gradle.util.FunctionUtils;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;

public class LocalRunTask extends Exec implements IFunctionTask {

    private static final String RUN_FUNCTIONS_FAILURE = "Failed to run Azure Functions. Please checkout console output.";

    @Nullable
    private AzureFunctionsExtension functionsExtension;

    public IFunctionTask setFunctionsExtension(final AzureFunctionsExtension functionsExtension) {
        this.functionsExtension = functionsExtension;
        return this;
    }

    @Nested
    @Nullable
    public AzureFunctionsExtension getFunctionsExtension() {
        return functionsExtension;
    }

    @TaskAction
    @Override
    protected void exec() {
        try {
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            final String cliExec = FunctionCliResolver.resolveFunc();
            if (StringUtils.isEmpty(cliExec)) {
                throw new GradleException(
                        "Cannot run functions locally due to error: Azure Functions Core Tools can not be found.");
            }

            final String stagingFolder = ctx.getDeploymentStagingDirectoryPath();
            FunctionUtils.checkStagingDirectory(stagingFolder);

            if (StringUtils.isNotEmpty(ctx.getLocalDebugConfig())) {
                this.commandLine(cliExec, "host", "start", "--language-worker", "--",
                        getEnableDebugJvmArgument(ctx.getLocalDebugConfig()));
            } else {
                this.commandLine(cliExec, "host", "start");
            }
            this.setWorkingDir(new File(stagingFolder));
            this.setIgnoreExitValue(true);
            super.exec();
            final int code = this.getExecResult().getExitValue();
            for (final Long validCode : CommandUtils.getValidReturnCodes()) {
                if (validCode != null && validCode.intValue() == code) {
                    return;
                }
            }
            throw new GradleException(RUN_FUNCTIONS_FAILURE);
        } catch (AzureExecutionException | IOException | InterruptedException e) {
            throw new GradleException("Cannot run functions locally due to error:" + e.getMessage(), e);
        }

    }

    private static String getEnableDebugJvmArgument(String debugConfig) {
        if (debugConfig.contains("-agentlib:jdwp=")) {
            return debugConfig;
        }
        return "-agentlib:jdwp=" + debugConfig;
    }
}