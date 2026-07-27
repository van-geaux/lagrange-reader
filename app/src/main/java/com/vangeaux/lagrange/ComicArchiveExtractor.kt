package com.vangeaux.lagrange

import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.exception.UnsupportedRarEncryptedException
import com.github.junrar.exception.UnsupportedRarMethodException
import com.github.junrar.exception.UnsupportedRarVersionException
import com.github.junrar.exception.WrongPasswordException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/** Centralized safety bounds for offline CBR/CB7 extraction on Android. */
internal object ComicArchiveExtractionLimits {
    const val MAX_ENTRY_COUNT: Int = 2000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 600L * 1024 * 1024
    const val MAX_SINGLE_ENTRY_UNCOMPRESSED_BYTES: Long = 60L * 1024 * 1024
}

/**
 * Testable view of [ComicArchiveExtractionLimits]. Production callers use the defaults; tests
 * can supply small bounds to exercise the entry-count/size-limit failure paths cheaply.
 */
internal data class ComicArchiveExtractionBounds(
    val maxEntryCount: Int = ComicArchiveExtractionLimits.MAX_ENTRY_COUNT,
    val maxTotalUncompressedBytes: Long = ComicArchiveExtractionLimits.MAX_TOTAL_UNCOMPRESSED_BYTES,
    val maxSingleEntryUncompressedBytes: Long = ComicArchiveExtractionLimits.MAX_SINGLE_ENTRY_UNCOMPRESSED_BYTES
)

internal enum class ComicArchiveKind {
    RAR,
    SEVEN_ZIP
}

internal enum class ComicArchiveExtractionFailureReason {
    UNSUPPORTED_FORMAT,
    ENCRYPTED,
    INVALID_ARCHIVE,
    NO_SUPPORTED_IMAGES,
    TOO_MANY_ENTRIES,
    ENTRY_TOO_LARGE,
    TOTAL_SIZE_EXCEEDED,
    IO_ERROR
}

internal sealed interface ComicArchiveExtractionResult {
    data class Success(val file: File) : ComicArchiveExtractionResult
    data class Failure(
        val reason: ComicArchiveExtractionFailureReason,
        val userMessage: String
    ) : ComicArchiveExtractionResult
}

private class ArchiveEntryTooLargeException : IOException()

/**
 * Reads a local RAR4/RAR5 or 7z comic archive and writes a normalized CBZ (plain ZIP of
 * supported comic images, in source archive order) to [output]. The original archive is
 * never modified; callers are responsible for staging [output] and promoting it atomically.
 */
internal object ComicArchiveExtractor {
    private val rar4Signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val rar5Signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
    private val sevenZipSignature = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    fun detectKind(file: File): ComicArchiveKind? {
        val header = runCatching {
            ByteArray(sevenZipSignature.size.coerceAtLeast(rar5Signature.size)).also { buffer ->
                FileInputStream(file).use { input -> input.read(buffer) }
            }
        }.getOrNull() ?: return null
        return when {
            header.startsWith(rar4Signature) || header.startsWith(rar5Signature) -> ComicArchiveKind.RAR
            header.startsWith(sevenZipSignature) -> ComicArchiveKind.SEVEN_ZIP
            else -> null
        }
    }

    /**
     * On failure, [output] is guaranteed not to exist: callers can rely on the extractor to
     * clean up its own partial writes and only need to stage/promote [output] on success.
     */
    fun extract(
        source: File,
        output: File,
        bounds: ComicArchiveExtractionBounds = ComicArchiveExtractionBounds()
    ): ComicArchiveExtractionResult {
        val result = when (detectKind(source)) {
            ComicArchiveKind.RAR -> extractRar(source, output, bounds)
            ComicArchiveKind.SEVEN_ZIP -> extractSevenZip(source, output, bounds)
            null -> ComicArchiveExtractionResult.Failure(
                ComicArchiveExtractionFailureReason.UNSUPPORTED_FORMAT,
                "This file is not a supported CBR or CB7 comic archive."
            )
        }
        if (result is ComicArchiveExtractionResult.Failure && output.exists()) {
            output.delete()
        }
        return result
    }

