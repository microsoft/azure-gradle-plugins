/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.auth.AzureTokenWrapper;
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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public class GradleFunctionContext implements IAppServiceContext {
    private static final String GRADLE_PLUGIN_POSTFIX = "-gradle-plugin";

    private File stagingDirectory;
    private Azure azure;
    private AzureTokenWrapper credential;

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
                    final String outputFolder = AzureFunctionsPlugin.GRADLE_PLUGIN_NAME.replaceAll(GRADLE_PLUGIN_POSTFIX, "");

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
    public Map<String, String> getAppSettings() {
        if (appSettings == null) {
            // we need to cache app settings since gradle will always return a new app settings.
            final Map<String, Object> map = functionsExtension.getAppSettings() != null ? functionsExtension.getAppSettings() : new HashMap<>();
            // convert <string, object> map to <string, string> map
            appSettings = map.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> Objects.toString(e.getValue(), null)));
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

    @Override
    public String getAppInsightsInstance() {
        return functionsExtension.getAppInsightsInstance();
    }

    @Override
    public String getAppInsightsKey() {
        return functionsExtension.getAppInsightsKey();
    }

    @Override
    public boolean isDisableAppInsights() {
        return BooleanUtils.isTrue(functionsExtension.isDisableAppInsights());
    }

    public String getLocalDebugConfig() {
        return this.functionsExtension.getLocalDebug();
    }

    @Override
    public synchronized Azure getAzureClient() throws AzureExecutionException {
        if (azure == null) {
            try {
                azure = AzureClientFactory.getAzureClient(getAzureTokenWrapper(), getSubscription());
            } catch (AzureLoginFailureException e) {
                throw new AzureExecutionException(e.getMessage(), e);
            }
        }
        final GradleAuthConfiguration auth = functionsExtension.getAuthentication();
        if (azure == null) {
            if (auth != null && StringUtils.isNotBlank(auth.getType())) {
                throw new AzureExecutionException(String.format("Failed to authenticate with Azure using type %s. Please check your configuration.",
                        auth.getType()));
            } else {
                throw new AzureExecutionException("Failed to authenticate with Azure. Please check your configuration.");
            }
        }
        return azure;
    }

    @Override
    public synchronized AzureTokenWrapper getAzureTokenWrapper() throws AzureExecutionException {
        if (credential == null) {
            try {
                final GradleAuthConfiguration auth = functionsExtension.getAuthentication();
                credential = AzureClientFactory.getAzureTokenWrapper(auth != null ? auth.getType() : null, auth);
            } catch (AzureLoginFailureException e) {
                throw new AzureExecutionException(e.getMessage(), e);
            }
        }
        return credential;
    }
}
