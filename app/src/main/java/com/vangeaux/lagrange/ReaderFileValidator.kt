package com.vangeaux.lagrange

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal object ReaderFileValidator {
    internal val comicImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
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

    fun isReadable(
        mediaKind: MediaKind,
        length: Long,
        openInput: () -> InputStream
    ): Boolean {
        if (length <= 0L) return false
        return when (mediaKind) {
            MediaKind.AUDIO,
            MediaKind.UNKNOWN -> true
            MediaKind.PDF -> hasHeader(openInput, "%PDF-".toByteArray(Charsets.US_ASCII))
            MediaKind.EPUB -> epubStreamContainsContainer(openInput)
            MediaKind.COMIC -> hasHeader(openInput, rar4Signature, rar5Signature, sevenZipSignature) ||
                zipContains(openInput) { name ->
                    name.substringAfterLast('.', "").lowercase(Locale.US) in comicImageExtensions
                }
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

    private fun hasHeader(openInput: () -> InputStream, vararg signatures: ByteArray): Boolean =
        runCatching {
            val maxSize = signatures.maxOf(ByteArray::size)
            val header = ByteArray(maxSize)
            val count = openInput().use { input -> input.read(header) }
            signatures.any { signature ->
                count >= signature.size && signature.indices.all { index -> header[index] == signature[index] }
            }
        }.getOrDefault(false)

    private fun zipContains(openInput: () -> InputStream, predicate: (String) -> Boolean): Boolean =
        runCatching {
            ZipInputStream(openInput().buffered()).use { zip ->
                var entries = 0
                var scannedUncompressedBytes = 0L
                while (entries < MAX_VALIDATION_ZIP_ENTRIES) {
                    val entry = zip.nextEntry ?: return false
                    entries += 1
                    if (!entry.isDirectory && predicate(entry.name)) return true
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        scannedUncompressedBytes += count
                        if (scannedUncompressedBytes > MAX_VALIDATION_ZIP_SCAN_BYTES) {
                            return false
                        }
                    }
                    zip.closeEntry()
                }
                false
            }
        }.getOrDefault(false)

    private fun epubStreamContainsContainer(openInput: () -> InputStream): Boolean {
        if (zipContains(openInput) { name -> name == "META-INF/container.xml" }) return true
        // Android's ZipInputStream rejects some otherwise readable EPUBs whose stored directory
        // entries carry a data descriptor. Fall back to local-header discovery only when that
        // exact malformed-archive marker is present and the required container entry is found.
        return runCatching { epubLocalHeadersContainContainer(openInput) }.getOrDefault(false)
    }

    private fun epubLocalHeadersContainContainer(openInput: () -> InputStream): Boolean = runCatching {
        openInput().buffered().use { input ->
            var scannedBytes = 0L
            var hasStoredDataDescriptor = false
            val header = ByteArray(26)

            fun readByte(): Int {
                val value = input.read()
                if (value >= 0) scannedBytes += 1L
                return value
            }

            fun readFully(buffer: ByteArray): Boolean {
                var offset = 0
                while (offset < buffer.size) {
                    val count = input.read(buffer, offset, buffer.size - offset)
                    if (count < 0) return false
                    if (count == 0) continue
                    offset += count
                    scannedBytes += count.toLong()
                    if (scannedBytes > MAX_VALIDATION_ZIP_SCAN_BYTES) return false
                }
                return true
            }

            fun littleEndianShort(offset: Int): Int =
                (header[offset].toInt() and 0xFF) or
                    ((header[offset + 1].toInt() and 0xFF) shl 8)

            while (scannedBytes < MAX_VALIDATION_ZIP_SCAN_BYTES) {
                val first = readByte()
                if (first < 0) return false
                if (first != 0x50 || readByte() != 0x4B ||
                    readByte() != 0x03 || readByte() != 0x04
                ) {
                    if (scannedBytes >= MAX_VALIDATION_ZIP_SCAN_BYTES) return false
                    continue
                }
                if (!readFully(header)) return false
                val nameLength = littleEndianShort(22)
                val extraLength = littleEndianShort(24)
                if (nameLength > MAX_VALIDATION_ZIP_ENTRY_NAME_BYTES) return false
                val flags = littleEndianShort(2)
                val method = littleEndianShort(4)
                val name = ByteArray(nameLength)
                if (!readFully(name)) return false
                if (extraLength > 0 && !readFully(ByteArray(extraLength))) return false
                val entryName = name.toString(Charsets.UTF_8)
                hasStoredDataDescriptor = hasStoredDataDescriptor ||
                    (entryName.endsWith("/") &&
                        flags and ZIP_DATA_DESCRIPTOR_FLAG != 0 && method == ZIP_STORED_METHOD)
                if (entryName == "META-INF/container.xml") {
                    if (hasStoredDataDescriptor) return true
                }
            }
            false
        }
    }.getOrDefault(false)

    private const val MAX_VALIDATION_ZIP_ENTRIES = 20_000
    private const val MAX_VALIDATION_ZIP_SCAN_BYTES = 32L * 1024L * 1024L
    private const val MAX_VALIDATION_ZIP_ENTRY_NAME_BYTES = 4 * 1024
    private const val ZIP_DATA_DESCRIPTOR_FLAG = 0x08
    private const val ZIP_STORED_METHOD = 0
}
