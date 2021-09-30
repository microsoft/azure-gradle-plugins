# Azure WebApp Plugin for Gradle
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v.svg?colorB=007ec6&label=Azure+WebApp+Plugin+for+Gradle&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Fcom%2Fmicrosoft%2Fazure%2Fazure-webapp-gradle-plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/com.microsoft.azure.azurewebapp)

This plugin helps Java developers to deploy Maven projects to [Azure App Service](https://docs.microsoft.com/en-us/azure/app-service/).


## Prerequisites

Tool | Required Version
---|---
JDK | 1.8
Gradle | 5.2 and above


## Setup
In your Gradle Java project, add the plugin to your `build.gradle`:
```groovy
plugins {
  id "com.microsoft.azure.azurewebapp" version "1.2.0"
}
```

## Configuration
Here is a sample configuration, for details, please refer to this [document](https://github.com/microsoft/azure-gradle-plugins/wiki/Webapp-Configuration).
### Groovy DSL
```groovy
azurewebapp {
    subscription = '<your subscription id>'
    resourceGroup = '<your resource group>'
    appName = '<your app name>'
    pricingTier = '<price tier like 'P1v2'>'
    region = '<region like 'westus'>'
    runtime {
      os = 'Linux'
      webContainer = 'Tomcat 9.0' // or 'Java SE' if you want to run an executable jar
      javaVersion = 'Java 8'
    }
    appSettings {
        <key> = <value>
    }
    auth {
        type = 'azure_cli' // support azure_cli, oauth2, device_code and service_principal
    }
}
```

### Kotlin DSL
```kotlin
azurewebapp {
  subscription = "<your subscription id>"
  resourceGroup = "<your resource group>"
  appName = "<your app name>"
  pricingTier = "<price tier like 'P1v2'>"
  region = "<region like 'westus'>"
  setRuntime(closureOf<com.microsoft.azure.gradle.configuration.GradleRuntimeConfig> {
    os("Linux")
    webContainer("Java SE")
    javaVersion("Java 11")
  })
  setAppSettings(closureOf<MutableMap<String, String>> {
    put("key", "value")
  })
  setAuth(closureOf<com.microsoft.azure.gradle.auth.GradleAuthConfig> {
    type = "azure_cli"
  })
}
```

## Usage

### Deploy your web project to Azure App Service
```shell
gradle azureWebAppDeploy
```

## Feedback and Questions
To report bugs or request new features, file issues on [Issues](https://github.com/microsoft/azure-gradle-plugins/issues). Or, ask questions on [Stack Overflow with tag azure-java-tools](https://stackoverflow.com/questions/tagged/azure-java-tools).

## Data and Telemetry
This project collects usage data and sends it to Microsoft to help improve our products and services.
Read Microsoft's [privacy statement](https://privacy.microsoft.com/en-us/privacystatement) to learn more.
If you would like to opt out of sending telemetry data to Microsoft, you can set `allowTelemetry` to false in the plugin configuration.
Please read our [document](https://github.com/microsoft/azure-gradle-plugins/wiki/Configuration) to find more details about *allowTelemetry*.
