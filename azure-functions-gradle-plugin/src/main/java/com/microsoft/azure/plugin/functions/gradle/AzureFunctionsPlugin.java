/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.plugin.functions.gradle.task.DeployTask;
import com.microsoft.azure.plugin.functions.gradle.task.LocalRunTask;
import com.microsoft.azure.plugin.functions.gradle.task.PackageTask;
import com.microsoft.azure.plugin.functions.gradle.task.PackageZipTask;
import com.microsoft.azure.plugin.functions.gradle.telemetry.TelemetryAgent;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public class AzureFunctionsPlugin implements Plugin<Project> {
    public static final String GRADLE_PLUGIN_NAME = "azure-functions-gradle-plugin";
    private static final String GRADLE_FUNCTION_EXTENSION = "azurefunctions";

    @Override
    public void apply(final Project project) {
        final AzureFunctionsExtension extension = project.getExtensions().create(GRADLE_FUNCTION_EXTENSION,
                AzureFunctionsExtension.class, project);
        TelemetryAgent.instance.showPrivacyStatement();
        if (extension.getAllowTelemetry() != null) {
            TelemetryAgent.instance.setAllowTelemetry(extension.getAllowTelemetry());
        }
        TelemetryAgent.instance.initTelemetry();

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
            packageTask.configure(task -> task.dependsOn("jar"));
            packageZipTask.configure(task -> task.dependsOn(packageTask));
            runTask.configure(task -> task.dependsOn(packageTask));
            deployTask.configure(task -> task.dependsOn(packageTask));
        });
    }
}
