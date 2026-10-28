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
- progress write: `POST /api/Reader/progress` with library, series, volume, chapter, and page IDs;
- chapter read: `POST /api/Reader/mark-chapter-read`;
- chapter download: `GET /api/Download/chapter?chapterId={chapterId}`;
- series cover: `GET /api/Image/series-cover?seriesId={seriesId}`.

## Authentication modes

Kavita profiles retain both supported authentication paths:

- username/password login uses `POST /api/Account/login` and stores the returned Kavita JWT in the encrypted provider credential store;
- server-hosted OIDC login opens Kavita's `/login` page in the existing Lagrange server-sign-in WebView. Kavita owns the identity-provider redirect and `/signin-oidc` callback, then establishes its own authentication cookie. Lagrange reuses that cookie for authenticated API, cover, reader, and download requests and verifies the resulting account through `GET /api/Account`. The WebView displays a reminder to wait while Lagrange verifies the session; it closes when verification completes.

The OIDC action is provider-capability gated. It is shown for Kavita profiles without replacing username/password login, and no identity-provider authorization code, access token, refresh token, password, or cookie is copied into Lagrange logs or application credential storage. Native AppAuth/Custom Tabs remains outside this integration because Kavita owns the registered callback and OIDC client configuration.

The series/volume/chapter hierarchy is not treated as a Komga-style book identity. The shared catalog presents chapters as book-like entries while provider requests retain the Kavita chapter, volume, series, and library IDs required by the API.

## Large-library catalog loading

Kavita browse loading is incremental. The first request fetches one page of series and their chapter metadata, then exposes those results immediately instead of scanning the complete library before rendering. The Browse screen offers `Load more` for subsequent series pages and displays a status explaining that additional books remain.

Each successful page is merged into the provider browser snapshot. If a later request fails or the app restarts, the saved partial catalog remains available and the user can retry loading more. Catalog refresh reuses the first page already loaded during startup, avoiding an immediate duplicate full scan. Book content is not downloaded by this metadata synchronization path.

## Capability state

Implemented provider modules are wired through `KavitaModuleSet` and selected only for profiles with provider ID `kavita`. Unsupported actions remain explicit: EPUB reader routing, chapter unread, audiobook downloads, Home shelves, authors, annotations, statistics, achievements, Smart Scopes, cache warming, and server reading-session history.

Compilation and parser/contract tests do not constitute authenticated demo-server or device validation. Manual validation requires locally supplied credentials through the secure local mechanism; credentials are not stored in profiles, source files, logs, fixtures, or documentation. The Kavita OIDC browser/session path remains pending live device validation against a configured Kavita server.
