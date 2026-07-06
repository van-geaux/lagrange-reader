# Documentation

This folder contains the working engineering documentation for `Lagrange Reader`.

## Documents

- [Architecture](./architecture.md)
- [Local Setup](./setup.md)
- [BookOrbit API Contract](./bookorbit-api.md)
- [Roadmap](./roadmap.md)

## Current status

The Android project builds locally with `assembleDebug`, can connect to a BookOrbit server, and has a working scaffold for login, library browsing, downloads, local persistence, and progress sync.

The main gaps are:

- production-grade EPUB/comic reading
- end-to-end offline verification on real content
- stronger sync queue compaction and retry behavior
- broader test coverage
