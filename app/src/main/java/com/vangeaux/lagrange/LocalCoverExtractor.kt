package com.vangeaux.lagrange

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import kotlin.math.max

/** Longest edge of a cover generated from a downloaded file; matches catalog thumbnail use. */
internal const val LOCAL_COVER_MAX_EDGE_PX = 600

/** Largest image read from a downloaded archive when looking for its cover (bytes actually inflated). */
private const val LOCAL_COVER_MAX_IMAGE_BYTES = 32 * 1024 * 1024

/** Largest container/OPF document read from an EPUB (bytes actually inflated). */
private const val LOCAL_COVER_MAX_XML_BYTES = 1024 * 1024

/** Archives with more entries, or longer names in total, are not searched for a cover. */
private const val LOCAL_COVER_MAX_ENTRIES = 20_000
private const val LOCAL_COVER_MAX_NAME_CHARS = 4 * 1024 * 1024

private val NATURAL_ORDER_CHUNK = Regex("\\d+|\\D+")

private val LOCAL_COVER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

private fun String.isLocalCoverImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in LOCAL_COVER_IMAGE_EXTENSIONS

private fun xmlAttribute(tag: String, name: String): String? =
    Regex("""\b$name\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)
        ?.let(::decodeXmlEntities)

private fun decodeXmlEntities(value: String): String =
    Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> runCatching {
                val code = if (entity.startsWith("#x", ignoreCase = true)) {
                    entity.substring(2).toInt(16)
                } else {
                    entity.substring(1).toInt()
                }
                String(Character.toChars(code))
            }.getOrDefault(match.value)
        }
    }

/** Percent-decodes a URI path; unlike form decoding, a literal '+' stays a '+'. */
internal fun percentDecodePath(path: String): String {
    if ('%' !in path) return path
    val out = java.io.ByteArrayOutputStream()
    var i = 0
    while (i < path.length) {
        if (path[i] == '%' && i + 2 <= path.lastIndex && path[i + 1].isHexDigitChar() && path[i + 2].isHexDigitChar()) {
            out.write(path.substring(i + 1, i + 3).toInt(16))
            i += 3
        } else {
            // Copy the literal run up to the next '%' whole, so surrogate pairs stay intact.
            val next = path.indexOf('%', i + 1).let { if (it < 0) path.length else it }
            out.write(path.substring(i, next).toByteArray(StandardCharsets.UTF_8))
            i = next
        }
    }
    return out.toString(StandardCharsets.UTF_8.name())
}

private fun Char.isHexDigitChar(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Picks the archive entry to use as a cover. EPUB: the OPF `cover-image` item, then the item named
 * by `<meta name="cover">`, then an image item whose id or href says "cover", then the first image
 * in the manifest. Comic archives: the first image in natural page order.
 */
internal fun selectLocalCoverEntry(names: List<String>, readText: (String) -> String?): String? {
    val available = names.toSet()
    if ("META-INF/container.xml" in available) {
        val container = readText("META-INF/container.xml").orEmpty()
        val opfPath = Regex("<rootfile\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(container)
            .mapNotNull { xmlAttribute(it.value, "full-path") }
            .firstOrNull()
        val opf = opfPath?.let(readText)
        if (opfPath != null && opf != null) {
            val baseDir = opfPath.substringBeforeLast('/', "")
            fun resolve(href: String): String {
                val decoded = percentDecodePath(href.substringBefore('#'))
                val joined = if (baseDir.isEmpty()) decoded else "$baseDir/$decoded"
                val parts = ArrayDeque<String>()
                joined.split('/').forEach { part ->
                    when (part) {
                        "", "." -> Unit
                        ".." -> parts.removeLastOrNull()
                        else -> parts.addLast(part)
                    }
                }
                return parts.joinToString("/")
            }
            val items = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).map { it.value }.toList()
            val images = items.filter { item ->
                xmlAttribute(item, "media-type")?.startsWith("image/", ignoreCase = true) == true ||
                    xmlAttribute(item, "href")?.isLocalCoverImageName() == true
            }
            val coverMetaId = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf)
                .map { it.value }
                .firstOrNull { xmlAttribute(it, "name").equals("cover", ignoreCase = true) }
                ?.let { xmlAttribute(it, "content") }
            val chosen = images.firstOrNull { item ->
                xmlAttribute(item, "properties")?.split(' ')?.any { it.equals("cover-image", true) } == true
            } ?: images.firstOrNull { xmlAttribute(it, "id") == coverMetaId }
                ?: images.firstOrNull { item ->
                    listOfNotNull(xmlAttribute(item, "id"), xmlAttribute(item, "href"))
                        .any { it.contains("cover", ignoreCase = true) }
                }
                ?: images.firstOrNull()
            chosen?.let { xmlAttribute(it, "href") }?.let(::resolve)?.takeIf { it in available }?.let { return it }
        }
    }
    return names
        .asSequence()
        .filter { it.isLocalCoverImageName() && !it.startsWith("__MACOSX/") && !it.substringAfterLast('/').startsWith(".") }
        .minWithOrNull(::compareNaturally)
}

/** Natural order so page "2.jpg" sorts before "10.jpg". */
private fun compareNaturally(a: String, b: String): Int {
    val left = NATURAL_ORDER_CHUNK.findAll(a.lowercase()).map { it.value }.toList()
    val right = NATURAL_ORDER_CHUNK.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val result = if (x[0].isDigit() && y[0].isDigit()) {
            x.trimStart('0').length.compareTo(y.trimStart('0').length)
                .takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
        } else {
            x.compareTo(y)
        }
        if (result != 0) return result
    }
    return left.size.compareTo(right.size)
}

/**
 * Cover image for a downloaded book file, used when no server cover is reachable (offline). EPUB and
 * CBZ use an image inside the archive, PDF renders its first page. Returns a JPEG thumbnail, or null
 * when the file has no usable cover. Runs on the caller's (IO) thread.
 */
internal fun extractLocalCover(
    file: File,
    maxEdgePx: Int = LOCAL_COVER_MAX_EDGE_PX,
    preferEmbeddedAudioCover: Boolean = false
): ByteArray? = runCatching {
    if (!file.isFile) return null
    val bitmap = when {
        preferEmbeddedAudioCover -> embeddedAudioCoverBitmap(file, maxEdgePx) ?: zipCoverBitmap(file, maxEdgePx)
        isPdfFile(file) -> renderPdfCover(file, maxEdgePx)
        else -> zipCoverBitmap(file, maxEdgePx)
    }
    bitmap ?: return null
    try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}.getOrNull()

private fun embeddedAudioCoverBitmap(file: File, maxEdgePx: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.embeddedPicture?.let { decodeSampledCover(it, maxEdgePx) }
    } finally {
        retriever.release()
    }
}

private fun isPdfFile(file: File): Boolean =
    file.inputStream().use { input ->
        val header = ByteArray(5)
        input.read(header) == 5 && String(header, StandardCharsets.US_ASCII) == "%PDF-"
    }

private fun zipCoverBitmap(file: File, maxEdgePx: Int): Bitmap? = ZipFile(file).use { zip ->
    val names = ArrayList<String>()
    var entryCount = 0
    var nameChars = 0L
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        entryCount += 1
        nameChars += entry.name.length
        if (entryCount > LOCAL_COVER_MAX_ENTRIES || nameChars > LOCAL_COVER_MAX_NAME_CHARS) return null
        if (!entry.isDirectory) names += entry.name
    }
    fun read(name: String, limit: Int): ByteArray? =
        zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).use { readAtMost(it, limit) } }
    val entryName = selectLocalCoverEntry(names) { name ->
        read(name, LOCAL_COVER_MAX_XML_BYTES)?.toString(StandardCharsets.UTF_8)
    } ?: return null
    val bytes = read(entryName, LOCAL_COVER_MAX_IMAGE_BYTES) ?: return null
    decodeSampledCover(bytes, maxEdgePx)
}

/** Reads at most [limit] inflated bytes; null when the entry is larger (declared sizes are not trusted). */
private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private fun decodeSampledCover(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdgePx) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null
    return scaleCoverDown(decoded, maxEdgePx)
}

private fun scaleCoverDown(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
    val edge = max(bitmap.width, bitmap.height)
    if (edge <= maxEdgePx) return bitmap
    val scale = maxEdgePx.toFloat() / edge
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}

private fun renderPdfCover(file: File, maxEdgePx: Int): Bitmap? =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val scale = maxEdgePx.toFloat() / max(page.width, page.height).coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
