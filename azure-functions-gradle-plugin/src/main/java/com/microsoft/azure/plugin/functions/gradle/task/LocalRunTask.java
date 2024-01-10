/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.util.FunctionUtils;
import com.microsoft.azure.toolkit.lib.appservice.utils.FunctionCliResolver;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.legacy.function.utils.CommandUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecResult;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;

public class LocalRunTask extends Exec implements IFunctionTask {

    private static final String FUNC_CORE_CLI_NOT_FOUND = "Cannot run functions locally due to error: Azure Functions Core Tools can not be found.";

    private static final String JDWP_DEBUG_PREFIX = "-agentlib:jdwp=";

    private static final String DEFAULT_DEBUG_CONFIG = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005";

    private static final String RUN_FUNCTIONS_FAILURE = "Failed to run Azure Functions. Please checkout console output.";

    @Option(option = "enableDebug", description = "Enable debug when running functions")
    private Boolean enableDebug;

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

    public void setEnableDebug(Boolean enableDebug) {
        this.enableDebug = enableDebug;
    }

    @TaskAction
    @Override
    @AzureOperation(name = "user/functionapp.run")
    public void exec() {
        try {
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            final String cliExec = FunctionCliResolver.resolveFunc();
            if (StringUtils.isEmpty(cliExec)) {
                throw new GradleException(FUNC_CORE_CLI_NOT_FOUND);
            }

            final String stagingFolder = ctx.getDeploymentStagingDirectoryPath();
            FunctionUtils.checkStagingDirectory(stagingFolder);

            if (BooleanUtils.isTrue(this.enableDebug) || StringUtils.isNotEmpty(ctx.getLocalDebugConfig())) {
                this.commandLine(cliExec, "host", "start", "--language-worker", "--",
                    getDebugJvmArgument(ctx.getLocalDebugConfig()));
            } else {
                this.commandLine(cliExec, "host", "start");
            }
            this.setWorkingDir(new File(stagingFolder));
            this.setIgnoreExitValue(true);
            super.exec();
            final int code = Optional.ofNullable(getExecResult()).map(ExecResult::getExitValue).orElse(-1);
            for (final Long validCode : CommandUtils.getValidReturnCodes()) {
                if (validCode != null && validCode.intValue() == code) {
                    TelemetryAgent.getInstance().trackTaskSuccess(this.getClass());
                    return;
                }
            }
            throw new GradleException(RUN_FUNCTIONS_FAILURE);
        } catch (Exception e) {
            TelemetryAgent.getInstance().traceException(this.getClass(), e);
            throw new GradleException("Cannot run functions locally due to error:" + e.getMessage(), e);
        }

    }

    private static String getDebugJvmArgument(String debugConfig) {
        if (StringUtils.isBlank(debugConfig)) {
            return DEFAULT_DEBUG_CONFIG;
        }
        if (debugConfig.contains(JDWP_DEBUG_PREFIX)) {
            return debugConfig;
        }
        return JDWP_DEBUG_PREFIX + debugConfig;
    }
}
