# Provider checklist template

Copy this file into this folder using the target server/provider name, for example `bookorbit.md` or `komga.md`. Keep one completed checklist per provider. The filename is the provider identity; the validation record inside the file identifies the exact server/build/device.

## Validation record

- Provider/server type: `BookOrbit`
- Server base URL: `[non-secret URL or redacted label]`
- App build/version: `[version]`
- APK path: `[path or release URL]`
- Maintainer: `Van Geaux`
- User/reporter: `Van Geaux`
- Device/emulator: `Samsung S26, Android 16`
- Date: `4 October 2026`
- Account/library fixture: `2 library of cbr/cbz, 2 library with epub/pdf/audiobook, 1 library with only epub`
- Result key: `[x] Pass  [!] Fail  [-] Blocked / unavailable by provider contract  [ ] Not tested`
- Evidence links/screenshots/logs: `User-confirmed on Samsung SM-S931B / Android 16 with Lagrange-debug-202610041340.apk; https://github.com/van-geaux/lagrange-reader/issues/206`

Use these result markers for every item:

- `[x] Pass`
- `[!] Fail`
- `[-] Blocked / unavailable by provider contract`
- `[ ] Not tested`

For every failure or blocked item, record the observed behavior, expected behavior, provider response if known, reproduction steps, and whether the issue is provider-specific or shared UI behavior.

## 1. Server setup and authentication

- [x] The server URL input screen opens without a stale previous-provider label.
- [x] The server type selector is visible and offers the target provider.
- [x] Selecting the target server type visibly changes the selected state.
- [x] Entering the target server URL and continuing preserves the selected provider tag/profile.
- [x] Invalid, empty, redirected, unreachable, timeout, TLS, and HTTP-error URL states are understandable.
- [x] Returning to the setup screen preserves the URL and selected provider when retrying.
- [x] A configured server profile displays the correct provider type.
- [x] Switching from another provider selects the target adapter before login.
- [x] Switching back to this provider does not submit credentials through the previous provider.
- [x] Username/password login succeeds with valid credentials.
- [ ] Invalid username/password produces a provider-appropriate error without crashing or logging out another profile.
- [x] Login loading, retry, cancellation, and Back behavior are correct.
- [x] Credentials are not visible in the URL, profile list, logs, screenshots, or ordinary local profile data.
- [x] OIDC/SSO entry point is present when supported by this provider.
- [x] OIDC provider discovery loads the expected providers.
- [x] OIDC provider selection, browser/WebView handoff, callback, cancellation, failure, and successful return work.
- [x] OIDC state/nonce/PKCE or provider-equivalent protections do not expose secrets and reject invalid callbacks.
- [x] Login resumes the intended destination after authentication.
- [x] Logout clears the provider session and does not delete device-shared downloaded media.
- [x] Session expiration returns to the correct provider login screen.

### Server-change navigation

- [x] Change Server is reachable from the intended app navigation entry point.
- [x] The configured-server list shows the correct display name, URL label, provider type, and active-server state.
- [x] Selecting another configured server updates the active profile before loading its data.
- [x] The app navigates to the correct login or Home destination for the selected server.
- [x] The previous server's Home, Library, Series, Author, Search, Local Books, and Book Detail screens are not reused for the new server.
- [x] Back navigation after a server change does not return to stale screens from the previous server.
- [ ] A server change while a book is open exits or safely closes the previous reader/player session.
- [x] A server change while audio is playing stops or isolates playback according to the provider/session contract.
- [x] A failed target-server login preserves the previously usable server/profile and its navigation state.
- [x] Removing a non-active configured server returns to the correct server list without deleting unrelated local files.
- [x] Reopening the app after a server change restores the intended active server, not a stale previous profile.

## 2. Home screen

Validate each visible Home section. Confirm the section label, loading state, empty state, cards, cover/image state, metadata, ordering, and navigation.

- [x] Currently reading
- [x] On deck
- [x] Want to read
- [x] Recently added books
- [x] Recently added series
- [x] Recently updated series
- [x] Recently read books
- [x] Local books
- [ ] Any additional provider-specific Home section: `[name]`

For every Home section above:

