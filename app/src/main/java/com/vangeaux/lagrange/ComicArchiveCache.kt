package com.vangeaux.lagrange

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Extracts a local CBR/CB7 into a normalized, cached CBZ keyed by stable server/file identity
 * plus source metadata, so an unchanged source archive is only ever extracted once and a
 * changed source (different size or modification time) invalidates the previous cache entry.
 */
internal object ComicArchiveCache {
    sealed interface Outcome {
        data class Ready(val file: File) : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun resolve(identity: String, source: File, cacheDir: File): Outcome {
        cacheDir.mkdirs()
        val identityToken = sha256Hex(identity)
        val signatureToken = sha256Hex("${source.length()}:${source.lastModified()}")
        val target = File(cacheDir, "$identityToken-$signatureToken.cbz")

        if (target.exists() && target.length() > 0L && ReaderFileValidator.canRenderComicLocally(target)) {
            return Outcome.Ready(target)
        }

        cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("$identityToken-") && file.name != target.name
        }?.forEach { it.delete() }
        if (target.exists()) target.delete()

        val staged = File(cacheDir, ".$identityToken-$signatureToken.${UUID.randomUUID()}.part")
        return try {
            when (val result = ComicArchiveExtractor.extract(source, staged)) {
                is ComicArchiveExtractionResult.Success -> {
                    atomicReplaceDownloadedFile(staged, target)
                    Outcome.Ready(target)
                }
                is ComicArchiveExtractionResult.Failure -> Outcome.Failed(result.userMessage)
            }
        } finally {
            if (staged.exists()) staged.delete()
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
