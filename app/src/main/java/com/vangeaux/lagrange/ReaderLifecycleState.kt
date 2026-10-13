package com.vangeaux.lagrange

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.withStateAtLeast
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

enum class ReaderCompletionReason {
    USER_CLOSED,
    LIFECYCLE_RECOVERY,
    OPEN_FAILED
}

internal fun shouldCloseReader(reason: ReaderCompletionReason): Boolean =
    reason == ReaderCompletionReason.USER_CLOSED

internal fun readerCompletionReason(saved: String?): ReaderCompletionReason =
    ReaderCompletionReason.entries.firstOrNull { it.name == saved }
        ?: ReaderCompletionReason.LIFECYCLE_RECOVERY

internal const val EXTRA_READER_COMPLETION_REASON = "reader_completion_reason"
internal const val STATE_READER_LOCATOR = "reader_saved_locator"
internal const val STATE_READER_CHROME_VISIBLE = "reader_chrome_visible"
internal const val STATE_READER_OPTIONS_VISIBLE = "reader_options_visible"
internal const val STATE_READER_TUTORIAL_SHOWN = "reader_tutorial_shown"
internal const val STATE_EPUB_TTS_LOCATOR = "epub_tts_saved_locator"
internal const val MAX_SAVED_READER_LOCATOR_BYTES = 64 * 1024

internal fun boundedReaderLocatorJson(json: String?): String? = json
    ?.takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_READER_LOCATOR_BYTES }

internal fun Bundle.readReaderLocator(): Locator? = getString(STATE_READER_LOCATOR)
    ?.let { saved -> runCatching { Locator.fromJSON(JSONObject(saved)) }.getOrNull() }

internal fun Bundle.putReaderLocator(locator: Locator?) {
    locator ?: return
    putString(STATE_READER_LOCATOR, locator.toJSON().toString())
}

internal fun Bundle.readEpubTtsLocator(): Locator? = getString(STATE_EPUB_TTS_LOCATOR)
    ?.let { saved -> runCatching { Locator.fromJSON(JSONObject(saved)) }.getOrNull() }

internal fun Bundle.putEpubTtsLocator(locator: Locator?) {
    locator ?: return
    putString(STATE_EPUB_TTS_LOCATOR, locator.toJSON().toString())
}

internal enum class ReaderRestoreAction { OPEN, REOPEN }

internal fun readerRestoreAction(hasSavedInstanceState: Boolean): ReaderRestoreAction =
    if (hasSavedInstanceState) ReaderRestoreAction.REOPEN else ReaderRestoreAction.OPEN

internal fun shouldPauseReadingSession(isChangingConfigurations: Boolean): Boolean =
    !isChangingConfigurations

internal fun shouldPauseEpubReadingSessionOnStop(
    isChangingConfigurations: Boolean,
    ttsPlayingInForeground: Boolean
): Boolean = shouldPauseReadingSession(isChangingConfigurations) && !ttsPlayingInForeground

/** Owns preparation, waits out saved fragment state, and closes if attachment never completes. */
internal suspend fun <T> LifecycleOwner.prepareAndAttachPublicationWhenResumed(
    publication: Publication,
    prepare: suspend () -> T,
    attach: (T) -> Unit
) {
    var attached = false
    try {
        val prepared = prepare()
        lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
            attach(prepared)
            attached = true
        }
    } finally {
        if (!attached) publication.close()
    }
}

internal data class ReaderLaunchState(
    val token: String? = null,
    val hasLaunched: Boolean = false
)

internal data class ReaderLaunchClaim(
    val state: ReaderLaunchState,
    val shouldLaunch: Boolean
)

internal fun claimReaderLaunch(
    current: ReaderLaunchState,
    token: String
): ReaderLaunchClaim {
    if (current.token == token && current.hasLaunched) {
        return ReaderLaunchClaim(current, shouldLaunch = false)
    }
    return ReaderLaunchClaim(
        state = ReaderLaunchState(token = token, hasLaunched = true),
        shouldLaunch = true
    )
}

internal val ReaderLaunchStateSaver: Saver<ReaderLaunchState, Any> = listSaver(
    save = { state -> listOf(state.token, state.hasLaunched) },
    restore = { saved ->
        ReaderLaunchState(
            token = saved.getOrNull(0) as? String,
            hasLaunched = saved.getOrNull(1) as? Boolean ?: false
        )
    }
)
