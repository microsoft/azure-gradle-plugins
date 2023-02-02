/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.plugin.functions.gradle.JavaProject;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import org.apache.commons.collections4.CollectionUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Convert a Gradle project to a common project
 */
public class GradleProjectUtils {
    private static final String MAIN_SOURCE_SET_NAME = "main";
    private static final String DEPENDENCY_WARNING = "The following dependencies could not be found, " +
            "please check whether related modules have been packaged \n %s";

    public static JavaProject convert(final Project project) {
        final JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        Objects.requireNonNull(javaPluginConvention, "Project " + project.getName() + " is not java project.");

        final SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

        final FileCollection classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs().filter(File::exists);
        final Path resourcesOutputDirectory = Objects.requireNonNull(mainSourceSet.getOutput().getResourcesDir()).toPath();
        final FileCollection allFiles = mainSourceSet.getRuntimeClasspath();

        final FileCollection allDependencies = allFiles.minus(classesOutputDirectories)
            .filter(file -> !file.toPath().equals(resourcesOutputDirectory));
        final JavaProject func = new JavaProject();
        func.setBaseDirectory(project.getProjectDir().toPath());
        func.setBuildDirectory(project.getBuildDir().toPath());

        final List<Path> dependencies = allDependencies.getFiles().stream().filter(File::exists).map(File::toPath).collect(Collectors.toList());
        final List<String> nonExistDependencies = allDependencies.getFiles().stream().filter(file -> !file.exists()).map(File::getPath).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(nonExistDependencies)) {
            AzureMessager.getMessager().warning(AzureString.format(DEPENDENCY_WARNING, String.join("\n", "\t" + nonExistDependencies)));
        }
        func.setDependencies(dependencies);

        final BasePluginConvention basePlugin = project.getConvention().getPlugin(BasePluginConvention.class);
        func.setArtifactFile(Paths.get(project.getBuildDir().getAbsolutePath(), basePlugin.getLibsDirName(),
            basePlugin.getArchivesBaseName() + "-" + project.getVersion() + ".jar"));
        return func;
    }
}
