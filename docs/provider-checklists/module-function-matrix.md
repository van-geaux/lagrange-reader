# Provider module and function matrix

Use this matrix to compare target servers before implementing or manually validating a provider. Add one column for each target server. Do not mark a function as supported because the UI can display a placeholder or because a similar endpoint exists. Each supported cell needs provider evidence and a manual validation result.

This matrix is separate from the per-provider manual checklist. The matrix answers “does this target server provide this module/function?” The checklist answers “does the implementation work correctly for this target server?”

## Latest manual validation

On 2026-10-04, the user confirmed the shared filter-surface behavior for BookOrbit and Komga on a Samsung SM-S931B running Android 16 with `Lagrange-debug-202610041340.apk`. Library, Series, Author, and Local Books filter-surface behavior was validated under issue [#206](https://github.com/van-geaux/lagrange-reader/issues/206). Provider capabilities that remain unsupported or partial are still represented as `U` or `P`; this confirmation records correct handling of exposed controls and unavailable provider-specific controls, not new provider capabilities.

On 2026-10-06, the user confirmed Komga server web OIDC sign-in on Samsung SM-S931B / Android 16 with `Lagrange-debug-202610061026.apk` against `https://komga.alredho.com` under issue [#225](https://github.com/van-geaux/lagrange-reader/issues/225). The confirmation covers the Komga login WebView, identity-provider handoff, callback, and authenticated server-session return; it does not cover native AppAuth/Custom Tabs or independent validation of server-side protocol protections.

## Status legend

- `S` Supported and implemented for the target provider.
- `P` Partially supported, or supported only for some formats/workflows.
- `U` Explicitly unsupported by the target provider or current adapter.
- `N` Not implemented in Lagrange yet, although the server may support it.
- `V` Not yet verified against the target server.
- `X` Not applicable, because the function is provider-neutral or local-only.

Do not use `S` without recording the endpoint/source evidence and manual validation reference. Use `U` when the provider contract does not offer the capability. Use `N` when the provider may offer it but the Lagrange module is not implemented. Use `V` when evidence or maintainer/user validation is still missing.

## Provider-exclusive functions and UI gating

A provider-exclusive function is a real capability supplied by one provider but not by another. Its UI entry point must be composed from the selected provider's module set. It must not appear merely because the app supports that function for a different provider.

| Provider-exclusive function | Provider currently supplying it | UI surfaces that must be gated | Other-provider behavior |
|---|---|---|---|
| Smart Scopes | BookOrbit | Series menu, Smart Scope picker, Series results | Do not render the Smart Scopes entry or picker for Komga. |
| Shelves and shelf bulk download | BookOrbit, where enabled | Shelves destination, shelf actions, bulk download | Do not render Shelf actions when the provider has no Shelf module. |
| Server reading sessions/attempts | BookOrbit, where enabled | Session/history surfaces and lifecycle actions | Hide session actions or show explicit unavailable state. |
| Annotations | BookOrbit, where enabled | Reader annotation actions and annotation lists | Do not render annotation actions for providers without an Annotation module. |
| Achievements | BookOrbit | Achievements destination and progress cards | Hide the destination for unsupported providers. |
| Statistics | BookOrbit, where enabled | Statistics destination and refresh actions | Hide the destination for unsupported providers. |
| Provider read-status mutations | BookOrbit, where enabled | Mark as menu, read-status filters, status cards | Do not show successful status actions for providers without a status module. |
| Provider progress synchronization | BookOrbit, where enabled | Reader/player resume, sync indicators, progress filters | Do not display sync controls or claim synchronization for unsupported providers. |

For every provider-exclusive row, manually verify all of the following:

- [ ] The function appears when the matching provider profile is active.
- [ ] The function does not appear when another provider profile is active.
- [ ] Switching providers refreshes the navigation/menu/action surface immediately.
- [ ] Deep links, Back navigation, cached screens, and restored app state cannot reopen the function under the wrong provider.
- [ ] Unsupported provider behavior is absent or explicitly unavailable, never a successful empty/no-op action.
- [ ] Provider-specific data, cache, session, progress, and download identity do not leak into the other provider.

## Current target-server matrix

| Module | Function | Manual/UI surface | BookOrbit | Komga | Evidence or note |
|---|---|---|---:|---:|---|
| Server selection | Server type selection | Server URL input | S | S | Explicit provider profile selection exists. |
| Server selection | Server profile and switching | Change Server | S | S | Provider ID is stored with the profile. |
| Server detection | Reachability and validation | URL validation/login entry | S | S | Provider-specific resolver selection. |
| Authentication | Username/password login | Login screen | S | S | BookOrbit session flow; Komga Basic Auth. |
| Authentication | OIDC/SSO login | Login screen/browser callback | S | S | Komga server web login is opened in the in-app WebView; user-confirmed under issue #225. Native AppAuth/Custom Tabs remain out of scope. |
| Authentication | Logout/session clearing | Account/server actions | S | S | Provider session clearing is implemented. |
| Authentication | Session expiry recovery | Login/retry | S | V | Komga live expiry behavior still requires validation. |
| Libraries | Library discovery | Library dropdown | S | S | Komga `/api/v1/libraries`. |
| Libraries | Selected-library persistence | Library screen/profile | S | V | Verify per-provider persistence manually. |
| Book catalog | Paginated library books | Library book list | S | S | Komga paginated `/api/v1/books`. |
| Book catalog | Search | Search screen | S | N | Komga search is not implemented in the current adapter. |
| Book catalog | Book filters and sorting | Library/Search/More screens | S | P | Verify title, author, series, genre, read state, format, and sort support. |
| Home shelves | Currently reading | Home | S | N | Komga Home shelf modules are not implemented. |
| Home shelves | On deck | Home | S | N | Komga Home shelf modules are not implemented. |
| Home shelves | Want to read | Home | S | N | Komga Home shelf modules are not implemented. |
| Home shelves | Recently added books | Home | S | N | Komga Home shelf modules are not implemented. |
| Home shelves | Recently added Series | Home | S | P | Komga Series exists, but Home-specific recency is not implemented. |
| Home shelves | Recently updated Series | Home | S | P | Verify whether a provider derivation is added. |
| Home shelves | Recently read | Home | S | U | Komga progress/status is unsupported in the current adapter. |
| Home shelves | Local Books shelf | Home | S | P | Local books are shared device state, but provider attribution must remain isolated. |
| Home shelves | More/See all destination | Home section action | S | P | Validate only for sections exposed by the provider. |
| Aggregation | Aggregate books across libraries | Home/Series/catalog | S | S | Komga Series aggregates all libraries. |
| Aggregation | Post-aggregation library filter | Series screen | S | S | Komga applies library filtering after aggregate loading. |
| Series | Series catalog | Series screen | S | S | Komga groups by series identity. |
| Series | Series detail/member ordering | Series detail | S | S | Komga uses series number/order where available. |
| Series | Completion/read-progress filters | Series filter | S | U | Requires Komga progress/status module. |
| Series | Smart Scopes | Series menu | S | U | Explicitly unsupported by Komga adapter. |
| Authors | Author catalog | Authors screen | S | U | Explicitly unsupported by Komga adapter. |
| Authors | Author books/navigation | Author detail | S | U | Explicitly unsupported by Komga adapter. |
| Book detail | Metadata hydration | Book Detail | S | S | Komga `/api/v1/books/{bookId}`. |
| Book detail | Multi-file/multi-format files | File selector | S | S | EPUB, PDF, comic, and audio mapping exists. |
| Book detail | Available format tags | Book cards/detail | S | S | Verify provider metadata and normalization. |
| Book detail | Size tag | Book Detail | S | S | Root/file size fallbacks implemented for Komga. |
| Book detail | File type tag | Book cards/detail | S | S | MIME/extension normalization implemented. |
| Book detail | Mark as/read-status actions | Context menu/detail | S | U | Komga reading-status synchronization is unsupported. |
| Book detail | Series button/navigation | Book Detail | S | S | Verify profile/library/format scope. |
| Covers | Book covers | Cards/detail | S | S | Komga authenticated thumbnail requests. |
| Covers | Catalog/Series images | Home/catalog/Series | S | S | Komga authenticated catalog image loading. |
| Reader | EPUB reading | Read/Preview | S | S | Verify authenticated file preparation and local reopen. |
| Reader | PDF reading | Read/Preview | S | S | Verify provider file mapping. |
| Reader | Comic reading | Read/Preview | S | S | Verify supported comic formats. |
| Reader | Audiobook streaming/listening | Listen/player | S | P | BookOrbit runtime playback rebinding after provider/server switch was user-confirmed under issue [#216](https://github.com/van-geaux/lagrange-reader/issues/216); Komga audio file access exists, while progress/session behavior is unsupported. |
| Reader | EPUB image library | Book Detail/reader | S | P | Verify durable local preparation and authenticated source access. |
| Reader | Preview isolation | Preview/Go to read | S | P | Verify no progress mutation for Komga. |
| Reader | Previous/Next navigation | Reader | S | P | Verify library and format scoping for each provider. |
| Reader lifecycle | Active-reader save/clear persistence | Reader open/close/server change | X | X | Shared `ReaderLifecycleModule`; validate once plus cross-provider isolation. |
| Reader feature | EPUB 3 read-along narration | EPUB reader/player | S | V | Reader feature is independent; provider file/overlay support needs validation. |
| Reader feature | EPUB text-to-speech | EPUB reader/player | X | X | Local feature module, not a server-provider capability. |
| Progress | Read position hydration | Reader open | S | U | Komga progress synchronization is unsupported. |
| Progress | Read position upload | Reader lifecycle | S | U | Komga adapter intentionally bypasses progress sync. |
| Progress | Audiobook position sync | Player lifecycle | S | U | Requires provider progress contract. |
| Status | Read/unread mutations | Mark as/context menu | S | U | Komga status module is not implemented. |
| Status | In-progress/completed state | Cards/filters | S | U | Komga status module is not implemented. |
| Sessions | Reading sessions/attempts | History/detail | S | U | Komga server-session synchronization unsupported. |
| Downloads | Authenticated file download | Download action | S | S | Komga provider-specific download module. |
| Downloads | Multipart audiobook download | Download/file selector | S | P | Verify all asset/file ordering and identity. |
| Downloads | Queue/cancel/retry/status | Downloads section | S | S | User confirmed Komga background/screen-off download completion and visible notification progress for issue #203. |
| Downloads | Downloaded status tag | Cards/detail/local | S | S | Shared DownloadStore metadata is used. |
| Local books | Local book discovery | Local Books | S | S | Komga downloads use shared local metadata. |
| Local books | Offline reopen | Local Books/reader | S | P | Verify all supported formats without network. |
| Local books | Local deletion | Context menu/detail | S | S | Deletes file and provider-scoped record. |
| Annotations | Annotation load/write/delete | Reader annotations | S | U | Explicitly unsupported by Komga adapter. |
| Achievements | Achievement loading | Achievements | S | U | Explicitly unsupported by Komga adapter. |
| Statistics | Statistics loading | Statistics | S | U | Explicitly unsupported by Komga adapter. |
| Cache | Catalog/detail cache | Offline/cache screens | S | P | Verify provider/profile cache keys and invalidation. |
| Cache | Background refresh | Cache settings/worker | S | U | Provider-specific cache refresh is not implemented for Komga. |
| Isolation | Provider/profile cache scope | Server change | S | P | Must be manually validated for every target server. |
| Isolation | Download identity scope | Local Books/server change | S | P | Local files may be shared, identity must remain retained. |
| Isolation | Reader/player session scope | Server change/logout | S | P | Verify active reader/player cleanup. |

## Blank target-server column template

When adding a target server, copy this column and replace `[TARGET]` with its provider name. Do not reuse the BookOrbit or Komga status without verifying the new provider's contract.

| Module/function | Provider-exclusive? (`Y/N`) | [TARGET] status (`S/P/U/N/V/X`) | Endpoint/source evidence | Manual validation result | UI gating/notes/follow-up issue |
|---|---:|---:|---|---|---|
| Server selection and detection |  |  |  |  |
| Username/password login |  |  |  |  |
| OIDC/SSO login |  |  |  |  |
| Library discovery and selection |  |  |  |  |
| Book catalog, search, filters, and sorting |  |  |  |  |
| Home shelves and More/See all |  |  |  |  |
| Multi-library aggregation |  |  |  |  |
| Series catalog/detail and filters |  |  |  |  |
| Authors |  |  |  |  |
| Book detail/files/metadata/tags |  |  |  |  |
| Covers and catalog images |  |  |  |  |
| EPUB/PDF/comic/audio reading or listening |  |  |  |  |
| Preview and Go to read |  |  |  |  |
| Progress/status/session synchronization |  |  |  |  |
| Downloads and local books |  |  |  |  |
| Annotations, achievements, statistics |  |  |  |  |
| Cache/offline behavior |  |  |  |  |
| Server-change/profile isolation |  |  |  |  |

## Matrix maintenance rules

1. Add a row when a new provider-facing function or independent feature module is introduced.
2. Keep local-only modules, such as EPUB TTS, in the matrix but mark them `X` for provider columns.
3. Split a row when support differs by format, authentication method, screen, or lifecycle phase.
4. Record exact endpoint/source evidence separately from the manual validation result.
5. A provider can be marked `S` only after the provider module exists and the maintainer/user has completed the applicable manual checklist.
6. If a provider lacks a capability, keep the row and mark it `U`; do not delete it from the comparison.
7. Update the matrix when an issue adds a new user-visible function or exposes a provider-specific regression.
8. Keep unsupported UI actions hidden or clearly unavailable; a matrix status must match the runtime UI behavior.
