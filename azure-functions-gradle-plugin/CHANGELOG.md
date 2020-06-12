# Change Log
All notable changes to the "Azure Function Plugin for Gradle" will be documented in this file.
- [Change Log](#change-log)
  - [1.3.0](#130)
  - [1.2.0](#120)
  - [1.1.0](#110)
  - [1.0.0](#100)

## 1.3.0
- Support provision of Application Insights when creating a new function app.
- Support detach Application Insights from an existing function app

## 1.2.0
- Update depdendencies
- Fix issue [46](https://github.com/microsoft/azure-gradle-plugins/issues/46): java.io.IOException: Resource not found: /version.txt


## 1.1.0
- Update depdendencies
- Fix the plugin name in telemetry 
- Fix bug: Cannot resolve func.exe when it is located at a folder with SPACE


## 1.0.0
- Run/debug azure functions locally
- Deploy/create azure functions
- Package a zip file which can be uploaded to do manual deploy
