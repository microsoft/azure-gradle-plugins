/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.PackageHandler;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;
import java.io.File;

public class PackageTask extends DefaultTask implements IFunctionTask {
    private static final String PACKAGE_FAILURE = "Cannot package functions due to error: ";
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
    @AzureOperation(name = "functionapp.package", type = AzureOperation.Type.ACTION)
    public void build() throws GradleException {
        try {
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            TelemetryAgent.getInstance().addDefaultProperties(ctx.getTelemetryProperties());
            final File stagingFolder = new File(ctx.getDeploymentStagingDirectoryPath());
            // package task will start from a empty staging folder
            if (stagingFolder.exists()) {
                FileUtils.cleanDirectory(stagingFolder);
            } else {
                stagingFolder.mkdirs();
            }
            final PackageHandler packageHandler = new PackageHandler(ctx.getProject(), ctx.getDeploymentStagingDirectoryPath());
            packageHandler.execute();
            TelemetryAgent.getInstance().trackTaskSuccess(this.getClass());
        } catch (Exception e) {
            TelemetryAgent.getInstance().traceException(this.getClass(), e);
            throw new GradleException(PACKAGE_FAILURE + e.getMessage(), e);
        }
    }

}
