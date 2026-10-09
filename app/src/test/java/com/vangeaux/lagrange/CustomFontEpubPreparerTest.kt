package com.vangeaux.lagrange

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFontEpubPreparerTest {
    @Test
    fun `streams ordinary entries and injects the custom font`() = withFixture("success") { fixture ->
        val image = ByteArray(1_300) { (it % 251).toByte() }
        writeEpub(fixture.source, extraEntries = listOf("OEBPS/Images/cover.jpg" to image))

        val prepared = fixture.prepare(
            TEST_LIMITS.copy(
                maxEntryBytes = 2_048,
                maxTotalBytes = 8_192
            )
        )

        ZipFile(prepared).use { zip ->
            val firstEntry = zip.entries().nextElement()
            assertTrue(firstEntry.name == "mimetype" && firstEntry.method == ZipEntry.STORED)
            assertArrayEquals(MIMETYPE.toByteArray(), zip.read("mimetype"))
            assertArrayEquals(image, zip.read("OEBPS/Images/cover.jpg"))
            assertArrayEquals(FONT_BYTES, zip.read(FONT_PATH))
            assertTrue(zip.readText("OEBPS/content.opf").contains("bookorbit-custom-font"))
            assertTrue(zip.readText("OEBPS/Text/chapter.xhtml").contains("bookorbit-custom-font"))
            assertTrue(zip.entries().toList().last().name == MARKER_PATH)
        }
        assertFalse(fixture.directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `rejects an archive with too many entries`() = withFixture("entry-count") { fixture ->
        writeEpub(fixture.source, extraEntries = listOf("extra.bin" to byteArrayOf(1)))

        assertThrows(IllegalStateException::class.java) {
            fixture.prepare(TEST_LIMITS.copy(maxEntries = 4))
        }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects an oversized individual entry`() = withFixture("entry-size") { fixture ->
        writeEpub(fixture.source, extraEntries = listOf("large.bin" to ByteArray(1_025)))
        replaceDeclaredUncompressedSize(fixture.source, "large.bin", 1)

        assertThrows(IllegalStateException::class.java) { fixture.prepare() }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects excessive aggregate uncompressed data`() = withFixture("aggregate-size") { fixture ->
        writeEpub(
            fixture.source,
            extraEntries = listOf(
                "first.bin" to ByteArray(400),
                "second.bin" to ByteArray(400)
            )
        )
        replaceDeclaredUncompressedSize(fixture.source, "first.bin", 1)
        replaceDeclaredUncompressedSize(fixture.source, "second.bin", 1)
        val limits = TEST_LIMITS.copy(
            maxEntryBytes = 512,
            maxTotalBytes = 900,
            maxModifiedResourceBytes = 512
        )

        assertThrows(IllegalStateException::class.java) { fixture.prepare(limits) }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects an html resource whose modified form exceeds its buffer bound`() = withFixture("html-size") { fixture ->
        val oversizedAfterInjection = "<html><head></head><body>${"x".repeat(650)}</body></html>"
        writeEpub(fixture.source, chapter = oversizedAfterInjection)

        assertThrows(IllegalStateException::class.java) {
            fixture.prepare(TEST_LIMITS.copy(maxModifiedResourceBytes = 800))
        }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects package xml whose injected form and container whose source cross their bounds`() {
        withFixture("package-injection-size") { fixture ->
            val padding = " ".repeat(500 - DEFAULT_PACKAGE.toByteArray().size)
            val packageAtLimit = DEFAULT_PACKAGE.replace("</manifest>", "$padding</manifest>")
            assertTrue(packageAtLimit.toByteArray().size <= 512)
            writeEpub(fixture.source, packageXml = packageAtLimit)
            assertThrows(IllegalStateException::class.java) {
                fixture.prepare(TEST_LIMITS.copy(maxModifiedResourceBytes = 512))
            }
            fixture.assertNoOutputOrStaging()
        }
        withFixture("container-size") { fixture ->
            writeEpub(fixture.source, containerPadding = " ".repeat(600))
            assertThrows(IllegalStateException::class.java) { fixture.prepare() }
            fixture.assertNoOutputOrStaging()
        }
    }

    @Test
    fun `rejects unsafe and non-normalized entry paths`() {
        listOf("../escape.bin", "/absolute.bin", "C:/windows.bin", "C:relative.bin", "OPS/./ambiguous.bin", "OPS//empty.bin").forEachIndexed { index, path ->
            withFixture("unsafe-$index") { fixture ->
                writeEpub(fixture.source, extraEntries = listOf(path to byteArrayOf(1)))

                assertThrows(IllegalStateException::class.java) { fixture.prepare() }

                fixture.assertNoOutputOrStaging()
            }
        }
    }

    @Test
    fun `rejects an unsafe package path from the container`() = withFixture("unsafe-package") { fixture ->
        writeEpub(fixture.source, packagePath = "../OEBPS/content.opf")

        assertThrows(IllegalStateException::class.java) { fixture.prepare() }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects duplicate zip entries`() = withFixture("duplicate") { fixture ->
        writeEpubWithDuplicateChapter(fixture.source)

        assertThrows(IllegalStateException::class.java) { fixture.prepare() }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects directory and file name collisions`() = withFixture("directory-file-collision") { fixture ->
        writeEpubWithDirectoryFileCollision(fixture.source)

        assertThrows(IllegalStateException::class.java) { fixture.prepare() }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `rejects oversized central-directory metadata before opening entries`() = withFixture("central-directory") { fixture ->
        writeEpub(fixture.source)
        replaceEocdCentralDirectorySize(fixture.source, 100_000)

        assertThrows(IllegalStateException::class.java) {
            fixture.prepare(TEST_LIMITS.copy(maxCentralDirectoryBytes = 4_096))
        }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `accepts a bounded zip64 source`() = withFixture("zip64") { fixture ->
        writeZip64Epub(fixture.source)

        val prepared = fixture.prepare()

        assertTrue(prepared.isFile)
        ZipFile(prepared).use { zip -> assertTrue(zip.getEntry(FONT_PATH) != null) }
    }

    @Test
    fun `rejects zip64 records that overlap their locator or central directory`() {
        withFixture("zip64-record-overlap") { fixture ->
            writeZip64Epub(fixture.source)
            alterZip64RecordPayloadSize(fixture.source, 1)
            assertThrows(IllegalStateException::class.java) { fixture.prepare() }
            fixture.assertNoOutputOrStaging()
        }
        withFixture("zip64-central-overlap") { fixture ->
            writeZip64Epub(fixture.source)
            moveZip64CentralDirectoryEndIntoRecord(fixture.source)
            assertThrows(IllegalStateException::class.java) { fixture.prepare() }
            fixture.assertNoOutputOrStaging()
        }
    }

    @Test
    fun `accepts exact configured entry count size and central-directory limit`() = withFixture("exact-limits") { fixture ->
        writeEpub(fixture.source)
        val centralDirectorySize = readEocdCentralDirectorySize(fixture.source)

        val prepared = fixture.prepare(
            TEST_LIMITS.copy(
                maxEntries = 4,
                maxCentralDirectoryBytes = centralDirectorySize
            )
        )

        assertTrue(prepared.isFile)
    }

    @Test
    fun `accepts an ordinary entry at the exact individual limit`() = withFixture("exact-entry-size") { fixture ->
        writeEpub(fixture.source, extraEntries = listOf("exact.bin" to ByteArray(1_024)))

        val prepared = fixture.prepare()

        ZipFile(prepared).use { zip -> assertTrue(zip.read("exact.bin").size == 1_024) }
    }

    @Test
    fun `rejects source collisions with generated font and completion paths`() {
        listOf(FONT_PATH, MARKER_PATH).forEachIndexed { index, path ->
            withFixture("generated-collision-$index") { fixture ->
                writeEpub(fixture.source, extraEntries = listOf(path to byteArrayOf(1, 2, 3)))
                assertThrows(IllegalStateException::class.java) { fixture.prepare() }
                fixture.assertNoOutputOrStaging()
            }
        }
    }

    @Test
    fun `rejects a missing or invalid epub mimetype`() {
        listOf(null, "application/zip").forEachIndexed { index, mimetype ->
            withFixture("mimetype-$index") { fixture ->
                val container = "<container><rootfile full-path=\"OEBPS/content.opf\"/></container>"
                writeZip(
                    fixture.source,
                    buildList {
                        if (mimetype != null) add("mimetype" to mimetype.toByteArray())
                        add("META-INF/container.xml" to container.toByteArray())
                        add("OEBPS/content.opf" to DEFAULT_PACKAGE.toByteArray())
                        add("OEBPS/Text/chapter.xhtml" to DEFAULT_CHAPTER.toByteArray())
                    }
                )

                assertThrows(IllegalStateException::class.java) { fixture.prepare() }

                fixture.assertNoOutputOrStaging()
            }
        }
    }

    @Test
    fun `failed conversion cleans staging and does not publish partial output`() = withFixture("failed-staging") { fixture ->
        writeEpub(fixture.source, packageXml = "<package><manifest>")

        assertThrows(IllegalStateException::class.java) { fixture.prepare() }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `an incomplete cached output is removed when rebuilding also fails`() = withFixture("bad-cache") { fixture ->
        requireNotNull(fixture.output.parentFile).mkdirs()
        fixture.output.writeText("not a zip")
        writeZip(fixture.source, listOf("mimetype" to MIMETYPE.toByteArray()))

        assertThrows(IllegalStateException::class.java) { fixture.prepare() }

        fixture.assertNoOutputOrStaging()
    }

    @Test
    fun `an incomplete cached output is replaced only after a successful conversion`() = withFixture("replace-cache") { fixture ->
        requireNotNull(fixture.output.parentFile).mkdirs()
        fixture.output.writeText("not a zip")
        writeEpub(fixture.source)

        val prepared = fixture.prepare()

        assertTrue(prepared.isFile)
        ZipFile(prepared).use { zip ->
            assertTrue(zip.getEntry(FONT_PATH) != null)
            assertTrue(zip.readText("OEBPS/content.opf").contains("bookorbit-custom-font"))
        }
    }

    @Test
    fun `an old structurally valid cache without a completion marker is rebuilt`() = withFixture("unmarked-cache") { fixture ->
        writeEpub(fixture.source)
        fixture.prepare()
        removeZipEntry(fixture.output, MARKER_PATH)

        val rebuilt = fixture.prepare()

        ZipFile(rebuilt).use { zip ->
            assertTrue(zip.getEntry(MARKER_PATH) != null)
        }
    }

    @Test
    fun `a marked cache hit does not reopen the source archive`() = withFixture("cache-hit") { fixture ->
        writeEpub(fixture.source)
        val prepared = fixture.prepare()
        fixture.source.writeText("source is no longer a zip")

        val cached = fixture.prepare()

        assertTrue(cached == prepared)
        ZipFile(cached).use { zip -> assertTrue(zip.getEntry(MARKER_PATH) != null) }
    }

    @Test
    fun `preparation failures become reader errors while cancellation still propagates`() {
        val rejected = captureCustomFontEpubPreparation {
            throw IllegalStateException("malformed EPUB")
        }
        assertTrue(rejected is CustomFontEpubPreparationResult.Rejected)
        assertTrue((rejected as CustomFontEpubPreparationResult.Rejected).message.contains("could not be prepared"))

        assertThrows(CancellationException::class.java) {
            captureCustomFontEpubPreparation { throw CancellationException("reader closed") }
        }
    }

    private data class Fixture(
        val directory: File,
        val source: File,
        val output: File,
        val font: File
    ) {
        fun prepare(limits: CustomFontEpubLimits = TEST_LIMITS): File = prepareCustomFontEpubCopy(
            source = source,
            output = output,
            record = FONT_RECORD,
            fontFile = font,
            limits = limits
        )

        fun assertNoOutputOrStaging() {
            assertFalse(output.exists())
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        }
    }

    private fun withFixture(name: String, block: (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("custom-font-epub-$name-").toFile()
        try {
            val fixture = Fixture(
                directory = directory,
                source = File(directory, "source.epub"),
                output = File(directory, "cache/prepared.epub"),
                font = File(directory, "font.ttf").apply { writeBytes(FONT_BYTES) }
            )
            block(fixture)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeEpub(
        file: File,
        packagePath: String = "OEBPS/content.opf",
        packageXml: String = DEFAULT_PACKAGE,
        chapter: String = DEFAULT_CHAPTER,
        containerPadding: String = "",
        extraEntries: List<Pair<String, ByteArray>> = emptyList()
    ) {
        val container = """
            <?xml version="1.0"?>
            <container><rootfiles><rootfile full-path="$packagePath"/></rootfiles>$containerPadding</container>
        """.trimIndent()
        writeZip(
            file,
            buildList {
                add("mimetype" to MIMETYPE.toByteArray())
                add("META-INF/container.xml" to container.toByteArray())
                add("OEBPS/content.opf" to packageXml.toByteArray())
                add("OEBPS/Text/chapter.xhtml" to chapter.toByteArray())
                addAll(extraEntries)
            }
        )
    }

    private fun writeZip(file: File, entries: List<Pair<String, ByteArray>>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { output ->
            entries.forEach { (path, bytes) ->
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun writeEpubWithDuplicateChapter(file: File) {
        val container = "<container><rootfile full-path=\"OEBPS/content.opf\"/></container>"
        val entries = listOf(
            "mimetype" to MIMETYPE.toByteArray(),
            "META-INF/container.xml" to container.toByteArray(),
            "OEBPS/content.opf" to DEFAULT_PACKAGE.toByteArray(),
            "OEBPS/Text/chapter.xhtml" to DEFAULT_CHAPTER.toByteArray(),
            "OEBPS/Text/chapter.xhtml" to "duplicate".toByteArray()
        )
        ZipArchiveOutputStream(file).use { output ->
            entries.forEach { (path, bytes) ->
                output.putArchiveEntry(ZipArchiveEntry(path))
                output.write(bytes)
                output.closeArchiveEntry()
            }
        }
    }

    private fun writeEpubWithDirectoryFileCollision(file: File) {
        val container = "<container><rootfile full-path=\"OEBPS/content.opf\"/></container>"
        val entries = listOf(
            "mimetype" to MIMETYPE.toByteArray(),
            "META-INF/container.xml" to container.toByteArray(),
            "OEBPS" to byteArrayOf(1),
            "OEBPS/" to byteArrayOf(),
            "OEBPS/content.opf" to DEFAULT_PACKAGE.toByteArray(),
            "OEBPS/Text/chapter.xhtml" to DEFAULT_CHAPTER.toByteArray()
        )
        ZipArchiveOutputStream(file).use { output ->
            entries.forEach { (path, bytes) ->
                output.putArchiveEntry(ZipArchiveEntry(path))
                output.write(bytes)
                output.closeArchiveEntry()
            }
        }
    }

    private fun writeZip64Epub(file: File) {
        val container = "<container><rootfile full-path=\"OEBPS/content.opf\"/></container>"
        val entries = listOf(
            "mimetype" to MIMETYPE.toByteArray(),
            "META-INF/container.xml" to container.toByteArray(),
            "OEBPS/content.opf" to DEFAULT_PACKAGE.toByteArray(),
            "OEBPS/Text/chapter.xhtml" to DEFAULT_CHAPTER.toByteArray()
        )
        ZipArchiveOutputStream(file).use { output ->
            output.setUseZip64(Zip64Mode.Always)
            entries.forEach { (path, bytes) ->
                output.putArchiveEntry(ZipArchiveEntry(path))
                output.write(bytes)
                output.closeArchiveEntry()
            }
        }
        forceClassicEocdToUseZip64(file)
    }

    private fun forceClassicEocdToUseZip64(file: File) {
        val bytes = file.readBytes()
        val endOffset = findSignatureBackwards(bytes, 0x50, 0x4b, 0x05, 0x06)
        writeLittleEndianShort(bytes, endOffset + 8, 0xffff)
        writeLittleEndianShort(bytes, endOffset + 10, 0xffff)
        writeLittleEndianInt(bytes, endOffset + 12, -1)
        writeLittleEndianInt(bytes, endOffset + 16, -1)
        file.writeBytes(bytes)
    }

    private fun replaceDeclaredUncompressedSize(file: File, targetPath: String, replacement: Int) {
        val bytes = file.readBytes()
        var offset = 0
        var replaced = false
        while (offset <= bytes.size - 46) {
            if (
                bytes[offset] == 0x50.toByte() &&
                bytes[offset + 1] == 0x4b.toByte() &&
                bytes[offset + 2] == 0x01.toByte() &&
                bytes[offset + 3] == 0x02.toByte()
            ) {
                val nameLength = littleEndianShort(bytes, offset + 28)
                val extraLength = littleEndianShort(bytes, offset + 30)
                val commentLength = littleEndianShort(bytes, offset + 32)
                val nameStart = offset + 46
                val nameEnd = nameStart + nameLength
                check(nameEnd <= bytes.size)
                val name = bytes.copyOfRange(nameStart, nameEnd).decodeToString()
                if (name == targetPath) {
                    writeLittleEndianInt(bytes, offset + 24, replacement)
                    replaced = true
                }
                offset = nameEnd + extraLength + commentLength
            } else {
                offset++
            }
        }
        check(replaced) { "Missing central-directory entry for $targetPath" }
        file.writeBytes(bytes)
    }

    private fun replaceEocdCentralDirectorySize(file: File, replacement: Int) {
        val bytes = file.readBytes()
        val endOffset = findSignatureBackwards(bytes, 0x50, 0x4b, 0x05, 0x06)
        writeLittleEndianInt(bytes, endOffset + 12, replacement)
        file.writeBytes(bytes)
    }

    private fun readEocdCentralDirectorySize(file: File): Long {
        val bytes = file.readBytes()
        val endOffset = findSignatureBackwards(bytes, 0x50, 0x4b, 0x05, 0x06)
        return littleEndianInt(bytes, endOffset + 12)
    }

    private fun alterZip64RecordPayloadSize(file: File, delta: Long) {
        val bytes = file.readBytes()
        val locatorOffset = findSignatureBackwards(bytes, 0x50, 0x4b, 0x06, 0x07)
        val recordOffset = littleEndianLong(bytes, locatorOffset + 8).toInt()
        writeLittleEndianLong(bytes, recordOffset + 4, littleEndianLong(bytes, recordOffset + 4) + delta)
        file.writeBytes(bytes)
    }

    private fun moveZip64CentralDirectoryEndIntoRecord(file: File) {
        val bytes = file.readBytes()
        val locatorOffset = findSignatureBackwards(bytes, 0x50, 0x4b, 0x06, 0x07)
        val recordOffset = littleEndianLong(bytes, locatorOffset + 8)
        val centralOffset = littleEndianLong(bytes, recordOffset.toInt() + 48)
        writeLittleEndianLong(bytes, recordOffset.toInt() + 40, recordOffset - centralOffset + 1L)
        file.writeBytes(bytes)
    }

    private fun removeZipEntry(file: File, removedPath: String) {
        val retained = ZipFile(file).use { zip ->
            zip.entries().toList().filterNot { it.name == removedPath }.map { entry ->
                entry.name to zip.getInputStream(entry).use { it.readBytes() }
            }
        }
        ZipOutputStream(file.outputStream().buffered()).use { output ->
            retained.forEach { (path, bytes) ->
                val entry = ZipEntry(path)
                if (path == "mimetype") {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                output.putNextEntry(entry)
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun findSignatureBackwards(bytes: ByteArray, first: Int, second: Int, third: Int, fourth: Int): Int {
        for (offset in bytes.size - 4 downTo 0) {
            if (
                bytes[offset] == first.toByte() &&
                bytes[offset + 1] == second.toByte() &&
                bytes[offset + 2] == third.toByte() &&
                bytes[offset + 3] == fourth.toByte()
            ) return offset
        }
        error("Missing ZIP signature")
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Long =
        (littleEndianShort(bytes, offset).toLong() or
            (littleEndianShort(bytes, offset + 2).toLong() shl 16)) and 0xffff_ffffL

    private fun littleEndianLong(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        repeat(8) { index -> result = result or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8)) }
        return result
    }

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeLittleEndianShort(bytes: ByteArray, offset: Int, value: Int) {
        repeat(2) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeLittleEndianLong(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun ZipFile.read(path: String): ByteArray =
        getInputStream(requireNotNull(getEntry(path))).use { it.readBytes() }

    private fun ZipFile.readText(path: String): String = read(path).decodeToString()

    private companion object {
        const val MIMETYPE = "application/epub+zip"
        const val FONT_PATH = "bookorbit-fonts/test-font.ttf"
        const val MARKER_PATH = "META-INF/bookorbit-custom-font.complete"
        val FONT_BYTES = byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val FONT_RECORD = CustomFontRecord("test-font.ttf", "Test font", "bookorbit-custom-test")
        val TEST_LIMITS = CustomFontEpubLimits(
            maxEntries = 16,
            maxEntryBytes = 1_024,
            maxTotalBytes = 4_096,
            maxModifiedResourceBytes = 1_024,
            maxContainerBytes = 512,
            maxFontBytes = 128,
            maxPathLength = 256
        )
        const val DEFAULT_PACKAGE = """
            <?xml version="1.0"?>
            <package><manifest><item id="chapter" href="Text/chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine/></package>
        """
        const val DEFAULT_CHAPTER = "<html><head></head><body><p>Hello</p></body></html>"
    }
}
