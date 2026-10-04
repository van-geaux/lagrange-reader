# Provider checklist template

Copy this file into this folder using the target server/provider name, for example `bookorbit.md` or `komga.md`. Keep one completed checklist per provider. The filename is the provider identity; the validation record inside the file identifies the exact server/build/device.

## Validation record

- Provider/server type: `[BookOrbit / Komga / other]`
- Server base URL: `[non-secret URL or redacted label]`
- App build/version: `[version]`
- APK path: `[path or release URL]`
- Maintainer: `[name]`
- User/reporter: `[name]`
- Device/emulator: `[model and Android version]`
- Date: `[YYYY-MM-DD]`
- Account/library fixture: `[description]`
- Result key: `[x] Pass  [!] Fail  [-] Blocked / unavailable by provider contract  [ ] Not tested`
- Evidence links/screenshots/logs: `[locations]`

Use these result markers for every item:

- `[x] Pass`
- `[!] Fail`
- `[-] Blocked / unavailable by provider contract`
- `[ ] Not tested`

For every failure or blocked item, record the observed behavior, expected behavior, provider response if known, reproduction steps, and whether the issue is provider-specific or shared UI behavior.

## 1. Server setup and authentication

- [ ] The server URL input screen opens without a stale previous-provider label.
- [ ] The server type selector is visible and offers the target provider.
- [ ] Selecting the target server type visibly changes the selected state.
- [ ] Entering the target server URL and continuing preserves the selected provider tag/profile.
- [ ] Invalid, empty, redirected, unreachable, timeout, TLS, and HTTP-error URL states are understandable.
- [ ] Returning to the setup screen preserves the URL and selected provider when retrying.
- [ ] A configured server profile displays the correct provider type.
- [ ] Switching from another provider selects the target adapter before login.
- [ ] Switching back to this provider does not submit credentials through the previous provider.
- [ ] Username/password login succeeds with valid credentials.
- [ ] Invalid username/password produces a provider-appropriate error without crashing or logging out another profile.
- [ ] Login loading, retry, cancellation, and Back behavior are correct.
- [ ] Credentials are not visible in the URL, profile list, logs, screenshots, or ordinary local profile data.
- [ ] OIDC/SSO entry point is present when supported by this provider.
- [ ] OIDC provider discovery loads the expected providers.
- [ ] OIDC provider selection, browser/WebView handoff, callback, cancellation, failure, and successful return work.
- [ ] OIDC state/nonce/PKCE or provider-equivalent protections do not expose secrets and reject invalid callbacks.
- [ ] Login resumes the intended destination after authentication.
- [ ] Logout clears the provider session and does not delete device-shared downloaded media.
- [ ] Session expiration returns to the correct provider login screen.

### Server-change navigation

- [ ] Change Server is reachable from the intended app navigation entry point.
- [ ] The configured-server list shows the correct display name, URL label, provider type, and active-server state.
- [ ] Selecting another configured server updates the active profile before loading its data.
- [ ] The app navigates to the correct login or Home destination for the selected server.
- [ ] The previous server's Home, Library, Series, Author, Search, Local Books, and Book Detail screens are not reused for the new server.
- [ ] Back navigation after a server change does not return to stale screens from the previous server.
- [ ] A server change while a book is open exits or safely closes the previous reader/player session.
- [ ] A server change while audio is playing stops or isolates playback according to the provider/session contract.
- [ ] A failed target-server login preserves the previously usable server/profile and its navigation state.
- [ ] Removing a non-active configured server returns to the correct server list without deleting unrelated local files.
- [ ] Reopening the app after a server change restores the intended active server, not a stale previous profile.

## 2. Home screen

Validate each visible Home section. Confirm the section label, loading state, empty state, cards, cover/image state, metadata, ordering, and navigation.

- [ ] Currently reading
- [ ] On deck
- [ ] Want to read
- [ ] Recently added books
- [ ] Recently added series
- [ ] Recently updated series
- [ ] Recently read books
- [ ] Local books
- [ ] Any additional provider-specific Home section: `[name]`

