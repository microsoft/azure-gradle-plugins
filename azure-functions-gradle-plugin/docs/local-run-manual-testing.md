# Manual testing: local environment variables and JVM system properties

This guide tests the unreleased plugin directly from a checkout, including environment inheritance,
overrides, JVM argument escaping, and local debugging. It does not create or deploy Azure resources.

## Prerequisites

- A checkout of the feature branch with the latest changes.
- JDK 17 or another JDK supported by both your Gradle version and Functions Core Tools.
- Azure Functions Core Tools 4.x; `func --version` must work.
- An unused HTTP port (7071 by default) and debugger ports 5005 and 5006.

The repository wrapper uses Gradle 9 and therefore requires JDK 17 or above. The commands below use
PowerShell. Replace the example directories with your actual paths. Use two terminals: one for the
Functions host and one for HTTP requests. Do not put secrets in this test project or expose its endpoint
outside your machine; it intentionally returns the explicitly selected test values.

```powershell
$pluginCheckout = 'C:\work\azure-gradle-plugins'
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
func --version
```

## Create a minimal HTTP function project

Create `C:\work\pr205-manual-test` with the following files. This is a separate test application, not a
new subproject in the plugin repository.

**`settings.gradle`**: the included build selects your checkout instead of the released plugin.

```groovy
pluginManagement {
    includeBuild('C:\\work\\azure-gradle-plugins')
}
rootProject.name = 'pr205-manual-test'
```

**`build.gradle`**:

```groovy
plugins {
    id 'java'
    id 'com.microsoft.azure.azurefunctions'
}

version = '1.0'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.microsoft.azure.functions:azure-functions-java-library:3.1.0'
}

tasks.withType(JavaCompile).configureEach {
    options.release = 8
}

azurefunctions {
    appName = 'pr205-manual-test'
    allowTelemetry = false
    envVars = [
        PR205_SETTING: 'configured',
        PR205_NUMBER: 42,
        JAVA_OPTS: '-Xmx256m -Dexisting=configured'
    ]
    sysProps = [
        existing: 'from-sysProps',
        message: 'hello world',
        quoted: 'say "hello"',
        path: 'C:\\path with spaces\\',
        empty: ''
    ]
    // Enable this only for the custom-debug test:
    // localDebug = 'transport=dt_socket,server=y,suspend=n,address=5006'
}
```

Do not specify the published plugin version when using this included build.

**`host.json`**:

```json
{
  "version": "2.0"
}
```

**`local.settings.json`**:

```json
{
  "IsEncrypted": false,
  "Values": {
    "FUNCTIONS_WORKER_RUNTIME": "java"
  },
  "Host": {
    "LocalHttpPort": 7071
  }
}
```

Change `LocalHttpPort` if another process already uses 7071, and update the request URLs accordingly.
HTTP-only tests do not need a storage account; existing apps with storage-backed triggers may require
their normal local storage configuration or Azurite.

**`src\main\java\probe\ConfigProbe.java`**:

```java
package probe;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

public class ConfigProbe {
    @FunctionName("ConfigProbe")
    public HttpResponseMessage run(
            @HttpTrigger(name = "request", methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS, route = "config-probe")
            HttpRequestMessage<Optional<String>> request) {
        String response = String.join("\n",
                "env=" + System.getenv("PR205_SETTING"),
                "inherited=" + System.getenv("PR205_INHERITED"),
                "number=" + System.getenv("PR205_NUMBER"),
                "existing=" + System.getProperty("existing"),
                "message=" + System.getProperty("message"),
                "quoted=" + System.getProperty("quoted"),
                "path=" + System.getProperty("path"),
                "empty=" + System.getProperty("empty"),
                "javaOpts=" + System.getenv("JAVA_OPTS"));
        return request.createResponseBuilder(HttpStatus.OK).body(response).build();
    }
}
```

## Test inheritance, overrides, and escaping

In the host terminal, set these test values and start the application with a fresh Gradle process:

```powershell
Set-Location 'C:\work\pr205-manual-test'
$env:PR205_SETTING = 'inherited'
$env:PR205_INHERITED = 'from-shell'
$env:JAVA_OPTS = '-Xmx512m -Dexisting=inherited'
& "$pluginCheckout\gradlew.bat" -p . azureFunctionsRun --no-daemon --console=plain
```

