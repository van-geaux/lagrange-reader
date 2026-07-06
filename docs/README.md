# Documentation

This folder contains the working engineering documentation for `Lagrange Reader`.

## Documents

- [Architecture](./architecture.md)
- [Local Setup](./setup.md)
- [BookOrbit API Contract](./bookorbit-api.md)
- [Roadmap](./roadmap.md)
- [Handover](./handover.md)

## Current status

The Android project builds locally with `assembleDebug`, can connect to a BookOrbit server, and has working paths for login, library browsing, downloads, local persistence, progress sync, PDF reading, audio playback, and basic EPUB reading.

The main gaps are:

- end-to-end offline verification on real content
- comic/CBZ reader support if the deployed server exposes comics
- stronger retry/backoff behavior and broader sync verification
- broader test coverage
