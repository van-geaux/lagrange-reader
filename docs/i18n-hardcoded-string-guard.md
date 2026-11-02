# Hardcoded UI string guard

## Why

Android lint's `HardcodedText` check only covers XML layouts. It does not catch Compose or Kotlin
UI literals such as:

```kotlin
Text("Home")
title = "Dashboard controls"
contentDescription = "Close"
```

These were the cause of the untranslated Android Auto labels fixed in the same workstream. A
manual review alone does not scale, so the project runs a small pull-request-only check.

## What runs

`tools/check_hardcoded_ui_strings.py` compares the pull request with its base commit
(`git diff --base...HEAD`) and scans only **added lines** under `app/src/main/java/**/*.kt`.

It flags obvious UI literals such as:

- `Text("...")`
- `title = "..."`, `subtitle = "..."`, `label = "..."`, `message = "..."`
- `contentDescription = "..."`, `placeholder = "..."`, `headline = "..."`
- `setDisplayName("...")`
- `OrbitEyebrow("...")`

It ignores:

- lines using `stringResource(...)`, `context.getString(...)` or `R.string.*`;
- brand names, URLs, MIME types, pure symbols/numbers;
- lines carrying `// i18n-ignore: <reason>`;
- existing code; only newly added lines are checked.

The check has its own unit tests (`tools/test_check_hardcoded_ui_strings.py`) and runs in
`.github/workflows/android-debug.yml` for pull requests. The workflow checks out full history so the
base commit is available.

## Exceptions

Use the inline marker only for a real, non-translatable value:

```kotlin
Text("Android Auto") // i18n-ignore: product name
```

Always include a reason after `i18n-ignore:`. Do not use it to silence a normal user-facing label;
add an Android string resource instead.

## Limitations

This is a heuristic, not a compiler rule. It is intentionally conservative to avoid false positives
for brands, protocol values and formatting. Multi-line string construction and unusual helpers may
need reviewer attention. A custom detekt/lint rule could replace it later if the project adopts
those tools.
