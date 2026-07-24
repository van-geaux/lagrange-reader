# Release Policy

This document defines the current package naming, versioning, and signing expectations for `Lagrange Reader`.

## Package and app naming policy

- repository/project name: `lagrange-reader`
- user-facing app name: `Lagrange`
- current Android namespace: `com.bookorbit.android`
- current Android application id: `com.bookorbit.android`

### Short-term rule

- keep `namespace` and `applicationId` aligned unless a product split requires otherwise
- treat `com.bookorbit.android` as the current shipping id until a deliberate rename is planned and migrated
- if the app is rebranded away from `BookOrbit`, update the repository docs, launcher label, namespace migration plan, and Play-distribution identifiers together rather than piecemeal

### Rename guidance

If a rename is required later:

- change the launcher label and branding assets in the same release window
- migrate package names in source deliberately rather than mixing package and branding transitions across multiple partial commits
- treat `applicationId` changes as a breaking distribution change because Android will treat the renamed app as a different install

## Versioning policy

- use semantic-style `versionName` values such as `0.1.0`, `0.2.0`, and `1.0.0`
- increment `versionCode` on every release build distributed outside local development
- reserve `0.x` for pre-release or prototype builds
- use patch bumps for bug-fix-only releases
- use minor bumps for additive feature releases
- use a major bump when the app reaches an intentionally stable public release line

### Current version marker

- current release: `versionName 1.2.1`, `versionCode 13`
- update both values at the marked `versionCode`/`versionName` lines in [`app/build.gradle.kts`](../app/build.gradle.kts) when preparing a distributed build
- the About screen reads `BuildConfig.VERSION_NAME`; do not hardcode a second version there
- use the `1.x` minor-release line for additive feature releases and increment `versionCode` for every distributed build

## Signing strategy

- debug builds continue using the default Android debug keystore
- release signing keys must not be committed to the repository
- release keystore paths, aliases, and passwords must come from local machine configuration or CI secrets
- keep signing material outside the repo and outside shared documentation that is meant to be committed
- the tag-triggered GitHub Actions release workflow uses GitHub secrets or an equivalent secret store rather than checked-in signing material

## Current state

- Debug and release compilation both pass locally.
- Release APKs are published as GitHub Release assets rather than committed to the repository.
- The historical `v1.1.0` APK is already uploaded to the `v1.1.0` GitHub Release.
- Future `v*` tag pushes use `.github/workflows/android-release.yml` to build a signed APK and create a GitHub Release asset.
- The repository secrets required by the workflow are configured: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
- No release keystore is committed in this repository.
- The tracked tree and repository history contain no sensitive environment/keystore/key paths, high-confidence secret signatures, hardcoded credential assignments, or unexplained production/internal hosts; remaining non-public URL literals are explicit test fixtures and Android emulator loopback addresses.
- The repository no longer stores release APK binaries after this migration.