For every Home section above:

- [ ] The section loads data from the selected provider.
- [ ] The section handles empty, loading, error, offline, and stale-cache states.
- [ ] Cards show the correct title, author/series metadata, format, cover, and reading/download state.
- [ ] Cards open the correct book or Series destination.
- [ ] The section preview limit and ordering are correct.
- [ ] The More/See all button is visible when the section has a complete destination.
- [ ] More/See all opens the matching full section, not a different library or provider.
- [ ] More/See all preserves the originating context when Back is pressed.
- [ ] Paging or Load more works where present.
- [ ] Refresh updates the section without duplicating or mixing records.

Cross-library Home behavior:

- [ ] Home data aggregates books from all configured/visible libraries as intended by the provider.
- [ ] A book present in multiple libraries follows the documented deduplication rule.
- [ ] Series identity remains stable when member books come from different libraries.
- [ ] Home aggregation does not show records from another server profile or provider.
- [ ] Library changes do not incorrectly reduce an explicitly aggregate Home section.

## 3. Library screen

- [ ] Library screen opens for the selected provider.
- [ ] Library selection dropdown opens and lists all accessible libraries.
- [ ] Selecting a library reloads the correct catalog.
- [ ] Selected library state persists after navigation and app restart where supported.
- [ ] Switching libraries does not display books from the previous library.
- [ ] Books list loads the correct books, covers, metadata, pagination, and empty state.
- [ ] Book list refresh works.
- [ ] Load more/paging reaches the correct end of the catalog.
- [ ] Offline/cached library behavior is correct.
- [ ] Series grouping/collapse control is visible where supported.
- [ ] Expanding a Series shows the correct member books in order.
- [ ] Collapsing a Series hides its members without changing catalog results.
- [ ] Series collapse state survives the intended refresh/navigation lifecycle.
- [ ] Library bulk actions, if present, use only the selected library.

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

- [ ] Home section More/See all uses the correct applicable filter set.
- [ ] Library book list exposes the applicable book-list filters.
- [ ] Search exposes the applicable search/book-list filters.
- [ ] Series screen exposes the applicable Series filters.
- [ ] Smart Scope screen, if supported, exposes and applies the documented filters.
- [ ] Author screen, if supported, exposes and applies the documented filters.
- [ ] Local books screen exposes only filters that make sense for local content.
- [ ] Filters never leak between screens, libraries, providers, or server profiles.

## 5. Series screen

- [ ] Series screen opens from navigation.
- [ ] Series catalog aggregates member books from all intended libraries.
- [ ] The aggregate book count is correct.
- [ ] The library filter filters the aggregate result after aggregation, rather than replacing it with the active Library screen selection.
- [ ] Series identity, title, authors, formats, read count, and cover are correct.
- [ ] Series thumbnail/cover loads with the provider's required authentication.
- [ ] Series sorting and pagination are correct.
- [ ] Series detail opens the correct member-book list.
- [ ] Series detail orders books correctly by provider series number/order.
- [ ] Empty, unavailable, offline, and partial-metadata Series states are understandable.

## 6. Author screen

- [ ] Author catalog opens.
- [ ] Author search/query works.
- [ ] Author names, counts, and images/metadata are correct where supported.
- [ ] Opening an author shows the correct books.
- [ ] Author book paging/Load more works.
- [ ] Author filters/sorting work where exposed.
- [ ] Author navigation does not mix providers or libraries unexpectedly.
- [ ] Unsupported author functionality is absent or clearly unavailable.

## 7. Reading, listening, preview, and navigation

- [ ] Stream/open a supported EPUB online.
- [ ] Stream/open a supported PDF online.
- [ ] Stream/open a supported comic online.
- [ ] Stream/listen to a supported audiobook online.
- [ ] Authentication headers/session handling work for every supported media type.
- [ ] Preview opens without writing normal progress or incorrectly marking the book read.
- [ ] Closing Preview resets its temporary location and does not overwrite the normal reading/listening resume position.
- [ ] Go to read from Preview opens the normal reader at the expected location.
- [ ] Reader controls, Back, Close, orientation change, backgrounding, and process recreation work.
- [ ] Unsupported formats show an honest unavailable state.
- [ ] Reader/player errors identify the affected provider operation.
- [ ] Read/listen progress behavior matches the provider capability contract.
- [ ] Provider-specific unsupported progress/session behavior is not represented as a false success.