Wait for Core Tools to list the `ConfigProbe` endpoint. In the second terminal:

```powershell
(Invoke-WebRequest 'http://localhost:7071/api/config-probe' -UseBasicParsing).Content
```

The response must include exactly these values:

```text
env=configured
inherited=from-shell
number=42
existing=from-sysProps
message=hello world
quoted=say "hello"
path=C:\path with spaces\
empty=
```

The `javaOpts` line must contain `-Xmx256m`, not the inherited `-Xmx512m`. It must include the
configured `-Dexisting=configured` followed by `-Dexisting=from-sysProps`: the appended system property
wins. Quoting on this environment-variable line is expected; the property values above must contain
no extra quotes or backslashes. There must be no `-agentlib:jdwp=` option yet.

To test appending rather than replacement, remove only the `JAVA_OPTS` entry from `envVars`, stop the
host with Ctrl+C, and restart it. `javaOpts` must now retain `-Xmx512m -Dexisting=inherited`, followed by
the configured system properties. `existing` must still be `from-sysProps`.

## Test optional configuration and unchanged baseline behavior

Stop the host before each restart. Temporarily remove both `envVars` and `sysProps` from the build
configuration and keep `localDebug` disabled.

With the shell environment from the preceding test unchanged, rerun `azureFunctionsRun`.
Expect `env=inherited`, `inherited=from-shell`, `number=null`, `existing=inherited`, and `message=null`,
`quoted=null`, `path=null`, and `empty=null`. `javaOpts` must remain the inherited value.

Then remove the inherited JVM options and restart:

```powershell
Remove-Item Env:\JAVA_OPTS -ErrorAction SilentlyContinue
& "$pluginCheckout\gradlew.bat" -p . azureFunctionsRun --no-daemon --console=plain
```

Expect `existing=null` and `javaOpts=null`. The host must still start normally. Empty maps
(`envVars = [:]` and `sysProps = [:]`) should have the same behavior.

## Test debugger options together with properties

Restore the original `envVars` and `sysProps` maps. Keep `localDebug` commented out and run:

```powershell
& "$pluginCheckout\gradlew.bat" -p . azureFunctionsRun --enableDebug --no-daemon --console=plain
```

Expect a debugger listener on port 5005. The HTTP endpoint must return the original configured values,
and `javaOpts` must end with
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`.
Attach an IDE remote Java debugger to `localhost:5005`, put a breakpoint in `ConfigProbe.run`, and
invoke the endpoint. The breakpoint must be hit; resume execution to finish the request.

Stop the host, uncomment the custom `localDebug` configuration, and run **without** `--enableDebug`.
Repeat the breakpoint test on port 5006. Then prefix `localDebug` with `-agentlib:jdwp=` and repeat.
Both forms must work, and `javaOpts` must contain only one generated debugger option.
Do not separately add a debugger agent to inherited or configured `JAVA_OPTS`.

## Kotlin DSL check

For an existing Kotlin DSL Functions project, use the same included-build arrangement in
`settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("C:\\work\\azure-gradle-plugins")
}
```

Use these settings in its `azurefunctions` block:

```kotlin
envVars = mapOf<String, Any>(
    "PR205_SETTING" to "configured",
    "PR205_NUMBER" to 42,
    "JAVA_OPTS" to "-Xmx256m"
)
sysProps = mapOf(
    "message" to "hello world",
    "quoted" to "say \"hello\"",
    "path" to "C:\\path with spaces\\"
)
```

Run the same endpoint checks. Kotlin and Groovy must produce the same worker-visible values.

## Local-only scope and cleanup

`envVars` and `sysProps` are applied when spawning the local host. They must not be written into
`local.settings.json`, `host.json`, or generated `function.json` files, and must not become deployed
Azure app settings. Use `appSettings` if you want settings deployed to Azure; a cloud deployment is
not necessary to complete these tests.

Stop the host and detach the debugger. Remove the test environment variables from the host terminal:

```powershell
Remove-Item Env:\PR205_SETTING, Env:\PR205_INHERITED, Env:\JAVA_OPTS -ErrorAction SilentlyContinue
```

Restore any pre-existing environment values you changed, remove the temporary probe from an existing
application, and retain normal local storage configuration if you used one.
