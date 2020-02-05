/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.auth.configuration.AuthConfiguration;
import com.microsoft.azure.auth.exception.AzureLoginFailureException;
import com.microsoft.azure.common.exceptions.AzureExecutionException;
import com.microsoft.azure.common.function.configurations.RuntimeConfiguration;
import com.microsoft.azure.common.project.IProject;
import com.microsoft.azure.common.project.JavaProject;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.plugin.functions.gradle.configuration.auth.AzureClientFactory;
import com.microsoft.azure.plugin.functions.gradle.configuration.auth.GradleAuthConfiguration;
import com.microsoft.azure.plugin.functions.gradle.util.GradleProjectUtils;

import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class GradleFunctionContext implements IAppServiceContext {
    private static final String GRADLE_PLUGIN_POSTFIX = "-gradle-plugin";
    private static final String GRADLE_PLUGIN_NAME = "azure-functions-gradle-plugin";

    private File stagingDirectory;
    private Azure azure;

    private JavaProject javaProject;
    private AzureFunctionsExtension functionsExtension;
    private Map<String, String> appSettings;

    public GradleFunctionContext(Project project, AzureFunctionsExtension functionsExtension)
            throws AzureExecutionException {
        this.functionsExtension = functionsExtension;
        this.javaProject = GradleProjectUtils.convert(project);
    }

    public IProject getProject() {
        return javaProject;
    }

    @Override
    public String getDeploymentStagingDirectoryPath() {
        if (stagingDirectory == null) {
            synchronized (this) {
                if (stagingDirectory == null) {
                    final String outputFolder = GRADLE_PLUGIN_NAME.replaceAll(GRADLE_PLUGIN_POSTFIX, "");

                    final String stagingDirectoryPath = Paths.get(this.javaProject.getBuildDirectory().toString(),
                            outputFolder, this.functionsExtension.getAppName()).toString();

                    stagingDirectory = new File(stagingDirectoryPath);
                    // If staging directory doesn't exist, create one and delete it on exit
                    if (!stagingDirectory.exists()) {
                        stagingDirectory.mkdirs();
                    }
                }
            }
        }
        return stagingDirectory.getPath();
    }

    @Override
    public String getSubscription() {
        return functionsExtension.getSubscription();
    }

    @Override
    public String getAppName() {
        return functionsExtension.getAppName();
    }

    @Override
    public String getResourceGroup() {
        return functionsExtension.getResourceGroup();
    }

    @Override
    public RuntimeConfiguration getRuntime() {
        return functionsExtension.getRuntime();
    }

    @Override
    public String getRegion() {
        return functionsExtension.getRegion();
    }

    @Override
    public String getPricingTier() {
        return functionsExtension.getPricingTier();
    }

    @Override
    public String getAppServicePlanResourceGroup() {
        return functionsExtension.getAppServicePlanResourceGroup();
    }

    @Override
    public String getAppServicePlanName() {
        return functionsExtension.getAppServicePlanName();
    }

    @Override
    public Map getAppSettings() {
        if (appSettings == null) {
            // we need to cache app settings since gradle will always return a new app settings.
            appSettings = functionsExtension.getAppSettings() != null ? new HashMap<>(functionsExtension.getAppSettings()) : new HashMap<>();
        }
        return appSettings;
    }

    @Override
    public AuthConfiguration getAuth() {
        if (functionsExtension.getAuthentication() == null) {
            return null;
        }
        return functionsExtension.getAuthentication();
    }

    @Override
    public String getDeploymentType() {
        if (this.functionsExtension.getDeployment() == null) {
            return null;
        }

        return functionsExtension.getDeployment().getType();
    }

    public String getLocalDebugConfig() {
        return this.functionsExtension.getLocalDebug();
    }

    public boolean isLocalDebugEnabled() {
        return this.functionsExtension.getEnableDebug() != null && this.functionsExtension.getEnableDebug().booleanValue();
    }

    @Override
    public Azure getAzureClient() throws AzureExecutionException {
        if (azure == null) {
            try {
                final GradleAuthConfiguration auth = functionsExtension.getAuthentication();
                azure = AzureClientFactory.getAzureClient(auth.getType(), auth, this.getSubscription());
            } catch (AzureLoginFailureException e) {
                throw new AzureExecutionException(e.getMessage(), e);
            }
        }
        return azure;

    }
}
