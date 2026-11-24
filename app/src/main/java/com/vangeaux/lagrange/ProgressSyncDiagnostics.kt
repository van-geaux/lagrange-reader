package com.vangeaux.lagrange

import android.util.Log

/** Temporary, debug-only EPUB progress diagnostics. Never pass identifiers or raw payloads. */
internal object ProgressSyncDiagnostics {
    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.i("EpubProgressDiag", message) }
    }
}
