package com.vangeaux.lagrange

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/**
 * One selectable app language.
 *
 * [tag] is the value persisted in preferences; [localeOverride] is the BCP-47 locale applied to
 * resources, or `null` for "follow the system / Android 13+ per-app locale".
 */
internal data class AppLanguage(
    val tag: String,
    @StringRes val labelRes: Int,
    val localeOverride: String?
)

/**
 * App-level language preference.
 *
 * Add a new language by appending an [AppLanguage] to [languages], adding its label string to
 * `values/strings.xml` and every locale, adding the locale to `res/xml/locales_config.xml`, and
 * creating the matching `values-<locale>/` resource directory. See `docs/i18n.md`.
 */
internal object AppLocale {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val CHINESE_SIMPLIFIED = "zh-CN"

    /** Ordered list shown in Options → General → Language. */
    val languages: List<AppLanguage> = listOf(
        AppLanguage(SYSTEM, R.string.language_system, null),
        AppLanguage(ENGLISH, R.string.language_english, "en"),
        AppLanguage(CHINESE_SIMPLIFIED, R.string.language_chinese, "zh-CN")
    )

    val choices: List<String> = languages.map(AppLanguage::tag)

    fun languageFor(tag: String): AppLanguage =
        languages.firstOrNull { it.tag == tag } ?: languages.first()

    private const val PREFERENCES_NAME = "lagrange_app_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun readTag(context: Context): String =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, SYSTEM)
            ?: SYSTEM

    fun writeTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, tag)
            .apply()
    }

    /** Returns [base] with the selected app locale applied, or [base] unchanged for [SYSTEM]. */
    fun wrap(base: Context, tag: String = readTag(base)): Context {
        val override = languageFor(tag).localeOverride ?: return base
        val locale = Locale.forLanguageTag(override)
        if (locale.language.isEmpty()) return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }
}

/** Finds the Activity that owns [this] context, if any. */
internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
