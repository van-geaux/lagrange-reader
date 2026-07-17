package com.bookorbit.android

import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.ZipFile

internal object ReaderFileValidator {
    private val comicImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val rar4Signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
    private val rar5Signature = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
    private val sevenZipSignature = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    fun isReadable(mediaKind: MediaKind, file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) {
            return false
        }
        return when (mediaKind) {
            MediaKind.AUDIO,
            MediaKind.UNKNOWN -> true
            MediaKind.PDF -> hasPdfHeader(file)
            MediaKind.EPUB -> hasEpubContainer(file)
            MediaKind.COMIC -> canRenderComicLocally(file) || hasHeader(file, rar4Signature, rar5Signature, sevenZipSignature)
        }
    }

    fun canRenderComicLocally(file: File): Boolean = hasComicImageEntry(file)

    private fun hasPdfHeader(file: File): Boolean {
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(5)
                if (input.read(header) != header.size) {
                    return false
                }
                String(header, Charsets.US_ASCII) == "%PDF-"
            }
        }.getOrDefault(false)
    }

    private fun hasEpubContainer(file: File): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry("META-INF/container.xml") != null
            }
        }.getOrDefault(false)
    }

    private fun hasComicImageEntry(file: File): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        continue
                    }
                    val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
                    if (extension in comicImageExtensions) {
                        return true
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    private fun hasHeader(file: File, vararg signatures: ByteArray): Boolean {
        return runCatching {
            val maxSize = signatures.maxOf(ByteArray::size)
            val header = ByteArray(maxSize)
            val count = FileInputStream(file).use { input -> input.read(header) }
            signatures.any { signature ->
                count >= signature.size && signature.indices.all { index -> header[index] == signature[index] }
            }
        }.getOrDefault(false)
    }
}
