package com.vangeaux.lagrange

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

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

internal fun Bundle.readReaderLocator(): Locator? = getString(STATE_READER_LOCATOR)
    ?.let { saved -> runCatching { Locator.fromJSON(JSONObject(saved)) }.getOrNull() }

internal fun Bundle.putReaderLocator(locator: Locator?) {
    locator ?: return
    putString(STATE_READER_LOCATOR, locator.toJSON().toString())
}

internal enum class ReaderRestoreAction { OPEN, REOPEN }

internal fun readerRestoreAction(hasSavedInstanceState: Boolean): ReaderRestoreAction =
    if (hasSavedInstanceState) ReaderRestoreAction.REOPEN else ReaderRestoreAction.OPEN

internal fun shouldPauseReadingSession(isChangingConfigurations: Boolean): Boolean =
    !isChangingConfigurations

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
