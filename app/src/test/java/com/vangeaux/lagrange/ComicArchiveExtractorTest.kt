package com.vangeaux.lagrange

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * junrar (8.0.0) is a read-only RAR implementation with no RAR writer, so a valid RAR4/RAR5
 * fixture cannot be generated programmatically without inventing archive bytes. RAR-specific
 * coverage is therefore limited to signature detection (shared with [ReaderFileValidatorTest])
 * and the safety path for a file that carries a genuine RAR signature but corrupt content, which
 * exercises the same failure mapping a real corrupt/unsupported CBR would hit. The format-neutral
 * behavior (image filtering, ordering, traversal rejection, size/entry limits, no-image
 * rejection) is covered against generated 7z fixtures, since commons-compress does support
 * writing 7z via [SevenZOutputFile].
 */
class ComicArchiveExtractorTest {
    @Test
    fun `detects rar4, rar5 and seven zip signatures`() {
        val dir = tempDir("detect-kind")
        val rar4 = File(dir, "a.cbr").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x01))
        }
        val rar5 = File(dir, "b.cbr").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0x01))
        }
        val sevenZip = File(dir, "c.cb7").apply {
            writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x01))
        }
        val garbage = File(dir, "d.bin").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02)) }

        assertEquals(ComicArchiveKind.RAR, ComicArchiveExtractor.detectKind(rar4))
        assertEquals(ComicArchiveKind.RAR, ComicArchiveExtractor.detectKind(rar5))
        assertEquals(ComicArchiveKind.SEVEN_ZIP, ComicArchiveExtractor.detectKind(sevenZip))
        assertEquals(null, ComicArchiveExtractor.detectKind(garbage))
    }

    @Test
    fun `rejects a file that is not a supported archive`() {
        val dir = tempDir("unsupported")
        val plain = File(dir, "notes.txt").apply { writeText("hello") }
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(plain, output) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.UNSUPPORTED_FORMAT, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `corrupt rar signature is rejected safely without crashing`() {
        val dir = tempDir("corrupt-rar")
        val corruptRar = File(dir, "broken.cbr").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00) + ByteArray(64))
        }
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(corruptRar, output)
        assertTrue(result is ComicArchiveExtractionResult.Failure)
        val failure = result as ComicArchiveExtractionResult.Failure
        assertTrue(
            failure.reason == ComicArchiveExtractionFailureReason.INVALID_ARCHIVE ||
                failure.reason == ComicArchiveExtractionFailureReason.UNSUPPORTED_FORMAT ||
                failure.reason == ComicArchiveExtractionFailureReason.IO_ERROR
        )
        assertFalse(output.exists())
    }

    @Test
    fun `extracts supported images from seven zip in archive order and skips non-images`() {
        val dir = tempDir("sevenzip-extract")
        val source = File(dir, "book.cb7")
        writeSevenZip(
            source,
            "readme.txt" to "not a page".toByteArray(),
            "pages/002.jpg" to jpegBytes(),
            "pages/subdir/" to null,
            "pages/001.png" to pngBytes(),
            "pages/003.webp" to "fake webp".toByteArray()
        )
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(source, output)
        val success = result as ComicArchiveExtractionResult.Success
        assertTrue(success.file.exists())

        val entryNames = ZipFile(success.file).use { zip -> zip.entries().toList().map { it.name }.sorted() }
        assertEquals(listOf("00000.jpg", "00001.png", "00002.webp"), entryNames)
    }

    @Test
    fun `rejects seven zip archive with no supported images`() {
        val dir = tempDir("sevenzip-no-images")
        val source = File(dir, "book.cb7")
        writeSevenZip(source, "readme.txt" to "hello".toByteArray())
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(source, output) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.NO_SUPPORTED_IMAGES, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `rejects seven zip entries with path traversal or ambiguous names`() {
        val dir = tempDir("sevenzip-traversal")
        val source = File(dir, "book.cb7")
        writeSevenZip(source, "../escape.jpg" to jpegBytes())
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(source, output) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.INVALID_ARCHIVE, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `rejects seven zip entries with absolute or windows drive style names`() {
        val dir = tempDir("sevenzip-absolute")
        val source = File(dir, "book.cb7")
        writeSevenZip(source, "/etc/passwd.jpg" to jpegBytes())
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(source, output) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.INVALID_ARCHIVE, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `rejects seven zip archive with more entries than the configured limit`() {
        val dir = tempDir("sevenzip-too-many")
        val source = File(dir, "book.cb7")
        val pages: List<Pair<String, ByteArray?>> = (0 until 5).map { index ->
            "pages/%03d.jpg".format(index) to jpegBytes()
        }
        writeSevenZip(source, *pages.toTypedArray())
        val output = File(dir, "out.cbz")

        val bounds = ComicArchiveExtractionBounds(maxEntryCount = 3)
        val result = ComicArchiveExtractor.extract(source, output, bounds) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.TOO_MANY_ENTRIES, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `rejects a single entry larger than the per-entry limit`() {
        val dir = tempDir("sevenzip-entry-too-large")
        val source = File(dir, "book.cb7")
        val bigPage = jpegBytes() + ByteArray(1024)
        writeSevenZip(source, "pages/001.jpg" to bigPage)
        val output = File(dir, "out.cbz")

        val bounds = ComicArchiveExtractionBounds(maxSingleEntryUncompressedBytes = 100)
        val result = ComicArchiveExtractor.extract(source, output, bounds) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.ENTRY_TOO_LARGE, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `rejects an archive whose total uncompressed size exceeds the configured limit`() {
        val dir = tempDir("sevenzip-total-too-large")
        val source = File(dir, "book.cb7")
        writeSevenZip(
            source,
            "pages/001.jpg" to (jpegBytes() + ByteArray(80)),
            "pages/002.jpg" to (jpegBytes() + ByteArray(80))
        )
        val output = File(dir, "out.cbz")

        val bounds = ComicArchiveExtractionBounds(
            maxSingleEntryUncompressedBytes = 200,
            maxTotalUncompressedBytes = 150
        )
        val result = ComicArchiveExtractor.extract(source, output, bounds) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.TOTAL_SIZE_EXCEEDED, result.reason)
        assertFalse(output.exists())
    }

    @Test
    fun `rejects an encrypted seven zip archive with a clear user facing message`() {
        val dir = tempDir("sevenzip-encrypted")
        val source = File(dir, "book.cb7")
        writeEncryptedSevenZip(source, "pages/001.jpg" to jpegBytes(), password = "secret")
        val output = File(dir, "out.cbz")

        val result = ComicArchiveExtractor.extract(source, output) as ComicArchiveExtractionResult.Failure
        assertEquals(ComicArchiveExtractionFailureReason.ENCRYPTED, result.reason)
        assertTrue(result.userMessage.contains("password", ignoreCase = true))
        assertFalse(output.exists())
    }

    @Test
    fun `does not leave a staged output file behind on failure`() {
        val dir = tempDir("sevenzip-no-partial")
        val source = File(dir, "book.cb7")
        writeSevenZip(source, "readme.txt" to "hello".toByteArray())
        val output = File(dir, "out.cbz")

        ComicArchiveExtractor.extract(source, output)

        assertFalse(output.exists())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
    }

    private fun writeSevenZip(target: File, vararg entries: Pair<String, ByteArray?>) {
        target.parentFile?.mkdirs()
        SevenZOutputFile(target).use { output ->
            entries.forEach { (name, content) ->
                val entry = SevenZArchiveEntry()
                entry.name = name
                if (content == null) {
                    entry.isDirectory = true
                    output.putArchiveEntry(entry)
                    output.closeArchiveEntry()
                } else {
                    output.putArchiveEntry(entry)
                    output.write(content)
                    output.closeArchiveEntry()
                }
            }
        }
    }

    private fun writeEncryptedSevenZip(target: File, vararg entries: Pair<String, ByteArray>, password: String) {
        target.parentFile?.mkdirs()
        SevenZOutputFile(target, password.toCharArray()).use { output ->
            entries.forEach { (name, content) ->
                val entry = SevenZArchiveEntry()
                entry.name = name
                output.putArchiveEntry(entry)
                output.write(content)
                output.closeArchiveEntry()
            }
        }
    }

    private fun jpegBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        out.write("fake jpeg body".toByteArray())
        return out.toByteArray()
    }

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.write("fake png body".toByteArray())
        return out.toByteArray()
    }

    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()
}