### Lagrange ↔ target-server book progress synchronization

- [ ] Opening a book with existing server progress restores the expected Lagrange reader/player position.
- [ ] Opening a book with existing Lagrange-local progress does not silently overwrite newer target-server progress.
- [ ] EPUB progress synchronization is verified, including chapter/resource and percentage behavior where supported.
- [ ] PDF progress synchronization is verified, including page/index behavior where supported.
- [ ] Comic progress synchronization is verified for each supported comic format.
- [ ] Audiobook progress synchronization is verified for single-file playback.
- [ ] Multipart audiobook progress maps to the correct book, file/asset, chapter, and playback position.
- [ ] Read and unread state synchronization is verified independently from exact position synchronization.
- [ ] In-progress, finished, and completion thresholds match the target server's contract.
- [ ] Progress updates are sent at the documented event/interval/close points and do not require a false completion.
- [ ] Progress updates are authenticated for the selected server and provider profile.
- [ ] Offline progress is retained locally and queued/reconciled when connectivity returns, where supported.
- [ ] Failed progress updates retry safely without duplicating or regressing the user's position.
- [ ] Conflicting local/server progress follows a documented resolution rule.
- [ ] Preview does not write ordinary book progress or change the saved resume position.
- [ ] Changing server/profile does not upload progress to the wrong server or expose it in another profile.
- [ ] Sign-out, server removal, and app restart preserve or clear local progress according to the documented retention contract.
- [ ] Unsupported progress synchronization is clearly marked unavailable and does not appear as a successful update.
- [ ] The target server's web UI/API reflects the validated Lagrange progress after synchronization.
- [ ] Lagrange reflects a progress change made directly on the target server after refresh/reopen.

## 8. Downloads and Local books

- [ ] Download a single supported book/file.
- [ ] Download a multipart or multi-file book where supported.
- [ ] Download progress is accurate and tied to the correct book/file.
- [ ] Queueing multiple downloads preserves order and identity.
- [ ] Background/screen-off download behavior works.
- [ ] Cancel works for active and queued downloads.
- [ ] Retry works after a failed download.
- [ ] Interrupted downloads recover according to the provider contract.
- [ ] Downloaded content is stored at the expected local path.
- [ ] Download metadata persists after process recreation.
- [ ] Download status appears on the originating book and remains correct after reopening.
- [ ] Local books screen lists completed local copies.
- [ ] Local books shows correct title, author, format, file, size, and status.
- [ ] Local books opens the selected local file offline.
- [ ] Delete local copy removes only the selected provider/profile file and metadata.
- [ ] Logout/server change does not unexpectedly delete device-shared downloaded media.
- [ ] Downloads from another server/profile do not appear as that server's remote catalog records.

## 9. Book detail

