package com.vangeaux.lagrange

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

private const val CUSTOM_FONT_EPUB_MAX_ENTRIES = 10_000
private const val CUSTOM_FONT_EPUB_MAX_ENTRY_BYTES = 64L * 1024 * 1024
private const val CUSTOM_FONT_EPUB_MAX_TOTAL_BYTES = 512L * 1024 * 1024
private const val CUSTOM_FONT_EPUB_MAX_MODIFIED_RESOURCE_BYTES = 8L * 1024 * 1024
private const val CUSTOM_FONT_EPUB_MAX_CONTAINER_BYTES = 1024L * 1024
private const val CUSTOM_FONT_EPUB_MAX_FONT_BYTES = 20L * 1024 * 1024
private const val CUSTOM_FONT_EPUB_MAX_PATH_LENGTH = 1_024
private const val CUSTOM_FONT_EPUB_MAX_CENTRAL_DIRECTORY_BYTES = 16L * 1024 * 1024
private const val CUSTOM_FONT_EPUB_MAX_FAMILY_LENGTH = 256
private const val CUSTOM_FONT_EPUB_MAX_MIMETYPE_BYTES = 256L
private const val CUSTOM_FONT_EPUB_MARKER_PATH = "META-INF/bookorbit-custom-font.complete"

internal data class CustomFontEpubLimits(
    val maxEntries: Int = CUSTOM_FONT_EPUB_MAX_ENTRIES,
    val maxEntryBytes: Long = CUSTOM_FONT_EPUB_MAX_ENTRY_BYTES,
    val maxTotalBytes: Long = CUSTOM_FONT_EPUB_MAX_TOTAL_BYTES,
    val maxModifiedResourceBytes: Long = CUSTOM_FONT_EPUB_MAX_MODIFIED_RESOURCE_BYTES,
    val maxContainerBytes: Long = CUSTOM_FONT_EPUB_MAX_CONTAINER_BYTES,
    val maxFontBytes: Long = CUSTOM_FONT_EPUB_MAX_FONT_BYTES,
    val maxPathLength: Int = CUSTOM_FONT_EPUB_MAX_PATH_LENGTH,
    val maxCentralDirectoryBytes: Long = CUSTOM_FONT_EPUB_MAX_CENTRAL_DIRECTORY_BYTES
) {
    init {
        require(maxEntries > 0 && maxEntries < Int.MAX_VALUE - 1)
        require(maxEntryBytes > 0L)
        require(maxTotalBytes >= maxEntryBytes)
        require(maxModifiedResourceBytes > 0L && maxModifiedResourceBytes <= maxEntryBytes)
        require(maxContainerBytes > 0L && maxContainerBytes <= maxModifiedResourceBytes)
        require(maxFontBytes > 0L && maxFontBytes <= maxEntryBytes)
        require(maxPathLength > 0)
        require(maxCentralDirectoryBytes > 0L)
    }
}

private data class SourceEntry(
    val entry: ZipEntry,
    val path: String
)

internal sealed interface CustomFontEpubPreparationResult {
    data class Prepared(val file: File) : CustomFontEpubPreparationResult
    data class Rejected(val message: String) : CustomFontEpubPreparationResult
}

/** Creates a cached EPUB copy with an imported font injected into every reflowable resource. */
internal fun prepareEpubWithCustomFont(
    context: Context,
    source: File,
    record: CustomFontRecord,
    fontFile: File
): File {
    require(source.isFile && fontFile.isFile)
    val key = listOf(source.absolutePath, source.length(), source.lastModified(), fontFile.length(), fontFile.lastModified(), record.familyName)
        .joinToString("|")
        .hashCode()
        .toUInt()
        .toString(16)
    val output = File(context.cacheDir, "reader-custom-font/$key.epub")
    return prepareCustomFontEpubCopy(source, output, record, fontFile)
}

