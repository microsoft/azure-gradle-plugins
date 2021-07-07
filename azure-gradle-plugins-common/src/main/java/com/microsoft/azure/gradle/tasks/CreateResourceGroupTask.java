/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.gradle.tasks;

import com.azure.core.management.exception.ManagementException;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.ResourceGroup;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry;
import com.microsoft.azure.toolkit.lib.resource.AzureGroup;


/**
 * Create the resource group if the specified resource group name doesn't exist:
 * `az group create -l westus -n MyResourceGroup`
 */
public class CreateResourceGroupTask extends AzureTask<ResourceGroup> {
    private static final String CREATE_NEW_RESOURCE_GROUP_KEY = "createNewResourceGroup";
    private static final String CREATE_RESOURCE_GROUP = "Creating resource group(%s) in region (%s)...";
    private static final String CREATE_RESOURCE_GROUP_DONE = "Successfully created resource group (%s).";
    private final String subscriptionId;
    private final String resourceGroupName;
    private final String region;

    public CreateResourceGroupTask(String subscriptionId, String resourceGroupName, String region) {
        this.subscriptionId = subscriptionId;
        this.resourceGroupName = resourceGroupName;
        this.region = region;
    }

    @Override
    @AzureOperation(name = "group.create_if_not_exists", params = {"this.resourceGroupName"}, type = AzureOperation.Type.SERVICE)
    public ResourceGroup execute() {
        final AzureGroup az = Azure.az(AzureGroup.class).subscription(subscriptionId);
        try {
            return az.getByName(resourceGroupName);
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() != 404) {
                throw e;
            }
            AzureMessager.getMessager().info(String.format(CREATE_RESOURCE_GROUP, resourceGroupName, region));
            AzureTelemetry.getActionContext().setProperty(CREATE_NEW_RESOURCE_GROUP_KEY, String.valueOf(true));
            final ResourceGroup result = az.create(resourceGroupName, region);
            AzureMessager.getMessager().info(String.format(CREATE_RESOURCE_GROUP_DONE, result.getName()));
            return result;
        }
    }
}
