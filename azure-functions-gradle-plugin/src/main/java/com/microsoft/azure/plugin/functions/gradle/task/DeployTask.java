/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.task;


import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.plugin.functions.gradle.GradleFunctionContext;
import com.microsoft.azure.plugin.functions.gradle.handler.DeployHandler;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

public abstract class DeployTask extends AbstractAzureTask {

    private static final String DEPLOY_FAILURE = "Cannot deploy functions due to error: ";

    @TaskAction
    public void deploy() throws GradleException {
        try {
            ProxyManager.getInstance().applyProxy();
            TelemetryAgent.getInstance().trackTaskStart(this.getClass());
            final GradleFunctionContext ctx = createContext();
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
