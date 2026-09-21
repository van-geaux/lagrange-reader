# OIDC / SSO Authentication

This document is the source of truth for direct OIDC/SSO authentication in Lagrange. It records the verified BookOrbit server contract, the current app-compatibility gap, and the phased plan to close it.

## Conclusion

BookOrbit's server OIDC endpoints return normal Lagrange session credentials. Lagrange now implements Dexxicon's explicit server-hosted OIDC flow in an in-app WebView, including provider discovery, server-issued state, PKCE S256, nonce, exact callback interception, callback validation, and code exchange. The generic server-sign-in WebView remains available as a fallback. Native AppAuth/Custom Tabs remains a separate future path.

## Verified server contract

Inspected against BookOrbit server `main` at commit `67ef7f29a52753b7c9e336da2c83c00aefa2e76b` on 2026-09-21.

- `GET /api/v1/app-settings/oidc/providers/public` — public provider discovery.
- `POST /api/v1/auth/oidc/:slug/state` — issues per-provider authorization state.
- `POST /api/v1/auth/oidc/callback` — accepts `code`, `codeVerifier`, `redirectUri`, `nonce`, `state`. On success it returns a normal BookOrbit access token and user, and sets the same access/refresh cookies as password login.

Because the callback response is a normal BookOrbit session, no separate OIDC-specific session handling is required on the client once a callback is completed — the existing Bearer/cookie refresh infrastructure applies unchanged.

### Native redirect blocker

The deployed stock server currently only accepts `APP_URL/oauth2-callback` as a redirect URI, which resolves to a web origin, not a native app callback. Upstream server issue [#490](https://github.com/bookorbit/bookorbit/issues/490) and open PR [#554](https://github.com/bookorbit/bookorbit/pull/554) add an `OIDC_MOBILE_REDIRECT_URIS` allow-list to support native redirect URIs. PR #554 is open, **not merged**, as of this writing.

A separate reference Android client, [deranjer/bookorbit-android](https://github.com/deranjer/bookorbit-android), demonstrates the AppAuth + Custom Tabs pattern, but it currently assumes the unmerged server-side redirect support and is not itself a deployable solution against a stock server.

The configured OIDC provider record on a given BookOrbit deployment can be reused as-is; native support additionally requires the exact native redirect URI to be allow-listed by the deployed server and registered on the identity provider's client configuration.

## Current app compatibility

The Lagrange login screen retains native username/password credentials and provides **Sign in with SSO** when BookOrbit exposes enabled OIDC providers. The explicit OIDC WebView flow is verified against `https://bookorbit.alredho.com` with Authentik, including callback completion and library opening. Native custom-scheme callback handling and AppAuth remain unimplemented.

## Phased plan

### Phase 1 — generic server-hosted WebView sign-in (no server change required)

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

### Phase 2 — explicit BookOrbit OIDC WebView (implemented)

The explicit SSO entry point discovers enabled providers through `GET /api/v1/app-settings/oidc/providers/public`. After the user selects a provider, Lagrange requests server-issued state from `POST /api/v1/auth/oidc/{slug}/state`, generates a nonce and PKCE S256 verifier/challenge, and builds the authorization URL with the server HTTPS callback `${server}/oauth2-callback` and `offline_access` scope.

The WebView intercepts only the exact configured server callback origin, port, and `/oauth2-callback` path. Lagrange validates the returned authorization code and state, submits `code`, `codeVerifier`, `redirectUri`, `nonce`, and `state` to `POST /api/v1/auth/oidc/callback`, stores any returned access token through the existing session store, and verifies `GET /api/v1/auth/me` before resuming the pending destination.

State, verifier, nonce, and callback data are transaction-scoped and are cleared on success, cancellation, server change, logout, or failure. Callback values and credentials are not logged. After callback interception, the dialog shows **Completing sign-in…** while the code exchange, session verification, and pending-destination/library bootstrap finish. Password login and the generic server WebView remain fallbacks.

### Phase 3 — native AppAuth (after deployment verification)

Once the target server exposes its configured native redirect and the identity-provider client registers the exact same URI, add AppAuth + Custom Tabs as an optional external-browser flow. The explicit WebView flow does not require native redirect support.

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

| | Generic WebView | Explicit OIDC WebView |
| --- | --- | --- |
| Server change required | None | None beyond the existing BookOrbit OIDC endpoints |
| New dependency | None | None |
| User-agent | Embedded WebView | Embedded WebView |
| System-browser SSO / passkey integration | Limited or unavailable | Limited or unavailable |
| IdP compatibility risk | Some IdPs block embedded WebView OAuth | Some IdPs block embedded WebView OAuth |
| Implementation cost | Low | Moderate (provider discovery, PKCE/nonce, callback handling) |

## Configuration

- Custom scheme: use `com.vangeaux.lagrange:/oauth2-callback` for Lagrange's native redirect URI, not `bookorbit://`, to avoid a scheme collision with the reference `deranjer/bookorbit-android` client.
- The existing configured provider record on the target BookOrbit deployment is reused. Native AppAuth additionally requires the exact native redirect URI on the deployed server and identity-provider client; the explicit WebView flow uses the already-supported HTTPS callback.

## Implementation seams

- A common external-auth completion function verifies `GET /api/v1/auth/me` and resumes the existing pending destination, shared between Phase 1's WebView completion and Phase 2's AppAuth callback exchange, so Phase 2 only replaces the launcher, not the completion path.
- Phase 1 adds the screen/loading state needed to host the WebView, but can reuse the existing coordinator destination-recovery path after the WebView closes.

## Logout requirement

Lagrange's current sign-out only clears local state. Final OIDC work (Phase 2, and ideally retrofitted to Phase 1) must call `POST /api/v1/auth/logout` before clearing local state, and may optionally open a `logoutUrl` returned by that call so IdP-side session state is also cleared.

## Tests

- Generic WebView: coordinator/repository tests for WebView completion → `GET /api/v1/auth/me` → pending-destination resume.
- Explicit OIDC WebView: provider parsing, scope normalization, PKCE S256, nonce generation, exact callback validation, state mismatch/replay rejection, callback exchange, cancellation, and password/fallback regression coverage.
- Current source/build verification is complete for the explicit OIDC implementation. The user verified the flow on a connected phone against `https://bookorbit.alredho.com`: Authentik renders instead of a blank screen, successful OIDC login opens the library, and the post-callback completion state works. Native AppAuth validation remains pending until mobile redirect support is deployed.

## External references

- Dexxicon OIDC client: https://github.com/dexxfm/dexxicon-reader/blob/e6216ab65afc10b569adbdc7d924e5bdffd014f0/core/serverapi/src/commonMain/kotlin/net/dexxicon/reader/core/serverapi/oidc/OidcClient.kt
- Dexxicon OIDC API models: https://github.com/dexxfm/dexxicon-reader/blob/e6216ab65afc10b569adbdc7d924e5bdffd014f0/core/serverapi/src/commonMain/kotlin/net/dexxicon/reader/core/serverapi/oidc/OidcApi.kt
- BookOrbit native redirect configuration: https://github.com/BookOrbit/bookorbit/blob/67ef7f29a52753b7c9e336da2c83c00aefa2e76b/server/src/config/config.ts
- RFC 8252, OAuth 2.0 for Native Apps: https://www.rfc-editor.org/rfc/rfc8252
