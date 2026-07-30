package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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
    if (output.isFile && output.length() > 0L) return output
    output.parentFile?.mkdirs()
    val fontPath = "bookorbit-fonts/${record.fileName}"
    val entries = linkedMapOf<String, ByteArray>()
    ZipFile(source).use { zip ->
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) {
            val entry = iterator.nextElement()
            if (entry.isDirectory) continue
            entries[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
        }
    }
    val containerPath = entries["META-INF/container.xml"]?.toString(StandardCharsets.UTF_8)
        ?: error("EPUB container.xml is missing")
    val packagePath = Regex("full-path\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']")
        .find(containerPath)?.groupValues?.get(1)
        ?: error("EPUB package path is missing")
    val packageText = entries[packagePath]?.toString(StandardCharsets.UTF_8)
        ?: error("EPUB package is missing")
    val packageDir = packagePath.substringBeforeLast('/', "")
    val packageFontPath = relativePath(packageDir, fontPath)
    val manifestItem = "<item id=\"bookorbit-custom-font\" href=\"${xmlEscape(packageFontPath)}\" media-type=\"${fontMediaType(record.fileName)}\"/>"
    val updatedPackage = if (packageText.contains("bookorbit-custom-font")) packageText else
        packageText.replaceFirst(Regex("</manifest>", RegexOption.IGNORE_CASE), "$manifestItem</manifest>")
    entries[packagePath] = updatedPackage.toByteArray(StandardCharsets.UTF_8)

    entries.keys.toList().filter(::isHtmlResource).forEach { path ->
        val html = entries.getValue(path).toString(StandardCharsets.UTF_8)
        val fontUrl = relativePath(path.substringBeforeLast('/', ""), fontPath)
        entries[path] = injectFontStyle(html, fontUrl, record.familyName).toByteArray(StandardCharsets.UTF_8)
    }
    entries[fontPath] = fontFile.readBytes()

    val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.tmp")
    try {
        ZipOutputStream(temporary.outputStream().buffered()).use { out ->
            entries.forEach { (path, bytes) ->
                val entry = ZipEntry(path)
                if (path == "mimetype") {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                out.putNextEntry(entry)
                out.write(bytes)
                out.closeEntry()
            }
        }
        check(temporary.renameTo(output)) { "Could not store the custom-font EPUB copy." }
    } finally {
        temporary.delete()
    }
    return output
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
