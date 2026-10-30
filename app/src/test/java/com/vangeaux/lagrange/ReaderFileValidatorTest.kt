package com.vangeaux.lagrange

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

    @Test
    fun `comic validator preserves rar and seven zip archives for server page extraction`() {
        val dir = Files.createTempDirectory("reader-file-validator-comic-archives").toFile()
        val rar4 = File(dir, "valid-rar4.cbr").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x01))
        }
        val rar5 = File(dir, "valid-rar5.cbr").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0x01))
        }
        val sevenZip = File(dir, "valid.cb7").apply {
            writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x01))
        }

        listOf(rar4, rar5, sevenZip).forEach { archive ->
            assertTrue(ReaderFileValidator.isReadable(MediaKind.COMIC, archive))
            assertFalse(ReaderFileValidator.canRenderComicLocally(archive))
        }
    }

    @Test
    fun `stream validators accept portable pdf epub and comic containers`() {
        val pdf = "%PDF-1.7\nbody".toByteArray()
        assertTrue(
            ReaderFileValidator.isReadable(MediaKind.PDF, pdf.size.toLong()) {
                ByteArrayInputStream(pdf)
            }
        )

        val epub = zipBytes("META-INF/container.xml" to "<container/>")
        assertTrue(
            ReaderFileValidator.isReadable(MediaKind.EPUB, epub.size.toLong()) {
                ByteArrayInputStream(epub)
            }
        )

        val comic = zipBytes("pages/001.jpg" to "image")
        assertTrue(
            ReaderFileValidator.isReadable(MediaKind.COMIC, comic.size.toLong()) {
                ByteArrayInputStream(comic)
            }
        )

    }

    @Test
    fun `stream epub validator tolerates stored directory data descriptors`() {
        val epub = malformedEpubWithStoredDirectoryDescriptor()

        assertTrue(
            ReaderFileValidator.isReadable(MediaKind.EPUB, epub.size.toLong()) {
                ByteArrayInputStream(epub)
            }
        )
    }

    @Test
    fun `stream zip validation stops before expanding an oversized leading entry`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("oversized.bin"))
            val block = ByteArray(1024)
            repeat(32 * 1024 + 1) { zip.write(block) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container/>".toByteArray())
            zip.closeEntry()
        }
        val archive = output.toByteArray()

        assertFalse(
            ReaderFileValidator.isReadable(MediaKind.EPUB, archive.size.toLong()) {
                ByteArrayInputStream(archive)
            }
        )
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

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { output ->
                entries.forEach { (name, content) ->
                    output.putNextEntry(ZipEntry(name))
                    output.write(content.toByteArray())
                    output.closeEntry()
                }
            }
        }.toByteArray()

    private fun malformedEpubWithStoredDirectoryDescriptor(): ByteArray =
        ByteArrayOutputStream().also { output ->
            localEntry(output, "mimetype", 0, 0, "application/epub+zip".toByteArray())
            localEntry(output, "META-INF/", 0x08, 0, byteArrayOf(), dataDescriptor = true)
            localEntry(output, "META-INF/container.xml", 0, 0, "<container/>".toByteArray())
        }.toByteArray()

    private fun localEntry(
        output: ByteArrayOutputStream,
        name: String,
        flags: Int,
        method: Int,
        data: ByteArray,
        dataDescriptor: Boolean = false
    ) {
        val nameBytes = name.toByteArray()
        output.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        writeLe16(output, 20)
        writeLe16(output, flags)
        writeLe16(output, method)
        writeLe16(output, 0)
        writeLe16(output, 0)
        writeLe32(output, 0)
        writeLe32(output, if (dataDescriptor) 0 else data.size)
        writeLe32(output, if (dataDescriptor) 0 else data.size)
        writeLe16(output, nameBytes.size)
        writeLe16(output, 0)
        output.write(nameBytes)
        output.write(data)
        if (dataDescriptor) {
            output.write(byteArrayOf(0x50, 0x4B, 0x07, 0x08))
            writeLe32(output, 0)
            writeLe32(output, 0)
            writeLe32(output, 0)
        }
    }

    private fun writeLe16(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    private fun writeLe32(output: ByteArrayOutputStream, value: Int) {
        writeLe16(output, value)
        writeLe16(output, value ushr 16)
    }
}
