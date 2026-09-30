# Change Log
All notable changes to the "Azure Function Plugin for Gradle" will be documented in this file.
- [Change Log](#change-log)
  - [1.18.0](#1180)
  - [1.17.0](#1170)
  - [1.15.0](#1150)
  - [1.11.0](#1110)
  - [1.10.0](#1100)
  - [1.9.0](#190)
  - [1.8.0](#180)  
  - [1.7.0](#170)
  - [1.6.0](#160)
  - [1.5.0](#150)
  - [1.4.0](#140)
  - [1.3.0](#130)
  - [1.2.0](#120)
  - [1.1.0](#110)
  - [1.0.0](#100)

## 1.18.0
- Build the plugin with Gradle 9.8; dependency versions moved to a version catalog (`gradle/libs.versions.toml`)
- Declare caching behaviour on all tasks (`@DisableCachingByDefault`), as required by Gradle 9
- Upgrade `azure-security-keyvault-keys` to 4.11.2 (critical severity fix in the local cryptographic verification path)
- Upgrade Azure toolkit libraries to 0.56.0 and all other dependencies to their latest release (`commons-io` 2.22.0, `commons-lang3` 3.21.0, `guava` 33.7.2, `gson` 2.14.0, `jackson` 2.22.3, `jansi` 2.4.3, `slf4j-api` 2.0.20, `reflections` 0.10.2, ...)
- Fix task-property metadata of the deprecated `isDisableAppInsights()` getter (`@ReplacedBy` no longer combined with `@Input`); use `getDisableAppInsights()`

## 1.17.0
- Support Gradle 9 by removing deprecated Gradle API usage
- Restore JDK 8 bytecode compatibility (`options.release = 8`); enables the plugin to run on JDK 8 build environments
- Migrate to `plugin-publish` 1.x DSL (`pluginBundle` merged into `gradlePlugin`)
- Fix `azureFunctionsRun` task so that `func host start` output streams live to the console instead of being captured silently
- Upgrade `gson` to 2.8.9 (CVE-2022-25647) and `commons-io` to 2.14.0 (CVE-2024-47554)

## 1.15.0
- Migrate to use `stacks` API to get and validate function app runtime stacks

## 1.11.0
- Support Java 17 for Function App
- Improve stability/reliability of Authentication

## 1.10.0
- Support Managed Identity for authentication
- Support azure-functions-java-library 2.*

## 1.9.0
- Support deployment slot for Function App

## 1.8.0
- Support default value for region/pricing tier/java version [#1755](https://github.com/microsoft/azure-maven-plugins/pull/1761)
- Fix warning message of `illegal reflective access from groovy` [#1763](https://github.com/microsoft/azure-maven-plugins/pull/1763)

## 1.7.0
- Support [proxy](https://github.com/microsoft/azure-gradle-plugins/wiki/Proxy) with credential
- Fix the unexpected Gradle settings which will ignore the Java 1.8 settings
- Fix the bug of cannot detect func installed by Choco install
- Change the configuration name from `authentication` to `auth`

## 1.6.0
- Support [proxy](https://github.com/microsoft/azure-gradle-plugins/wiki/Proxy) in azure function Gradle plugin
- Create default files when there are no local.settings.json and host.json files instead of reporting errors.
- Use track2 SDK to manage azure functions

## 1.5.0
- Fix issue [https://github.com/microsoft/azure-maven-plugins/issues/1110](https://github.com/microsoft/azure-gradle-plugins/issues/46): Cross-Site Scripting: Reflected
- Fix issue [69](https://github.com/microsoft/azure-gradle-plugins/issues/69): Task azureFunctionsDeploy fails if runtime is not configured

## 1.4.0
- Support Java 11 Azure Functions (Preview)
- Support specify Azure environment for auth method 'azure_auth_maven_plugin'

## 1.3.0
- Support provision of Application Insights when creating a new function app
- Support detach Application Insights from an existing function app

## 1.2.0
- Update dependencies
- Fix issue [46](https://github.com/microsoft/azure-gradle-plugins/issues/46): java.io.IOException: Resource not found: /version.txt


## 1.1.0
- Update dependencies
- Fix the plugin name in telemetry
- Fix bug: Cannot resolve func.exe when it is located at a folder with SPACE


## 1.0.0
- Run/debug azure functions locally
- Deploy/create azure functions
- Package a zip file which can be uploaded to do manual deploy
