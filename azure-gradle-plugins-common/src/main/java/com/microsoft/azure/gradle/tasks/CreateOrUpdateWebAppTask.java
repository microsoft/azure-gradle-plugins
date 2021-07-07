/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.tasks;

import com.microsoft.azure.gradle.appservice.GradleAppServiceConfig;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.appservice.model.DockerConfiguration;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.appservice.model.Runtime;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppServicePlan;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebApp;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CreateOrUpdateWebAppTask extends AzureTask<IWebApp> {
    private static final String CREATE_NEW_WEB_APP = "createNewWebApp";
    private static final String CREATE_NEW_APP_SERVICE_PLAN = "createNewAppServicePlan";
    private static final String CREATE_WEBAPP = "Creating web app %s...";
    private static final String CREATE_WEB_APP_DONE = "Successfully created Web App %s.";
    private static final String UPDATE_WEBAPP = "Updating target Web App %s...";
    private static final String UPDATE_WEBAPP_DONE = "Successfully updated Web App %s.";
    private static final String CREATE_RESOURCE_GROUP = "Creating resource group %s in region %s...";
    private static final String CREATE_RESOURCE_GROUP_DONE = "Successfully created resource group %s.";
    private static final String CREATE_APP_SERVICE_PLAN = "Creating app service plan %s...";
    private static final String CREATE_APP_SERVICE_PLAN_DONE = "Successfully created app service plan %s.";

    private final GradleAppServiceConfig config;
    private final List<AzureTask<?>> subTasks;

    public CreateOrUpdateWebAppTask(GradleAppServiceConfig config) {
        this.config = config;
        this.subTasks = this.initTasks();
    }

    private List<AzureTask<?>> initTasks() {
        final List<AzureTask<?>> tasks = new ArrayList<>();
        tasks.add(new CreateResourceGroupTask(this.config.getSubscriptionId(), this.config.getResourceGroup(), this.config.getRegion()));
        if (!StringUtils.equalsIgnoreCase(this.config.getServicePlanResourceGroup(), this.config.getResourceGroup())) {
            tasks.add(new CreateResourceGroupTask(this.config.getSubscriptionId(), this.config.getServicePlanResourceGroup(), this.config.getRegion()));
        }
        final IAzureMessager messager = AzureMessager.getMessager();
        final String CREATE_WEBP_APP_TITLE = String.format("Create new web app(%s)", messager.value(this.config.getAppName()));

        tasks.add(new AzureTask<>(CREATE_WEBP_APP_TITLE, () -> {
            final IWebApp target = Azure.az(AzureAppService.class).subscription(config.getSubscriptionId())
                .webapp(config.getResourceGroup(), config.getAppName());
            if (!target.exists()) {
                return create();
            }
            return update(target);
        }));
        return tasks;
    }

    private IWebApp create() {
        AzureTelemetry.getActionContext().setProperty(CREATE_NEW_WEB_APP, String.valueOf(true));
        AzureMessager.getMessager().info(String.format(CREATE_WEBAPP, config.getAppName()));
        final AzureAppService az = Azure.az(AzureAppService.class).subscription(config.getSubscriptionId());
        final IWebApp webapp = az.webapp(config.getResourceGroup(), config.getAppName());
        IAppServicePlan appServicePlan = createOrUpdateAppServicePlan(az.appServicePlan(config.getServicePlanResourceGroup(), config.getServicePlanName()));
        final IWebApp result = webapp.create().withName(config.getAppName())
            .withResourceGroup(config.getResourceGroup())
            .withPlan(appServicePlan.id())
            .withRuntime(getRuntime(config.getRuntime()))
            .withDockerConfiguration(getDockerConfiguration(config.getRuntime()))
            .withAppSettings(config.getAppSettings())
            .commit();
        AzureMessager.getMessager().info(String.format(CREATE_WEB_APP_DONE, result.name()));
        return result;
    }

    private IWebApp update(final IWebApp webApp) {
        AzureMessager.getMessager().info(String.format(UPDATE_WEBAPP, webApp.name()));
        final IAppServicePlan currentPlan = webApp.plan();
        IAppServicePlan appServicePlan = createOrUpdateAppServicePlan(currentPlan);
        final IWebApp result = webApp.update().withPlan(appServicePlan.id())
            .withRuntime(getRuntime(config.getRuntime()))
            .withDockerConfiguration(getDockerConfiguration(config.getRuntime()))
            .withAppSettings(ObjectUtils.firstNonNull(config.getAppSettings(), new HashMap<>()))
            .commit();
        AzureMessager.getMessager().info(String.format(UPDATE_WEBAPP_DONE, webApp.name()));
        return result;
    }

    @NotNull
    private IAppServicePlan createOrUpdateAppServicePlan(IAppServicePlan currentPlan) {
        AzureAppService az = Azure.az(AzureAppService.class).subscription(config.getSubscriptionId());
        IAppServicePlan appServicePlan = StringUtils.isEmpty(config.getServicePlanName()) ? currentPlan :
            az.appServicePlan(config.getServicePlanResourceGroup(), config.getServicePlanName());
        final String servicePlanName = config.getServicePlanName();
        if (!appServicePlan.exists()) {
            AzureMessager.getMessager().info(String.format(CREATE_APP_SERVICE_PLAN, servicePlanName));
            AzureTelemetry.getActionContext().setProperty(CREATE_NEW_APP_SERVICE_PLAN, String.valueOf(true));
            appServicePlan.create()
                .withName(servicePlanName)
                .withResourceGroup(config.getServicePlanResourceGroup())
                .withPricingTier(PricingTier.fromString(config.getPricingTier()))
                .withRegion(Region.fromName(config.getRegion()))
                .withOperatingSystem(OperatingSystem.fromString(config.getRuntime().getOs()))
                .commit();
            AzureMessager.getMessager().info(String.format(CREATE_APP_SERVICE_PLAN_DONE, appServicePlan.name()));
        } else if (config.getPricingTier() != null) {
            appServicePlan.update().withPricingTier(PricingTier.fromString(config.getPricingTier())).commit();
        }
        return appServicePlan;
    }

    private DockerConfiguration getDockerConfiguration(GradleRuntimeConfig runtime) {
        if (runtime != null && OperatingSystem.DOCKER == OperatingSystem.fromString(runtime.getOs())) {
            return DockerConfiguration.builder()
                .userName(runtime.getUsername())
                .password(runtime.getPassword())
                .registryUrl(runtime.getRegistryUrl())
                .image(runtime.getImage())
                .startUpCommand(runtime.getStartUpCommand())
                .build();
        }
        return null;

    }

    private Runtime getRuntime(GradleRuntimeConfig runtime) {
        if (runtime != null && OperatingSystem.DOCKER != OperatingSystem.fromString(runtime.getOs())) {
            return Runtime.getRuntime(OperatingSystem.fromString(runtime.getOs()),
                WebContainer.fromString(runtime.getWebContainer()),
                JavaVersion.fromString(runtime.getJavaVersion()));
        }
        return null;
    }

    @Override
    @AzureOperation(name = "webapp|create_update", params = {"this.config.getAppName()"}, type = AzureOperation.Type.SERVICE)
    public IWebApp execute() {
        return (IWebApp) Flux.fromStream(this.subTasks.stream().map(t->t.getSupplier().get())).last().block();
    }
}
