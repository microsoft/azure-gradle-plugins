# Gradle Plugin for Azure Functions

[![Maven Central](https://img.shields.io/maven-central/v/com.microsoft.azure/azure-functions-gradle-plugin.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.microsoft.azure%22%20AND%20a%3A%22azure-functions-gradle-plugin%22)

The Gradle Plugin for Azure Functions provides seamless integration into Gradle projects.

## Documentation
[![Maven Central](https://img.shields.io/maven-central/v/com.microsoft.azure/azure-functions-gradle-plugin.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.microsoft.azure%22%20AND%20a%3A%22azure-functions-gradle-plugin%22)

## Prerequisites

Tool | Required Version
---|---
JDK | 1.8
Gradle | 4.10 and above
[.Net Core SDK](https://www.microsoft.com/net/core) | Latest version
[Azure Functions Core Tools](https://www.npmjs.com/package/azure-functions-core-tools) | 2.0 and above
>Note: [See how to install Azure Functions Core Tools - 2.x](https://aka.ms/azfunc-install)


## Setup
In your Gradle Java project, add the plugin to your `build.gradle`:
```groovy
buildscript {
  repositories {
    mavenCentral()
    maven {
        url 'https://oss.sonatype.org/content/repositories/snapshots/'
    }
    maven {
      url 'https://pkgs.dev.azure.com/azure-toolkits/Java/_packaging/azure-toolkits-maven/maven/v1'
      name 'azure-toolkits-maven'
    }
  }
  dependencies {
    classpath "com.microsoft.azure:azure-functions-gradle-plugin:1.0.0-SNAPSHOT"
  }
}

apply plugin: "com.microsoft.azure.azurefunctions"
```

## Configuration
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

```shell
gradle azureFunctionsPackage
gradle azureFunctionsPackageZip
gradle azureFunctionsRun
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
Please read our [documents](https://aka.ms/azure-gradle-config) to find more details.