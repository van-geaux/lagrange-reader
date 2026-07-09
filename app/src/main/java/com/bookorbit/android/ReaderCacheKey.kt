package com.bookorbit.android

import java.security.MessageDigest

internal object ReaderCacheKey {
    fun build(serverUrl: String, fileId: String, extension: String): String {
        val normalizedServer = normalizeStoredServerUrl(serverUrl)
        val payload = "$normalizedServer|$fileId"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val token = digest.joinToString("") { "%02x".format(it) }
        return "$token.$extension"
    }
}
