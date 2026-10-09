# EPUB text-to-speech module

EPUB text-to-speech is an independent reader capability. It is not a BookOrbit or Komga server module because narration operates on the opened EPUB publication and local reader state rather than a provider endpoint.

## Contract

`core/EpubTtsModule.kt` defines the application boundary for:

- account/session invalidation;
- request generation and stale-request checks;
- starting and stopping the playback service;
- awaiting service shutdown during account-bound cleanup.

`epubtts/EpubTtsModuleImpl.kt` is the current application implementation. It owns the boundary to the service and request/account session guards, while the service retains Android foreground-service and MediaSession responsibilities.

## Implementation pieces

The current implementation is split across these feature files:

- `EpubTtsPlaybackService.kt`: foreground playback service, MediaSession, wake lock, service state, reader-position updates, notification actions, and lifecycle cleanup.
- `EpubTtsControls.kt`: reader playback controls and settings UI.
- `EpubTtsSettings.kt`: speed, pitch, punctuation-pause settings, normalization, and parsing.
- `EpubTtsProgress.kt`: bounded progress-queue policy.
- `PunctuationPausingTtsEngine.kt`: punctuation-aware utterance segmentation and pause behavior.
- `ReadiumEpubTtsPositionStore.kt`: EPUB TTS resume-position persistence.

The EPUB reader owns publication-specific navigation and overlays. It uses `EpubTtsModuleImpl` for service/session operations and observes the service binder for playback state, controls, and locator updates.

The EPUB reader passes both the selected text and a paragraph locator when `Listen from here` is invoked. TTS scans utterances for the normalized selected text first, so a selected word or phrase starts in the sentence containing that text rather than at the paragraph's first sentence or the chapter beginning; the paragraph selector is only a fallback when selected text is unavailable.

## Account and lifecycle isolation

TTS request IDs reject stale starts. Account epochs reject work belonging to a previous authenticated session. Server change, logout, and related account-bound cleanup invalidate the TTS account epoch and stop the TTS service before the old session is discarded.

The TTS module does not upload credentials or expose provider-specific network behavior. Its progress updates use the existing reader/progress boundary when the active provider supports that operation; the module itself does not assume that every provider can synchronize reading progress.

## Persistence and settings

TTS preferences are stored by `AppPreferencesStore`, separately from server profiles and credentials. The persisted settings include speed, pitch, punctuation pauses, and whether the book title appears on the lock screen. Speed and pitch use immediate fine/coarse adjustments; punctuation and lock-screen settings remain Apply-based. Custom punctuation pauses are disabled by default and their fields are shown only when enabled. EPUB TTS locator state is stored separately from normal EPUB reader position.

## Unsupported and deferred behavior

TTS requires a readable EPUB and an available Android/Readium TTS engine. It is unavailable for Preview mode and publications where the engine cannot be created. Unsupported provider progress synchronization must remain unavailable rather than being represented as a successful TTS operation.