internal fun prepareEpubWithCustomFontSafely(
    context: Context,
    source: File,
    record: CustomFontRecord,
    fontFile: File
): CustomFontEpubPreparationResult = captureCustomFontEpubPreparation {
    prepareEpubWithCustomFont(context, source, record, fontFile)
}

internal fun captureCustomFontEpubPreparation(
    prepare: () -> File
): CustomFontEpubPreparationResult = try {
    CustomFontEpubPreparationResult.Prepared(prepare())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    CustomFontEpubPreparationResult.Rejected(
        "This EPUB could not be prepared with the selected custom font."
    )
}

internal fun prepareCustomFontEpubCopy(
    source: File,
    output: File,
    record: CustomFontRecord,
    fontFile: File,
    limits: CustomFontEpubLimits = CustomFontEpubLimits()
): File {
    require(source.isFile) { "The source EPUB is missing." }
    require(fontFile.isFile) { "The custom font is missing." }
    require(record.fileName.isNotBlank() && record.fileName == record.fileName.substringAfterLast('/')) {
        "The custom font filename is invalid."
    }
    require(!record.fileName.contains('\\') && !record.fileName.contains('\u0000')) {
        "The custom font filename is invalid."
    }
    require(record.familyName.isNotBlank() && record.familyName.length <= CUSTOM_FONT_EPUB_MAX_FAMILY_LENGTH) {
        "The custom font family is invalid."
    }
    require(fontFile.length() in 1..limits.maxFontBytes) { "The custom font is empty or too large." }

    val fontPath = normalizeArchivePath("bookorbit-fonts/${record.fileName}", false, limits.maxPathLength)
    if (output.isFile) {
        if (isCompletePreparedEpub(output, fontPath, limits)) return output
        check(output.delete()) { "Could not remove an incomplete custom-font EPUB copy." }
    }
    output.parentFile?.let { parent ->
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the custom-font EPUB cache directory." }
    }

    val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.tmp")
    try {
        val expectedOutputBytes = writePreparedEpub(source, temporary, record, fontFile, fontPath, limits)
        check(isCompletePreparedEpub(temporary, fontPath, limits, expectedOutputBytes)) {
            "The custom-font EPUB copy was incomplete."
        }
        moveCompletedOutput(temporary, output)
    } finally {
        temporary.delete()
    }
    return output
}

