package com.vangeaux.lagrange

import android.content.Context

/**
 * Resolves a provider/repository error message for display. Falls back to the verbatim original
 * English text when no context is available, so behavior never changes for callers without one.
 */
internal fun providerErrorMessage(
    context: Context?,
    messageRes: Int,
    fallback: String,
    vararg formatArgs: Any
): String = runCatching { context?.getString(messageRes, *formatArgs) }
    .getOrNull()
    ?: fallback
