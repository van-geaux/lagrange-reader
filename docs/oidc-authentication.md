# OIDC / SSO Authentication

This document is the source of truth for direct OIDC/SSO authentication in Lagrange. It records the verified BookOrbit server contract, the current app-compatibility gap, and the phased plan to close it.

## Conclusion

BookOrbit's server OIDC endpoints are real and return normal Lagrange session credentials, but the stock server only allows `APP_URL/oauth2-callback` as a redirect target, so a robust native custom-scheme callback (the AppAuth + Custom Tabs pattern this document recommends as the final design) is currently blocked upstream. Phase 1 is now implemented as a low-cost, clearly-labeled in-app WebView interim path; Phase 2 can swap in native AppAuth once server-side mobile-redirect support ships.

## Verified server contract

Inspected against BookOrbit server `main` at commit `23701a7b40b7d5705511fcae507945361c6ed3c1` on 2026-07-28.

- `GET /api/v1/app-settings/oidc/providers/public` — public provider discovery.
- `POST /api/v1/auth/oidc/:slug/state` — issues per-provider authorization state.
- `POST /api/v1/auth/oidc/callback` — accepts `code`, `codeVerifier`, `redirectUri`, `nonce`, `state`. On success it returns a normal BookOrbit access token and user, and sets the same access/refresh cookies as password login.

Because the callback response is a normal BookOrbit session, no separate OIDC-specific session handling is required on the client once a callback is completed — the existing Bearer/cookie refresh infrastructure applies unchanged.

### Native redirect blocker

