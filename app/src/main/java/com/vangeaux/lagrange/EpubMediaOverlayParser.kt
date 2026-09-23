package com.vangeaux.lagrange

import java.io.File
import java.io.StringReader
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

internal data class EpubMediaOverlayClip(
    val index: Int,
    val sectionIndex: Int,
    val textHref: String,
    val textFragment: String?,
    val audioHref: String,
    val clipBeginSeconds: Double,
    val clipEndSeconds: Double?,
    val durationSeconds: Double?
)

internal data class EpubMediaOverlayPlaylist(
    val items: List<EpubMediaOverlayClip>
) {
    val durationSeconds: Double?
        get() = items.map { it.durationSeconds }.takeIf { durations -> durations.all { it != null } }
            ?.sumOf { it!! }
}

internal object EpubMediaOverlayParser {
    fun parse(epubFile: File): EpubMediaOverlayPlaylist = runCatching {
        ZipFile(epubFile).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return@use emptyList()
            val containerXml = parseXml(zip.getInputStream(container))
            val opfPath = containerXml.elements("rootfile")
                .firstOrNull()
                ?.getAttribute("full-path")
                ?.let(::normalizeArchivePath)
                ?.takeIf(String::isNotBlank)
                ?: return@use emptyList()
            val opfEntry = zip.getEntry(opfPath) ?: return@use emptyList()
            val opfXml = parseXml(zip.getInputStream(opfEntry))
            val manifest = opfXml.elements("item").mapNotNull { item ->
                val id = item.getAttribute("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val href = resolveEpubArchiveHref(opfPath, item.getAttribute("href")) ?: return@mapNotNull null
                ManifestItem(
                    id = id,
                    href = href,
                    mediaOverlay = item.getAttribute("media-overlay").takeIf(String::isNotBlank)
                )
            }.associateBy(ManifestItem::id)
            val spine = opfXml.elements("spine").firstOrNull()
                ?.elements("itemref")
                .orEmpty()
                .mapNotNull { it.getAttribute("idref").takeIf(String::isNotBlank) }
            val clips = mutableListOf<EpubMediaOverlayClip>()

            spine.forEachIndexed { sectionIndex, idref ->
                val contentItem = manifest[idref] ?: return@forEachIndexed
                val overlayItem = contentItem.mediaOverlay?.let(manifest::get) ?: return@forEachIndexed
                val smilEntry = zip.getEntry(overlayItem.href) ?: return@forEachIndexed
                val smil = parseXml(zip.getInputStream(smilEntry))
                smil.elements("par").forEach { par ->
                    val textElement = par.directChild("text") ?: return@forEach
                    val audioElement = par.directChild("audio") ?: return@forEach
                    val textReference = resolveEpubArchiveReference(overlayItem.href, textElement.getAttribute("src"))
                        ?: return@forEach
                    val audioReference = resolveEpubArchiveReference(overlayItem.href, audioElement.getAttribute("src"))
                        ?: return@forEach
                    if (zip.getEntry(textReference.path) == null || zip.getEntry(audioReference.path) == null) {
                        return@forEach
                    }
                    val begin = parseEpubMediaOverlayClock(audioElement.getAttribute("clipBegin")) ?: 0.0
                    val end = parseEpubMediaOverlayClock(audioElement.getAttribute("clipEnd"))
                    if (end != null && end <= begin) return@forEach
                    clips += EpubMediaOverlayClip(
                        index = clips.size,
                        sectionIndex = sectionIndex,
                        textHref = textReference.path,
                        textFragment = textReference.fragment,
                        audioHref = audioReference.path,
                        clipBeginSeconds = begin,
                        clipEndSeconds = end,
                        durationSeconds = epubMediaOverlayDuration(begin, end)
                    )
                }
            }
            clips
        }
    }.getOrDefault(emptyList()).let(::EpubMediaOverlayPlaylist)

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaOverlay: String?
    )

    private data class ArchiveReference(val path: String, val fragment: String?)

    private fun parseXml(input: java.io.InputStream): org.w3c.dom.Document = input.use { stream ->
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
            setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }.parse(stream)
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: ParserConfigurationException) {
            // Android's built-in XML provider rejects these optional hardening flags.
        }
    }

    private fun org.w3c.dom.Document.elements(localName: String): List<Element> =
        getElementsByTagName("*").asSequence()
            .mapNotNull { it as? Element }
            .filter { (it.localName ?: it.tagName.substringAfter(':')) == localName }
            .toList()

    private fun Element.elements(localName: String): List<Element> =
        childNodes.asSequence()
            .mapNotNull { it as? Element }
            .filter { (it.localName ?: it.tagName.substringAfter(':')) == localName }
            .toList()

    private fun Element.directChild(localName: String): Element? = elements(localName).firstOrNull()

    private fun NodeList.asSequence(): Sequence<Node> =
        (0 until length).asSequence().map(::item)

    private fun resolveEpubArchiveHref(baseFile: String, href: String): String? =
        resolveEpubArchiveReference(baseFile, href)?.path

    private fun resolveEpubArchiveReference(baseFile: String, href: String): ArchiveReference? {
        val reference = href.trim().replace(" ", "%20")
        if (reference.isEmpty()) return null
        return runCatching {
            val resolved = URI(baseFile).resolve(URI(reference)).normalize()
            val rawPath = resolved.path ?: return null
            val path = normalizeArchivePath(rawPath) ?: return null
            if (path.isBlank()) return null
            ArchiveReference(path, resolved.fragment?.takeIf(String::isNotBlank))
        }.getOrNull()
    }

    private fun normalizeArchivePath(path: String): String? = runCatching {
        val normalized = URI(null, null, "/$path", null).normalize().path.removePrefix("/")
        if (normalized == ".." || normalized.startsWith("../")) null else normalized
    }.getOrNull()
}

internal fun parseEpubMediaOverlayClock(value: String?): Double? {
    val raw = value?.trim()?.removePrefix("npt=")?.takeIf(String::isNotEmpty) ?: return null
    val unitMatch = Regex("^([+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(ms|s|min|h)$", RegexOption.IGNORE_CASE)
        .matchEntire(raw)
    if (unitMatch != null) {
        val amount = unitMatch.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        return when (unitMatch.groupValues[2].lowercase()) {
            "ms" -> amount / 1000.0
            "min" -> amount * 60.0
            "h" -> amount * 3600.0
            else -> amount
        }
    }
    val components = raw.split(':').map { it.toDoubleOrNull() ?: return null }
    if (components.any { !it.isFinite() || it < 0.0 } || components.size !in 1..3) return null
    if (components.dropLast(1).any { it % 1.0 != 0.0 }) return null
    if (components.size >= 2 && components.last() >= 60.0) return null
    if (components.size == 3 && components[1] >= 60.0) return null
    return when (components.size) {
        1 -> components[0]
        2 -> components[0] * 60.0 + components[1]
        else -> components[0] * 3600.0 + components[1] * 60.0 + components[2]
    }
}

internal fun epubMediaOverlayDuration(beginSeconds: Double, endSeconds: Double?): Double? =
    endSeconds?.takeIf { it.isFinite() && it >= beginSeconds }?.minus(beginSeconds)
