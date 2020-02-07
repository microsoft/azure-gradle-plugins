/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.common.exceptions.AzureExecutionException;
import com.microsoft.azure.common.logging.Log;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.DeployHandler;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;

public class DeployTask extends DefaultTask implements IFunctionTask {

    private static final String DEPLOY_FAILURE = "Cannot deploy functions due to error: ";

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
    public void deploy() throws GradleException {
        try {
            checkJavaVersion();
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            final DeployHandler deployHandler = new DeployHandler(ctx);
            deployHandler.execute();
        } catch (final AzureExecutionException e) {
            Log.error(e);
            throw new GradleException(DEPLOY_FAILURE + e.getMessage(), e);
        }
    }

}
