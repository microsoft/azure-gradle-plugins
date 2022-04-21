/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;


import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.AzureFunctionsExtension;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.DeployHandler;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;

public class DeployTask extends DefaultTask implements IFunctionTask {
    private static final String PROXY = "proxy";
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
    @AzureOperation(name = "functionapp.deploy_app", type = AzureOperation.Type.ACTION)
    public void deploy() throws GradleException {
        try {
            ProxyManager.getInstance().applyProxy();
            TelemetryAgent.getInstance().addDefaultProperty(PROXY, String.valueOf(ProxyManager.getInstance().isProxyEnabled()));
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = new GradleFunctionContext(getProject(), this.getFunctionsExtension());
            TelemetryAgent.getInstance().addDefaultProperties(ctx.getTelemetryProperties());
            final DeployHandler deployHandler = new DeployHandler(ctx);
            deployHandler.execute();
            TelemetryAgent.getInstance().trackTaskSuccess(this.getClass());
        } catch (final Exception e) {
            AzureMessager.getMessager().error(e);
            TelemetryAgent.getInstance().traceException(this.getClass(), e);
            throw new GradleException(DEPLOY_FAILURE + e.getMessage(), e);
        }
    }
}
