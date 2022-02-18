/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.PackageHandler;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public abstract class PackageTask extends AbstractAzureTask {
    private static final String PACKAGE_FAILURE = "Cannot package functions due to error: ";

    @TaskAction
    public void build() throws GradleException {
        try {
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = createContext();
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
