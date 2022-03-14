# Change Log
All notable changes to the "Azure WebApp Plugin for Gradle" will be documented in this file.
- [Change Log](#change-log)
  - [1.3.0](#130)
  - [1.2.0](#120)
  - [1.1.0](#110)
  - [1.0.0](#100)

## 1.3.0
- Support Tomcat 10.0 and Java SE 17 runtime

## 1.2.0
- Support default value for region/pricing tier/webContainer/javaVersion [#1755](https://github.com/microsoft/azure-maven-plugins/pull/1755)
- Support flag to skip create azure resources PR [#1762](https://github.com/microsoft/azure-maven-plugins/pull/1762), Issue [#1651](https://github.com/microsoft/azure-maven-plugins/issues/1651)
- Check whether webapp name is available before creating webapp [#1728](https://github.com/microsoft/azure-maven-plugins/pull/1728)
- Remove unsupported JBoss 7.2 Runtime(use JBoss 7 instead) [#1751](https://github.com/microsoft/azure-maven-plugins/pull/1751)
- Fix warning message of `illegal reflective access from groovy`  [#1763](https://github.com/microsoft/azure-maven-plugins/pull/1763)

## 1.1.0
- Fix the unexpected gradle settings which will ignore the Java 1.8 settings
- Allow proxy with credential, see how to use proxy here: https://github.com/microsoft/azure-gradle-plugins/wiki/Proxy


## 1.0.0
- Deploy azure webapp
