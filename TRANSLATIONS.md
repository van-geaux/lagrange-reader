# Translation Guide

Lagrange Reader uses [Crowdin](https://crowdin.com/project/lagrange-reader) for translation work. English Android resources remain the source of truth in Git, and approved translations return through generated pull requests.

## For translators

1. Work in the Lagrange Reader Crowdin project instead of editing Android XML files directly.
2. Translate the English source strings in Crowdin.
3. Preserve every resource key exactly. Do not rename `name="..."` values.
4. Preserve placeholders exactly, including argument numbers such as `%1$s`, `%1$d`, and `%2$d`.
5. Provide the plural quantities appropriate for the target language, such as `one`, `few`, `many`, and `other`.
6. Do not translate brand names, provider names, URLs, MIME types, file formats, identifiers, or server-provided values.
7. Report unclear wording or missing context in a GitHub issue and include the language, resource key, and surrounding UI context.

Current Crowdin target languages are:

- Chinese Traditional
- Chinese Simplified
- Japanese
- Russian

A target language being present in Crowdin does not necessarily mean it is selectable in the app. Maintainers must register a locale in the Android app before exposing it through the language picker.

## How translation changes arrive

Crowdin exports approved translations to a generated branch and opens or updates a pull request against `main`. Do not open manual pull requests containing copied translation resource files.

Maintainers review the generated resource changes, run the localization and Android build checks, and decide when a language is ready to merge. Technical validation confirms XML structure, resource keys, placeholders, and plural handling; it does not prove that wording is linguistically correct.

## For maintainers

- Add or change English source strings in `app/src/main/res/values/`.
- Do not add source keys only to translated resource directories.
- Keep Crowdin credentials in GitHub Actions secrets. Never commit tokens.
- Register a new app locale in `AppLocale.kt` and `locales_config.xml` only when the project is ready to expose it.
- Use `python3 tools/validate_android_localizations.py` and the normal Android verification gate before merging a translation pull request.

See [`docs/i18n.md`](docs/i18n.md) for the complete resource layout, locale-registration procedure, Crowdin configuration, and validation details.