    private fun extractRar(
        source: File,
        output: File,
        bounds: ComicArchiveExtractionBounds
    ): ComicArchiveExtractionResult {
        return try {
            Archive(source).use { archive ->
                if (runCatching { archive.isEncrypted() }.getOrDefault(false)) {
                    return encryptedFailure("CBR")
                }
                val headers = archive.fileHeaders.filterNot { it.isDirectory }
                if (headers.size > bounds.maxEntryCount) {
                    return tooManyEntriesFailure()
                }
                var totalBytes = 0L
                var imageCount = 0
                ZipOutputStream(FileOutputStream(output)).use { zipOut ->
                    headers.forEach { header ->
                        if (header.isEncrypted) {
                            return encryptedFailure("CBR")
                        }
                        val name = header.fileName
                        if (name.isNullOrBlank() || isUnsafeEntryName(name)) {
                            return invalidArchiveFailure("This CBR contains an unsafe or ambiguous entry name.")
                        }
                        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
                        if (extension !in ReaderFileValidator.comicImageExtensions) {
                            return@forEach
                        }
                        val remainingBudget = bounds.maxTotalUncompressedBytes - totalBytes
                        if (remainingBudget <= 0L) {
                            return totalSizeExceededFailure()
                        }
                        val entryLimit = minOf(bounds.maxSingleEntryUncompressedBytes, remainingBudget)
                        zipOut.putNextEntry(ZipEntry("%05d.%s".format(imageCount, extension)))
                        val written = try {
                            archive.getInputStream(header).use { input -> copyBounded(input, zipOut, entryLimit) }
                        } catch (overflow: ArchiveEntryTooLargeException) {
                            return if (entryLimit == bounds.maxSingleEntryUncompressedBytes) {
                                entryTooLargeFailure()
                            } else {
                                totalSizeExceededFailure()
                            }
                        }
                        zipOut.closeEntry()
                        totalBytes += written
                        imageCount++
                    }
                }
                if (imageCount == 0) {
                    return noSupportedImagesFailure()
                }
                ComicArchiveExtractionResult.Success(output)
            }
        } catch (encrypted: UnsupportedRarEncryptedException) {
            encryptedFailure("CBR")
        } catch (wrongPassword: WrongPasswordException) {
            encryptedFailure("CBR")
        } catch (unsupported: UnsupportedRarVersionException) {
            unsupportedFormatFailure("This CBR uses a RAR variant BookOrbit does not support yet.")
        } catch (unsupported: UnsupportedRarMethodException) {
            unsupportedFormatFailure("This CBR uses a RAR compression method BookOrbit does not support yet.")
        } catch (rarError: RarException) {
            invalidArchiveFailure("This CBR could not be read. It may be corrupt.")
        } catch (io: IOException) {
            ComicArchiveExtractionResult.Failure(
                ComicArchiveExtractionFailureReason.IO_ERROR,
                "This CBR could not be read from local storage."
            )
        }
    }

    private fun extractSevenZip(
        source: File,
        output: File,
        bounds: ComicArchiveExtractionBounds
    ): ComicArchiveExtractionResult {
        return try {
            SevenZFile.builder().setFile(source).get().use { sevenZFile ->
                val entries = sevenZFile.entries.filterNot { it.isDirectory }
                if (entries.size > bounds.maxEntryCount) {
                    return tooManyEntriesFailure()
                }
                var totalBytes = 0L
                var imageCount = 0
                ZipOutputStream(FileOutputStream(output)).use { zipOut ->
                    entries.forEach { entry ->
                        if (!entry.hasStream()) {
                            return@forEach
                        }
                        val name = entry.name
                        if (name.isNullOrBlank() || isUnsafeEntryName(name)) {
                            return invalidArchiveFailure("This CB7 contains an unsafe or ambiguous entry name.")
                        }
                        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
                        if (extension !in ReaderFileValidator.comicImageExtensions) {
                            return@forEach
                        }
                        val remainingBudget = bounds.maxTotalUncompressedBytes - totalBytes
                        if (remainingBudget <= 0L) {
                            return totalSizeExceededFailure()
                        }
                        val entryLimit = minOf(bounds.maxSingleEntryUncompressedBytes, remainingBudget)
                        zipOut.putNextEntry(ZipEntry("%05d.%s".format(imageCount, extension)))
                        val written = try {
                            sevenZFile.getInputStream(entry).use { input -> copyBounded(input, zipOut, entryLimit) }
                        } catch (overflow: ArchiveEntryTooLargeException) {
                            return if (entryLimit == bounds.maxSingleEntryUncompressedBytes) {
                                entryTooLargeFailure()
                            } else {
                                totalSizeExceededFailure()
                            }
                        }
                        zipOut.closeEntry()
                        totalBytes += written
                        imageCount++
                    }
                }
                if (imageCount == 0) {
                    return noSupportedImagesFailure()
                }
                ComicArchiveExtractionResult.Success(output)
            }
        } catch (passwordRequired: PasswordRequiredException) {
            encryptedFailure("CB7")
        } catch (io: IOException) {
            invalidArchiveFailure("This CB7 could not be read. It may be corrupt.")
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw ArchiveEntryTooLargeException()
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun isUnsafeEntryName(name: String): Boolean {
        if (name.contains(' ')) return true
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) return true
        if (normalized.contains(':')) return true
        return normalized.split('/').any { it == ".." }
    }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean {
        return size >= signature.size && signature.indices.all { index -> this[index] == signature[index] }
    }

    private fun encryptedFailure(label: String) = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.ENCRYPTED,
        "This $label is password-protected. BookOrbit cannot open encrypted comic archives yet."
    )

    private fun unsupportedFormatFailure(message: String) = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.UNSUPPORTED_FORMAT,
        message
    )

    private fun invalidArchiveFailure(message: String) = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.INVALID_ARCHIVE,
        message
    )

    private fun noSupportedImagesFailure() = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.NO_SUPPORTED_IMAGES,
        "This comic archive does not contain any supported comic page images."
    )

    private fun tooManyEntriesFailure() = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.TOO_MANY_ENTRIES,
        "This comic archive has too many entries for BookOrbit to safely extract."
    )

    private fun entryTooLargeFailure() = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.ENTRY_TOO_LARGE,
        "This comic archive contains a page image that is too large for BookOrbit to safely extract."
    )

    private fun totalSizeExceededFailure() = ComicArchiveExtractionResult.Failure(
        ComicArchiveExtractionFailureReason.TOTAL_SIZE_EXCEEDED,
        "This comic archive is too large for BookOrbit to safely extract."
    )
}