- [ ] Book detail opens from Home, Library, Search, Series, Author, Local books, and direct navigation.
- [ ] Title, subtitle, author, series, number, description, publisher, language, release date, and other provider metadata are correct.
- [ ] Book cover loads and uses the correct provider authentication.
- [ ] Metadata remains stable after refresh and offline fallback.
- [ ] Multi-file book/file selector opens when applicable.
- [ ] Every physical file has the correct filename, identity, format, size, and availability state.
- [ ] Selecting a file changes the intended reader/download target without losing the selection unexpectedly.
- [ ] Book size tag is shown when known and says unavailable when genuinely missing.
- [ ] File type/format tag is normalized and correct.
- [ ] Download status tag is correct for not downloaded, queued, downloading, completed, failed, and local-only states.
- [ ] The Mark as button opens the correct menu.
- [ ] Mark as: Read
- [ ] Mark as: Unread
- [ ] Mark as: In progress / currently reading, if exposed
- [ ] Mark as: Want to read, if exposed
- [ ] Mark as: On deck, if exposed
- [ ] Mark as: remove/clear status, if exposed
- [ ] Any additional Mark as action: `[name]`
- [ ] Mark-as changes persist or report provider unavailability accurately.
- [ ] Series button opens the correct Series.
- [ ] Book-in-Series navigation shows the correct previous/next members.
- [ ] Book-in-Series navigation respects the current provider/library/series context.
- [ ] Available formats and downloaded formats are correct.
- [ ] EPUB Image Library/image preview appears only when supported.
- [ ] EPUB image preview loads the correct images, ordering, thumbnails, zoom/pan, dismissal, and offline behavior.
- [ ] Non-EPUB files do not expose EPUB-only image-preview actions.

## 10. Cross-provider/profile isolation

- [ ] Configure one BookOrbit profile and one Komga/other-provider profile.
- [ ] Switch A → B → A and authenticate through the selected provider each time.
- [ ] The login label, endpoint behavior, auth method, and error messages match the selected provider.
- [ ] Catalog, details, covers, progress, reader state, filters, and selections do not leak across profiles.
- [ ] Provider-specific unsupported actions are not shown on the other provider.
- [ ] Download records retain their originating server/provider/profile identity.
- [ ] Removing a configured server removes only that profile entry and does not delete unrelated local media.
- [ ] Failed target validation preserves the previously usable server/profile.

## 11. Resilience and accessibility

- [ ] Loading, empty, error, offline, and stale-cache states are understandable on every tested screen.
- [ ] Retry actions retry the correct provider operation.
- [ ] Back navigation returns to the expected parent screen.
- [ ] Rotation/recreation does not duplicate requests, playback, downloads, or reader sessions.
- [ ] TalkBack/content descriptions identify provider selection, filters, More/See all, download, preview, reader, and destructive actions.
- [ ] Buttons and controls have usable touch targets and remain reachable at the tested display size.
- [ ] Long titles, missing covers, missing metadata, large sizes, and multiple formats do not break layout.
- [ ] No password, cookie, API key, or private URL appears in screenshots, logs, or shared validation artifacts.

## 12. Issue-derived module checks

These checks were derived from repository issues and should be applied when the target provider exposes the corresponding capability. If the provider does not support a capability, mark it as `Blocked / unavailable by provider contract` rather than silently omitting it. Source issues include #1, #3, #4, #5, #8, #12, #20, #21, #37, #38, #42, #78, #79, #88, #90, #102, #104, #105, #106, #116, #122, #125, #127, #143, #146, #159, #165, #170, #182, and #195.

### Home sections and catalog navigation

- [ ] Each Home section uses the documented item limit and does not grow without bound.
- [ ] Each Home section's More/See all action opens the correct complete destination.
- [ ] Recently Added Series is based on Series metadata, not only recently added books.
- [ ] The Home Local Books shelf includes downloaded books that are not currently being read.
- [ ] Home section ordering, empty states, and provider-specific sections are correct.
- [ ] Library, Series, and Authors controls remain fixed while their catalog content scrolls.
- [ ] Jump-rail navigation lands on the correct card after fixed headers are applied.

### Refresh and additional catalog modules

- [ ] Pull-to-refresh works on Book Detail, Series, Authors, Local Books, Statistics, and Achievements where those screens exist.
- [ ] Refresh preserves the intended filter, sort, and scroll state.
- [ ] Repeated refresh gestures do not create concurrent duplicate requests.
- [ ] Refresh error/offline behavior retains the last usable data and remains recoverable.
- [ ] Smart Scopes are available under the Series menu when supported.
- [ ] Smart Scope selection, filtering, empty states, and navigation work correctly.
- [ ] Shelves are available when supported by the provider.
- [ ] Shelf browsing and bulk download preserve individual book identity and status.
- [ ] Unsupported Smart Scope, Shelf, Statistics, or Achievement actions are absent or clearly unavailable.

