package com.bookorbit.android

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
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

fun loadEpubBook(context: Context, sourceFile: File): EpubBook? {
    return runCatching {
        val extractedRoot = extractEpubIfNeeded(context, sourceFile)
        val containerFile = File(extractedRoot, "META-INF/container.xml")
        if (!containerFile.exists()) {
            return null
        }

        val packageRelativePath = parseContainerPath(containerFile) ?: return null
        val packageFile = File(extractedRoot, packageRelativePath.replace('/', File.separatorChar))
        if (!packageFile.exists()) {
            return null
        }

        parsePackage(packageFile)
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

private fun parsePackage(packageFile: File): EpubBook {
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
    val chapters = spine.mapNotNull { idref ->
        val manifestEntry = manifest[idref] ?: return@mapNotNull null
        val href = manifestEntry.first
        val mediaType = manifestEntry.second.orEmpty().lowercase(Locale.US)
        if (!isHtmlMediaType(mediaType) && !href.lowercase(Locale.US).endsWith(".xhtml") && !href.lowercase(Locale.US).endsWith(".html")) {
            return@mapNotNull null
        }

        val chapterFile = File(baseDir, href.replace('/', File.separatorChar)).canonicalFile
        if (!chapterFile.exists()) {
            return@mapNotNull null
        }

        EpubChapter(
            title = chapterFile.nameWithoutExtension.replace('-', ' ').replace('_', ' '),
            file = chapterFile
        )
    }

    return EpubBook(
        rootDir = baseDir,
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