The deployed stock server currently only accepts `APP_URL/oauth2-callback` as a redirect URI, which resolves to a web origin, not a native app callback. Upstream server issue [#490](https://github.com/bookorbit/bookorbit/issues/490) and open PR [#554](https://github.com/bookorbit/bookorbit/pull/554) add an `OIDC_MOBILE_REDIRECT_URIS` allow-list to support native redirect URIs. PR #554 is open, **not merged**, as of this writing.

A separate reference Android client, [deranjer/bookorbit-android](https://github.com/deranjer/bookorbit-android), demonstrates the AppAuth + Custom Tabs pattern, but it currently assumes the unmerged server-side redirect support and is not itself a deployable solution against a stock server.

The configured OIDC provider record on a given BookOrbit deployment can be reused as-is; native support additionally requires the exact native redirect URI to be allow-listed by the deployed server and registered on the identity provider's client configuration.

## Current app compatibility

The Lagrange login screen retains native username/password credentials as its primary flow and now provides the implemented **Open server sign-in** WebView path. Native provider discovery and custom-scheme callback handling remain unimplemented until the BookOrbit mobile-redirect contract is deployed.

## Phased plan

### Phase 1 — interim WebView sign-in (no server change required)

The implementation provides a transient in-app WebView screen labeled **"Open server sign-in"** that loads `{server}/login`. It is launched from Login rather than added as a permanent app-navigation tab. This renders BookOrbit's own web login, including any configured OIDC providers and local username/password, using the existing shared `android.webkit.CookieManager`-backed OkHttp cookie jar. While the WebView is open, the coordinator continuously watches `GET /api/v1/auth/me`; this accommodates BookOrbit's asynchronous JavaScript callback exchange and client-side route replacement. As soon as the server session becomes authenticated, the app closes the WebView by resuming the existing pending destination — the same recovery path used after native login.

Requirements and constraints:

- No BookOrbit server change and no new browser/OIDC dependency: `android.webkit.WebView` / `androidx.webkit` are already available.
- JavaScript and DOM storage may be enabled for BookOrbit's web app, but no native JavaScript bridge is added.
- TLS errors are not bypassed.
- The WebView is detached and destroyed after completion; the BookOrbit authentication cookies it established remain in the shared cookie jar for API refresh and media requests.
- Label it **"Open server sign-in"**, not "OIDC," since the page also exposes local password login — the label must represent both auth methods the server page offers.

Trade-offs:

- Low implementation cost because the WebView and shared cookie bridge already exist. A WebView renderer is heavier than a Compose login form, but it exists only while sign-in is visible, performs no background work after destruction, and adds no OIDC/browser library to the APK; this is a modest transient resource cost rather than a persistent app cost.
- This is an interim compatibility path, not native OIDC. Embedded WebView OAuth may be blocked by some identity providers, lacks system-browser SSO and passkey integration, and is explicitly discouraged by IdPs and by [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252) in favor of external user agents.

### Phase 2 — native AppAuth (after #554 or equivalent is deployed)

Once server-side mobile redirect support is deployed, replace the Phase 1 WebView launcher behind the same login entry point with AppAuth + Custom Tabs. Phase 2 adds native provider discovery while retaining the common external-auth completion function shared with Phase 1's resume-and-verify logic.

Adds:

- A manifest redirect-receiver activity for the custom-scheme callback.
- The AppAuth dependency.
- Native provider discovery and provider selection.
- PKCE code verifier/challenge and nonce generation.
- Callback code exchange against `POST /api/v1/auth/oidc/callback`.

Preserves:

- Password login as a fallback authentication method.
- Existing pending-destination recovery after authentication.

## Resource / security trade-offs summary

| | Phase 1 (WebView) | Phase 2 (AppAuth) |
| --- | --- | --- |
| Server change required | None | Requires #554 or equivalent mobile-redirect support |
| New dependency | None | AppAuth |
| User-agent | Embedded WebView | External Custom Tabs / system browser |
| System-browser SSO / passkey integration | Limited or unavailable | Supported by the external browser and provider |
| IdP compatibility risk | Some IdPs block embedded WebView OAuth | Standard RFC 8252 pattern |
| Implementation cost | Low | Moderate (manifest, PKCE/nonce, redirect handling) |

## Configuration

- Custom scheme: use `com.vangeaux.lagrange:/oauth2-callback` for Lagrange's native redirect URI, not `bookorbit://`, to avoid a scheme collision with the reference `deranjer/bookorbit-android` client.
- The existing configured provider record on the target BookOrbit deployment is reused; only the native redirect URI needs to be added to the server's mobile-redirect allow-list and to the identity provider's registered client redirect URIs.

## Implementation seams

- A common external-auth completion function verifies `GET /api/v1/auth/me` and resumes the existing pending destination, shared between Phase 1's WebView completion and Phase 2's AppAuth callback exchange, so Phase 2 only replaces the launcher, not the completion path.
- Phase 1 adds the screen/loading state needed to host the WebView, but can reuse the existing coordinator destination-recovery path after the WebView closes.

## Logout requirement

Lagrange's current sign-out only clears local state. Final OIDC work (Phase 2, and ideally retrofitted to Phase 1) must call `POST /api/v1/auth/logout` before clearing local state, and may optionally open a `logoutUrl` returned by that call so IdP-side session state is also cleared.

## Tests

- Phase 1: coordinator/repository tests for WebView-completion → `GET /api/v1/auth/me` → pending-destination resume, matching the pattern used for native login recovery.
- Phase 2: PKCE/nonce generation, redirect-URI handling, and callback-exchange tests, plus regression coverage that password login and pending-destination recovery remain intact after the launcher swap.
- Phase 1 source/unit verification is complete; physical-device validation is still required before considering the interim flow complete. Phase 2 also requires device/provider validation after AppAuth is implemented, consistent with this project's existing device-validation practice.

## External references

- BookOrbit server issue: https://github.com/bookorbit/bookorbit/issues/490
- BookOrbit server PR (open, not merged): https://github.com/bookorbit/bookorbit/pull/554
- Reference Android client (assumes unmerged server support): https://github.com/deranjer/bookorbit-android
- RFC 8252, OAuth 2.0 for Native Apps: https://www.rfc-editor.org/rfc/rfc8252
