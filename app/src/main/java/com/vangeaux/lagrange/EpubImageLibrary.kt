package com.vangeaux.lagrange

import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

sealed interface EpubImageLibrarySourceResult {
    data class Ready(val file: File) : EpubImageLibrarySourceResult
    data object RemoteConsentRequired : EpubImageLibrarySourceResult
    data class Unavailable(val message: String) : EpubImageLibrarySourceResult
    data class Error(val message: String) : EpubImageLibrarySourceResult
}

internal fun epubImageLibrarySourceResult(
    localFile: File?,
    localFileError: String?,
    fileId: String?,
    allowRemoteCache: Boolean
): EpubImageLibrarySourceResult = when {
    localFile != null -> EpubImageLibrarySourceResult.Ready(localFile)
    localFileError != null -> EpubImageLibrarySourceResult.Error(localFileError)
    !allowRemoteCache && !fileId.isNullOrBlank() ->
        EpubImageLibrarySourceResult.RemoteConsentRequired
    else -> EpubImageLibrarySourceResult.Unavailable("The selected EPUB is not available locally.")
}

internal fun shouldShowEpubImageLibrary(book: BookSummary): Boolean =
    book.mediaKind == MediaKind.EPUB

internal data class EpubImageDimensions(
    val width: Int,
    val height: Int
)

internal data class EpubImageEntry(
    val archivePath: String,
    val width: Int,
    val height: Int
)

internal data class EpubImageCatalog(
    val sourceFile: File,
    val entries: List<EpubImageEntry>,
    val skippedCount: Int
)

internal object EpubImageLibraryScanner {
    private const val MAX_XML_BYTES = 2 * 1024 * 1024L
    private const val MAX_IMAGE_ENTRY_BYTES = 64 * 1024 * 1024L
    private const val MAX_ARCHIVE_UNCOMPRESSED_BYTES = 256 * 1024 * 1024L
    private const val MAX_ARCHIVE_ENTRIES = 20_000
    private const val MAX_IMAGE_DIMENSION = 100_000
    private val supportedRasterExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    internal fun scan(
        sourceFile: File,
        minimumDimensionPx: Int
    ): EpubImageCatalog = scan(sourceFile, minimumDimensionPx) { path, zipFile ->
        probeBitmapDimensions(zipFile, path)
    }

    internal fun scan(
        sourceFile: File,
        minimumDimensionPx: Int,
        probe: (String, ZipFile) -> EpubImageDimensions?
    ): EpubImageCatalog {
        val minimum = normalizeEpubImageMinimumDimensionPx(minimumDimensionPx)
        ZipFile(sourceFile).use { zipFile ->
            val entries = zipFile.entries().asSequence().take(MAX_ARCHIVE_ENTRIES + 1).toList()
            if (entries.size > MAX_ARCHIVE_ENTRIES) {
                throw IllegalArgumentException("EPUB contains too many archive entries")
            }
            var totalUncompressedBytes = 0L
            val byPath = buildMap {
                entries.filterNot(ZipEntry::isDirectory).forEach { entry ->
                    totalUncompressedBytes += entry.size.coerceAtLeast(0L)
                    if (totalUncompressedBytes > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                        throw IllegalArgumentException("EPUB archive is too large")
                    }
                    putIfAbsent(normalizeArchivePath(null, entry.name) ?: entry.name, entry)
                }
            }
            val packagePath = findPackagePath(zipFile)
            val packageDocument = parseXml(readEntry(zipFile, byPath, packagePath))
            val manifest = manifestItems(packageDocument, packagePath)
            val orderedContentPaths = spineContentPaths(packageDocument, manifest)
            val seen = linkedSetOf<String>()
            val images = mutableListOf<EpubImageEntry>()
            var skipped = 0

            fun addImage(path: String) {
                if (!seen.add(path)) return
                val archiveEntry = byPath[path]
                if (archiveEntry == null || !isSupportedRaster(path)) {
                    skipped += 1
                    return
                }
                val dimensions = runCatching { probe(path, zipFile) }.getOrNull()
                if (dimensions == null || !isEligible(dimensions, minimum)) {
                    skipped += 1
                    return
                }
                images += EpubImageEntry(path, dimensions.width, dimensions.height)
            }

            orderedContentPaths.forEach { contentPath ->
                val content = runCatching {
                    parseXml(readEntry(zipFile, byPath, contentPath))
                }.getOrNull()
                if (content == null) {
                    skipped += 1
                    return@forEach
                }
                imageReferences(content).forEach { reference ->
                    normalizeArchivePath(contentPath, reference)?.let(::addImage)
                }
            }

            byPath.keys
                .filter(::isSupportedRaster)
                .filterNot(seen::contains)
                .sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })
                .forEach(::addImage)

