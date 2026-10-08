# Contributing to Lagrange Reader

## Contribution workflow

1. Search existing issues before opening a new one. For a new feature or fix, describe the problem, expected behavior, affected provider or format, and any reproduction details.
2. Start from the latest `origin/main` and use a dedicated branch, such as `feat/issue-123-short-description` or `fix/issue-123-short-description`. Do not work directly on `main`.
3. Keep provider-specific HTTP, authentication, endpoint, payload, and mapping behavior under `app/src/main/java/com/vangeaux/lagrange/provider/`. Keep reader, UI, local media, orchestration, persistence coordination, and provider-neutral contracts in core.
4. Keep the change limited to the issue scope. Preserve unrelated worktree changes and do not commit credentials, cookies, API keys, private server URLs, generated local data, or signing material.
5. Add focused regression coverage for changed behavior. Run the relevant Kotlin compilation, JVM tests, lint, and Android APK/build checks. If device or live-server validation is required, report it separately from automated verification.
6. Review the complete diff, run `git diff --check`, and commit with a concise conventional-commit message.
7. Push the feature or fix branch and open a pull request against `main`. Include the issue link, summary, verification commands and results, and any remaining manual-validation status.
8. Wait for required checks and maintainer review before merging. Maintainers handle the final merge and remote branch cleanup.

## Security reports

Do not disclose security vulnerabilities in a public issue or pull request. Follow [`SECURITY.md`](SECURITY.md) and use GitHub's private vulnerability reporting or security-advisory channel when available.
