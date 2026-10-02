# Contributing to Lagrange Reader

## Current contribution status

General pull requests are temporarily paused for a few days while the project prepares and stabilizes:

- multi-server support;
- provider-specific module separation;
- the refactor that moves provider behavior behind those module boundaries.

Please do not submit a feature or general bug-fix pull request during this short pause unless a maintainer has explicitly requested it. This prevents unrelated changes from entering the architecture transition and reduces rework for contributors.

Issue reports, reproducible bug reports, architecture discussion, and narrowly scoped design feedback remain welcome. A maintainer may request a pull request when a change is aligned with the active refactor.

Security vulnerabilities are exempt from this pause. Follow [`SECURITY.md`](SECURITY.md) and do not disclose sensitive details in a public issue or pull request.

## If a maintainer requested a pull request

Before opening it:

1. Confirm the requested scope and target branch with the maintainer.
2. Keep the change limited to that scope.
3. Explain how it fits the multi-server and provider-module architecture.
4. Include the relevant tests and manual-validation status.
5. Do not include credentials, cookies, API keys, private server URLs, or generated local data.

Pull requests opened without an approved exception may be closed with a reference to this policy until the contribution pause ends.
