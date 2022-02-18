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
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class AzureFunctionsPlugin implements Plugin<Project> {
    public static final String GRADLE_PLUGIN_NAME = "azure-functions-gradle-plugin";
    private static final String GRADLE_FUNCTION_EXTENSION = "azurefunctions";
    private static final String AZURE_BINARIES_EXTENSION = "azureBinaries";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        AzureMessager.setDefaultMessager(new GradleAzureMessager(project.getLogger()));
        NamedDomainObjectContainer<AzureFunctionsExtension> binaries = project.getObjects()
                .domainObjectContainer(AzureFunctionsExtension.class, name ->
                        project.getObjects().newInstance(AzureFunctionsExtension.class, name, project)
                );
        project.getExtensions().add(AZURE_BINARIES_EXTENSION, binaries);
        AzureFunctionsExtension mainExtension = binaries.create("main");
        // This is for backwards compatibility
        project.getExtensions().add(GRADLE_FUNCTION_EXTENSION, mainExtension);
        TelemetryAgent.getInstance().initTelemetry(GRADLE_PLUGIN_NAME,
            StringUtils.firstNonBlank(AzureFunctionsPlugin.class.getPackage().getImplementationVersion(), "develop"), // default version: develop
            BooleanUtils.isNotFalse(mainExtension.getAllowTelemetry()));
        TelemetryAgent.getInstance().showPrivacyStatement();

        try {
            CacheManager.evictCache(CacheEvict.ALL, CacheEvict.ALL);
        } catch (ExecutionException e) {
            //ignore
        }

        Azure.az().config().setLogLevel(HttpLogDetailLevel.NONE.name());
        Azure.az().config().setUserAgent(TelemetryAgent.getInstance().getUserAgent());

        final TaskContainer tasks = project.getTasks();
        binaries.all(extension -> {
            String name = extension.getName();
            final TaskProvider<PackageTask> packageTask = tasks.register(deriveTaskName(name, "azure", "FunctionsPackage"), PackageTask.class, task -> {
                task.setGroup("AzureFunctions");
                task.setDescription("Package current project to staging folder.");
                configureDefaults(project, task, extension);
            });

            final TaskProvider<PackageZipTask> packageZipTask = tasks.register(deriveTaskName(name, "azure", "FunctionsPackageZip"), PackageZipTask.class, task -> {
                task.setGroup("AzureFunctions");
                task.setDescription("Package current project to staging folder.");
                configureDefaults(project, task, extension);
                task.dependsOn(packageTask);
            });

            final TaskProvider<LocalRunTask> runTask = tasks.register(deriveTaskName(name, "azure", "FunctionsRun"), LocalRunTask.class, task -> {
                task.setGroup("AzureFunctions");
                task.setDescription("Builds a local folder structure ready to run on azure functions environment.");
                configureDefaults(project, task, extension);
                task.dependsOn(packageTask);
            });

            final TaskProvider<DeployTask> deployTask = tasks.register(deriveTaskName(name, "azure", "FunctionsDeploy"), DeployTask.class, task -> {
                task.setGroup("AzureFunctions");
                task.setDescription("Deploy current project to azure cloud.");
                configureDefaults(project, task, extension);
                task.dependsOn(packageTask);
            });
        });
    }

    private void configureDefaults(Project project, AbstractAzureTask task, AzureFunctionsExtension extension) {
        task.setFunctionsExtension(extension);
        task.getClasspath().from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        task.getArchiveFile().convention(project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile));
    }

    private static String deriveTaskName(String name, String prefix, String suffix) {
        if ("main".equals(name)) {
            return prefix + suffix;
        }
        return prefix + capitalize(name) + suffix;
    }

    private static String capitalize(String name) {
        if (name.length() > 0) {
            return name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        }
        return name;
    }

}
