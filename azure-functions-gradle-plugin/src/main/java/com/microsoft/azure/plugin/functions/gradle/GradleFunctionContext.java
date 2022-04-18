/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.gradle.auth.GradleAuthConfig;
import com.microsoft.azure.gradle.auth.GradleAuthHelper;
import com.microsoft.azure.gradle.configuration.GradleDeploymentSlotConfig;
import com.microsoft.azure.gradle.configuration.GradleRuntimeConfig;
import com.microsoft.azure.plugin.functions.gradle.util.GradleProjectUtils;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionAppModule;
import com.microsoft.azure.toolkit.lib.common.IProject;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GradleFunctionContext {
    private static final String FUNCTION_JAVA_VERSION_KEY = "functionJavaVersion";
    private static final String DISABLE_APP_INSIGHTS_KEY = "disableAppInsights";
    private static final String FUNCTION_RUNTIME_KEY = "os";
    private static final String FUNCTION_IS_DOCKER_KEY = "isDockerFunction";
    private static final String FUNCTION_REGION_KEY = "region";
    private static final String FUNCTION_PRICING_KEY = "pricingTier";
    private static final String GRADLE_PLUGIN_POSTFIX = "-gradle-plugin";
    private volatile File stagingDirectory;
    private final JavaProject javaProject;
    private final AzureFunctionsExtension functionsExtension;
    private Map<String, String> appSettings;
    private FunctionAppModule appServiceClient;

    public GradleFunctionContext(Project project, AzureFunctionsExtension functionsExtension) {
        this.functionsExtension = functionsExtension;
        this.javaProject = GradleProjectUtils.convert(project);
    }

    public IProject getProject() {
        return javaProject;
    }

    public FunctionAppModule getOrCreateAzureAppServiceClient() {
        if (appServiceClient == null) {
            try {
                final String subscriptionId = GradleAuthHelper.login(functionsExtension.getAuth(), functionsExtension.getSubscription());
                appServiceClient = Azure.az(AzureAppService.class).functionApps(subscriptionId);
            } catch (AzureToolkitRuntimeException e) {
                throw new AzureToolkitRuntimeException(String.format("Cannot authenticate due to error %s", e.getMessage()), e);
            }
        }
        return appServiceClient;
    }

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

    public String getSubscription() {
        return functionsExtension.getSubscription();
    }

    public String getAppName() {
        return functionsExtension.getAppName();
    }

    public String getResourceGroup() {
        return functionsExtension.getResourceGroup();
    }

    public GradleRuntimeConfig getRuntime() {
        return functionsExtension.getRuntime();
    }

    public String getRegion() {
        return functionsExtension.getRegion();
    }

    public String getPricingTier() {
        return functionsExtension.getPricingTier();
    }

    public String getAppServicePlanResourceGroup() {
        return functionsExtension.getAppServicePlanResourceGroup();
    }

    public String getAppServicePlanName() {
        return functionsExtension.getAppServicePlanName();
    }

    public Map<String, String> getAppSettings() {
        if (appSettings == null) {
            // we need to cache app settings since gradle will always return a new app settings.
            final Map<String, String> map = functionsExtension.getAppSettings() != null ? functionsExtension.getAppSettings() : new HashMap<>();
            // convert <string, object> map to <string, string> map
            appSettings = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Objects.toString(e.getValue(), null)));
        }
        return appSettings;
    }

    public GradleAuthConfig getAuth() {
        return functionsExtension.getAuth();
    }

    public String getDeploymentType() {
        if (this.functionsExtension.getDeployment() == null) {
            return null;
        }

        return functionsExtension.getDeployment().getType();
    }

    public String getAppInsightsInstance() {
        return functionsExtension.getAppInsightsInstance();
    }

    public String getAppInsightsKey() {
        return functionsExtension.getAppInsightsKey();
    }

    public String getDeploymentSlotName() {
        return Optional.ofNullable(functionsExtension.getDeploymentSlot()).map(GradleDeploymentSlotConfig::name).orElse(null);
    }

    public String getDeploymentSlotConfigurationSource() {
        return Optional.ofNullable(functionsExtension.getDeploymentSlot()).map(GradleDeploymentSlotConfig::configurationSource).orElse(null);
    }

    public boolean isDisableAppInsights() {
        return BooleanUtils.isTrue(functionsExtension.isDisableAppInsights());
    }

    public String getLocalDebugConfig() {
        return this.functionsExtension.getLocalDebug();
    }

    public Map<String, String> getTelemetryProperties() {
        final Map<String, String> result = new HashMap<>();
        final GradleRuntimeConfig runtime = getRuntime();
        final String javaVersion = runtime == null ? null : runtime.javaVersion();
        final String os = runtime == null ? null : runtime.os();
        final boolean isDockerFunction = runtime != null && StringUtils.isNotEmpty(runtime.image());
        result.put(FUNCTION_JAVA_VERSION_KEY, StringUtils.isEmpty(javaVersion) ? "" : javaVersion);
        result.put(FUNCTION_RUNTIME_KEY, StringUtils.isEmpty(os) ? "" : os);
        result.put(FUNCTION_IS_DOCKER_KEY, String.valueOf(isDockerFunction));
        result.put(FUNCTION_REGION_KEY, getRegion());
        result.put(FUNCTION_PRICING_KEY, getPricingTier());
        result.put(DISABLE_APP_INSIGHTS_KEY, String.valueOf(isDisableAppInsights()));
        return result;
    }
}
