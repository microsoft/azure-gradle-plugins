/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.util.GradleAzureMessager;
import com.microsoft.azure.plugin.functions.gradle.task.DeployTask;
import com.microsoft.azure.plugin.functions.gradle.task.LocalRunTask;
import com.microsoft.azure.plugin.functions.gradle.task.PackageTask;
import com.microsoft.azure.plugin.functions.gradle.task.PackageZipTask;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.cache.CacheEvict;
import com.microsoft.azure.toolkit.lib.common.cache.CacheManager;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.utils.InstallationIdUtils;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.microsoft.azure.gradle.temeletry.TelemetryConstants.INSTALLATION_ID_KEY;
import static com.microsoft.azure.gradle.temeletry.TelemetryConstants.SESSION_ID_KEY;

public class AzureFunctionsPlugin implements Plugin<Project> {
    public static final String GRADLE_PLUGIN_NAME = "azure-functions-gradle-plugin";
    private static final String GRADLE_FUNCTION_EXTENSION = "azurefunctions";

    @Override
    public void apply(final Project project) {
        final AzureFunctionsExtension extension = project.getExtensions().create(GRADLE_FUNCTION_EXTENSION,
                AzureFunctionsExtension.class, project);
        try {
            CacheManager.evictCache(CacheEvict.ALL, CacheEvict.ALL);
        } catch (ExecutionException e) {
            //ignore
        }

        final TaskContainer tasks = project.getTasks();

        final TaskProvider<PackageTask> packageTask = tasks.register("azureFunctionsPackage", PackageTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Package current project to staging folder.");
            task.setFunctionsExtension(extension);
        });

        final TaskProvider<PackageZipTask> packageZipTask = tasks.register("azureFunctionsPackageZip", PackageZipTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Package current project to staging folder.");
            task.setFunctionsExtension(extension);
        });

        final TaskProvider<LocalRunTask> runTask = tasks.register("azureFunctionsRun", LocalRunTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Builds a local folder structure ready to run on azure functions environment.");
            task.setFunctionsExtension(extension);
        });

        final TaskProvider<DeployTask> deployTask = tasks.register("azureFunctionsDeploy", DeployTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Deploy current project to azure cloud.");
            task.setFunctionsExtension(extension);
        });

        project.afterEvaluate(projectAfterEvaluation -> {
            mergeCommandLineParameters(extension);

            AzureMessager.setDefaultMessager(new GradleAzureMessager(project.getLogger()));
            Azure.az().config().setLogLevel(HttpLogDetailLevel.NONE.name());
            Azure.az().config().setProduct(GRADLE_PLUGIN_NAME);
            final String pluginVersion = StringUtils.firstNonBlank(AzureFunctionsPlugin.class.getPackage().getImplementationVersion(), "develop");
            Azure.az().config().setVersion(pluginVersion);
            final boolean enableTelemetry = BooleanUtils.isNotFalse(extension.getAllowTelemetry());
            Azure.az().config().setTelemetryEnabled(enableTelemetry);
            final String installationId = InstallationIdUtils.getHashMac();
            Azure.az().config().setMachineId(installationId);
            final String sessionId = UUID.randomUUID().toString();
            Azure.az().config().setSessionId(sessionId);
            final String userAgent = enableTelemetry ?
                    String.format("%s/%s %s:%s %s:%s", GRADLE_PLUGIN_NAME, pluginVersion,
                            INSTALLATION_ID_KEY, installationId, SESSION_ID_KEY, sessionId)
                    : String.format("%s/%s", GRADLE_PLUGIN_NAME, pluginVersion);
            Azure.az().config().setUserAgent(userAgent);

            TelemetryAgent.getInstance().showPrivacyStatement();

            packageTask.configure(task -> task.dependsOn("jar"));
            packageZipTask.configure(task -> task.dependsOn(packageTask));
            runTask.configure(task -> task.dependsOn(packageTask));
            deployTask.configure(task -> task.dependsOn(packageTask));
        });
    }

    private static void mergeCommandLineParameters(final AzureFunctionsExtension config) {
        final JavaPropsMapper mapper = new JavaPropsMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final Properties properties = System.getProperties();
        try {
            final AzureFunctionsExtension commandLineParameters = mapper.readPropertiesAs(properties, AzureFunctionsExtension.class);
            Utils.copyProperties(config, commandLineParameters, false);
        } catch (IOException | IllegalAccessException e) {
            AzureMessager.getMessager().warning(AzureString.format("Failed to read parameters from command line : %s", e.getMessage()));
        }
    }
}
