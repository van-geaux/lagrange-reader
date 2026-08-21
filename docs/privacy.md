# Privacy Notes

`Lagrange Reader` stores a limited amount of user and server data locally so the app can reconnect, reopen content, and recover progress.

## Local data currently stored

- configured BookOrbit server URL
- selected library id
- WebView and HTTP session cookies managed by Android
- cached library and book browser snapshots for offline fallback
- downloaded book files
- authenticated reader-cache copies for EPUB, PDF, and CBZ open flows before full download
- active reader state used to reopen the last book after restart
- queued and last-synced reading/listening progress markers

## Storage location

- app-private files and cache directories
- Android `DataStore` preferences for small app settings
- `WorkManager` state for queued sync work

## Current behavior

- local data is scoped to the current Android app sandbox and is not shared with other apps
- explicit HTTP BookOrbit URLs are supported, but cleartext transport can expose credentials, session tokens, metadata, progress, and content to other parties on the network; HTTPS is strongly recommended
- the app trusts both Android's system CA store and user-installed CAs for all HTTPS connections; this is app-wide because the BookOrbit server URL is chosen by the user at runtime, so it lets self-hosted servers using a private/internal CA work over HTTPS without extra configuration, but it also means the app will trust any CA the user (or someone with access to the device) has installed, which is a broader trust surface than the system-only default
- changing or clearing the configured server resets session state, but server-scoped cached browser and progress data may remain on disk
- sign-out clears account-owned progress, reading-session events, annotations, catalog/detail/browser/reader state, and queued account-owned progress; downloaded media is preserved on-device as device-shared Local books and remains available to any account on the same device
- downloaded files remain on disk until the user removes them from the browser UI
- queued progress may remain on disk until it is successfully synced or superseded

## Current gaps

- there is no in-app privacy policy screen yet
- there is no one-tap “wipe all local data” action yet
- retention periods for cached browser snapshots and queued progress are not yet configurable
