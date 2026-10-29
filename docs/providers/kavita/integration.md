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

Each successful page is merged into the provider browser snapshot. If a later request fails or the app restarts, the saved partial catalog remains available and the user can retry loading more. Catalog refresh reuses the first page already loaded during startup, avoiding an immediate duplicate full scan. Book content is not downloaded by this metadata synchronization path.

## Cache and projection rules

- Home snapshots are written only after a complete Home request succeeds. A failed refresh returns the previous server/profile-scoped snapshot instead of replacing it with empty shelves.
- Kavita `pagesRead` values are one-based. Every browse, Home, detail, progress, and offline-reader projection converts them to Lagrange's zero-based reader index; an explicit zero-page progress response clears stale local progress fields.
- Multi-file chapter details preserve each nested Kavita file ID. Cached detail restoration returns a Book Detail shell from the persisted browser snapshot and reapplies completed local-download metadata before exposing it offline.
- Background cover/offline warming advances by Kavita catalog page rather than reloading the complete library for every batch. The batch may contain all chapters returned for that page, because Kavita pages series and expands their chapters server-side.

## Capability state

- Implemented provider modules are wired through `KavitaModuleSet` and selected only for profiles with provider ID `kavita`. Unsupported actions remain explicit: audiobook downloads, authors, annotations, statistics, achievements, Smart Scopes, and server reading-session history. Home shelves and paged background cache warming are implemented; audiobook download expansion raises a capability-unavailable error rather than silently returning an empty success.

Compilation and parser/contract tests do not constitute authenticated demo-server or device validation. Manual validation requires locally supplied credentials through the secure local mechanism; credentials are not stored in profiles, source files, logs, fixtures, or documentation. The Kavita OIDC browser/session path remains pending live device validation against a configured Kavita server.
