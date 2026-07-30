package com.vangeaux.lagrange

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.UUID

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