private fun writePreparedEpub(
    source: File,
    temporary: File,
    record: CustomFontRecord,
    fontFile: File,
    fontPath: String,
    limits: CustomFontEpubLimits
): Long {
    validateZipCentralDirectory(source, limits, limits.maxEntries)
    return ZipFile(source).use { zip ->
    val entries = inspectEntries(zip, limits, limits.maxEntries)
    check(entries.none { it.path == fontPath || it.path == CUSTOM_FONT_EPUB_MARKER_PATH }) {
        "The EPUB already contains a generated custom-font entry."
    }

    val mimetypeEntry = entries.singleOrNull { it.path == "mimetype" }
        ?: error("EPUB mimetype is missing")
    val mimetypeBytes = readEntryBytes(zip, mimetypeEntry, CUSTOM_FONT_EPUB_MAX_MIMETYPE_BYTES)
    check(mimetypeBytes.contentEquals(EPUB_MIMETYPE_BYTES)) { "EPUB mimetype is invalid" }

    val containerEntry = entries.singleOrNull { it.path == "META-INF/container.xml" }
        ?: error("EPUB container.xml is missing")
    val containerText = readEntryBytes(zip, containerEntry, limits.maxContainerBytes)
        .toString(StandardCharsets.UTF_8)
    val rawPackagePath = Regex("full-path\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']")
        .find(containerText)?.groupValues?.get(1)
        ?: error("EPUB package path is missing")
    val packagePath = normalizeArchivePath(rawPackagePath, false, limits.maxPathLength)
    check(entries.count { it.path == packagePath } == 1) { "EPUB package is missing" }

    val inputBudget = ArchiveReadBudget(limits)
    var outputBytes = mimetypeBytes.size.toLong()
    ZipOutputStream(temporary.outputStream().buffered()).use { out ->
        inputBudget.add(mimetypeEntry.path, 0L, mimetypeBytes.size)
        val outputMimetype = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetypeBytes.size.toLong()
            crc = CRC32().apply { update(mimetypeBytes) }.value
        }
        out.putNextEntry(outputMimetype)
        out.write(mimetypeBytes)
        out.closeEntry()
        entries.forEach { sourceEntry ->
            val path = sourceEntry.path
            if (path == "mimetype") return@forEach
            val outputEntry = ZipEntry(path)
            when {
                path == packagePath -> {
                    val bytes = readEntryBytes(zip, sourceEntry, limits.maxModifiedResourceBytes, inputBudget)
                    val packageText = bytes.toString(StandardCharsets.UTF_8)
                    val packageDir = packagePath.substringBeforeLast('/', "")
                    val packageFontPath = relativePath(packageDir, fontPath)
                    val manifestItem = "<item id=\"bookorbit-custom-font\" href=\"${xmlEscape(packageFontPath)}\" media-type=\"${fontMediaType(record.fileName)}\"/>"
                    check(!packageText.contains("bookorbit-custom-font")) {
                        "The EPUB already defines the generated custom-font manifest item."
                    }
                    val manifestEnd = Regex("</manifest>", RegexOption.IGNORE_CASE)
                    check(manifestEnd.containsMatchIn(packageText)) { "EPUB package manifest is invalid" }
                    val updated = manifestEnd.replaceFirst(packageText, "$manifestItem</manifest>")
                        .toByteArray(StandardCharsets.UTF_8)
                    check(updated.size.toLong() <= limits.maxModifiedResourceBytes) {
                        "The modified EPUB package is too large."
                    }
                    out.putNextEntry(outputEntry)
                    out.write(updated)
                    out.closeEntry()
                    outputBytes = checkedAdd(outputBytes, updated.size.toLong(), "The prepared EPUB is too large.")
                }
                isHtmlResource(path) -> {
                    val bytes = readEntryBytes(zip, sourceEntry, limits.maxModifiedResourceBytes, inputBudget)
                    val html = bytes.toString(StandardCharsets.UTF_8)
                    val fontUrl = relativePath(path.substringBeforeLast('/', ""), fontPath)
                    val updated = injectFontStyle(html, fontUrl, record.familyName)
                        .toByteArray(StandardCharsets.UTF_8)
                    check(updated.size.toLong() <= limits.maxModifiedResourceBytes) {
                        "A modified EPUB HTML resource is too large."
                    }
                    out.putNextEntry(outputEntry)
                    out.write(updated)
                    out.closeEntry()
                    outputBytes = checkedAdd(outputBytes, updated.size.toLong(), "The prepared EPUB is too large.")
                }
                else -> {
                    out.putNextEntry(outputEntry)
                    val copied = zip.getInputStream(sourceEntry.entry).use { input ->
                        copyEntry(input, out, sourceEntry.path, inputBudget)
                    }
                    out.closeEntry()
                    outputBytes = checkedAdd(outputBytes, copied, "The prepared EPUB is too large.")
                }
            }
        }

        val fontEntry = ZipEntry(fontPath)
        out.putNextEntry(fontEntry)
        val fontBytes = fontFile.inputStream().buffered().use { input ->
            copyWithLimit(input, out, limits.maxFontBytes, "The custom font is too large.")
        }
        check(fontBytes > 0L) { "The custom font is empty." }
        out.closeEntry()
        outputBytes = checkedAdd(outputBytes, fontBytes, "The prepared EPUB is too large.")

        val markerEntry = ZipEntry(CUSTOM_FONT_EPUB_MARKER_PATH).apply {
            method = ZipEntry.STORED
            size = CUSTOM_FONT_EPUB_MARKER_BYTES.size.toLong()
            crc = CRC32().apply { update(CUSTOM_FONT_EPUB_MARKER_BYTES) }.value
        }
        out.putNextEntry(markerEntry)
        out.write(CUSTOM_FONT_EPUB_MARKER_BYTES)
        out.closeEntry()
        outputBytes = checkedAdd(
            outputBytes,
            CUSTOM_FONT_EPUB_MARKER_BYTES.size.toLong(),
            "The prepared EPUB is too large."
        )
    }
        outputBytes
    }
}

