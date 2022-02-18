/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.util.FunctionUtils;
import com.microsoft.azure.toolkit.lib.appservice.utils.FunctionCliResolver;
import com.microsoft.azure.toolkit.lib.legacy.function.utils.CommandUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;

public abstract class LocalRunTask extends AbstractAzureTask {

    private static final String FUNC_CORE_CLI_NOT_FOUND = "Cannot run functions locally due to error: Azure Functions Core Tools can not be found.";

    private static final String JDWP_DEBUG_PREFIX = "-agentlib:jdwp=";

    private static final String DEFAULT_DEBUG_CONFIG = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005";

    private static final String RUN_FUNCTIONS_FAILURE = "Failed to run Azure Functions. Please checkout console output.";

    @Option(option = "enableDebug", description = "Enable debug when running functions")
    private Boolean enableDebug;

    public void setEnableDebug(Boolean enableDebug) {
        this.enableDebug = enableDebug;
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void exec() {
        try {
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = createContext();
            final String cliExec = FunctionCliResolver.resolveFunc();
            if (StringUtils.isEmpty(cliExec)) {
                throw new GradleException(FUNC_CORE_CLI_NOT_FOUND);
            }

            final String stagingFolder = ctx.getDeploymentStagingDirectoryPath();
            FunctionUtils.checkStagingDirectory(stagingFolder);
            int code = getExecOperations().exec(spec -> {
                if (BooleanUtils.isTrue(this.enableDebug) || StringUtils.isNotEmpty(ctx.getLocalDebugConfig())) {
                    spec.commandLine(cliExec, "host", "start", "--language-worker", "--",
                            getDebugJvmArgument(ctx.getLocalDebugConfig()));
                } else {
                    spec.commandLine(cliExec, "host", "start");
                }
                spec.setWorkingDir(new File(stagingFolder));
                spec.setIgnoreExitValue(true);
            }).getExitValue();

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
