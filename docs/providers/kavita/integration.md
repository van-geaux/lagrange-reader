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

The series/volume/chapter hierarchy is not treated as a Komga-style book identity. The shared catalog presents chapters as book-like entries while provider requests retain the Kavita chapter, volume, series, and library IDs required by the API.

## Capability state

Implemented provider modules are wired through `KavitaModuleSet` and selected only for profiles with provider ID `kavita`. Unsupported actions remain explicit: EPUB reader routing, chapter unread, audiobook downloads, Home shelves, authors, annotations, statistics, achievements, Smart Scopes, cache warming, and server reading-session history.

Compilation and parser/contract tests do not constitute authenticated demo-server or device validation. Manual validation requires locally supplied credentials through the secure local mechanism; credentials are not stored in profiles, source files, logs, fixtures, or documentation.