            return EpubImageCatalog(sourceFile, images, skipped)
        }
    }

    private fun findPackagePath(zipFile: ZipFile): String {
        val paths = zipFile.entries().asSequence()
            .filter { it.name == "META-INF/container.xml" }
            .toList()
        val container = paths.firstOrNull()
            ?: throw IllegalArgumentException("EPUB container.xml is missing")
        val document = parseXml(zipFile.getInputStream(container).use(::readBounded))
        val rootfile = document.elementsByLocalName("rootfile").firstOrNull()
            ?: throw IllegalArgumentException("EPUB rootfile is missing")
        return normalizeArchivePath(null, rootfile.getAttribute("full-path"))
            ?: throw IllegalArgumentException("EPUB rootfile path is invalid")
    }

    private fun manifestItems(document: org.w3c.dom.Document, packagePath: String): Map<String, String> =
        document.elementsByLocalName("item").mapNotNull { item ->
            val id = item.getAttribute("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val href = item.getAttribute("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = normalizeArchivePath(packagePath, href) ?: return@mapNotNull null
            id to path
        }.toMap()

    private fun spineContentPaths(
        document: org.w3c.dom.Document,
        manifest: Map<String, String>
    ): List<String> = document.elementsByLocalName("itemref").mapNotNull { itemref ->
        manifest[itemref.getAttribute("idref")]
    }

    private fun imageReferences(document: org.w3c.dom.Document): List<String> =
        document.getElementsByTagName("*").nodeSequence()
            .flatMap { node ->
                node.attributes?.attributeSequence().orEmpty()
                    .filter { attribute ->
                        attribute.localName in setOf("src", "href", "data") ||
                            attribute.nodeName == "xlink:href"
                    }
                    .mapNotNull { attribute -> attribute.nodeValue }
            }
            .toList()

    private fun isSupportedRaster(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in supportedRasterExtensions

    private fun isEligible(dimensions: EpubImageDimensions, minimum: Int): Boolean =
        dimensions.width in 1..MAX_IMAGE_DIMENSION &&
            dimensions.height in 1..MAX_IMAGE_DIMENSION &&
            dimensions.width >= minimum && dimensions.height >= minimum

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }.newDocumentBuilder().parse(bytes.inputStream())

    private fun readEntry(zipFile: ZipFile, entries: Map<String, ZipEntry>, path: String): ByteArray {
        val entry = entries[path] ?: throw IllegalArgumentException("EPUB entry is missing: $path")
        return zipFile.getInputStream(entry).use(::readBounded)
    }

    private fun readBounded(input: InputStream, maximumBytes: Long = MAX_XML_BYTES): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maximumBytes) throw IllegalArgumentException("EPUB entry is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun probeBitmapDimensions(zipFile: ZipFile, path: String): EpubImageDimensions? {
        val entry = zipFile.getEntry(path) ?: return null
        if (entry.size > MAX_IMAGE_ENTRY_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zipFile.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return EpubImageDimensions(options.outWidth, options.outHeight)
    }

    internal fun decodeEpubImageBitmap(
        sourceFile: File,
        archivePath: String,
        maximumDimension: Int
    ): android.graphics.Bitmap? = ZipFile(sourceFile).use { zipFile ->
        val entry = zipFile.getEntry(archivePath) ?: return@use null
        if (entry.size > MAX_IMAGE_ENTRY_BYTES) return@use null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zipFile.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
        val sampleSize = calculateSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maximumDimension = maximumDimension
        )
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        zipFile.getInputStream(entry).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    internal fun readEpubImageBytes(sourceFile: File, archivePath: String): ByteArray? =
        ZipFile(sourceFile).use { zipFile ->
            val entry = zipFile.getEntry(archivePath) ?: return@use null
            if (entry.size > MAX_IMAGE_ENTRY_BYTES) return@use null
            zipFile.getInputStream(entry).use { input ->
                readBounded(input, MAX_IMAGE_ENTRY_BYTES)
            }
        }

    private fun calculateSampleSize(width: Int, height: Int, maximumDimension: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maximumDimension || height / sampleSize > maximumDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun normalizeArchivePath(basePath: String?, reference: String): String? {
        val withoutFragment = reference.substringBefore('#').substringBefore('?')
        if (withoutFragment.isBlank() || withoutFragment.startsWith('/')) return null
        val decoded = runCatching { URI(withoutFragment).path ?: return null }
            .getOrElse { withoutFragment }
            .replace('\\', '/')
        val segments = buildList {
            basePath?.substringBeforeLast('/', "")?.split('/')?.forEach(::add)
            decoded.split('/').forEach(::add)
        }
        val normalized = ArrayDeque<String>()
        segments.forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isEmpty()) return null else normalized.removeLast()
                else -> normalized.addLast(segment)
            }
        }
        return normalized.joinToString("/").takeIf(String::isNotBlank)
    }

    private fun org.w3c.dom.Document.elementsByLocalName(name: String): List<Element> =
        getElementsByTagName("*").nodeSequence()
            .filter { it.localName == name || it.nodeName.substringAfter(':') == name }
            .map { it as Element }
            .toList()

    private fun org.w3c.dom.NodeList.nodeSequence(): Sequence<Node> = sequence {
        for (index in 0 until length) yield(item(index))
    }

    private fun org.w3c.dom.NamedNodeMap.attributeSequence(): Sequence<Node> = sequence {
        for (index in 0 until length) yield(item(index))
    }
}
