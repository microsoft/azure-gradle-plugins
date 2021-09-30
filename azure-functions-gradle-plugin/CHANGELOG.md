# Change Log
All notable changes to the "Azure Function Plugin for Gradle" will be documented in this file.
- [Change Log](#change-log)
  - [1.8.0](#180)  
  - [1.7.0](#170)
  - [1.6.0](#160)
  - [1.5.0](#150)
  - [1.4.0](#140)
  - [1.3.0](#130)
  - [1.2.0](#120)
  - [1.1.0](#110)
  - [1.0.0](#100)

## 1.8.0
- Support default value for region/pricing tier/javaVersion [#1755](https://github.com/microsoft/azure-maven-plugins/pull/1761)
- Fix warning message of `illegal reflective access from groovy` [#1763](https://github.com/microsoft/azure-maven-plugins/pull/1763)

## 1.7.0
- Support [proxy](https://github.com/microsoft/azure-gradle-plugins/wiki/Proxy) with credential
- Fix the unexpected gradle settings which will ignore the Java 1.8 settings
- Fix the bug of cannot detect func installed by choco install
- Change the configuration name from `authentication` to `auth`

## 1.6.0
- Support [proxy](https://github.com/microsoft/azure-gradle-plugins/wiki/Proxy) in azure function gradle plugin
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
