/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.util.GradleAzureMessager;
import com.microsoft.azure.plugin.functions.gradle.task.AbstractAzureTask;
import com.microsoft.azure.plugin.functions.gradle.task.DeployTask;
import com.microsoft.azure.plugin.functions.gradle.task.LocalRunTask;
import com.microsoft.azure.plugin.functions.gradle.task.PackageTask;
import com.microsoft.azure.plugin.functions.gradle.task.PackageZipTask;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.cache.CacheEvict;
import com.microsoft.azure.toolkit.lib.common.cache.CacheManager;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.util.concurrent.ExecutionException;

public class AzureFunctionsPlugin implements Plugin<Project> {
    public static final String GRADLE_PLUGIN_NAME = "azure-functions-gradle-plugin";
    private static final String GRADLE_FUNCTION_EXTENSION = "azurefunctions";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        AzureMessager.setDefaultMessager(new GradleAzureMessager(project.getLogger()));
        final AzureFunctionsExtension extension = project.getExtensions().create(GRADLE_FUNCTION_EXTENSION,
                AzureFunctionsExtension.class, project);

        TelemetryAgent.getInstance().initTelemetry(GRADLE_PLUGIN_NAME,
            StringUtils.firstNonBlank(AzureFunctionsPlugin.class.getPackage().getImplementationVersion(), "develop"), // default version: develop
            BooleanUtils.isNotFalse(extension.getAllowTelemetry()));
        TelemetryAgent.getInstance().showPrivacyStatement();

        try {
            CacheManager.evictCache(CacheEvict.ALL, CacheEvict.ALL);
        } catch (ExecutionException e) {
            //ignore
        }

        Azure.az().config().setLogLevel(HttpLogDetailLevel.NONE.name());
        Azure.az().config().setUserAgent(TelemetryAgent.getInstance().getUserAgent());

        final TaskContainer tasks = project.getTasks();

        final TaskProvider<PackageTask> packageTask = tasks.register("azureFunctionsPackage", PackageTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Package current project to staging folder.");
            configureDefaults(project, task, extension);
        });

        final TaskProvider<PackageZipTask> packageZipTask = tasks.register("azureFunctionsPackageZip", PackageZipTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Package current project to staging folder.");
            configureDefaults(project, task, extension);
            task.dependsOn(packageTask);
        });

        final TaskProvider<LocalRunTask> runTask = tasks.register("azureFunctionsRun", LocalRunTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Builds a local folder structure ready to run on azure functions environment.");
            configureDefaults(project, task, extension);
            task.dependsOn(packageTask);
        });

        final TaskProvider<DeployTask> deployTask = tasks.register("azureFunctionsDeploy", DeployTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Deploy current project to azure cloud.");
            configureDefaults(project, task, extension);
            task.dependsOn(packageTask);
        });

    }

    private void configureDefaults(Project project, AbstractAzureTask task, AzureFunctionsExtension extension) {
        task.setFunctionsExtension(extension);
        task.getClasspath().from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        task.getArchiveFile().convention(project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile));
        task.getBinaryName().convention(project.getProviders().provider(extension::getAppName));
    }
}
