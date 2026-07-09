# Lagrange Reader

Android client for BookOrbit focused on reading and listening.

## Current scope

- Connect to a user-provided BookOrbit server
- Authenticate through the server login flow
- Browse libraries and books
- Stream supported content
- Download books for offline reading or listening
- Queue progress updates offline and sync them later

## Project status

Working prototype. The app shell, API wiring, local download tracking, sync queue, explicit authenticated-session bootstrap via `/api/v1/auth/me`, sign-out/session reset behavior, and EPUB/PDF/audio/CBZ reading paths are in place. Focused JVM coverage now also exercises coordinator bootstrap, cached offline fallback, post-login resume flows, and recoverable initial browser-load failure handling. The remaining work is concentrated around end-to-end offline verification, reader quality, and release hardening.

## Build

From the project root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test

Run the local JVM unit suite with:

```powershell
.\gradlew.bat testDebugUnitTest
```

The release build also compiles locally with:

```powershell
.\gradlew.bat assembleRelease
```

The repository now also includes a GitHub Actions workflow that runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug` on pushes to `main` and on pull requests.

Server policy: production-style server URLs must use `https://`. Plain `http://` is only accepted for local development targets such as `localhost` and common Android emulator loopback aliases.

## Manual app testing

The current manual test entry point is documented in [docs/testing.md](./docs/testing.md).

Minimum baseline before manual app testing:

- `.\gradlew.bat assembleDebug` passes
- `.\gradlew.bat testDebugUnitTest` passes
- a reachable BookOrbit server is available
- a test account can sign in and access real content

## Local setup

Machine-specific SDK setup and environment notes are in [docs/setup.md](./docs/setup.md).

## Documentation

- [docs/README.md](./docs/README.md)
- [docs/architecture.md](./docs/architecture.md)
- [docs/setup.md](./docs/setup.md)
- [docs/privacy.md](./docs/privacy.md)
- [docs/release.md](./docs/release.md)
- [docs/bookorbit-api.md](./docs/bookorbit-api.md)
- [docs/testing.md](./docs/testing.md)
- [docs/roadmap.md](./docs/roadmap.md)
- [docs/handover.md](./docs/handover.md)
