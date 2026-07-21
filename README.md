# Lagrange Reader

![Lagrange Reader logo](artwork/lagrange-app-icon-512.png)

Lagrange Reader is an independent Android app for reading and listening to books hosted on [BookOrbit](https://github.com/BookOrbit). It started with a simple personal need: I love BookOrbit, but I wanted an app that lets me take my library with me and read offline.

This is a community project, not an official BookOrbit application. Development was AI-assisted, with the implementation, testing, and product decisions reviewed by the project owner.

## What it does

- Connects to a BookOrbit server and signs in with your normal account.
- Browses libraries, series, authors, search results, achievements, and local books.
- Reads books online or after downloading them for offline use.
- Keeps reading progress synchronized in both directions: local changes are sent to BookOrbit, and newer server progress/status can return to the app on refresh.
- Queues progress from offline reading/listening sessions and syncs it automatically after the connection returns.
- Provides reader themes, text size, margins, chapter/page navigation, orientation lock, and keep-awake behavior.
- Plays audiobooks with a compact player, seeking, chapter selection, playback speed, and resume support.
- Supports multiple app themes, cellular-download controls, cache management, and a cache-first offline browser.

## Supported formats

| Format | Online | Offline | Notes |
| --- | :---: | :---: | --- |
| EPUB / KEPUB | Yes | Yes | Full paginated reader with themes, margins, chapters, and resume. |
| PDF | Yes | Yes | Readium PDF reader with page navigation and resume. |
| CBZ | Yes | Yes | Image-based comic reader. |
| CBR / CB7 | Yes | Limited | Online page extraction is supported; offline reading requires the server and is not client-side RAR/7z extraction. |
| Audiobooks supported by BookOrbit | Yes | Yes | Readium audio playback with chapters, speed control, seeking, and resume. |

The following ebook formats are intentionally not supported at this time: MOBI, AZW, AZW3, and FB2. Conversion may be considered later. Audiobook and unusual comic files still benefit from broader device testing.

## Roadmap

Planned follow-up work includes:

- More reading-direction options, including right-to-left and continuous scrolling with configurable page spacing for PDF and CBZ/CBR.
- Moving a book to read status directly from Preview.
- Support for additional book formats.
- Bulk deletion of local books.

## Limitations to know about

Lagrange needs a reachable BookOrbit server for sign-in, online browsing, progress synchronization, and CBR/CB7 page extraction. Downloaded EPUB, PDF, CBZ, and supported audiobook files can be opened without a connection. HTTPS is strongly recommended when connecting to a remote server; explicit HTTP URLs are supported for trusted networks.

The app currently uses the standard BookOrbit username/password login. Direct OIDC/SSO is deferred until the mobile redirect and token contract is confirmed. A connected Android device or emulator is also needed for the full physical validation matrix; automated JVM tests and APK builds are maintained in the repository.

## Screenshots

Screenshots can be added manually to [`screenshots/`](screenshots/). The sections below are intentionally reserved for release documentation:

<!-- Add screenshots here, for example: ![Home](screenshots/home.png) -->

### Home and library browsing

_Screenshot placeholder_

### Reading and offline books

_Screenshot placeholder_

### Audiobook player

_Screenshot placeholder_

## Building manually

### Requirements

- Windows, macOS, or Linux with a current Android Studio installation.
- JDK 17.
- Android SDK with API 35 installed.
- An Android device or emulator running API 26 or newer for manual testing.

Clone the repository, open it in Android Studio, and let it use the included Gradle wrapper. From a terminal at the repository root, the usual debug build is:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Useful verification commands are:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

For machine setup details and the manual test matrix, see [`docs/setup.md`](docs/setup.md) and [`docs/testing.md`](docs/testing.md).

## Relationship with BookOrbit

I have not yet asked the BookOrbit maintainers for permission to distribute or promote this client. I want to test it further first—roughly another two to three weeks of real-world use—before starting that conversation. The app is independent, and its name, logo, and documentation should not be read as an endorsement by the BookOrbit maintainers.

## Project status

Version **1.0.0** is considered feature-complete and worthy of release for personal and community testing. The remaining work is mostly wider device and format validation, not a claim that every BookOrbit server configuration or file is covered.

## License and acknowledgements

See [`docs/privacy.md`](docs/privacy.md) for the app's local-data and network behavior. The project builds on BookOrbit, Readium, AndroidX, Jetpack Compose, Kotlin, Media3, OkHttp, Room, and other open-source libraries; their respective licenses and notices remain authoritative.

Thank you to the BookOrbit maintainers and contributors for the server and library experience that inspired this app, to the Readium Foundation and open-source library authors whose work makes the reader possible, and to everyone who tests Lagrange and reports issues.