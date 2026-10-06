# Provider documentation

Provider-specific contracts and implementation notes live under this directory. Shared architecture, reader/player behavior, and provider-neutral module contracts remain at the `docs/` root.

## BookOrbit

- [API contract](./bookorbit/api.md) — endpoints, payloads, authentication, progress, downloads, and synchronization contracts.
- [Modules](./bookorbit/modules.md) — BookOrbit composition root and compatibility seam.
- [OIDC / SSO authentication](./bookorbit/oidc-authentication.md) — BookOrbit WebView flow, native redirect gap, and AppAuth status.

## Komga

- [Integration](./komga/integration.md) — verified authentication, library, catalog, download, and reader boundaries.
- [Modules](./komga/modules.md) — Komga provider modules, endpoints, and unsupported capabilities.

Cross-provider manual validation remains in [`../provider-checklists/`](../provider-checklists/), including the provider function matrix and copyable checklist template.
