# Provider checklist template

Copy this file into this folder using the target server/provider name, for example `bookorbit.md` or `komga.md`. Keep one completed checklist per provider. The filename is the provider identity; the validation record inside the file identifies the exact server/build/device.

## Validation record

- Provider/server type: `Komga`
- Server base URL: `[non-secret URL or redacted label]`
- App build/version: `Lagrange-debug-202610042328.apk`
- APK path: `/projects/bookorbit-android-issue-199/app/build/outputs/apk/debug/Lagrange-debug-202610042328.apk`
- Maintainer: `Van Geaux`
- User/reporter: `Van Geaux`
- Device/emulator: `Samsung S26, Android 16`
- Date: `4 October 2026`
- Account/library fixture: `1 test library with only epub books, 1 test library with mixed epub and pdf books, 1 test library with cbr/cbz books`
- Result key: `[x] Pass  [!] Fail  [-] Blocked / unavailable by provider contract  [ ] Not tested`
- Evidence links/screenshots/logs: `User-confirmed on Samsung SM-S931B / Android 16 over USB (serial RRGL4029FRB). Komga book 0RSF6TWS1GRPH was reset to approximately 55% on the server and opened at 54% in Lagrange with the exact server EPUB locator. https://github.com/van-geaux/lagrange-reader/issues/199`

### Latest comic validation

- User-confirmed on 6 October 2026 with `Lagrange-debug-202610060853.apk` on Samsung SM-S931B / Android 16 over Tailscale ADB (`100.90.237.107:32927`). An online Komga comic opened in paginated mode, the visible page counter advanced from page 1 while navigating forward and backward, and the streamed book did not become a durable local download. The validation covers the online CBZ/CBR reader/progress path; no PDF sample was available and no separate CB7 validation is claimed.

### Latest Komga web OIDC validation

