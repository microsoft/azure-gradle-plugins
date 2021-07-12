/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.microsoft.azure.gradle.GradleAzureOperationTitleProvider;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.TaskProvider;

import java.util.Optional;

public class AzureWebappPlugin implements Plugin<Project> {
    public static final String GRADLE_PLUGIN_NAME = "azure-webapp-gradle-plugin";
    private static final String GRADLE_FUNCTION_EXTENSION = "azurewebapp";

    @Override
    public void apply(final Project project) {
        final AzureWebappPluginExtension extension = project.getExtensions().create(GRADLE_FUNCTION_EXTENSION,
            AzureWebappPluginExtension.class, project);

        GradleAzureOperationTitleProvider.register();

        String pluginVersion = StringUtils.firstNonBlank(AzureWebappPlugin.class.getPackage().getImplementationVersion(), "develop");
        TelemetryAgent.getInstance().initTelemetry(GRADLE_PLUGIN_NAME, pluginVersion, BooleanUtils.isNotFalse(extension.getAllowTelemetry()));
        TelemetryAgent.getInstance().showPrivacyStatement();
        final TaskContainer tasks = project.getTasks();

        final TaskProvider<DeployTask> deployTask = tasks.register("azureWebappDeploy", DeployTask.class, task -> {
            task.setGroup("AzureWebapp");
            task.setDescription("Deploy current project to azure webapp.");
            task.setAzureWebappExtension(extension);
        });

        project.afterEvaluate(projectAfterEvaluation -> {
            final TaskProvider<Task> warTask = getWarTaskProvider(projectAfterEvaluation);
            final TaskProvider<Task> bootWarTask = getBootWarTaskProvider(projectAfterEvaluation);
            final TaskProvider<Task> bootJarTask = getBootJarTaskProvider(projectAfterEvaluation);
            final TaskProvider<Task> jarTask = projectAfterEvaluation.getTasks().named("jar");

            deployTask.configure(task -> {
                if (!(extension.getRuntime() != null && StringUtils.isNotBlank(extension.getRuntime().image())
                    && (StringUtils.isBlank(extension.getRuntime().os()) || StringUtils.equalsIgnoreCase(extension.getRuntime().os(), "docker")))) {
                    TaskProvider<Task> targetTask = ObjectUtils.firstNonNull(bootWarTask, bootJarTask, warTask, jarTask);
                    task.dependsOn(targetTask);
                    task.setArtifactFile(Optional.ofNullable(targetTask)
                        .map(Provider::get).map(Task::getOutputs).map(TaskOutputs::getFiles).map(FileCollection::getAsPath).orElse(null));
                }
            });
        });
    }

    static TaskProvider<Task> getWarTaskProvider(Project project) {
        if (project.getPlugins().hasPlugin(WarPlugin.class)) {
            return project.getTasks().named(WarPlugin.WAR_TASK_NAME);
        }
        return null;
    }

    static TaskProvider<Task> getBootJarTaskProvider(Project project) {
        if (project.getPlugins().hasPlugin("org.springframework.boot")) {
            try {
                return project.getTasks().named("bootJar");
            } catch (UnknownTaskException ignored) { // fall through
            }
        }
        return null;
    }

    static TaskProvider<Task> getBootWarTaskProvider(Project project) {
        if (project.getPlugins().hasPlugin("org.springframework.boot")) {
            try {
                return project.getTasks().named("bootWar");
            } catch (UnknownTaskException ignored) { // fall through
            }
        }
        return null;
    }
}