private fun inspectEntries(
    zip: ZipFile,
    limits: CustomFontEpubLimits,
    maxEntries: Int
): List<SourceEntry> {
    val result = ArrayList<SourceEntry>()
    val names = hashSetOf<String>()
    var declaredTotal = 0L
    var count = 0
    val iterator = zip.entries()
    while (iterator.hasMoreElements()) {
        val entry = iterator.nextElement()
        count++
        check(count <= maxEntries) { "The EPUB contains too many entries." }
        val normalized = normalizeArchivePath(entry.name, entry.isDirectory, limits.maxPathLength)
        val collisionKey = normalized.removeSuffix("/")
        check(names.add(collisionKey)) { "The EPUB contains a duplicate entry: $collisionKey" }
        if (entry.isDirectory) continue
        val declaredSize = entry.size
        if (declaredSize >= 0L) {
            check(declaredSize <= limits.maxEntryBytes) { "An EPUB entry is too large: $normalized" }
            declaredTotal = checkedAdd(declaredTotal, declaredSize, "The EPUB expands beyond the allowed size.")
            check(declaredTotal <= limits.maxTotalBytes) { "The EPUB expands beyond the allowed size." }
        }
        result += SourceEntry(entry, normalized)
    }
    return result
}

private class ArchiveReadBudget(private val limits: CustomFontEpubLimits) {
    private var totalBytes = 0L

    fun add(path: String, entryBytes: Long, count: Int): Long {
        val nextEntryBytes = checkedAdd(entryBytes, count.toLong(), "An EPUB entry is too large: $path")
        check(nextEntryBytes <= limits.maxEntryBytes) { "An EPUB entry is too large: $path" }
        totalBytes = checkedAdd(totalBytes, count.toLong(), "The EPUB expands beyond the allowed size.")
        check(totalBytes <= limits.maxTotalBytes) { "The EPUB expands beyond the allowed size." }
        return nextEntryBytes
    }
}

private fun readEntryBytes(
    zip: ZipFile,
    sourceEntry: SourceEntry,
    maxBytes: Long,
    budget: ArchiveReadBudget? = null
): ByteArray {
    val declaredSize = sourceEntry.entry.size
    check(declaredSize < 0L || declaredSize <= maxBytes) { "EPUB resource is too large: ${sourceEntry.path}" }
    val initialCapacity = when {
        declaredSize in 1..maxBytes -> declaredSize.coerceAtMost(8L * 1024).toInt()
        else -> 8 * 1024
    }
    val output = ByteArrayOutputStream(initialCapacity)
    zip.getInputStream(sourceEntry.entry).use { input ->
        val buffer = ByteArray(8 * 1024)
        var entryBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            entryBytes = budget?.add(sourceEntry.path, entryBytes, read)
                ?: checkedAdd(entryBytes, read.toLong(), "EPUB resource is too large: ${sourceEntry.path}")
            check(entryBytes <= maxBytes) { "EPUB resource is too large: ${sourceEntry.path}" }
            output.write(buffer, 0, read)
        }
    }
    return output.toByteArray()
}

private fun copyEntry(
    input: InputStream,
    output: OutputStream,
    path: String,
    budget: ArchiveReadBudget
): Long {
    val buffer = ByteArray(32 * 1024)
    var entryBytes = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return entryBytes
        entryBytes = budget.add(path, entryBytes, read)
        output.write(buffer, 0, read)
    }
}

