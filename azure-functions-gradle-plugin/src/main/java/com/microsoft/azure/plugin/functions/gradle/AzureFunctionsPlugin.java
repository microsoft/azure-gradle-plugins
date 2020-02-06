/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle;

import com.microsoft.azure.plugin.functions.gradle.task.PackageTask;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;

public class AzureFunctionsPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        final AzureFunctionsExtension extension = project.getExtensions().create("azurefunctions",
                AzureFunctionsExtension.class, project);

        final TaskContainer tasks = project.getTasks();

        final TaskProvider<PackageTask> packageTask = tasks.register("azureFunctionsPackage", PackageTask.class, task -> {
            task.setGroup("AzureFunctions");
            task.setDescription("Package current project to staging folder.");
            task.setFunctionsExtension(extension);
        });

        project.afterEvaluate(projectAfterEvaluation -> {
            final List<TaskProvider<?>> dependsOnTask = new ArrayList<>();
            dependsOnTask.add(projectAfterEvaluation.getTasks().named("jar"));
            packageTask.configure(task -> task.dependsOn(dependsOnTask));
        });

    }
}
