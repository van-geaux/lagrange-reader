# Roadmap

This document contains current project direction only. Historical work orders and dated implementation logs are preserved in [`docs/roadmap-archive.md`](roadmap-archive.md).

## Current status

- Release: Lagrange 1.5.4 is published at [`v1.5.4`](https://github.com/van-geaux/lagrange-reader/releases/tag/v1.5.4). See [`release-notes/v1.5.4.md`](release-notes/v1.5.4.md).
- Version marker: `versionName 1.5.4`, `versionCode 25` in `app/build.gradle.kts`.
- Product direction: maintain Lagrange while expanding provider support beyond BookOrbit. BookOrbit and Komga provider modules are the current integration surfaces; additional providers require a focused contract and validation plan.
- Current release capabilities include EPUB 3 read-along narration, BookOrbit OIDC WebView sign-in, immersive reader controls, audiobook speed/progress controls, grouped physical-file downloads, and client-side offline CBR/CB7 extraction.
- MOBI, AZW, AZW3, and FB2 remain explicitly unsupported. Native AppAuth/Custom Tabs remains deferred until upstream mobile-redirect support is deployed.

## Active validation and follow-up

1. [ ] Validate native AppAuth/Custom Tabs only after the upstream redirect and provider-registration prerequisites are available.
2. [ ] Validate [#118](https://github.com/van-geaux/lagrange-reader/issues/118) PDF links and unsupported-scheme fallback on a connected device or emulator; automated verification is complete.
3. [ ] Continue provider checklist validation when a provider module or server contract changes. Record user/device confirmation separately from automated checks.
4. [ ] Decide the next user-directed implementation item before opening new feature work.
5. [ ] Consider Android e-ink validation for [#25](https://github.com/van-geaux/lagrange-reader/issues/25) only when a suitable device is available; no minSdk change is currently recommended.
6. [ ] Treat broader offline archive extraction and optional device matrices as separately scoped follow-up.

## Decision and documentation rules

- Use [`CHECKLIST.md`](../CHECKLIST.md) for active completion and validation status.
- Use [`docs/testing.md`](testing.md) for reusable automated and manual procedures.
- Use [`docs/architecture.md`](architecture.md), [`docs/bookorbit-api.md`](bookorbit-api.md), and [`docs/ui-ux.md`](ui-ux.md) for current contracts and guardrails.
- Record historical implementation detail in [`docs/roadmap-archive.md`](roadmap-archive.md), not in this active roadmap.
- Before changing roadmap priorities, confirm the product decision and update only the current sections.
