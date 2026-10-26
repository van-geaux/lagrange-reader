package com.vangeaux.lagrange

import android.content.Context
import java.util.Locale
import org.json.JSONObject

/** Where the language used for hyphenation and read-aloud comes from. */
internal enum class EpubContentLanguageSource { OVERRIDE, BOOK, DEVICE, NONE }

internal data class EpubContentLanguage(val tag: String?, val source: EpubContentLanguageSource)

/**
 * Resolves the book's content language. A per-book override wins; otherwise the book's declared
 * language, otherwise the device language when it is one that hyphenates (see
 * [epubFallbackContentLanguage]). This mirrors what Readium does on its own when nothing is set.
 */
internal fun resolveEpubContentLanguage(
    override: String?,
    publicationLanguages: List<String>,
    deviceLocale: Locale
): EpubContentLanguage {
    override?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return EpubContentLanguage(it, EpubContentLanguageSource.OVERRIDE)
    }
    publicationLanguages.firstOrNull { it.isNotBlank() }?.let {
        return EpubContentLanguage(it.trim(), EpubContentLanguageSource.BOOK)
    }
    epubFallbackContentLanguage(publicationLanguages, deviceLocale)?.let {
        return EpubContentLanguage(it, EpubContentLanguageSource.DEVICE)
    }
    return EpubContentLanguage(null, EpubContentLanguageSource.NONE)
}

/**
 * Language to hand to the Readium EPUB navigator. A declared book language is left to Readium so
 * per-element `xml:lang` keeps working; an override or the device fallback must be set explicitly.
 */
internal fun epubNavigatorLanguage(language: EpubContentLanguage): String? = when (language.source) {
    EpubContentLanguageSource.OVERRIDE, EpubContentLanguageSource.DEVICE -> language.tag
    EpubContentLanguageSource.BOOK, EpubContentLanguageSource.NONE -> null
}

/**
 * Readium only injects its `language` into documents whose `<html>` declares none, so a per-book
 * override would not reach chapters that carry their own `lang`/`xml:lang`. This script, run on the
 * current document, sets the override on `<html>` and `<body>` and restores the publisher's values
 * once the override is cleared (Auto). Elements below `<body>` keep their own language.
 */
internal fun epubDocumentLanguageScript(override: String?): String {
    val tag = override?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { JSONObject.quote(it) } ?: "null"
    return """
        (function(lang) {
          var XML = 'http://www.w3.org/XML/1998/namespace';
          [document.documentElement, document.body].forEach(function(el) {
            if (!el) return;
            var saved = el.__lagrangeLang;
            if (lang !== null) {
              if (!saved) {
                saved = el.__lagrangeLang = {
                  lang: el.getAttribute('lang'),
                  xml: el.getAttributeNS(XML, 'lang')
                };
              }
              if (el.getAttribute('lang') !== lang) el.setAttribute('lang', lang);
              if (el.getAttributeNS(XML, 'lang') !== lang) el.setAttributeNS(XML, 'xml:lang', lang);
            } else if (saved) {
              if (saved.lang === null) el.removeAttribute('lang'); else el.setAttribute('lang', saved.lang);
              if (saved.xml === null) el.removeAttributeNS(XML, 'lang'); else el.setAttributeNS(XML, 'xml:lang', saved.xml);
              delete el.__lagrangeLang;
            }
          });
        })($tag);
    """.trimIndent()
}

/** Language to hand to the Readium TTS navigator: only an override; Auto keeps Readium's choice. */
internal fun epubTtsLanguage(language: EpubContentLanguage): String? =
    language.tag.takeIf { language.source == EpubContentLanguageSource.OVERRIDE }

internal fun epubLanguageDisplayName(tag: String, displayLocale: Locale): String {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(displayLocale).takeIf { it.isNotBlank() && it != tag } ?: tag
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }
}

