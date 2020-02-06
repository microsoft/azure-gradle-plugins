/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.applicationinsights.core.dependencies.apachecommons.io.FileUtils;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.PackageHandler;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;

import java.io.File;

public class PackageTask extends DefaultTask implements IFunctionTask {
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
    public void deploy() throws Exception {
        try {
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            final File stagingFolder = new File(ctx.getDeploymentStagingDirectoryPath());
            // package task will start from a empty staging folder
            if (stagingFolder.exists()) {
                FileUtils.cleanDirectory(stagingFolder);
            } else {
                stagingFolder.mkdirs();
            }
            final PackageHandler packageHandler = new PackageHandler(ctx.getProject(), ctx.getDeploymentStagingDirectoryPath());
            packageHandler.execute();
        } catch (final Exception ex) {
            this.getLogger().error(ex.getMessage());
        }
    }

}