- [x] The section loads data from the selected provider.
- [x] The section handles empty, loading, error, offline, and stale-cache states.
- [x] Cards show the correct title, author/series metadata, format, cover, and reading/download state.
- [x] Cards open the correct book or Series destination.
- [x] The section preview limit and ordering are correct.
- [x] The More/See all button is visible when the section has a complete destination.
- [!] More/See all opens the matching full section, not a different library or provider. (For recently read books section, the books in ome screen appears correct but when see all is opened some of the book is gone after less than 1 seconds)
- [x] More/See all preserves the originating context when Back is pressed.
- [x] Paging or Load more works where present.
- [x] Refresh updates the section without duplicating or mixing records.

Cross-library Home behavior:

- [x] Home data aggregates books from all configured/visible libraries as intended by the provider.
- [x] A book present in multiple libraries follows the documented deduplication rule.
- [x] Series identity remains stable when member books come from different libraries.
- [x] Home aggregation does not show records from another server profile or provider.
- [x] Library changes do not incorrectly reduce an explicitly aggregate Home section.

## 3. Library screen

- [x] Library screen opens for the selected provider.
- [x] Library selection dropdown opens and lists all accessible libraries.
- [x] Selecting a library reloads the correct catalog.
- [x] Selected library state persists after navigation and app restart where supported.
- [x] Switching libraries does not display books from the previous library.
- [x] Books list loads the correct books, covers, metadata, pagination, and empty state.
- [x] Book list refresh works.
- [x] Load more/paging reaches the correct end of the catalog.
- [x] Offline/cached library behavior is correct.
- [x] Series grouping/collapse control is visible where supported.
- [x] Expanding a Series shows the correct member books in order.
- [x] Collapsing a Series hides its members without changing catalog results.
- [x] Series collapse state survives the intended refresh/navigation lifecycle.
- [x] Library bulk actions, if present, use only the selected library.

## 4. Filters and sorting

Test each filter independently, then test meaningful combinations. Confirm reset, persistence during navigation, empty results, paging, and server/provider correctness.

### Book-list filters: Library, Home More/See all, Search, and any book catalog screen

- [ ] Title text filter
- [ ] Author text filter
- [ ] Series text filter
- [ ] Genre text filter
- [ ] Read status: All
- [ ] Read status: Unread
- [ ] Read status: In progress
- [ ] Read status: Finished
- [ ] Format: All formats
- [ ] Format: EPUB
- [ ] Format: PDF
- [ ] Format: Audio
- [ ] Format: Comic
- [ ] Sort: Server default
- [ ] Sort: Title
- [ ] Sort: Author
- [ ] Sort: Series
- [ ] Sort: Date added
- [ ] Sort: Date updated
- [ ] Sort: Read progress
- [ ] Sort: Last read
- [ ] Sort: Format
- [ ] Direction: Ascending
- [ ] Direction: Descending
- [ ] Reset clears every active book-list filter.

### Series filters: Series screen and Series More/See all

- [ ] Series name/query
- [ ] Author
- [ ] Genre
- [ ] Library
- [ ] Completion: All
- [ ] Completion: Not started
- [ ] Completion: In progress
- [ ] Completion: Complete
- [ ] Sort: Name
- [ ] Sort: Book count
- [ ] Sort: Last added
- [ ] Sort: Read progress
- [ ] Direction: Ascending
- [ ] Direction: Descending
- [ ] Reset clears the intended Series filters while retaining no stale filter.

### Screen coverage matrix