private fun copyWithLimit(
    input: InputStream,
    output: OutputStream,
    limit: Long,
    message: String
): Long {
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return total
        total = checkedAdd(total, read.toLong(), message)
        check(total <= limit) { message }
        output.write(buffer, 0, read)
    }
}

private fun isCompletePreparedEpub(
    file: File,
    fontPath: String,
    limits: CustomFontEpubLimits,
    expectedBytes: Long? = null
): Boolean = runCatching {
    check(file.isFile && file.length() > 0L)
    validateZipCentralDirectory(
        file,
        limits,
        limits.maxEntries + 2,
        checkedAdd(
            limits.maxCentralDirectoryBytes,
            checkedMultiply(generatedCentralDirectoryAllowance(limits), 2L, "The EPUB ZIP directory limit is invalid."),
            "The EPUB ZIP directory limit is invalid."
        )
    )
    ZipFile(file).use { zip ->
        val entries = inspectEntries(
            zip,
            limits.copy(maxTotalBytes = outputValidationLimit(limits, expectedBytes)),
            limits.maxEntries + 2
        )
        val mimetypeEntry = entries.firstOrNull().takeIf { it?.path == "mimetype" }
            ?: error("Missing mimetype")
        check(mimetypeEntry.entry.method == ZipEntry.STORED)
        check(
            readEntryBytes(zip, mimetypeEntry, CUSTOM_FONT_EPUB_MAX_MIMETYPE_BYTES)
                .contentEquals(EPUB_MIMETYPE_BYTES)
        )
        val fontEntry = entries.singleOrNull { it.path == fontPath } ?: error("Missing font")
        check(fontEntry.entry.size in 1..limits.maxFontBytes)
        val markerEntry = entries.lastOrNull().takeIf { it?.path == CUSTOM_FONT_EPUB_MARKER_PATH }
            ?: error("Missing completion marker")
        check(markerEntry.entry.method == ZipEntry.STORED)
        check(
            readEntryBytes(zip, markerEntry, CUSTOM_FONT_EPUB_MARKER_BYTES.size.toLong())
                .contentEquals(CUSTOM_FONT_EPUB_MARKER_BYTES)
        )
        val containerEntry = entries.singleOrNull { it.path == "META-INF/container.xml" } ?: error("Missing container")
        val container = readEntryBytes(zip, containerEntry, limits.maxContainerBytes).toString(StandardCharsets.UTF_8)
        val rawPackagePath = Regex("full-path\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']")
            .find(container)?.groupValues?.get(1) ?: error("Missing package path")
        val packagePath = normalizeArchivePath(rawPackagePath, false, limits.maxPathLength)
        val packageEntry = entries.singleOrNull { it.path == packagePath } ?: error("Missing package")
        val packageText = readEntryBytes(zip, packageEntry, limits.maxModifiedResourceBytes)
            .toString(StandardCharsets.UTF_8)
        check(packageText.contains("bookorbit-custom-font"))

        if (expectedBytes != null) {
            var actualBytes = 0L
            entries.forEach { sourceEntry ->
                val entryLimit = if (sourceEntry.path == fontPath) limits.maxFontBytes else limits.maxEntryBytes
                var entryBytes = 0L
                val crc = CRC32()
                zip.getInputStream(sourceEntry.entry).use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        entryBytes = checkedAdd(entryBytes, read.toLong(), "The prepared EPUB is too large.")
                        check(entryBytes <= entryLimit) { "The prepared EPUB contains an oversized entry." }
                        actualBytes = checkedAdd(actualBytes, read.toLong(), "The prepared EPUB is too large.")
                        check(actualBytes <= expectedBytes) { "The prepared EPUB size does not match its bounds." }
                        crc.update(buffer, 0, read)
                    }
                }
                if (sourceEntry.path == fontPath) check(entryBytes > 0L) { "The prepared EPUB font is empty." }
                if (sourceEntry.entry.crc >= 0L) check(crc.value == sourceEntry.entry.crc) {
                    "The prepared EPUB contains a corrupt entry."
                }
            }
            check(actualBytes == expectedBytes)
        }
    }
}.isSuccess

