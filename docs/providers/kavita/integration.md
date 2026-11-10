# Kavita integration checkpoint

This checkpoint adds the Kavita provider path through authentication, library discovery, catalog projection, details, covers, reader routing, progress, status, and downloads.

## Verified API contract

The adapter follows Kavita's upstream OpenAPI contract:

- authentication: `POST /api/Account/login` with a JSON username/password body; the response supplies the JWT used for subsequent requests;
- libraries: `GET /api/Library/libraries`;
- series catalog: `POST /api/Series/v2?PageNumber={page}&PageSize={size}`;
- series details and chapters: `GET /api/Series/series-detail?seriesId={seriesId}`;
- chapter details: `GET /api/Series/chapter?chapterId={chapterId}`;
- reader information: `GET /api/Reader/chapter-info?chapterId={chapterId}`;
- comic pages: `GET /api/Reader/image?chapterId={chapterId}&page={page}`;
- PDF reader stream: `GET /api/Reader/pdf?chapterId={chapterId}`;
- progress read: `GET /api/Reader/get-progress?chapterId={chapterId}`;
- progress write: `POST /api/Reader/progress` with library, series, volume, chapter, and page IDs; `pageNum = 0` resets one chapter to unread;
- chapter read: `POST /api/Reader/mark-chapter-read`;
- chapter download: `GET /api/Download/chapter?chapterId={chapterId}`;
- series cover: `GET /api/Image/series-cover?seriesId={seriesId}`.

## Authentication modes

Kavita profiles retain both supported authentication paths:

- username/password login uses `POST /api/Account/login` and stores the returned Kavita JWT in the encrypted provider credential store;
- server-hosted OIDC login opens Kavita's `/login` page in the existing Lagrange server-sign-in WebView. Kavita owns the identity-provider redirect and `/signin-oidc` callback, then establishes its own authentication cookie. Lagrange saves that cookie encrypted per profile, clears the process-wide WebView cookie jar before restoring another profile, and reuses only the active profile's cookie for authenticated API, cover, reader, and download requests. It verifies the resulting account through `GET /api/Account`. The WebView displays a reminder to wait while Lagrange verifies the session; it closes when verification completes.

The OIDC action is provider-capability gated. It is shown for Kavita profiles without replacing username/password login. Identity-provider authorization codes, access tokens, refresh tokens, and passwords are not copied into Lagrange logs or profile data; the server session cookie is retained only in the encrypted provider credential store so the active profile can restore its Kavita session. Native AppAuth/Custom Tabs remains outside this integration because Kavita owns the registered callback and OIDC client configuration.

The series/volume/chapter hierarchy is not treated as a Komga-style book identity. The shared catalog presents chapters as book-like entries while provider requests retain the Kavita chapter, volume, series, and library IDs required by the API.

## Large-library catalog loading

Kavita browse loading is incremental. The first request fetches one page of series and their chapter metadata, then exposes those results immediately instead of scanning the complete library before rendering. The Browse screen offers `Load more` for subsequent series pages and displays a status explaining that additional books remain.

When Browse is scoped to a selected library, the adapter includes Kavita's library statement in each `POST /api/Series/v2` request before requesting that page. This keeps the server-side result set library-scoped before series pagination; the client still applies the matching library projection while flattening series chapters. An unfiltered catalog omits that statement.

Each successful page is merged into the provider browser snapshot. If a later request fails or the app restarts, the saved partial catalog remains available and the user can retry loading more. Catalog refresh reuses the first page already loaded during startup, avoiding an immediate duplicate full scan. Book content is not downloaded by this metadata synchronization path.

## Home shelf failure isolation

Home loads the On Deck, recently updated, and recently added shelves independently. A failure in one shelf request (including its dependent series-card or On Deck book resolution) produces an empty result for that shelf while successful sibling shelves remain available; coroutine cancellation is still propagated. This is distinct from the persisted snapshot fallback used when the broader Home load is unavailable.

## Cache and projection rules

- A completed Home snapshot is persisted only after the Home projection finishes. On a partial refresh, each failed shelf contributes an empty shelf to the result rather than aborting the other shelf requests; an unavailable broader Home load can still return the previous server/profile-scoped snapshot.
- Kavita `pagesRead` values are one-based. Every browse, Home, detail, progress, and offline-reader projection converts them to Lagrange's zero-based reader index; an explicit zero-page progress response clears stale local progress fields.
- Multi-file chapter details preserve each nested Kavita file ID. Cached detail restoration returns a Book Detail shell from the persisted browser snapshot and reapplies completed local-download metadata before exposing it offline.
- Kavita's background cache module accepts page and item bounds, but current `warmOfflineCacheBatch` does not enforce `maxItems`/`maxDownloads` within a page, and `startOfflineCacheUpdate` still preloads the full catalog before starting background work. Bounded, resumable cache warming remains a follow-up; each requested catalog page can expand to multiple chapters.

## Capability state

- Implemented provider modules are wired through `KavitaModuleSet` and selected only for profiles with provider ID `kavita`. The provider advertises its implemented library book catalog and Series catalog capabilities; focused contract tests cover the capability flags, library-filter request shape, and per-shelf fallback behavior. On 2026-11-10, an authenticated Android 15/API 35 emulator run validated username/password sign-in, Home Continue reading/On deck display, and Libraries → Browse paging for the Light Novel and Manga libraries. EPUB PToC text bookmarks support list/add/open/delete when Kavita's chapter page and structural `BookScrollId` map to the opened EPUB; title/note editing is unavailable, and unmappable locations fail explicitly. Other unsupported actions remain explicit: audiobook downloads, authors, annotations, statistics, achievements, Smart Scopes, and server reading-session history. Home shelves and paged background cache warming are implemented; audiobook download expansion raises a capability-unavailable error rather than silently returning an empty success.

Compilation, contract tests, and the scoped emulator run do not validate every server capability. Manual validation used credentials from a local secret file that were not recorded in profiles, source, logs, fixtures, or documentation. The emulator run did not induce a per-shelf failure, did not inspect every Home shelf, and did not validate physical-device behavior. The Kavita OIDC browser/session path remains pending live device validation against a configured Kavita server.