- User-confirmed on 6 October 2026 with `Lagrange-debug-202610061026.apk` on Samsung SM-S931B / Android 16 using Komga at `https://komga.alredho.com`. The Komga login screen opened in Lagrange, the server's OIDC button opened the configured identity-provider flow inside the WebView, and authentication returned to Lagrange successfully. This validates the server web-session path, including the WebView popup handling and Komga session-cookie reuse; it does not claim native AppAuth/Custom Tabs support.

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
- [x] Invalid username/password produces a provider-appropriate error without crashing or logging out another profile.
- [x] Login loading, retry, cancellation, and Back behavior are correct.
- [x] Credentials are not visible in the URL, profile list, logs, screenshots, or ordinary local profile data.
- [x] OIDC/SSO entry point is present when supported by this provider. (The Komga login screen exposes `Open server sign-in`.)
- [x] OIDC provider discovery loads the expected providers. (The provider is selected by Komga's server login page.)
- [x] OIDC provider selection, browser/WebView handoff, callback, cancellation, failure, and successful return work. (User-confirmed through the Komga WebView flow; native AppAuth/Custom Tabs are not included.)
- [ ] OIDC state/nonce/PKCE or provider-equivalent protections do not expose secrets and reject invalid callbacks. (Identity-provider protocol protections remain server/provider-owned and were not independently tested in Lagrange.)
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
- [x] A server change while a book is open exits or safely closes the previous reader/player session.
- [x] A server change while audio is playing stops or isolates playback according to the provider/session contract.
- [x] A failed target-server login preserves the previously usable server/profile and its navigation state.
- [x] Removing a non-active configured server returns to the correct server list without deleting unrelated local files.
- [x] Reopening the app after a server change restores the intended active server, not a stale previous profile.

## 2. Home screen

Validate each visible Home section. Confirm the section label, loading state, empty state, cards, cover/image state, metadata, ordering, and navigation.

- [x] Currently reading
- [x] On deck
- [x] Want to read (Mark as want to read is not available in Komga so Lagrange correctly doesn't show the section)
- [x] Recently added books
- [x] Recently released books
- [x] Recently added series
- [!] Recently updated series (Even if not available in Komga, it should be able to calculate)
- [x] Recently read books
- [x] Local books
- [ ] Any additional provider-specific Home section: `[name]`

For every Home section above:

- [x] The section loads data from the selected provider.
- [x] The section handles empty, loading, error, offline, and stale-cache states.
- [x] Cards show the correct title, author/series metadata, format, cover, and reading/download state.
- [x] Home and Libraries → Recommended cards show Komga media-level format labels, completed-download check marks, and the yellow read-progress bar when `media.files` is omitted but `mediaType`, `pagesCount`, and `readProgress` are present. User confirmed this behavior after the merged fix; device/build details were not supplied.
- [x] Cards open the correct book or Series destination.
- [x] The section preview limit and ordering are correct.
- [x] The More/See all button is visible when the section has a complete destination.
- [x] More/See all opens the matching full section, not a different library or provider.
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
- [x] Selecting a library reloads the correct catalog. The user confirmed Libraries → Recommended now scopes Currently reading, Recently added books, Recently added series, Recently read books, and Local books to the selected library after issue #201; device/build identifiers were not supplied.
- [x] Selected library state persists after navigation and app restart where supported.
- [x] Switching libraries does not display books from the previous library. The user confirmed switching Libraries → Recommended replaces the previous library's shelf content after issue #201; device/build identifiers were not supplied.
- [x] Books list loads the correct books, covers, metadata, pagination, and empty state.
- [x] Book list refresh works.
- [ ] Load more/paging reaches the correct end of the catalog.
- [x] Offline/cached library behavior is correct. (Validated by user on 2026-10-04 with Lagrange 1.5.4, version code 25, APK `Lagrange-debug-202610042205.apk`, Samsung SM-S931B `RRGL4029FRB` over USB, after online catalog population followed by force-stop, airplane mode, and Wi-Fi disabled. Library, See all, and Series retained cached content.)
- [x] Series grouping/collapse control is visible where supported.
- [x] Expanding a Series shows the correct member books in order.
- [x] Collapsing a Series hides its members without changing catalog results.
- [x] Series collapse state survives the intended refresh/navigation lifecycle.
- [x] Library bulk actions, if present, use only the selected library.

## 4. Filters and sorting

Test each filter independently, then test meaningful combinations. Confirm reset, persistence during navigation, empty results, paging, and server/provider correctness.

### Book-list filters: Library, Home More/See all, Search, and any book catalog screen

- [x] Title text filter
- [x] Author text filter
- [x] Series text filter
- [!] Genre text filter (Even tho there are books with genre and tag, the filter does not appear. Also book detail currently only show tag but not genre. Also doing anything here does not have any effect on the book list)
- [x] Read status: All
- [x] Read status: Unread
- [x] Read status: In progress
- [x] Read status: Finished
- [ ] Format: All formats
- [ ] Format: EPUB
- [ ] Format: PDF
- [ ] Format: Audio
- [ ] Format: Comic
- [x] Sort: Server default
- [x] Sort: Title
- [x] Sort: Author
- [x] Sort: Series
- [x] Sort: Date added
- [ ] Sort: Date updated
- [!] Sort: Read progress (I don't understand what to test here but changing this filter to asc or desc does not change anything even tho there are read books)
- [!] Sort: Last read (I don't understand what to test here but changing this filter to asc or desc does not change anything even tho there are read books)
- [ ] Sort: Format
- [x] Direction: Ascending
- [x] Direction: Descending
- [x] Reset clears every active book-list filter.

### Series filters: Series screen and Series More/See all

- [!] Series name/query (Does not appear)
- [x] Author
- [!] Genre (Even tho there are books with genre and tag, the filter does not appear. Also book detail currently only show tag but not genre. Also doing anything here does not have any effect on the book list)
- [x] Library
- [x] Completion: All
- [!] Completion: Not started (It also list series that have read books and series with currently read books including currently read first book)
- [!] Completion: In progress (It also list series that have read books and series with currently read books including currently read first book)
- [!] Completion: Complete (It shows everything else not just series with all books marked as read)
- [x] Sort: Name
- [x] Sort: Book count
- [ ] Sort: Last added
- [!] Sort: Read progress (It simply does not sort as intended)
- [x] Direction: Ascending
- [x] Direction: Descending
- [x] Reset clears the intended Series filters while retaining no stale filter.

### Screen coverage matrix

- [x] Home section More/See all uses the correct applicable filter set.
- [x] Library book list exposes the applicable book-list filters. (User-confirmed exposed filters behave correctly; provider-unsupported filters remain unavailable; see #206.)
- [x] Search exposes the applicable search/book-list filters.
- [x] Series screen exposes the applicable Series filters. (User-confirmed exposed filters behave correctly; provider-unsupported filters remain unavailable; see #206.)
- [-] Smart Scope screen, if supported, exposes and applies the documented filters. (Not supported by Komga)
- [-] Author screen, if supported, exposes and applies the documented filters. (Author functionality is unavailable by the Komga provider contract, and no ineffective controls are exposed; see #206.)
- [x] Local books screen exposes only filters that make sense for local content. (User-confirmed exposed Local Books filters are appropriate; provider-unsupported filters remain unavailable; see #206.)
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

- [!] Author catalog opens. (Author screen does not appear, altho there is no such menu in Komga, the data could be calculated)
- [ ] Author search/query works.
- [ ] Author names, counts, and images/metadata are correct where supported.
- [ ] Opening an author shows the correct books.
- [ ] Author book paging/Load more works.
- [ ] Author filters/sorting work where exposed.
- [ ] Author navigation does not mix providers or libraries unexpectedly.
- [ ] Unsupported author functionality is absent or clearly unavailable.

## 7. Reading, listening, preview, and navigation

- [x] Stream/open a supported EPUB online.
- [ ] Stream/open a supported PDF online.
- [x] Stream/open a supported comic online. (User confirmed the online Komga comic opens and the page counter advances in the issue-223 APK; the validated scope is the streamed CBZ/CBR path.)
- [-] Stream/listen to a supported audiobook online. (Komga does not support audiobook)
- [x] Authentication headers/session handling work for every supported media type.
- [x] Preview opens without writing normal progress or incorrectly marking the book read.
- [x] Closing Preview resets its temporary location and does not overwrite the normal reading/listening resume position.
- [!] Go to read from Preview opens the normal reader at the expected location. (Moving from read mode to preview mode does open the reader at the correct location. But when the read screen is closed it does not update the progress to the book, as effect the book also does not appear in continue reading section)
- [x] Reader controls, Back, Close, orientation change, backgrounding, and process recreation work.
- [ ] Unsupported formats show an honest unavailable state.
- [ ] Reader/player errors identify the affected provider operation.
- [x] Read/listen progress behavior matches the provider capability contract.
- [ ] Provider-specific unsupported progress/session behavior is not represented as a false success.

### Lagrange ↔ target-server book progress synchronization

- [x] Opening a book with existing server progress restores the expected Lagrange reader/player position. (User confirmed Komga book 0RSF6TWS1GRPH at approximately 55% opened at 54% in Lagrange rather than the stale 96% position.)
- [ ] Opening a book with existing Lagrange-local progress does not silently overwrite newer target-server progress.
- [x] EPUB progress synchronization is verified, including chapter/resource and percentage behavior where supported. (The Komga Readium progression locator restored Chapter 32/63 and Book 151/279, approximately 54%.)
- [ ] PDF progress synchronization is verified, including page/index behavior where supported.
- [x] Comic page progress is verified for the streamed CBZ/CBR path. (The user confirmed forward/backward page movement and the visible page counter on the issue-223 APK; no separate CB7 validation is claimed.)
- [-] Audiobook progress synchronization is verified for single-file playback. (Komga does not support audiobook)
- [-] Multipart audiobook progress maps to the correct book, file/asset, chapter, and playback position. (Komga does not support audiobook)
- [x] Read and unread state synchronization is verified independently from exact position synchronization.
- [x] In-progress, finished, and completion thresholds match the target server's contract.
- [x] Progress updates are sent at the documented event/interval/close points and do not require a false completion.
- [x] Progress updates are authenticated for the selected server and provider profile.
- [!] Offline progress is retained locally and queued/reconciled when connectivity returns, where supported. (Even tho the book is downloaded locally, on airplane mode user can't open it and presented with server not available. I also noticed that sometimes on online mode when opening a downloaded book it took about the same amount of time compared to streaming a book. It seems that komga provider in Lagrange does not prioritize local file, this is high priority assessment target)
- [ ] Failed progress updates retry safely without duplicating or regressing the user's position.
- [x] Conflicting local/server progress follows a documented resolution rule. (When online, the Komga server Readium locator is authoritative; stale local EPUB position data is not applied over it.)
- [x] Preview does not write ordinary book progress or change the saved resume position.
- [x] Changing server/profile does not upload progress to the wrong server or expose it in another profile.
- [ ] Sign-out, server removal, and app restart preserve or clear local progress according to the documented retention contract.
- [ ] Unsupported progress synchronization is clearly marked unavailable and does not appear as a successful update.
- [x] The target server's web UI/API reflects the validated Lagrange progress after synchronization.
- [x] Lagrange reflects a progress change made directly on the target server after refresh/reopen. (After the server position was reset to approximately 55%, reopening the book reflected approximately 54% in Lagrange.)

## 8. Downloads and Local books

- [x] Download a single supported book/file.
- [-] Download a multipart or multi-file book where supported. (Komga does not support folder based book)
- [x] Download progress is accurate and tied to the correct book/file.
- [x] Queueing multiple downloads preserves order and identity.
- [x] Background/screen-off download behavior works. (User confirmed the issue-203 build continues Komga downloads when the app is out of focus, keeps the download visible in the notification with progress, and avoids the prior in-app network-failure result. Device/build identifiers beyond the supplied APK are not available.)
- [x] Cancel works for active and queued downloads.
- [ ] Retry works after a failed download.
- [x] Interrupted downloads recover according to the provider contract.
- [ ] Downloaded content is stored at the expected local path.
- [ ] Download metadata persists after process recreation.
- [!] Download status appears on the originating book and remains correct after reopening. (Download status does shows up in local books screen but does not show up in home nor libraries screen's recommended tab)
- [x] Local books screen lists completed local copies.
- [x] Local books shows correct title, author, format, file, size, and status.
- [x] Local books opens the selected local file offline. (Validated on 2026-10-04 with Lagrange 1.5.4, version code 25, APK `Lagrange-debug-202610042127.apk`, Samsung SM-S931B over USB `RRGL4029FRB`, with airplane mode enabled and Wi-Fi disabled. The cached Komga Home screen opened without Login and exposed downloaded books.)
- [x] Delete local copy removes only the selected provider/profile file and metadata.
- [x] Logout/server change does not unexpectedly delete device-shared downloaded media.
- [x] Downloads from another server/profile do not appear as that server's remote catalog records.

## 9. Book detail

- [x] Book detail opens from Home, Library, Search, Series, Author, Local books, and direct navigation.
- [x] Title, subtitle, author, series, number, description, publisher, language, release date, and other provider metadata are correct.
- [x] Book cover loads and uses the correct provider authentication.
- [x] Metadata remains stable after refresh and offline fallback. (Validated by user on 2026-10-04 with Lagrange 1.5.4, version code 25, APK `Lagrange-debug-202610042205.apk`, Samsung SM-S931B `RRGL4029FRB` over USB, after online catalog population followed by force-stop, airplane mode, and Wi-Fi disabled. Cached Library, See all, and Series content remained available instead of returning Login or zero books.)
- [-] Multi-file book/file selector opens when applicable. (Not supported by Komga)
- [x] Every physical file has the correct filename, identity, format, size, and availability state.
- [-] Selecting a file changes the intended reader/download target without losing the selection unexpectedly. (Not supported by Komga)
- [x] Book size tag is shown when known and says unavailable when genuinely missing.
- [x] File type/format tag is normalized and correct.
- [x] Download status tag is correct for not downloaded, queued, downloading, completed, failed, and local-only states.
- [x] The Mark as button opens the correct menu.
- [x] Mark as: Read
- [x] Mark as: Unread
- [-] Mark as: In progress / currently reading, if exposed (Not supported by Komga)
- [-] Mark as: Want to read, if exposed (Not supported by Komga)
- [-] Mark as: On deck, if exposed (Not supported by Komga)
- [-] Mark as: remove/clear status, if exposed (Not supported by Komga)
- [ ] Any additional Mark as action: `[name]`
- [x] Mark-as changes persist or report provider unavailability accurately.
- [x] Series button opens the correct Series.
- [x] Book-in-Series navigation shows the correct previous/next members.
- [x] Book-in-Series navigation respects the current provider/library/series context.
- [x] Available formats and downloaded formats are correct.
- [x] EPUB Image Library/image preview appears only when supported.
- [x] EPUB image preview loads the correct images, ordering, thumbnails, zoom/pan, dismissal, and offline behavior.
- [ ] Non-EPUB files do not expose EPUB-only image-preview actions.

## 10. Cross-provider/profile isolation

- [x] Configure one BookOrbit profile and one Komga/other-provider profile.
- [x] Switch A → B → A and authenticate through the selected provider each time.
- [x] The login label, endpoint behavior, auth method, and error messages match the selected provider.
- [x] Catalog, details, covers, progress, reader state, filters, and selections do not leak across profiles.
- [x] Provider-specific unsupported actions are not shown on the other provider.
- [x] Download records retain their originating server/provider/profile identity.
- [!] Removing a configured server removes only that profile entry and does not delete unrelated local media. (Should it not delete the local media of that configured server also?)
- [x] Failed target validation preserves the previously usable server/profile.

## 11. Resilience and accessibility

- [x] Loading, empty, error, offline, and stale-cache states are understandable on every tested screen.
- [x] Retry actions retry the correct provider operation.
- [x] Back navigation returns to the expected parent screen.
- [ ] Rotation/recreation does not duplicate requests, playback, downloads, or reader sessions.
- [x] TalkBack/content descriptions identify provider selection, filters, More/See all, download, preview, reader, and destructive actions.
- [x] Buttons and controls have usable touch targets and remain reachable at the tested display size.
- [x] Long titles, missing covers, missing metadata, large sizes, and multiple formats do not break layout.
- [x] No password, cookie, API key, or private URL appears in screenshots, logs, or shared validation artifacts.

## 12. Issue-derived module checks

These checks were derived from repository issues and should be applied when the target provider exposes the corresponding capability. If the provider does not support a capability, mark it as `Blocked / unavailable by provider contract` rather than silently omitting it. Source issues include #1, #3, #4, #5, #8, #12, #20, #21, #37, #38, #42, #78, #79, #88, #90, #102, #104, #105, #106, #116, #122, #125, #127, #143, #146, #159, #165, #170, #182, and #195.

### Home sections and catalog navigation

- [ ] Each Home section uses the documented item limit and does not grow without bound.
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
- [-] Smart Scopes are available under the Series menu when supported. (Not supported by Komga)
- [-] Smart Scope selection, filtering, empty states, and navigation work correctly. (Not supported by Komga)
- [-] Shelves are available when supported by the provider. (Not supported by Komga)
- [-] Shelf browsing and bulk download preserve individual book identity and status. (Not supported by Komga)
- [x] Unsupported Smart Scope, Shelf, Statistics, or Achievement actions are absent or clearly unavailable.

### Book Detail and format selection

- [-] Every available format remains selectable from a multi-format book. (Not supported by Komga)
- [-] Mixed-format and alternate file groups remain selectable. (Not supported by Komga)
- [-] A downloaded format is automatically selected when opening a multi-format book, where supported. (Not supported by Komga)
- [x] The selected format remains stable when opening Preview, Read, Listen, or Download.
- [-] Available formats are shown on browse cards where supported. (Not supported by Komga)
- [-] Format selection never changes the server, library, provider profile, or book identity. (Not supported by Komga)

### Download and Local Books lifecycle

- [x] Remote, queued, active, failed, completed, cancelled, and local-only states are visually distinct.
- [x] Queued downloads are not displayed as active `Downloading`.
- [x] Downloads continue correctly when the app loses focus or the screen is locked. (User-confirmed for the current issue-203 build, including visible notification progress and successful completion.)
- [x] Active downloads can be cancelled without affecting unrelated downloads.
- [ ] Failed downloads can be retried and cleared without deleting the local book.
- [x] Local Books shows active and failed download sections only when applicable.
- [x] Active download counts match the actual scheduler state.
- [x] A downloaded local file opens in airplane mode immediately after completion. (Validated on 2026-10-04 with Lagrange 1.5.4, version code 25, APK `Lagrange-debug-202610042127.apk`, Samsung SM-S931B over USB `RRGL4029FRB`, with airplane mode enabled and Wi-Fi disabled. The cached Home and Local books surfaces were available offline.)
- [x] Local deletion removes only the selected file and its related metadata.

### Reading, listening, and progress

- [x] Previous/Next navigation remains within the current library and selected format.
- [-] Format fallback behavior is correct when the same-format target is unavailable. (Not supported by Komga)
- [-] Multipart audiobook playback follows the provider manifest/asset ordering. (Not supported by Komga)
- [x] Selected-file playback starts at the selected file or chapter.
- [x] Range requests, authentication renewal, resume position, and progress identity remain correct.
- [ ] EPUB 3 read-along narration is distinct from ordinary EPUB reading.
- [ ] TTS and publisher-provided narration are mutually exclusive.
- [x] Preview mode does not create reading/listening progress or alter the normal resume position.
- [x] Reader/player behavior remains correct after rotation, screen lock, backgrounding, and explicit close.

### Offline and local reader behavior

- [ ] Downloaded CBR/CB7 files open offline when supported.
- [ ] Invalid, encrypted, empty, traversal-containing, and resource-excessive archives fail safely.
- [ ] The original downloaded archive remains intact after extraction.
- [ ] Extraction results are reused only while the source archive is unchanged.
- [!] Offline reader fallback does not make an unnecessary provider request. (Everything seems to make provider request)
- [ ] Cached Series Previous/Next navigation works offline.

### EPUB image preview

- [x] Image Library works for online EPUBs by creating or using the required durable local copy.
- [ ] Configurable minimum image-size behavior is applied correctly.
- [x] Image ordering, thumbnails, preview navigation, zoom, dismissal, and offline reopening work.
- [x] EPUB image-preview state does not leak into another book or provider profile.

### Status and account lifecycle

- [x] The complete Mark as menu is available from every applicable book context-menu surface.
- [x] Each Mark as action updates the originating screen and related screens immediately.
- [-] Logout closes the audiobook player and isolates account/provider state. (Not supported by Komga)
- [x] Session expiry recovers through the correct provider login flow.
- [ ] OIDC logout and expired-session behavior are distinct from username/password behavior where required.

### Reader settings and persistence

- [ ] Reader settings persist per library where applicable.
- [ ] Reader settings survive reader close/reopen, process recreation, and app update.
- [ ] Reading-direction changes navigation behavior only and do not change typography or text layout.
- [ ] Preview and normal reading preserve the exact intended location.
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
