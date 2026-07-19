package com.bookorbit.android

import android.content.Context
import android.util.Xml
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class EpubBook(
    val rootDir: File,
    val chapters: List<EpubChapter>,
    val title: String?
)

data class EpubChapter(
    val title: String,
    val file: File
)

private const val EPUB_ASSET_DOMAIN = "appassets.androidplatform.net"
private const val EPUB_ASSET_PATH = "/"
private const val EPUB_RENDERED_DOCUMENT_PATH = "/_bookorbit-reader/"
private const val MAX_RENDERED_DOCUMENTS = 4

internal class EpubWebViewAssetSession(context: Context, rootDir: File) {
    private val renderedDocuments = LinkedHashMap<String, ByteArray>()
    private val renderedDocumentHandler = WebViewAssetLoader.PathHandler { path ->
        val document = synchronized(renderedDocuments) { renderedDocuments[path] }
        if (document == null) {
            WebResourceResponse("text/plain", Charsets.UTF_8.name(), null)
        } else {
            WebResourceResponse(
                "text/html",
                Charsets.UTF_8.name(),
                ByteArrayInputStream(document)
            ).apply {
                responseHeaders = mapOf("Cache-Control" to "no-store")
            }
        }
    }

    val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(EPUB_ASSET_DOMAIN)
        .addPathHandler(EPUB_RENDERED_DOCUMENT_PATH, renderedDocumentHandler)
        .addPathHandler(
            EPUB_ASSET_PATH,
            WebViewAssetLoader.InternalStoragePathHandler(context, rootDir.canonicalFile)
        )
        .build()

    fun registerRenderedDocument(html: String): String {
        val documentName = "${UUID.randomUUID()}.html"
        synchronized(renderedDocuments) {
            renderedDocuments[documentName] = html.toByteArray(Charsets.UTF_8)
            while (renderedDocuments.size > MAX_RENDERED_DOCUMENTS) {
                val oldest = renderedDocuments.entries.firstOrNull()?.key ?: break
                renderedDocuments.remove(oldest)
            }
        }
        return "https://$EPUB_ASSET_DOMAIN$EPUB_RENDERED_DOCUMENT_PATH$documentName"
    }
}

internal fun epubChapterBaseUrl(rootDir: File, chapterFile: File): String {
    val rootPath = rootDir.canonicalFile.toPath()
    val chapterParentPath = requireNotNull(chapterFile.canonicalFile.parentFile).toPath()
    require(chapterParentPath.startsWith(rootPath)) { "EPUB chapter must be inside its extracted root." }
    val relativeSegments = rootPath.relativize(chapterParentPath)
        .map { segment -> URLEncoder.encode(segment.toString(), Charsets.UTF_8.name()).replace("+", "%20") }
    return buildString {
        append("https://")
        append(EPUB_ASSET_DOMAIN)
        append(EPUB_ASSET_PATH)
        if (relativeSegments.isNotEmpty()) {
            append(relativeSegments.joinToString("/"))
            append('/')
        }
    }
}

fun loadEpubBook(context: Context, sourceFile: File): EpubBook? {
    return runCatching {
        val extractedRoot = extractEpubIfNeeded(context, sourceFile).canonicalFile
        val containerFile = File(extractedRoot, "META-INF/container.xml")
        if (!containerFile.exists()) {
            return null
        }

        val packageRelativePath = parseContainerPath(containerFile) ?: return null
        val packageFile = File(
            extractedRoot,
            packageRelativePath.replace('/', File.separatorChar)
        ).canonicalFile
        if (!packageFile.toPath().startsWith(extractedRoot.toPath()) || !packageFile.exists()) {
            return null
        }

        parsePackage(packageFile, extractedRoot)
    }.getOrNull()
}

private fun extractEpubIfNeeded(context: Context, sourceFile: File): File {
    val key = buildExtractionKey(sourceFile)
    val root = File(context.cacheDir, "epub/$key")
    val marker = File(root, ".complete")
    if (marker.exists()) {
        return root
    }

    root.deleteRecursively()
    root.mkdirs()

    ZipFile(sourceFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            unzipEntry(zip, entry, root)
        }
    }

    marker.writeText("ok")
    return root
}

private fun unzipEntry(zipFile: ZipFile, entry: ZipEntry, outputRoot: File) {
    val target = File(outputRoot, entry.name.replace('/', File.separatorChar))
    val targetPath = target.canonicalFile.toPath()
    val rootPath = outputRoot.canonicalFile.toPath()
    require(targetPath.startsWith(rootPath)) { "Invalid EPUB entry path: ${entry.name}" }

    if (entry.isDirectory) {
        target.mkdirs()
        return
    }

    target.parentFile?.mkdirs()
    zipFile.getInputStream(entry).use { input ->
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

private fun parseContainerPath(containerFile: File): String? {
    val parser = Xml.newPullParser()
    FileInputStream(containerFile).use { input ->
        parser.setInput(input, Charsets.UTF_8.name())
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
        }
    }
    return null
}

private fun parsePackage(packageFile: File, extractedRoot: File): EpubBook {
    val manifest = linkedMapOf<String, Pair<String, String?>>()
    val spine = mutableListOf<String>()
    var title: String? = null
    val parser = Xml.newPullParser()

    FileInputStream(packageFile).use { input ->
        parser.setInput(input, Charsets.UTF_8.name())
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name.lowercase(Locale.US)) {
                "item" -> {
                    val id = parser.getAttributeValue(null, "id") ?: continue
                    val href = parser.getAttributeValue(null, "href") ?: continue
                    val mediaType = parser.getAttributeValue(null, "media-type")
                    manifest[id] = href to mediaType
                }
                "itemref" -> {
                    parser.getAttributeValue(null, "idref")?.let(spine::add)
                }
                "dc:title", "title" -> {
                    if (title.isNullOrBlank()) {
                        title = parser.nextText().trim().ifBlank { null }
                    }
                }
            }
        }
    }

    val baseDir = requireNotNull(packageFile.parentFile) {
        "EPUB package file must have a parent directory."
    }
    val extractedRootPath = extractedRoot.canonicalFile.toPath()
    val chapters = spine.mapNotNull { idref ->
        val manifestEntry = manifest[idref] ?: return@mapNotNull null
        val href = manifestEntry.first
        val mediaType = manifestEntry.second.orEmpty().lowercase(Locale.US)
        if (!isHtmlMediaType(mediaType) && !href.lowercase(Locale.US).endsWith(".xhtml") && !href.lowercase(Locale.US).endsWith(".html")) {
            return@mapNotNull null
        }

        val chapterFile = File(baseDir, href.replace('/', File.separatorChar)).canonicalFile
        if (!chapterFile.toPath().startsWith(extractedRootPath) || !chapterFile.exists()) {
            return@mapNotNull null
        }

        EpubChapter(
            title = chapterFile.nameWithoutExtension.replace('-', ' ').replace('_', ' '),
            file = chapterFile
        )
    }

    return EpubBook(
        rootDir = extractedRoot.canonicalFile,
        chapters = chapters,
        title = title
    )
}

private fun isHtmlMediaType(mediaType: String): Boolean {
    return mediaType.contains("xhtml") || mediaType.contains("html")
}

private fun buildExtractionKey(sourceFile: File): String {
    val payload = "${sourceFile.absolutePath}|${sourceFile.length()}|${sourceFile.lastModified()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