private fun outputValidationLimit(limits: CustomFontEpubLimits, expectedBytes: Long?): Long {
    if (expectedBytes != null) return maxOf(expectedBytes, limits.maxEntryBytes, limits.maxFontBytes)
    val styleAllowance = checkedMultiply(
        limits.maxEntries.toLong(),
        injectionAllowancePerEntry(limits),
        "The EPUB limit is invalid."
    )
    return checkedAdd(
        checkedAdd(limits.maxTotalBytes, limits.maxFontBytes, "The EPUB limit is invalid."),
        styleAllowance,
        "The EPUB limit is invalid."
    )
}

private fun injectionAllowancePerEntry(limits: CustomFontEpubLimits): Long = checkedAdd(
    checkedMultiply(limits.maxPathLength.toLong(), 8L, "The EPUB limit is invalid."),
    checkedAdd(
        checkedMultiply(CUSTOM_FONT_EPUB_MAX_FAMILY_LENGTH.toLong(), 8L, "The EPUB limit is invalid."),
        1_024L,
        "The EPUB limit is invalid."
    ),
    "The EPUB limit is invalid."
)

private fun normalizeArchivePath(rawPath: String, directory: Boolean, maxLength: Int): String {
    check(rawPath.isNotBlank() && rawPath.length <= maxLength && !rawPath.contains('\u0000')) {
        "The EPUB contains an invalid entry path."
    }
    check(!rawPath.contains('\\') && !rawPath.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.matches(rawPath)) {
        "The EPUB contains an unsafe entry path: $rawPath"
    }
    val withoutTrailingSlash = if (directory) rawPath.removeSuffix("/") else rawPath
    check(withoutTrailingSlash.isNotBlank() && (directory || !rawPath.endsWith('/'))) {
        "The EPUB contains an invalid entry path: $rawPath"
    }
    val segments = withoutTrailingSlash.split('/')
    check(segments.none { it.isBlank() || it == "." || it == ".." }) {
        "The EPUB contains an unsafe entry path: $rawPath"
    }
    val normalized = segments.joinToString("/") + if (directory) "/" else ""
    check(normalized == rawPath) { "The EPUB contains a non-normalized entry path: $rawPath" }
    return normalized
}

private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:.*")
private val EPUB_MIMETYPE_BYTES = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
private val CUSTOM_FONT_EPUB_MARKER_BYTES = "lagrange-custom-font-v1\n".toByteArray(StandardCharsets.US_ASCII)

private fun validateZipCentralDirectory(
    file: File,
    limits: CustomFontEpubLimits,
    maxEntries: Int,
    maxCentralDirectoryBytes: Long = limits.maxCentralDirectoryBytes
) {
    RandomAccessFile(file, "r").use { archive ->
        val length = archive.length()
        check(length >= ZIP_END_MIN_BYTES) { "The EPUB ZIP structure is incomplete." }
        val searchLength = minOf(length, ZIP_END_MIN_BYTES + ZIP_MAX_COMMENT_BYTES).toInt()
        val searchOffset = length - searchLength
        val tail = ByteArray(searchLength)
        archive.seek(searchOffset)
        archive.readFully(tail)
        val endOffsetInTail = findZipEndOffset(tail)
        check(endOffsetInTail >= 0) { "The EPUB ZIP end record is missing." }
        val endOffset = searchOffset + endOffsetInTail

        val diskNumber = littleEndianUnsignedShort(tail, endOffsetInTail + 4)
        val centralDisk = littleEndianUnsignedShort(tail, endOffsetInTail + 6)
        val entriesOnDisk = littleEndianUnsignedShort(tail, endOffsetInTail + 8)
        val totalEntries16 = littleEndianUnsignedShort(tail, endOffsetInTail + 10)
        val centralSize32 = littleEndianUnsignedInt(tail, endOffsetInTail + 12)
        val centralOffset32 = littleEndianUnsignedInt(tail, endOffsetInTail + 16)
        check(diskNumber == 0 && centralDisk == 0 && entriesOnDisk == totalEntries16) {
            "Multi-disk EPUB archives are not supported."
        }

        val usesZip64 = totalEntries16 == ZIP16_SENTINEL ||
            centralSize32 == ZIP32_SENTINEL ||
            centralOffset32 == ZIP32_SENTINEL
        val directory = if (usesZip64) {
            readZip64Directory(archive, endOffset)
        } else {
            ZipDirectoryMetadata(totalEntries16.toLong(), centralSize32, centralOffset32, endOffset)
        }
        check(directory.entryCount <= maxEntries.toLong()) { "The EPUB contains too many entries." }
        check(directory.size <= maxCentralDirectoryBytes) {
            "The EPUB ZIP directory is too large."
        }
        check(directory.offset <= length && directory.size <= length - directory.offset) {
            "The EPUB ZIP directory is invalid."
        }
        check(directory.offset + directory.size <= directory.upperBound) { "The EPUB ZIP directory is invalid." }
    }
}

