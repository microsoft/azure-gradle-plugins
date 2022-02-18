/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.util;

import com.microsoft.azure.plugin.functions.gradle.JavaProject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Convert a Gradle project to a common project
 */
public class GradleProjectUtils {

    public static JavaProject convert(ProjectLayout layout, ConfigurableFileCollection runtimeClasspath, RegularFileProperty archive) {
        final JavaProject func = new JavaProject();
        func.setBaseDirectory(layout.getProjectDirectory().getAsFile().toPath());
        func.setBuildDirectory(layout.getBuildDirectory().get().getAsFile().toPath());
        Path archivePath = archive.get().getAsFile().toPath();
        final List<Path> dependencies = new ArrayList<>();
        func.setDependencies(dependencies);

        for (final File dependency : runtimeClasspath.getFiles()) {
            Path dependencyPath = dependency.toPath();
            if (!dependencyPath.equals(archivePath)) {
                dependencies.add(dependencyPath);
            }
        }

        func.setArtifactFile(archivePath);
        return func;
    }
}