internal fun epubContentLanguageSummary(
    language: EpubContentLanguage,
    displayLocale: Locale,
    context: Context? = null
): String {
    val name = language.tag?.let { epubLanguageDisplayName(it, displayLocale) }
    fun text(resourceId: Int, fallback: String, vararg args: Any): String =
        context?.getString(resourceId, *args) ?: fallback
    return when (language.source) {
        EpubContentLanguageSource.OVERRIDE -> name.orEmpty()
        EpubContentLanguageSource.BOOK -> text(
            R.string.extras_book_language_auto_from_book,
            "Auto · $name (from book)",
            name.orEmpty()
        )
        EpubContentLanguageSource.DEVICE -> text(
            R.string.extras_book_language_auto_from_device,
            "Auto · $name (device; book declares none)",
            name.orEmpty()
        )
        EpubContentLanguageSource.NONE -> text(
            R.string.extras_book_language_auto_unknown,
            "Auto · unknown (book declares none)"
        )
    }
}

internal data class EpubLanguageOption(val tag: String, val displayName: String)

internal data class EpubBookLanguageChoice(
    val resolved: EpubContentLanguage,
    val summary: String,
    val suggested: List<EpubLanguageOption>,
    val all: List<EpubLanguageOption>
)

/**
 * Suggested languages (the book's, the device's and English) followed by every base language the
 * platform knows, by display name. Base tags only: voices are matched without region and the
 * browser picks the regional hyphenation patterns itself.
 */
internal fun epubBookLanguageChoice(
    override: String?,
    publicationLanguages: List<String>,
    deviceLocale: Locale,
    availableLanguages: List<String> = Locale.getAvailableLocales().map { it.language },
    context: Context? = null
): EpubBookLanguageChoice {
    val displayLocale = context?.resources?.configuration?.locales?.get(0) ?: deviceLocale
    fun option(tag: String) = EpubLanguageOption(tag, epubLanguageDisplayName(tag, displayLocale))
    val suggestedTags = (
        publicationLanguages.map { Locale.forLanguageTag(it.trim()).language } +
            deviceLocale.language +
            "en"
        )
        .filter { it.isNotBlank() && it != "und" }
        .distinct()
    val all = availableLanguages
        .filter { it.length in 2..3 && it != "und" }
        .distinct()
        .map(::option)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    return EpubBookLanguageChoice(
        resolved = resolveEpubContentLanguage(override, publicationLanguages, deviceLocale),
        summary = epubContentLanguageSummary(
            resolveEpubContentLanguage(override, publicationLanguages, deviceLocale),
            displayLocale,
            context
        ),
        suggested = suggestedTags.map(::option),
        all = all
    )
}

/**
 * Per-book language override, kept separately for each saved server profile (account), because
 * the same book ID can exist on another server or account. Absent means Auto.
 */
internal class EpubBookLanguageStore(context: Context, private val profileId: String?) {
    /** Scoped to the active profile, read once: a reader keeps the profile it was opened with. */
    constructor(context: Context) : this(context, ServerProfileStore(context).activeId())

    private val preferences = context.getSharedPreferences(
        "readium_epub_book_languages",
        Context.MODE_PRIVATE
    )

    fun read(readerKey: String): String? =
        readerKey.takeIf { it.isNotBlank() }
            ?.let { preferences.getString(epubBookLanguageKey(profileId, it), null) }

    fun save(readerKey: String, languageTag: String?) {
        if (readerKey.isBlank()) return
        val key = epubBookLanguageKey(profileId, readerKey)
        val tag = languageTag?.trim()?.takeIf { it.isNotEmpty() }
        preferences.edit().apply {
            if (tag == null) remove(key) else putString(key, tag)
        }.apply()
    }

    /** Forgets this profile's choices; other profiles keep theirs. */
    fun clearProfile() {
        val prefix = epubBookLanguageKeyPrefix(profileId)
        val keys = preferences.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        preferences.edit().apply { keys.forEach(::remove) }.apply()
    }
}

/** URL-safe Base64 never contains '/', so a profile ID cannot spill into the reader key. */
private fun epubBookLanguageKeyPrefix(profileId: String?): String =
    java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(profileId.orEmpty().toByteArray(Charsets.UTF_8)) + "/"

internal fun epubBookLanguageKey(profileId: String?, readerKey: String): String =
    epubBookLanguageKeyPrefix(profileId) + readerKey
