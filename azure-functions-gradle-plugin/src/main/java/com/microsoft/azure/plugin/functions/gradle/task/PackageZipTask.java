/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;

import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.PackageHandler;
import com.microsoft.azure.plugin.functions.gradle.util.FunctionUtils;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.zip.ZipUtil;

import javax.annotation.Nullable;
import java.io.File;

public class PackageZipTask extends DefaultTask implements IFunctionTask {
    private static final String PACKAGE_ZIP_FAILURE = "Cannot build zip for azure functions due to error: ";

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
    public void buildZip() throws GradleException {
        try {
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            FunctionUtils.checkStagingDirectory(ctx.getDeploymentStagingDirectoryPath());
            final File zipFile = new File(ctx.getDeploymentStagingDirectoryPath() + ".zip");
            ZipUtil.pack(new File(ctx.getDeploymentStagingDirectoryPath()), zipFile);
            ZipUtil.removeEntry(zipFile, PackageHandler.LOCAL_SETTINGS_JSON);
            AzureMessager.getMessager().info("Build zip from staging folder successfully: " + zipFile.getAbsolutePath());
            TelemetryAgent.getInstance().trackTaskSuccess(this.getClass());
        } catch (Exception e) {
            TelemetryAgent.getInstance().traceException(this.getClass(), e);
            throw new GradleException(PACKAGE_ZIP_FAILURE + e.getMessage(), e);
        }
    }
}
