# Generic OPDS 1 integration

## Scope

The generic OPDS adapter consumes the Atom feed URL supplied by the user. It preserves that URL as configured, including its path and query; it does not guess a server root or append an `/opds` path.

Implemented OPDS 1 behavior:

- Atom feed parsing with DTD and external-entity processing disabled, using JAXP security settings where the selected factory supports them and the Android platform parser's DTD-disabled default otherwise.
- Navigation entries and publication metadata, including title, author, publisher, summary, language, updated date, categories, covers, and acquisition links.
- Server-provided `rel="next"` pagination links.
- Acquisition file selection and bounded streaming downloads into Lagrange-managed storage.
- Local reading of acquired publications through the shared reader.
- Same-origin checks for feed links, images, acquisitions, and redirects; response-size limits apply to feeds and images.
- Optional HTTP Basic authentication with the OPDS account username and password. Login credentials are stored encrypted in the active profile's session and sent on feed, image, and acquisition requests within the configured origin.

Setup accepts the exact feed URL. HTTP 401 and 403 responses are treated as evidence that the feed endpoint is reachable and route the user to sign in. Invalid credentials (401) and forbidden access (403) have distinct user-facing feedback. BookOrbit documents its catalog at `/api/v1/opds` and requires the separate OPDS account credentials with HTTP Basic authentication; see [BookOrbit OPDS setup](https://bookorbit.app/opds/). Bearer tokens, cookies, OIDC, and server-specific authentication extensions are not implemented.

## Calibre-Web Automated compatibility

The OPDS transport is also used to preserve existing Calibre-Web Automated profiles. This legacy provider mode continues to append `/opds` to its configured server URL, uses its saved HTTP Basic credentials for same-origin requests, and keeps the established profile, acquisition-file, local-download, and device-local reading-progress identities. Credentials remain in the encrypted profile-session store. Generic OPDS profiles keep a separate provider identity and use the exact configured feed URL; no existing CWA profile IDs are rewritten. CWA server-side progress synchronization is not implemented.

## Explicit limitations

Generic OPDS does not implement OPDS 2, feed search, series/authors/collections, provider-side reading status or progress synchronization, server-side ratings, Home shelves, or offline metadata-cache synchronization. CWA local reading progress remains device-only and is not synchronized to the server. Reading requires a downloaded publication; direct streaming in the reader is not implemented. Do not infer support for unlisted OPDS extensions or server-specific behavior.

## Verification and manual validation

Focused JVM tests exercise Atom mapping, navigation and continuation links, publication metadata, acquisition mapping, generic HTTP Basic headers on feed/image/acquisition requests, CWA legacy profile/URL/authentication/progress identity, cross-origin link rejection, redirect rejection, unauthorized-feed handling, and acquisition streaming. Kotlin main, JVM-test, and Android-test source compilation is required for the implementation gate.

No live OPDS/CWA server or Android-device validation is claimed here. Manual checks remain pending for anonymous and authenticated generic setup (including BookOrbit's separate OPDS account credentials), navigation/back/refresh, next-page behavior, cover retrieval, alternate acquisition selection, download/reopen/offline behavior, CWA sign-in and retained downloads/progress, and provider switching.
