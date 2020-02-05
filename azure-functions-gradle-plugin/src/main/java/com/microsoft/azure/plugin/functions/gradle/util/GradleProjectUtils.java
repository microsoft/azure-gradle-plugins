/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.common.exceptions.AzureExecutionException;
import com.microsoft.azure.common.project.JavaProject;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Convert a Gradle project to a common project
 */
public class GradleProjectUtils {
    private static final String MAIN_SOURCE_SET_NAME = "main";

    public static JavaProject convert(final Project project) throws AzureExecutionException {
        final JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        if (javaPluginConvention == null) {
            throw new AzureExecutionException("Project " + project.getName() + " is not java project.");
        }

        final SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

        final FileCollection classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs().filter(File::exists);
        final Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();
        final FileCollection allFiles = mainSourceSet.getRuntimeClasspath().filter(File::exists);

        final FileCollection allDependencies = allFiles.minus(classesOutputDirectories)
                .filter(file -> !file.toPath().equals(resourcesOutputDirectory));
        final JavaProject func = new JavaProject();
        func.setBaseDirectory(project.getProjectDir().toPath());
        func.setBuildDirectory(project.getBuildDir().toPath());
        final List<Path> dependencies = new ArrayList<>();
        func.setDependencies(dependencies);

        for (final File dependency : allDependencies) {
            dependencies.add(dependency.toPath());
        }

        final BasePluginConvention basePlugin = project.getConvention().getPlugin(BasePluginConvention.class);
        func.setArtifactFile(Paths.get(project.getBuildDir().getAbsolutePath(), basePlugin.getLibsDirName(),
                basePlugin.getArchivesBaseName() + "-" + project.getVersion().toString() + ".jar"));
        return func;
    }
}
