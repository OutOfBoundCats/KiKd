# buildSrc

`buildSrc` contains shared Gradle build logic for the KiKd workspace.

## Purpose

Gradle automatically builds `buildSrc` and makes its convention plugins available to the main build. KiKd uses this to keep every Kotlin module on the same JVM and test configuration.

## Convention Plugin

The main convention plugin is:

```text
buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
```

It applies:

- Kotlin JVM support
- JDK toolchain configuration
- JUnit Platform test execution
- consistent test logging

Subprojects apply it with:

```kotlin
plugins {
    id("buildsrc.convention.kotlin-jvm")
}
```

## When To Change This Module

Update `buildSrc` when build behavior should apply across multiple modules, such as:

- common compiler settings
- common test setup
- shared publishing setup
- shared lint or formatting tasks

Do not put product DSL or renderer code here. Product code belongs in the `kikd-*` modules.
