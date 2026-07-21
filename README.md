# Lagrange Reader

<div align="center">

<img src="artwork/lagrange-mark.svg" alt="Lagrange Reader logo" width="180">

# Lagrange Reader

An offline-first Android reader for BookOrbit.

[![License: Personal and Non-Commercial](https://img.shields.io/badge/license-personal--non--commercial-orange)](LICENSE)
[![Version 1.0.0](https://img.shields.io/badge/version-1.0.0-blue)](app/build/release-artifacts/Lagrange-1.0.0.apk)
[![Build](https://img.shields.io/github/actions/workflow/status/van-geaux/lagrange-reader/android-debug.yml?branch=main&label=build)](https://github.com/van-geaux/lagrange-reader/actions/workflows/android-debug.yml)

</div>

Lagrange Reader is an independent Android app for reading and listening to books hosted on [BookOrbit](https://github.com/BookOrbit). It started with a simple personal need: I love BookOrbit, but I wanted an app that lets me take my library with me and read offline.

This is a community project, not an official BookOrbit application. Development was AI-assisted, with the implementation, testing, and product decisions reviewed by the project owner.

## Screenshots

The following screenshots show the main reading and library experience. More screenshots are available below.

<p align="center">
  <img src="screenshots/03-home-screen.jpg" alt="Lagrange Reader home screen" width="220">
  <img src="screenshots/05-reader-options.jpg" alt="Reader options" width="220">
  <img src="screenshots/09-audiobook-player.jpg" alt="Audiobook player" width="220">
</p>

<details>
<summary>More screenshots</summary>

<p align="center">
  <img src="screenshots/01-server-input.jpg" alt="Image 1" width="200">
  <img src="screenshots/02-login-screen.jpg" alt="Image 2" width="200">
  <img src="screenshots/04-book-detail.jpg" alt="Image 3" width="200">
  <img src="screenshots/08-download-local.jpg" alt="Image 4" width="200">
</p>

<p align="center">
  <img src="screenshots/06-app-options.jpg" alt="Image 5" width="200">
  <img src="screenshots/07-achievements.jpg" alt="Image 6" width="200">
  <img src="screenshots/10-audiobook-read-along.jpg" alt="Image 7" width="200">
</p>

</details>

## Features

- **Offline-first library:** browse cached books and reopen downloaded EPUB, PDF, CBZ, and supported audiobook files without a connection.
- **Two-way sync:** send local reading/listening progress to BookOrbit, receive server-side progress and status changes, and replay queued offline progress after reconnecting.
- **EPUB reading:** paginated chapters, themes, text size, independent margins, chapter/page navigation, exact resume, and keep-awake mode.
- **PDF and comic reading:** Readium-powered PDF and image readers with fullscreen controls, page navigation, Preview isolation, and CBZ/online CBR support.
- **Audiobook playback:** compact player with seeking, chapter selection, playback speed, resume, and read-along support.
- **Library discovery:** Home, libraries, series, authors, search, achievements, local books, filters, sorting, and series navigation.
- **Reliable offline downloads:** progress, cancellation, retry/update flows, cache validation, and safe local replacement.
- **Personalized controls:** five app themes, reader themes, orientation lock, reduce motion, cellular download policy, cache management, and background-network controls.

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

Planned follow-up work includes but not limited to:

- More reading-direction options, including right-to-left and continuous scrolling with configurable page spacing for PDF and CBZ/CBR.
- Moving a book to read status directly from Preview.
- Support for additional book formats.
- Bulk action of local books.

More details are in the [Roadmap](docs/roadmap.md)

## Limitations to know about

Lagrange needs a reachable BookOrbit server for sign-in, online browsing, progress synchronization, and CBR/CB7 page extraction. Downloaded EPUB, PDF, CBZ, and supported audiobook files can be opened without a connection. HTTPS is strongly recommended when connecting to a remote server; explicit HTTP URLs are supported for trusted networks.

The app currently uses the standard BookOrbit username/password login. Direct OIDC/SSO is deferred for now. A connected Android device or emulator is also needed for the full physical validation matrix. Automated JVM tests and APK builds are maintained in the repository.

## Building manually

### Requirements

- Windows, macOS, or Linux with a current Android Studio installation.
- JDK 17.
- Android SDK with API 35 installed.
- An Android device or emulator running API 26 or newer for manual testing.

Clone the repository, open it in Android Studio, and let it use the included Gradle wrapper. From a terminal at the repository root, the release build is:

```powershell
.\gradlew.bat assembleRelease
```

The generated APK is:

```text
app/build/release-artifacts/Lagrange-1.0.0.apk
```

The signed APK is generated at `app/build/release-artifacts/Lagrange-1.0.0.apk`. Keep `release-key.jks` and `keystore.properties` backed up securely; they are intentionally ignored by Git.

Useful verification commands are:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

For machine setup details and the manual test matrix, see [`docs/setup.md`](docs/setup.md) and [`docs/testing.md`](docs/testing.md).

## Relationship with BookOrbit

I have not yet asked the BookOrbit maintainers for permission to distribute or promote this client. I want to test it further first, roughly another two to three weeks of real world use, before starting that conversation. The app is independent, and its name, logo, and documentation should not be read as an endorsement by the BookOrbit maintainers.

## Project status

Version **1.0.0** is considered feature-complete and worthy of release for personal and community testing. The remaining work is mostly wider device and format validation, not a claim that every BookOrbit server configuration or file is covered.

## License and acknowledgements

The project uses the custom [`LICENSE`](LICENSE), which allows free personal and non-commercial use, modification, building, and redistribution. Commercial rights are reserved to the project owner. This is source-available, but it is not an OSI-approved open-source license.

See [`docs/privacy.md`](docs/privacy.md) for the app's local-data and network behavior. The project builds on BookOrbit, Readium, AndroidX, Jetpack Compose, Kotlin, Media3, OkHttp, Room, and other open-source libraries; their respective licenses and notices remain authoritative.

Thank you to the BookOrbit maintainers and contributors for the server and library experience that inspired this app, to the Readium Foundation and open-source library authors whose work makes the reader possible, and to everyone who tests Lagrange and reports issues.