private data class ZipDirectoryMetadata(
    val entryCount: Long,
    val size: Long,
    val offset: Long,
    val upperBound: Long
)

private fun readZip64Directory(archive: RandomAccessFile, zipEndOffset: Long): ZipDirectoryMetadata {
    val locatorOffset = zipEndOffset - ZIP64_LOCATOR_BYTES
    check(locatorOffset >= 0L) { "The EPUB ZIP64 locator is missing." }
    val locator = ByteArray(ZIP64_LOCATOR_BYTES.toInt())
    archive.seek(locatorOffset)
    archive.readFully(locator)
    check(littleEndianUnsignedInt(locator, 0) == ZIP64_LOCATOR_SIGNATURE) {
        "The EPUB ZIP64 locator is missing."
    }
    check(littleEndianUnsignedInt(locator, 4) == 0L && littleEndianUnsignedInt(locator, 16) == 1L) {
        "Multi-disk EPUB archives are not supported."
    }
    val recordOffset = littleEndianLong(locator, 8)
    check(recordOffset >= 0L && recordOffset <= archive.length() - ZIP64_END_MIN_BYTES) {
        "The EPUB ZIP64 end record is invalid."
    }
    val record = ByteArray(ZIP64_END_MIN_BYTES.toInt())
    archive.seek(recordOffset)
    archive.readFully(record)
    check(littleEndianUnsignedInt(record, 0) == ZIP64_END_SIGNATURE) {
        "The EPUB ZIP64 end record is missing."
    }
    val recordPayloadSize = littleEndianLong(record, 4)
    check(recordPayloadSize >= ZIP64_END_MIN_PAYLOAD_BYTES && recordPayloadSize <= archive.length() - recordOffset - 12L) {
        "The EPUB ZIP64 end record is invalid."
    }
    check(recordOffset <= Long.MAX_VALUE - 12L - recordPayloadSize) {
        "The EPUB ZIP64 end record is invalid."
    }
    check(recordOffset + 12L + recordPayloadSize == locatorOffset) {
        "The EPUB ZIP64 end record is not adjacent to its locator."
    }
    val diskNumber = littleEndianUnsignedInt(record, 16)
    val centralDisk = littleEndianUnsignedInt(record, 20)
    val entriesOnDisk = littleEndianLong(record, 24)
    val totalEntries = littleEndianLong(record, 32)
    check(diskNumber == 0L && centralDisk == 0L && entriesOnDisk == totalEntries && totalEntries >= 0L) {
        "Multi-disk EPUB archives are not supported."
    }
    val centralSize = littleEndianLong(record, 40)
    val centralOffset = littleEndianLong(record, 48)
    check(centralSize >= 0L && centralOffset >= 0L) { "The EPUB ZIP64 directory is invalid." }
    return ZipDirectoryMetadata(totalEntries, centralSize, centralOffset, recordOffset)
}

