/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.plugin.webapps.gradle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.microsoft.azure.gradle.common.GradleAzureTaskManager;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.gradle.util.GradleAzureMessager;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.cache.CacheEvict;
import com.microsoft.azure.toolkit.lib.common.cache.CacheManager;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class AzureWebappPlugin implements Plugin<Project> {
    public static final String GRADLE_PLUGIN_NAME = "azure-webapp-gradle-plugin";
    private static final String GRADLE_FUNCTION_EXTENSION = "azurewebapp";

    @Override
    @AzureOperation(name = "internal/webapp.init_gradle_plugin")
    public void apply(final Project project) {
        AzureTaskManager.register(new GradleAzureTaskManager());
        final AzureWebappPluginExtension extension = project.getExtensions().create(GRADLE_FUNCTION_EXTENSION,
            AzureWebappPluginExtension.class, project);
        AzureMessager.setDefaultMessager(new GradleAzureMessager(project.getLogger()));

        try {
            CacheManager.evictCache(CacheEvict.ALL, CacheEvict.ALL);
        } catch (ExecutionException e) {
            //ignore
        }
        final TaskContainer tasks = project.getTasks();

        final TaskProvider<DeployTask> deployTask = tasks.register("azureWebAppDeploy", DeployTask.class, task -> {
            task.setGroup("AzureWebapp");
            task.setDescription("Deploy current project to azure webapp.");
            task.setAzureWebappExtension(extension);
        });

        project.afterEvaluate(projectAfterEvaluation -> {
            mergeCommandLineParameters(extension);
            String pluginVersion = StringUtils.firstNonBlank(AzureWebappPlugin.class.getPackage().getImplementationVersion(), "develop");
            TelemetryAgent.getInstance().initTelemetry(GRADLE_PLUGIN_NAME, pluginVersion, BooleanUtils.isNotFalse(extension.getAllowTelemetry()));
            TelemetryAgent.getInstance().showPrivacyStatement();
            final TaskProvider<Task> warTask = getWarTaskProvider(projectAfterEvaluation);
            final TaskProvider<Task> bootWarTask = getBootWarTaskProvider(projectAfterEvaluation);
            final TaskProvider<Task> bootJarTask = getBootJarTaskProvider(projectAfterEvaluation);
            final TaskProvider<Task> jarTask = projectAfterEvaluation.getTasks().named("jar");

            deployTask.configure(task -> {
                boolean isDocker = extension.getRuntime() != null && OperatingSystem.fromString(extension.getRuntime().os()) == OperatingSystem.DOCKER;
                if (extension.getRuntime() != null && StringUtils.isBlank(extension.getRuntime().os())) {
                    isDocker = StringUtils.isNotBlank(extension.getRuntime().image());
                }
                if (!isDocker) {
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

    @Nullable
    private static void mergeCommandLineParameters(final AzureWebappPluginExtension config) {
        final JavaPropsMapper mapper = new JavaPropsMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final Properties properties = System.getProperties();
        try {
            final AzureWebappPluginExtension commandLineParameters = mapper.readPropertiesAs(properties, AzureWebappPluginExtension.class);
            Utils.copyProperties(config, commandLineParameters, false);
        } catch (IOException | IllegalAccessException e) {
            AzureMessager.getMessager().warning(AzureString.format("Failed to read parameters from command line : %s", e.getMessage()));
        }
    }
}
