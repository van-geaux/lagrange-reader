# Achievement text localization

## What is localized

The Achievements screen itself is localized through Android resources
(`strings_secondary.xml`, `strings_browse.xml`): loading, empty/error states, section headings,
locked/unlocked labels, counts, award-date prefix and accessibility descriptions.

## What is server-owned

BookOrbit's `GET /api/v1/achievements` response contains the achievement catalogue. The client
stores and displays these fields exactly as returned by the server:

- `achievements[*].name`
- `achievements[*].description`
- `categories[*].label`

These are server-owned text, not Android resources, so adding a language to the app cannot translate
them. This is why the Achievements page can be fully Chinese while achievement names and
descriptions remain in the server's language.

Komga and Kavita return an unsupported achievement catalogue, so this only applies to BookOrbit.

## App-side support

BookOrbit localizes its server-owned text from `Accept-Language`, and its achievement controller
uses that header. The app now sends the **app interface locale** on BookOrbit API requests:

- `AppLocale.bookOrbitAcceptLanguage()` derives the tag from `AppLocale.languages`:
  - an explicit app language wins (`zh-CN`, `en`, or any future entry);
  - “system” resolves to the first app-supported language that matches the system locale;
  - unsupported system languages fall back to English, matching the current UI resource fallback.
- No per-language code branch is required: adding an `AppLanguage` entry makes that locale eligible
  for both explicit selection and system matching. BookOrbit then matches the tag against its own
  supported locale list.
- `BookOrbitRepository` adds the header through an OkHttp interceptor.
- `BookOrbitAcceptLanguageTest` covers explicit, system, Traditional Chinese and future-language
  cases.

If the server supports the header, it can return localized `name`, `description` and category
labels. Older BookOrbit versions simply ignore it.

## Current limitation

BookOrbit's server-side text resolver (`serverText`) is implemented in the BookOrbit project, but
the translation catalogs I checked did not yet contain `serverText` entries for languages such as
Simplified Chinese. When a catalog has no translation, BookOrbit intentionally falls back to the
seeded English text. The app therefore still shows English achievement names/descriptions against a
localized UI until the server catalogs are updated.

This cannot be fixed by adding more Android strings. The alternatives are:

1. **BookOrbit server translations (recommended)** — add the `serverText` achievement and category
   translations to BookOrbit's locale catalogs (Crowdin or direct PR). Every client, including this
   app, then benefits.
2. **Client-side fallback table (not recommended)** — duplicate the current 87-achievement seed
   (name + description, plus categories, secret achievements and monthly-challenge text) in the
   Android app. It would drift from the server, require maintenance for every new achievement, and
   override future server translations.

The app keeps the header support only; it does not add a client-side translation table.

## Verification

- `BookOrbitAcceptLanguageTest`
- `tools/validate_android_localizations.py`
- `./gradlew testDebugUnitTest lintDebug assembleDebug`

A real end-to-end verification needs a BookOrbit server version that supports `Accept-Language` and
has a `serverText` translation for the requested language; otherwise the server's default text is
expected.
