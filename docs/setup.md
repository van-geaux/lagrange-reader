# Local Setup

## Prerequisites

- Windows machine
- JDK 17
- Android SDK command-line tools
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0
- Git

## Current local build command

From the project root:

```powershell
.\gradlew.bat assembleDebug
```

The standard Gradle debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`assembleDebug` also copies that same binary to a timestamped testing handoff file using the actual build datetime:

```text
app/build/outputs/apk/debug/Lagrange-debug-yyyymmddhhmm.apk
```

This is one build and one binary with two filenames. Use the timestamped filename reported by the build when installing for testing.

## Local SDK configuration

This repo ignores `local.properties`, so each machine should set its own SDK path there.

Example:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

## First run flow

1. Launch the app.
2. Enter the BookOrbit server base URL.
3. Complete login in the embedded WebView.
4. Wait for the app to confirm the authenticated session through `/api/v1/auth/me` after login-page navigation completes.
5. Browse libraries and books.

## Known environment notes

- The current project compiles with Android Gradle Plugin `8.5.2`.
- `compileSdk` is set to `35`.
- The build currently suppresses the unsupported compile SDK warning in `gradle.properties`.
- Non-local BookOrbit servers are expected to use `https://`. Plain `http://` is reserved for local development targets such as `localhost` and emulator loopback addresses.