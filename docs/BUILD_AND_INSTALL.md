# MIMORA Build & Installation Guide

## Prerequisites

- **JDK**: Java Development Kit 17 (OpenJDK 17 recommended)
- **Android SDK**: API Platform 34 to 37, Build Tools 34.0.0+
- **Gradle**: 9.1.0 (managed via included Gradle wrapper `./gradlew`)

---

## Environment Configuration

Verify that `local.properties` specifies your Android SDK directory:
```properties
sdk.dir=/path/to/android-sdk
```

Use JDK 17 (set `JAVA_HOME` or `org.gradle.java.home` to your own local JDK 17 path).
This path is machine-specific and is intentionally not included in the published source.

---

## Building the Application

### 1. Run Automated Unit Tests
```bash
./gradlew testDebugUnitTest
```
Test reports are generated at:
`app/build/reports/tests/testDebugUnitTest/index.html`

### 2. Run Code Analysis & Lint
```bash
./gradlew check
```
Lint reports are generated at:
`app/build/reports/lint-results-debug.html`

### 3. Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Installing on a Device or Emulator

With an Android device or emulator connected via `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or launch directly using the Android CLI tool:
```bash
android install app/build/outputs/apk/debug/app-debug.apk
```
