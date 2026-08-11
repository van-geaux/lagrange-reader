# Security Policy

Lagrange Reader is the Android client for the `van-geaux/lagrange-reader` repository. This policy explains how to report a security concern responsibly.

## Supported versions

Please report issues against the latest publicly available version. Older versions may not receive security fixes; support for a particular version is assessed during triage rather than guaranteed for a fixed time window.

## Reporting a vulnerability

No security contact is currently published for this repository. Please use GitHub's private vulnerability reporting or security-advisory channel for `van-geaux/lagrange-reader` if it is available. If it is not available, contact the maintainer privately through GitHub before making any public disclosure. Do not open a public issue for an unpatched vulnerability.

Include, where safe to share:

- a clear description of the issue and its security impact;
- affected app version, Android version, and BookOrbit/server context;
- reproducible steps or a minimal proof of concept;
- relevant logs, screenshots, traces, or sample requests with sensitive values removed.

Never include passwords, session cookies, access/refresh tokens, private keys, personal data, or live server credentials. Redact secrets before sending and revoke or rotate any credential that may have been exposed.

## Triage and coordination

The maintainer will review reports, may request clarification or a minimal reproduction, and will coordinate scope, remediation, and any public disclosure timeline with the reporter. Please allow reasonable time for investigation and a fix before disclosure. Do not test against systems or accounts without authorization.

## Scope and upstream reporting

The app stores cookies/session data and downloaded content locally, so reports should explain the affected storage or access boundary where relevant. The app recommends HTTPS, does not bypass TLS errors in its interim server-hosted WebView sign-in flow, and keeps authentication-origin, TLS, cookie, token, and redirect checks separate. These safeguards do not make every deployment or network secure.

Issues in a BookOrbit server or an identity provider may require reporting to the relevant upstream maintainer or provider as well as, or instead of, this repository. Please avoid including another party's confidential information in a report here.
