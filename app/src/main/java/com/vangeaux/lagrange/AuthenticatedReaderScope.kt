package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal fun unambiguousPersistenceKey(vararg fields: String): String = buildString {
    fields.forEach { field ->
        append(field.toByteArray(StandardCharsets.UTF_8).size)
        append(':')
        append(field)
    }
}

data class EpubReaderScope(
    val serverUrl: String,
    val profileId: String,
    val providerId: String,
    val accountScope: String
)

internal fun resolveEpubReaderScope(context: Context, serverUrl: String? = null): EpubReaderScope? {
    val requestedServer = serverUrl?.takeIf(String::isNotBlank)
    val active = ServerProfileStore(context.applicationContext).active()
    val profile = active
        ?.takeIf { requestedServer == null || serverUrlsMatch(it.serverUrl, requestedServer) }
        ?: return null
    val accountScope = AuthenticatedAccountScopeStore(context.applicationContext).read(profile.id)
        ?.takeIf(String::isNotBlank)
        ?: return null
    return EpubReaderScope(
        serverUrl = normalizeServerUrl(profile.serverUrl) ?: profile.serverUrl.trim(),
        profileId = profile.id,
        providerId = profile.providerId,
        accountScope = accountScope
    )
}

internal fun epubPublicationFingerprint(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun verifiedEpubPublicationFingerprint(file: File): String? =
    runCatching { epubPublicationFingerprint(file) }.getOrNull()
