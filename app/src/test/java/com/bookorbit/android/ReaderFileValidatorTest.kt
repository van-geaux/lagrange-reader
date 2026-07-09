package com.bookorbit.android

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFileValidatorTest {
    @Test
    fun `pdf validator accepts pdf header and rejects garbage`() {
        val dir = Files.createTempDirectory("reader-file-validator-pdf").toFile()
        val valid = File(dir, "valid.pdf").apply { writeText("%PDF-1.7\nbody") }
        val invalid = File(dir, "invalid.pdf").apply { writeText("not a pdf") }

        assertTrue(ReaderFileValidator.isReadable(MediaKind.PDF, valid))
        assertFalse(ReaderFileValidator.isReadable(MediaKind.PDF, invalid))
    }

    @Test
    fun `epub validator requires container xml`() {
        val dir = Files.createTempDirectory("reader-file-validator-epub").toFile()
        val valid = File(dir, "valid.epub")
        val invalid = File(dir, "invalid.epub")

        zip(valid,
            "META-INF/container.xml" to "<container/>",
            "OPS/chapter1.xhtml" to "<html/>"
        )
        zip(invalid, "OPS/chapter1.xhtml" to "<html/>")

        assertTrue(ReaderFileValidator.isReadable(MediaKind.EPUB, valid))
        assertFalse(ReaderFileValidator.isReadable(MediaKind.EPUB, invalid))
    }

    @Test
    fun `comic validator requires at least one image entry`() {
        val dir = Files.createTempDirectory("reader-file-validator-comic").toFile()
        val valid = File(dir, "valid.cbz")
        val invalid = File(dir, "invalid.cbz")

        zip(valid, "pages/001.jpg" to "fake image")
        zip(invalid, "notes/readme.txt" to "not a page")

        assertTrue(ReaderFileValidator.isReadable(MediaKind.COMIC, valid))
        assertFalse(ReaderFileValidator.isReadable(MediaKind.COMIC, invalid))
    }

    private fun zip(target: File, vararg entries: Pair<String, String>) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
    }
}
