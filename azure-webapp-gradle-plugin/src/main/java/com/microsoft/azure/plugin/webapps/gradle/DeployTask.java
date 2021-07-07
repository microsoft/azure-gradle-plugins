/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.microsoft.azure.gradle.auth.GradleAuthHelper;
import com.microsoft.azure.gradle.configuration.GradleWebAppConfig;
import com.microsoft.azure.gradle.tasks.CreateOrUpdateWebAppTask;
import com.microsoft.azure.gradle.tasks.DeployWebAppTask;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.util.GradleAzureMessager;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.model.WebAppArtifact;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebApp;
import com.microsoft.azure.toolkit.lib.appservice.utils.Utils;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.proxy.ProxyManager;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Collections;

public class DeployTask extends DefaultTask {
    @Setter
    private AzureWebappPluginExtension azureWebappExtension;

    @Setter
    private String artifactFile;

    @TaskAction
    public void deploy() throws GradleException {
        initTask();
        final GradleWebAppConfig config = parseConfiguration();
        fillDefaultValue(config);
        validate(config);
        config.setSubscriptionId(GradleAuthHelper.login(azureWebappExtension.getAuth(), config.getSubscriptionId()));
        validateOnline(config);
        final IWebApp target = createOrUpdateWebapp(config);
        deployArtifact(target, config);
    }

    private void deployArtifact(IWebApp target, GradleWebAppConfig config) {
        new DeployWebAppTask(target, config.getWebAppArtifacts()).execute();
    }

    private void validateOnline(GradleWebAppConfig config) {
    }

    private void validate(GradleWebAppConfig config) {
    }

    private IWebApp createOrUpdateWebapp(GradleWebAppConfig config) {
        return new CreateOrUpdateWebAppTask(config).execute();
    }

    private void initTask() {
        ProxyManager.getInstance().init();
        AzureMessager.setDefaultMessager(new GradleAzureMessager());
        Azure.az().config().setLogLevel(HttpLogDetailLevel.NONE.name());
        Azure.az().config().setUserAgent(TelemetryAgent.getInstance().getUserAgent());
    }

    private GradleWebAppConfig parseConfiguration() {
        final AzureWebappPluginExtension ctx = this.azureWebappExtension;
        GradleWebAppConfig config = new GradleWebAppConfig();
        config.setSubscriptionId(ctx.getSubscription());
        config.setResourceGroup(ctx.getResourceGroup());
        config.setAppName(ctx.getAppName());
        config.setPricingTier(ctx.getPricingTier());
        config.setRegion(ctx.getRegion());
        config.setRuntime(ctx.getRuntime());
        config.setAppSettings(ctx.getAppSettings());
        config.setServicePlanName(ctx.getAppServicePlanName());
        config.setServicePlanResourceGroup(StringUtils.firstNonBlank(ctx.getAppServicePlanResourceGroup(), ctx.getResourceGroup()));
        File file = new File(this.artifactFile);
        if (!file.exists()) {
            throw new AzureToolkitRuntimeException(String.format("artifact file(%s) cannot be found.", file.getAbsolutePath()));
        }
        final WebAppArtifact webAppArtifact = WebAppArtifact.builder()
            .deployType(Utils.getDeployTypeByFileExtension(file))
            .file(file).build();

        config.setWebAppArtifacts(Collections.singletonList(webAppArtifact));
        return config;
    }

    private void fillDefaultValue(GradleWebAppConfig config) {
        if (StringUtils.isBlank(config.getServicePlanName())) {
            config.setServicePlanName(String.format("asp-%s", config.getAppName()));
        }

        if (StringUtils.isBlank(config.getServicePlanResourceGroup())) {
            config.setServicePlanResourceGroup(config.getResourceGroup());
        }
    }
}
