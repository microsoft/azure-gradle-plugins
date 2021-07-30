# Azure Functions Plugin for Gradle
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v.svg?colorB=007ec6&label=Azure+Functions+Plugin+for+Gradle&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Fcom%2Fmicrosoft%2Fazure%2Fazure-functions-gradle-plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/com.microsoft.azure.azurefunctions)

This plugin provides seamless integration into Gradle projects. You can package a Java Functions project, run it locally or deploy it to Azure with tasks provided by this plugin.

## Prerequisites

Tool | Required Version
---|---
JDK | 1.8
Gradle | 5.2 and above
[.Net Core SDK](https://www.microsoft.com/net/core) | Latest version
[Azure Functions Core Tools](https://www.npmjs.com/package/azure-functions-core-tools) | 2.0 and above
>Note: [See how to install Azure Functions Core Tools - 2.x](https://aka.ms/azfunc-install)


## Setup
In your Gradle Java project, add the plugin to your `build.gradle`:
```groovy
plugins {
  id "com.microsoft.azure.azurefunctions" version "1.6.0"
}
```

## Configuration
Here is a sample configuration, for details, please refer to this [document](https://github.com/microsoft/azure-gradle-plugins/wiki/Configuration).
```groovy
azurefunctions {
    subscription = <your subscription id>
    resourceGroup = <your resource group>
    appName = <your function app name>
    pricingTier = <price tier like 'Consumption'>
    region = <region like 'westus'>
    runtime {
      os = <os like 'windows'>
    }
    appSettings {
        <key> = <value>
    }

    authentication {
        type = 'azure_cli'
    }
    // enable local debug
    // localDebug = "transport=dt_socket,server=y,suspend=n,address=5005"
    deployment {
        type = 'run_from_blob'
    }
}
```

## Usage

### Package Staging folder
Use the script below to  to package your staging folder:

```shell
gradle azureFunctionsPackage
```
or package the staging folder with a zip file:

```shell
gradle azureFunctionsPackageZip
```
### Run Azure Functions locally
Use the script below to run the function locally, if you want to debug your functions, please add `localDebug = "transport=dt_socket,server=y,suspend=n,address=5005"` to the `azurefunctions` section of your build.gradle.

```shell
gradle azureFunctionsRun
```
### Deploy Azure Functions to Azure Cloud
```shell
gradle azureFunctionsDeploy
```

## Common Questions
**Q: How to do when reporting "Cannot run functions locally due to error: Azure Functions Core Tools can not be found."**

**A:** You can install Azure Functions Core Tools at: https://aka.ms/azfunc-install

## Feedback and Questions
To report bugs or request new features, file issues on [Issues](https://github.com/microsoft/azure-gradle-plugins/issues). Or, ask questions on [Stack Overflow with tag azure-java-tools](https://stackoverflow.com/questions/tagged/azure-java-tools).

## Data and Telemetry
This project collects usage data and sends it to Microsoft to help improve our products and services.
Read Microsoft's [privacy statement](https://privacy.microsoft.com/en-us/privacystatement) to learn more.
If you would like to opt out of sending telemetry data to Microsoft, you can set `allowTelemetry` to false in the plugin configuration.
Please read our [document](https://github.com/microsoft/azure-gradle-plugins/wiki/Configuration) to find more details about *allowTelemetry*.
