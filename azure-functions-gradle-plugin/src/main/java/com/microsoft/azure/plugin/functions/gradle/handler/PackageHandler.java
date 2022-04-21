/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.plugin.functions.gradle.handler;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.gradle.temeletry.TelemetryAgent;
import com.microsoft.azure.toolkit.lib.common.IProject;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.Binding;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.BindingEnum;
import com.microsoft.azure.toolkit.lib.legacy.function.configurations.FunctionConfiguration;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.AnnotationHandler;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.AnnotationHandlerImpl;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.CommandHandler;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.CommandHandlerImpl;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.FunctionCoreToolsHandler;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.FunctionCoreToolsHandlerImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class PackageHandler {
    public static final String HOST_JSON = "host.json";
    public static final String LOCAL_SETTINGS_JSON = "local.settings.json";
    private static final String LINE_FEED = "\r\n";
    private static final String DOCS_LINK = "https://aka.ms/functions-local-settings";
    private static final String SEARCH_FUNCTIONS = "Step 1 of 8: Searching for Azure Functions entry points";
    private static final String FOUND_FUNCTIONS = " Azure Functions entry point(s) found.";
    private static final String NO_FUNCTIONS = "Azure Functions entry point not found, plugin will exit.";
    private static final String GENERATE_CONFIG = "Step 2 of 8: Generating Azure Functions configurations";
    private static final String GENERATE_SKIP = "No Azure Functions found. Skip configuration generation.";
    private static final String GENERATE_DONE = "Generation done.";
    private static final String VALIDATE_CONFIG = "Step 3 of 8: Validating generated configurations";
    private static final String VALIDATE_SKIP = "No configurations found. Skip validation.";
    private static final String VALIDATE_DONE = "Validation done.";
    private static final String SAVE_HOST_JSON = "Step 4 of 8: Saving host.json";
    private static final String SAVE_LOCAL_SETTINGS_JSON = "Step 5 of 8: Saving local.settings.json";
    private static final String SAVE_FUNCTION_JSONS = "Step 6 of 8: Saving configurations to function.json";
    private static final String SAVE_SKIP = "No configurations found. Skip save.";
    private static final String SAVE_FUNCTION_JSON = "Starting processing function: ";
    private static final String SAVE_SUCCESS = "Successfully saved to ";
    private static final String COPY_JARS = "Step 7 of 8: Copying JARs to staging directory: ";
    private static final String COPY_SUCCESS = "Copied successfully.";
    private static final String INSTALL_EXTENSIONS = "Step 8 of 8: Installing function extensions if needed";
    private static final String SKIP_INSTALL_EXTENSIONS_HTTP = "Skip install Function extension for HTTP Trigger Functions";
    private static final String INSTALL_EXTENSIONS_FINISH = "Function extension installation done.";
    private static final String BUILD_SUCCESS = "Successfully built Azure Functions.";
    private static final String FUNCTION_JSON = "function.json";
    private static final String EXTENSION_BUNDLE = "extensionBundle";
    private static final BindingEnum[] FUNCTION_WITHOUT_FUNCTION_EXTENSION = { BindingEnum.HttpOutput,
        BindingEnum.HttpTrigger };
    private static final String EXTENSION_BUNDLE_ID = "Microsoft.Azure.Functions.ExtensionBundle";
    private static final String EXTENSION_BUNDLE_PREVIEW_ID = "Microsoft.Azure.Functions.ExtensionBundle.Preview";
    private static final String SKIP_INSTALL_EXTENSIONS_BUNDLE = "Extension bundle specified, skip install extension";
    private static final String DEFAULT_LOCAL_SETTINGS_JSON = "{ \"IsEncrypted\": false, \"Values\": " +
        "{ \"FUNCTIONS_WORKER_RUNTIME\": \"java\" } }";
    private static final String DEFAULT_HOST_JSON = "{\"version\":\"2.0\",\"extensionBundle\":" +
        "{\"id\":\"Microsoft.Azure.Functions.ExtensionBundle\",\"version\":\"[1.*, 2.0.0)\"}}\n";
    private static final String TRIGGER_TYPE = "triggerType";

    private final IProject project;
    private final String deploymentStagingDirectoryPath;

    public PackageHandler(IProject project, String deploymentStagingDirectoryPath) {
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(deploymentStagingDirectoryPath);
        Preconditions.checkArgument(!new File(deploymentStagingDirectoryPath).isFile());
        this.deploymentStagingDirectoryPath = deploymentStagingDirectoryPath;
        this.project = project;
    }

    public void execute() throws AzureExecutionException, IOException {
        final AnnotationHandler annotationHandler = getAnnotationHandler();
        final Set<Method> methods = findAnnotatedMethods(annotationHandler);

        if (methods.size() == 0) {
            throw new AzureExecutionException(NO_FUNCTIONS);
        }

        final Map<String, FunctionConfiguration> configMap = getFunctionConfigurations(annotationHandler, methods);

        validateFunctionConfigurations(configMap);

        copyHostJsonFile();

        copyLocalSettingJsonFile();

        writeFunctionJsonFiles(getObjectWriter(), configMap);

        copyJarsToStageDirectory();

        final CommandHandler commandHandler = new CommandHandlerImpl();
        final FunctionCoreToolsHandler functionCoreToolsHandler = getFunctionCoreToolsHandler(commandHandler);
        final Set<BindingEnum> bindingClasses = this.getFunctionBindingEnums(configMap);

        installExtension(functionCoreToolsHandler, bindingClasses);

        AzureMessager.getMessager().info(BUILD_SUCCESS);
    }

    private AnnotationHandler getAnnotationHandler() {
        return new AnnotationHandlerImpl();
    }

    private Set<Method> findAnnotatedMethods(final AnnotationHandler handler) throws MalformedURLException {
        AzureMessager.getMessager().info(LINE_FEED + SEARCH_FUNCTIONS);
        Set<Method> functions;
        try {
            log.debug("ClassPath to resolve: " + getArtifactFileUrl());
            final List<URL> dependencyWithTargetClass = getDependencyArtifactUrls();
            dependencyWithTargetClass.add(getArtifactFileUrl());
            functions = handler.findFunctions(dependencyWithTargetClass);
        } catch (NoClassDefFoundError e) {
            // fallback to reflect through artifact url, for shaded project(fat jar)
            log.debug("ClassPath to resolve: " + getArtifactUrl());
            functions = handler.findFunctions(Collections.singletonList(getArtifactUrl()));
        }
        AzureMessager.getMessager().info(functions.size() + FOUND_FUNCTIONS);
        return functions;
    }

    private URL getArtifactUrl() throws MalformedURLException {
        return this.project.getArtifactFile().toFile().toURI().toURL();
    }

    private URL getArtifactFileUrl() throws MalformedURLException {
        return project.getArtifactFile().toFile().toURI().toURL();
    }

    /**
     * @return URLs for the classpath with compile scope needed jars
     */
    private List<URL> getDependencyArtifactUrls() {
        final List<URL> urlList = new ArrayList<>();
        for (final Path jarFilePath : project.getProjectDependencies()) {
            final File f = jarFilePath.toFile();
            try {
                urlList.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
                log.debug("Failed to get URL for file: " + f);
            }
        }
        return urlList;
    }

    private Map<String, FunctionConfiguration> getFunctionConfigurations(final AnnotationHandler handler,
                                                                         final Set<Method> methods) throws AzureExecutionException {
        AzureMessager.getMessager().info(LINE_FEED + GENERATE_CONFIG);
        final Map<String, FunctionConfiguration> configMap = handler.generateConfigurations(methods);
        if (configMap.size() == 0) {
            AzureMessager.getMessager().info(GENERATE_SKIP);
        } else {
            final String scriptFilePath = getScriptFilePath();
            configMap.values().forEach(config -> config.setScriptFile(scriptFilePath));
            AzureMessager.getMessager().info(GENERATE_DONE);
        }

        return configMap;
    }

    private String getScriptFilePath() {
        return "../" + project.getArtifactFile().getFileName().toString();
    }

    private void validateFunctionConfigurations(final Map<String, FunctionConfiguration> configMap) {
        AzureMessager.getMessager().info(LINE_FEED + VALIDATE_CONFIG);
        if (configMap.size() == 0) {
            AzureMessager.getMessager().info(VALIDATE_SKIP);
        } else {
            configMap.values().forEach(FunctionConfiguration::validate);
            AzureMessager.getMessager().info(VALIDATE_DONE);
        }
        trackFunctionProperties(configMap);
    }

    private void writeFunctionJsonFiles(final ObjectWriter objectWriter,
                                        final Map<String, FunctionConfiguration> configMap) throws IOException {
        AzureMessager.getMessager().info(LINE_FEED + SAVE_FUNCTION_JSONS);
        if (configMap.size() == 0) {
            AzureMessager.getMessager().info(SAVE_SKIP);
        } else {
            for (final Map.Entry<String, FunctionConfiguration> config : configMap.entrySet()) {
                writeFunctionJsonFile(objectWriter, config.getKey(), config.getValue());
            }
        }
    }

    private void writeFunctionJsonFile(final ObjectWriter objectWriter, final String functionName,
                                       final FunctionConfiguration config) throws IOException {
        AzureMessager.getMessager().info(SAVE_FUNCTION_JSON + functionName);
        final File functionJsonFile = Paths.get(deploymentStagingDirectoryPath, functionName, FUNCTION_JSON)
            .toFile();
        writeObjectToFile(objectWriter, config, functionJsonFile);
        AzureMessager.getMessager().info(SAVE_SUCCESS + functionJsonFile.getAbsolutePath());
    }

    private void copyHostJsonFile() throws IOException {
        AzureMessager.getMessager().info(LINE_FEED + SAVE_HOST_JSON);
        final File sourceHostJsonFile = new File(project.getBaseDirectory().toFile(), HOST_JSON);
        final File hostJsonFile = Paths.get(this.deploymentStagingDirectoryPath, HOST_JSON).toFile();
        copyFilesWithDefaultContent(sourceHostJsonFile, hostJsonFile, DEFAULT_HOST_JSON);
        AzureMessager.getMessager().info(SAVE_SUCCESS + hostJsonFile.getAbsolutePath());
    }

    private void copyLocalSettingJsonFile() throws AzureExecutionException, IOException {
        AzureMessager.getMessager().info(LINE_FEED + SAVE_LOCAL_SETTINGS_JSON);
        final File localSettingJsonTargetFile = Paths.get(this.deploymentStagingDirectoryPath, LOCAL_SETTINGS_JSON)
            .toFile();
        final File localSettingJsonSrcFile = new File(project.getBaseDirectory().toFile(), LOCAL_SETTINGS_JSON);
        if (localSettingJsonSrcFile.exists() && localSettingJsonSrcFile.length() == 0) {
            throw new AzureExecutionException("The " + localSettingJsonSrcFile.getAbsolutePath() +
                " file is empty, please check the document at" + DOCS_LINK);
        } else {
            copyFilesWithDefaultContent(localSettingJsonSrcFile, localSettingJsonTargetFile, DEFAULT_LOCAL_SETTINGS_JSON);
        }

        AzureMessager.getMessager().info(SAVE_SUCCESS + localSettingJsonTargetFile.getAbsolutePath());
    }

    private static void copyFilesWithDefaultContent(File src, File dest, String defaultContent) throws IOException {
        if (src.exists()) {
            FileUtils.copyFile(src, dest);
        } else {
            FileUtils.write(dest, StringUtils.firstNonBlank(defaultContent, ""), Charset.defaultCharset());
        }
    }

    private void writeObjectToFile(final ObjectWriter objectWriter, final Object object, final File targetFile)
        throws IOException {
        if (!targetFile.getParentFile().mkdirs()) {
            throw new AzureToolkitRuntimeException("Cannot create folder: " + targetFile.getParentFile().getAbsolutePath());
        }
        targetFile.createNewFile();
        objectWriter.writeValue(targetFile, object);
    }

    private ObjectWriter getObjectWriter() {
        final DefaultPrettyPrinter.Indenter indenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withLinefeed("\n");
        final PrettyPrinter prettyPrinter = new DefaultPrettyPrinter().withObjectIndenter(indenter);
        return new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).writer(prettyPrinter);
    }

    private void copyJarsToStageDirectory() throws IOException {
        AzureMessager.getMessager().info(LINE_FEED + COPY_JARS + deploymentStagingDirectoryPath);
        final File libFolder = new File(deploymentStagingDirectoryPath, "lib");
        if (libFolder.exists()) {
            FileUtils.cleanDirectory(libFolder);
        }

        for (final Path jarFilePath : project.getProjectDependencies()) {
            if (!jarFilePath.getFileName().toString().startsWith("azure-functions-java-library-")) {
                FileUtils.copyFileToDirectory(jarFilePath.toFile(), libFolder);
            }
        }

        FileUtils.copyFileToDirectory(project.getArtifactFile().toFile(), new File(deploymentStagingDirectoryPath));
        AzureMessager.getMessager().info(COPY_SUCCESS);
    }

    private FunctionCoreToolsHandler getFunctionCoreToolsHandler(final CommandHandler commandHandler) {
        return new FunctionCoreToolsHandlerImpl(commandHandler);
    }

    private void installExtension(final FunctionCoreToolsHandler handler, Set<BindingEnum> bindingEnums)
        throws AzureExecutionException {
        AzureMessager.getMessager().info(LINE_FEED + INSTALL_EXTENSIONS);
        if (!isInstallingExtensionNeeded(bindingEnums)) {
            return;
        }
        handler.installExtension(new File(this.deploymentStagingDirectoryPath),
            project.getBaseDirectory().toFile());
        AzureMessager.getMessager().info(INSTALL_EXTENSIONS_FINISH);
    }

    private Set<BindingEnum> getFunctionBindingEnums(Map<String, FunctionConfiguration> configMap) {
        final Set<BindingEnum> result = new HashSet<>();
        configMap.values().forEach(
            configuration -> configuration.getBindings().forEach(binding -> result.add(binding.getBindingEnum())));
        return result;
    }

    private boolean isInstallingExtensionNeeded(Set<BindingEnum> bindingTypes) {
        final JsonObject hostJson = readHostJson();
        final String extensionBundleId = Optional.ofNullable(hostJson)
                .map(host -> host.getAsJsonObject(EXTENSION_BUNDLE))
                .map(extensionBundle -> extensionBundle.get("id"))
                .map(JsonElement::getAsString).orElse(null);
        if (StringUtils.equalsAnyIgnoreCase(extensionBundleId, EXTENSION_BUNDLE_ID, EXTENSION_BUNDLE_PREVIEW_ID)) {
            AzureMessager.getMessager().info(SKIP_INSTALL_EXTENSIONS_BUNDLE);
            return false;
        }
        final boolean isNonHttpTriggersExist = bindingTypes.stream()
            .anyMatch(binding -> !Arrays.asList(FUNCTION_WITHOUT_FUNCTION_EXTENSION).contains(binding));
        if (!isNonHttpTriggersExist) {
            AzureMessager.getMessager().info(SKIP_INSTALL_EXTENSIONS_HTTP);
            return false;
        }
        return true;
    }

    private JsonObject readHostJson() {
        final File hostJson = new File(project.getBaseDirectory().toFile(), HOST_JSON);
        try (final FileInputStream fis = new FileInputStream(hostJson);
             final Scanner scanner = new Scanner(new BOMInputStream(fis))) {
            final String jsonRaw = scanner.useDelimiter("\\Z").next();
            return JsonParser.parseString(jsonRaw).getAsJsonObject();
        } catch (IOException e) {
            return null;
        }
    }

    private void trackFunctionProperties(Map<String, FunctionConfiguration> configMap) {
        final List<String> bindingTypeSet = configMap.values().stream().flatMap(configuration -> configuration.getBindings().stream())
                .map(Binding::getType)
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        TelemetryAgent.getInstance().addDefaultProperty(TRIGGER_TYPE, StringUtils.join(bindingTypeSet, ","));
    }
}