### Book Detail and format selection

- [ ] Every available format remains selectable from a multi-format book.
- [ ] Mixed-format and alternate file groups remain selectable.
- [ ] A downloaded format is automatically selected when opening a multi-format book, where supported.
- [ ] The selected format remains stable when opening Preview, Read, Listen, or Download.
- [ ] Available formats are shown on browse cards where supported.
- [ ] Format selection never changes the server, library, provider profile, or book identity.

### Download and Local Books lifecycle

- [ ] Remote, queued, active, failed, completed, cancelled, and local-only states are visually distinct.
- [ ] Queued downloads are not displayed as active `Downloading`.
- [ ] Downloads continue correctly when the app loses focus or the screen is locked.
- [ ] Active downloads can be cancelled without affecting unrelated downloads.
- [ ] Failed downloads can be retried and cleared without deleting the local book.
- [ ] Local Books shows active and failed download sections only when applicable.
- [ ] Active download counts match the actual scheduler state.
- [ ] A downloaded local file opens in airplane mode immediately after completion.
- [ ] Local deletion removes only the selected file and its related metadata.

### Reading, listening, and progress

- [ ] Previous/Next navigation remains within the current library and selected format.
- [ ] Format fallback behavior is correct when the same-format target is unavailable.
- [ ] Multipart audiobook playback follows the provider manifest/asset ordering.
- [ ] Selected-file playback starts at the selected file or chapter.
- [ ] Range requests, authentication renewal, resume position, and progress identity remain correct.
- [ ] EPUB 3 read-along narration is distinct from ordinary EPUB reading.
- [ ] TTS and publisher-provided narration are mutually exclusive.
- [ ] Preview mode does not create reading/listening progress or alter the normal resume position.
- [ ] Reader/player behavior remains correct after rotation, screen lock, backgrounding, and explicit close.

### Offline and local reader behavior

- [ ] Downloaded CBR/CB7 files open offline when supported.
- [ ] Invalid, encrypted, empty, traversal-containing, and resource-excessive archives fail safely.
- [ ] The original downloaded archive remains intact after extraction.
- [ ] Extraction results are reused only while the source archive is unchanged.
- [ ] Offline reader fallback does not make an unnecessary provider request.
- [ ] Cached Series Previous/Next navigation works offline.

### EPUB image preview

- [ ] Image Library works for online EPUBs by creating or using the required durable local copy.
- [ ] Configurable minimum image-size behavior is applied correctly.
- [ ] Image ordering, thumbnails, preview navigation, zoom, dismissal, and offline reopening work.
- [ ] EPUB image-preview state does not leak into another book or provider profile.

### Status and account lifecycle

- [ ] The complete Mark as menu is available from every applicable book context-menu surface.
- [ ] Each Mark as action updates the originating screen and related screens immediately.
- [ ] Logout closes the audiobook player and isolates account/provider state.
- [ ] Session expiry recovers through the correct provider login flow.
- [ ] OIDC logout and expired-session behavior are distinct from username/password behavior where required.

### Reader settings and persistence

- [ ] Reader settings persist per library where applicable.
- [ ] Reader settings survive reader close/reopen, process recreation, and app update.
- [ ] Reading-direction changes navigation behavior only and do not change typography or text layout.
- [ ] Preview uses a temporary location, while normal reading/listening preserves the expected resume location.
- [ ] Reader controls remain usable across supported screen sizes and orientations.

## 13. Final provider result

- [ ] All applicable items passed.
- [ ] All unsupported items are marked `Blocked / unavailable by provider contract` and documented.
- [ ] All failures have reproduction steps and evidence.
- [ ] Maintainer reviewed the result.
- [ ] User/reporter reviewed the result where user validation is required.
- [ ] Follow-up issues were created for confirmed failures.
- [ ] Provider support status was updated only after manual validation.

### Notes and evidence

`[Record failures, provider-specific exceptions, screenshots, server responses, and follow-up issue links here.]`