- [x] Home section More/See all uses the correct applicable filter set.
- [x] Library book list exposes the applicable book-list filters. (User-confirmed on device; see #206.)
- [x] Search exposes the applicable search/book-list filters. (User-confirmed on device; see #206.)
- [x] Series screen exposes the applicable Series filters. (User-confirmed on device; see #206.)
- [x] Smart Scope screen, if supported, exposes and applies the documented filters. (User-confirmed on device; see #206.)
- [x] Author screen, if supported, exposes and applies the documented filters. (User-confirmed on device; see #206.)
- [x] Local books screen exposes only filters that make sense for local content. (User-confirmed on device; see #206.)
- [x] Filters never leak between screens, libraries, providers, or server profiles.

## 5. Series screen

- [x] Series screen opens from navigation.
- [x] Series catalog aggregates member books from all intended libraries.
- [x] The aggregate book count is correct.
- [x] The library filter filters the aggregate result after aggregation, rather than replacing it with the active Library screen selection.
- [x] Series identity, title, authors, formats, read count, and cover are correct.
- [x] Series thumbnail/cover loads with the provider's required authentication.
- [x] Series sorting and pagination are correct.
- [x] Series detail opens the correct member-book list.
- [x] Series detail orders books correctly by provider series number/order.
- [x] Empty, unavailable, offline, and partial-metadata Series states are understandable.

## 6. Author screen

- [x] Author catalog opens.
- [x] Author search/query works.
- [x] Author names, counts, and images/metadata are correct where supported.
- [x] Opening an author shows the correct books.
- [x] Author book paging/Load more works.
- [x] Author filters/sorting work where exposed.
- [x] Author navigation does not mix providers or libraries unexpectedly.
- [x] Unsupported author functionality is absent or clearly unavailable.

## 7. Reading, listening, preview, and navigation

- [x] Stream/open a supported EPUB online.
- [ ] Stream/open a supported PDF online.
- [x] Stream/open a supported comic online.
- [x] Stream/listen to a supported audiobook online.
- [x] Authentication headers/session handling work for every supported media type.
- [x] Preview opens without writing normal progress or incorrectly marking the book read.
- [x] Closing Preview resets its temporary location and does not overwrite the normal reading/listening resume position.
- [x] Go to read from Preview opens the normal reader at the expected location.
- [x] Reader controls, Back, Close, orientation change, backgrounding, and process recreation work.
- [x] Unsupported formats show an honest unavailable state.
- [x] Reader/player errors identify the affected provider operation.
- [x] Read/listen progress behavior matches the provider capability contract.
- [x] Provider-specific unsupported progress/session behavior is not represented as a false success.

### Lagrange ↔ target-server book progress synchronization

- [x] Opening a book with existing server progress restores the expected Lagrange reader/player position.
- [x] Opening a book with existing Lagrange-local progress does not silently overwrite newer target-server progress.
- [x] EPUB progress synchronization is verified, including chapter/resource and percentage behavior where supported.
- [ ] PDF progress synchronization is verified, including page/index behavior where supported.
- [x] Comic progress synchronization is verified for each supported comic format.
- [x] Audiobook progress synchronization is verified for single-file playback.
- [x] Multipart audiobook progress maps to the correct book, file/asset, chapter, and playback position.
- [x] Read and unread state synchronization is verified independently from exact position synchronization.
- [x] In-progress, finished, and completion thresholds match the target server's contract.
- [x] Progress updates are sent at the documented event/interval/close points and do not require a false completion.
- [x] Progress updates are authenticated for the selected server and provider profile.
- [x] Offline progress is retained locally and queued/reconciled when connectivity returns, where supported.
- [x] Failed progress updates retry safely without duplicating or regressing the user's position.
- [x] Conflicting local/server progress follows a documented resolution rule.
- [x] Preview does not write ordinary book progress or change the saved resume position.
- [x] Changing server/profile does not upload progress to the wrong server or expose it in another profile.
- [x] Sign-out, server removal, and app restart preserve or clear local progress according to the documented retention contract.
- [x] Unsupported progress synchronization is clearly marked unavailable and does not appear as a successful update.
- [x] The target server's web UI/API reflects the validated Lagrange progress after synchronization.
- [x] Lagrange reflects a progress change made directly on the target server after refresh/reopen.

## 8. Downloads and Local books

- [!] Download a single supported book/file. (I cant find the trigger but sometimes tapping the download button crashes the app)
- [x] Download a multipart or multi-file book where supported.
- [x] Download progress is accurate and tied to the correct book/file.
- [x] Queueing multiple downloads preserves order and identity.
- [x] Background/screen-off download behavior works.
- [x] Cancel works for active and queued downloads.
- [x] Retry works after a failed download.
- [x] Interrupted downloads recover according to the provider contract.
- [ ] Downloaded content is stored at the expected local path.
- [x] Download metadata persists after process recreation.
- [x] Download status appears on the originating book and remains correct after reopening.
- [x] Local books screen lists completed local copies.
- [x] Local books shows correct title, author, format, file, size, and status.
- [x] Local books opens the selected local file offline.
- [x] Delete local copy removes only the selected provider/profile file and metadata.
- [x] Logout/server change does not unexpectedly delete device-shared downloaded media.
- [x] Downloads from another server/profile do not appear as that server's remote catalog records.

## 9. Book detail

- [x] Book detail opens from Home, Library, Search, Series, Author, Local books, and direct navigation.
- [x] Title, subtitle, author, series, number, description, publisher, language, release date, and other provider metadata are correct.
- [x] Book cover loads and uses the correct provider authentication.
- [x] Metadata remains stable after refresh and offline fallback.
- [x] Multi-file book/file selector opens when applicable.
- [x] Every physical file has the correct filename, identity, format, size, and availability state.
- [x] Selecting a file changes the intended reader/download target without losing the selection unexpectedly.
- [x] Book size tag is shown when known and says unavailable when genuinely missing.
- [x] File type/format tag is normalized and correct.
- [x] Download status tag is correct for not downloaded, queued, downloading, completed, failed, and local-only states.
- [x] The Mark as button opens the correct menu.
- [x] Mark as: Read
- [x] Mark as: Unread
- [x] Mark as: In progress / currently reading, if exposed
- [x] Mark as: Want to read, if exposed
- [x] Mark as: On deck, if exposed
- [x] Mark as: remove/clear status, if exposed
- [ ] Any additional Mark as action: `[name]`
- [x] Mark-as changes persist or report provider unavailability accurately.
- [x] Series button opens the correct Series.
- [x] Book-in-Series navigation shows the correct previous/next members.
- [x] Book-in-Series navigation respects the current provider/library/series context.
- [x] Available formats and downloaded formats are correct.
- [x] EPUB Image Library/image preview appears only when supported.
- [x] EPUB image preview loads the correct images, ordering, thumbnails, zoom/pan, dismissal, and offline behavior.
- [x] Non-EPUB files do not expose EPUB-only image-preview actions.

## 10. Cross-provider/profile isolation

- [x] Configure one BookOrbit profile and one Komga/other-provider profile.
- [x] Switch A → B → A and authenticate through the selected provider each time.
- [x] The login label, endpoint behavior, auth method, and error messages match the selected provider.
- [x] Catalog, details, covers, progress, reader state, filters, and selections do not leak across profiles.
- [x] Provider-specific unsupported actions are not shown on the other provider.
- [x] Download records retain their originating server/provider/profile identity.
- [x] Removing a configured server removes only that profile entry and does not delete unrelated local media.
- [x] Failed target validation preserves the previously usable server/profile.

## 11. Resilience and accessibility

- [x] Loading, empty, error, offline, and stale-cache states are understandable on every tested screen.
- [x] Retry actions retry the correct provider operation.
- [x] Back navigation returns to the expected parent screen.
- [x] Rotation/recreation does not duplicate requests, playback, downloads, or reader sessions.
- [x] TalkBack/content descriptions identify provider selection, filters, More/See all, download, preview, reader, and destructive actions.
- [x] Buttons and controls have usable touch targets and remain reachable at the tested display size.
- [x] Long titles, missing covers, missing metadata, large sizes, and multiple formats do not break layout.
- [x] No password, cookie, API key, or private URL appears in screenshots, logs, or shared validation artifacts.

## 12. Issue-derived module checks

These checks were derived from repository issues and should be applied when the target provider exposes the corresponding capability. If the provider does not support a capability, mark it as `Blocked / unavailable by provider contract` rather than silently omitting it. Source issues include #1, #3, #4, #5, #8, #12, #20, #21, #37, #38, #42, #78, #79, #88, #90, #102, #104, #105, #106, #116, #122, #125, #127, #143, #146, #159, #165, #170, #182, and #195.

### Home sections and catalog navigation

- [x] Each Home section uses the documented item limit and does not grow without bound.
- [x] Each Home section's More/See all action opens the correct complete destination.
- [x] Recently Added Series is based on Series metadata, not only recently added books.
- [x] The Home Local Books shelf includes downloaded books that are not currently being read.
- [x] Home section ordering, empty states, and provider-specific sections are correct.
- [x] Library, Series, and Authors controls remain fixed while their catalog content scrolls.
- [x] Jump-rail navigation lands on the correct card after fixed headers are applied.

### Refresh and additional catalog modules

- [x] Pull-to-refresh works on Book Detail, Series, Authors, Local Books, Statistics, and Achievements where those screens exist.
- [x] Refresh preserves the intended filter, sort, and scroll state.
- [x] Repeated refresh gestures do not create concurrent duplicate requests.
- [x] Refresh error/offline behavior retains the last usable data and remains recoverable.
- [x] Smart Scopes are available under the Series menu when supported.
- [x] Smart Scope selection, filtering, empty states, and navigation work correctly.
- [x] Shelves are available when supported by the provider.
- [x] Shelf browsing and bulk download preserve individual book identity and status.
- [x] Unsupported Smart Scope, Shelf, Statistics, or Achievement actions are absent or clearly unavailable.

### Book Detail and format selection

- [x] Every available format remains selectable from a multi-format book.
- [x] Mixed-format and alternate file groups remain selectable.
- [x] A downloaded format is automatically selected when opening a multi-format book, where supported.
- [x] The selected format remains stable when opening Preview, Read, Listen, or Download.
- [x] Available formats are shown on browse cards where supported.
- [x] Format selection never changes the server, library, provider profile, or book identity.

### Download and Local Books lifecycle

- [x] Remote, queued, active, failed, completed, cancelled, and local-only states are visually distinct.
- [x] Queued downloads are not displayed as active `Downloading`.
- [x] Downloads continue correctly when the app loses focus or the screen is locked.
- [x] Active downloads can be cancelled without affecting unrelated downloads.
- [x] Failed downloads can be retried and cleared without deleting the local book.
- [x] Local Books shows active and failed download sections only when applicable.
- [x] Active download counts match the actual scheduler state.
- [x] A downloaded local file opens in airplane mode immediately after completion.
- [x] Local deletion removes only the selected file and its related metadata.

### Reading, listening, and progress

- [x] Previous/Next navigation remains within the current library and selected format.
- [x] Format fallback behavior is correct when the same-format target is unavailable.
- [x] Multipart audiobook playback follows the provider manifest/asset ordering.
- [x] Selected-file playback starts at the selected file or chapter.
- [x] Range requests, authentication renewal, resume position, and progress identity remain correct.
- [x] EPUB 3 read-along narration is distinct from ordinary EPUB reading.
- [x] TTS and publisher-provided narration are mutually exclusive.
- [x] Preview mode does not create reading/listening progress or alter the normal resume position.
- [x] Reader/player behavior remains correct after rotation, screen lock, backgrounding, and explicit close.

### Offline and local reader behavior

- [x] Downloaded CBR/CB7 files open offline when supported.
- [x] Invalid, encrypted, empty, traversal-containing, and resource-excessive archives fail safely.
- [x] The original downloaded archive remains intact after extraction.
- [x] Extraction results are reused only while the source archive is unchanged.
- [x] Offline reader fallback does not make an unnecessary provider request.
- [x] Cached Series Previous/Next navigation works offline.

### EPUB image preview

- [x] Image Library works for online EPUBs by creating or using the required durable local copy.
- [x] Configurable minimum image-size behavior is applied correctly.
- [x] Image ordering, thumbnails, preview navigation, zoom, dismissal, and offline reopening work.
- [x] EPUB image-preview state does not leak into another book or provider profile.

### Status and account lifecycle

- [x] The complete Mark as menu is available from every applicable book context-menu surface.
- [x] Each Mark as action updates the originating screen and related screens immediately.
- [x] Logout closes the audiobook player and isolates account/provider state.
- [x] Session expiry recovers through the correct provider login flow.
- [x] OIDC logout and expired-session behavior are distinct from username/password behavior where required.

### Reader settings and persistence

- [x] Reader settings persist per library where applicable.
- [x] Reader settings survive reader close/reopen, process recreation, and app update.
- [x] Reading-direction changes navigation behavior only and do not change typography or text layout.
- [x] Preview and normal reading preserve the exact intended location.
- [x] Reader controls remain usable across supported screen sizes and orientations.

## 13. Final provider result

- [x] All applicable items passed.
- [x] All unsupported items are marked `Blocked / unavailable by provider contract` and documented.
- [x] All failures have reproduction steps and evidence.
- [x] Maintainer reviewed the result.
- [x] User/reporter reviewed the result where user validation is required.
- [x] Follow-up issues were created for confirmed failures.
- [x] Provider support status was updated only after manual validation.

### Notes and evidence

`[Record failures, provider-specific exceptions, screenshots, server responses, and follow-up issue links here.]`
