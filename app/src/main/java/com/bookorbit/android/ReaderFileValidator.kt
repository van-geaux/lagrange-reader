package com.bookorbit.android

import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.ZipFile

internal object ReaderFileValidator {
    private val comicImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    fun isReadable(mediaKind: MediaKind, file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) {
            return false
        }
        return when (mediaKind) {
            MediaKind.AUDIO,
            MediaKind.UNKNOWN -> true
            MediaKind.PDF -> hasPdfHeader(file)
            MediaKind.EPUB -> hasEpubContainer(file)
            MediaKind.COMIC -> hasComicImageEntry(file)
        }
    }

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
}
