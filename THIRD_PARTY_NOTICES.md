# Third-party notices

Lagrange Reader is distributed under the GNU General Public License, version 3. The libraries and files below remain under their own licenses. This document records the direct runtime dependencies and the additional notices identified in the resolved release dependency graph.

## Direct runtime dependencies

| Component | Version | License | Source and license notice |
| --- | --- | --- | --- |
| Readium Kotlin Toolkit (`readium-*`) | 3.0.2 | BSD-3-Clause | [Repository](https://github.com/readium/kotlin-toolkit), [license](https://github.com/readium/kotlin-toolkit/blob/main/LICENSE) |
| PdfiumAndroid | 1.9.8 | Apache-2.0 and PDFium BSD terms | [Repository](https://github.com/marain87/PdfiumAndroid), [license](https://github.com/marain87/PdfiumAndroid/blob/master/LICENSE) |
| Jetpack Compose and AndroidX | resolved versions in `app/build.gradle.kts` | Apache-2.0 | [Android Open Source Project](https://source.android.com/docs/setup/about/licenses), [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Kotlin and kotlinx libraries | resolved versions in `app/build.gradle.kts` | Apache-2.0 | [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| Media3 | 1.4.1 | Apache-2.0 | [Repository](https://github.com/androidx/media), [license](https://github.com/androidx/media/blob/release/LICENSE) |
| OkHttp and Okio | 4.12.0 / resolved transitive versions | Apache-2.0 | [Repository](https://github.com/square/okhttp), [license](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| Room, WorkManager, DataStore, Lifecycle, WebKit, Activity | resolved versions in `app/build.gradle.kts` | Apache-2.0 | [AndroidX](https://github.com/androidx/androidx), [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Material Components for Android | 1.12.0 | Apache-2.0 | [Repository](https://github.com/material-components/material-components-android), [license](https://github.com/material-components/material-components-android/blob/master/LICENSE) |
| multiplatform-markdown-renderer | 0.24.0 | Apache-2.0, with MIT-origin portions | [Repository](https://github.com/mikepenz/multiplatform-markdown-renderer), [license](https://github.com/mikepenz/multiplatform-markdown-renderer/blob/develop/LICENSE) |
| Junrar | 8.0.0 | UnRAR license | [Repository](https://github.com/junrar/junrar), [license](https://github.com/junrar/junrar/blob/master/LICENSE) |
| Apache Commons Compress | 1.28.0 | Apache-2.0 | [Repository](https://github.com/apache/commons-compress), [LICENSE](https://github.com/apache/commons-compress/blob/master/LICENSE.txt), [NOTICE](https://github.com/apache/commons-compress/blob/master/NOTICE.txt) |
| XZ for Java | 1.10 | BSD Zero Clause | [Project](https://tukaani.org/xz/java.html), [license](https://github.com/tukaani-project/xz-java/blob/master/COPYING) |
| Junrar runtime SLF4J API | 2.0.17 | MIT | [Repository](https://github.com/qos-ch/slf4j), [license](https://github.com/qos-ch/slf4j/blob/master/LICENSE.txt) |
| JUnit | 4.13.2 | EPL-1.0 | [Repository](https://github.com/junit-team/junit4), [license](https://github.com/junit-team/junit4/blob/main/LICENSE) |

The complete resolved dependency graph is generated from the Gradle release runtime configuration. Versions can change through dependency resolution, so this document must be reviewed when dependencies change.

## Embedded and source-level notices

- `app/src/main/assets/foliate-selection-cfi.js` contains MIT-licensed code by John Factotum. Its license and copyright notice are retained in the file.
- Readium's packaged navigator assets include the `AccessibleDfa` and `IaWriterDuospace` font license notices. Those notices remain inside the dependency artifact.
- OkHttp includes a public-suffix notice in its artifact.
- Joda-Time, Jsoup, SLF4J, Commons Codec, Commons IO, Commons Lang, and Commons Compress include their own license or notice files in their artifacts.

## UnRAR license terms

Junrar includes code governed by the UnRAR license. The relevant restriction is that the source may be used to handle RAR archives, but may not be used to recreate the proprietary RAR compression algorithm. Lagrange uses Junrar for archive extraction only. The upstream license also permits distribution inside other software packages and must be preserved with the distribution.

Full text: [Junrar LICENSE](https://github.com/junrar/junrar/blob/master/LICENSE).

## PdfiumAndroid and PDFium terms

The PdfiumAndroid upstream license contains Apache License 2.0 terms for the Android binding and BSD-style PDFium redistribution terms. Binary redistribution must retain the applicable copyright, license, disclaimer, and non-endorsement notices.

Full text: [PdfiumAndroid LICENSE](https://github.com/marain87/PdfiumAndroid/blob/master/LICENSE).

## Project-owned assets and trademarks

The Lagrange source, artwork, screenshots, and documentation in this repository are project-owned as confirmed by the project owner. GPLv3 does not grant permission to use third-party names, logos, or trademarks. BookOrbit remains an independent project, and the BookOrbit name and marks are not licensed by this notice.