private fun generatedCentralDirectoryAllowance(limits: CustomFontEpubLimits): Long = checkedAdd(
    checkedMultiply(limits.maxPathLength.toLong(), 4L, "The EPUB ZIP directory limit is invalid."),
    512L,
    "The EPUB ZIP directory limit is invalid."
)

private fun findZipEndOffset(bytes: ByteArray): Int {
    for (offset in bytes.size - ZIP_END_MIN_BYTES.toInt() downTo 0) {
        if (littleEndianUnsignedInt(bytes, offset) != ZIP_END_SIGNATURE) continue
        val commentLength = littleEndianUnsignedShort(bytes, offset + 20)
        if (offset + ZIP_END_MIN_BYTES + commentLength == bytes.size.toLong()) return offset
    }
    return -1
}

private fun littleEndianUnsignedShort(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun littleEndianUnsignedInt(bytes: ByteArray, offset: Int): Long =
    (littleEndianUnsignedShort(bytes, offset).toLong() or
        (littleEndianUnsignedShort(bytes, offset + 2).toLong() shl 16)) and ZIP32_SENTINEL

private fun littleEndianLong(bytes: ByteArray, offset: Int): Long {
    var result = 0L
    repeat(8) { index -> result = result or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8)) }
    return result
}

private const val ZIP_END_MIN_BYTES = 22L
private const val ZIP_MAX_COMMENT_BYTES = 65_535L
private const val ZIP64_LOCATOR_BYTES = 20L
private const val ZIP64_END_MIN_BYTES = 56L
private const val ZIP64_END_MIN_PAYLOAD_BYTES = 44L
private const val ZIP16_SENTINEL = 0xffff
private const val ZIP32_SENTINEL = 0xffff_ffffL
private const val ZIP_END_SIGNATURE = 0x0605_4b50L
private const val ZIP64_LOCATOR_SIGNATURE = 0x0706_4b50L
private const val ZIP64_END_SIGNATURE = 0x0606_4b50L

private fun moveCompletedOutput(temporary: File, output: File) {
    try {
        Files.move(
            temporary.toPath(),
            output.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    check(output.isFile && output.length() > 0L) { "Could not store the custom-font EPUB copy." }
}

private fun checkedAdd(left: Long, right: Long, message: String): Long {
    check(right >= 0L && left <= Long.MAX_VALUE - right) { message }
    return left + right
}

private fun checkedMultiply(left: Long, right: Long, message: String): Long {
    check(left >= 0L && right >= 0L && (left == 0L || right <= Long.MAX_VALUE / left)) { message }
    return left * right
}

private fun isHtmlResource(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
}

private fun injectFontStyle(html: String, fontUrl: String, familyName: String): String {
    val css = "<style id=\"bookorbit-custom-font\">@font-face{font-family:'$familyName';src:url('$fontUrl');}html,body,body *{font-family:'$familyName' !important;}</style>"
    if (html.contains("bookorbit-custom-font")) return html
    val head = Regex("</head>", RegexOption.IGNORE_CASE)
    if (head.containsMatchIn(html)) return head.replaceFirst(html, "$css</head>")
    val body = Regex("<body\\b[^>]*>", RegexOption.IGNORE_CASE)
    val bodyMatch = body.find(html)
    if (bodyMatch != null) {
        return html.replaceRange(bodyMatch.range, "${bodyMatch.value}$css")
    }
    return "$css$html"
}

private fun relativePath(fromDirectory: String, target: String): String {
    val from = fromDirectory.split('/').filter(String::isNotBlank)
    val to = target.split('/').filter(String::isNotBlank)
    var common = 0
    while (common < from.size && common < to.size && from[common] == to[common]) common++
    return buildString {
        repeat(from.size - common) { append("../") }
        append(to.drop(common).joinToString("/"))
    }
}

private fun fontMediaType(fileName: String): String =
    if (fileName.lowercase().endsWith(".otf")) "font/otf" else "font/ttf"

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
