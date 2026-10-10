@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.vangeaux.lagrange

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.ContentService

internal class EpubTtsImageContentServiceFactory(
    private val delegate: ((Publication.Service.Context) -> Publication.Service?)?,
    private val settings: EpubTtsImageSettings
) : (Publication.Service.Context) -> Publication.Service? {
    override fun invoke(context: Publication.Service.Context): Publication.Service =
        EpubTtsImageContentService(
            delegate = requireNotNull(delegate?.invoke(context) as? ContentService),
            settings = settings
        )
}

internal class EpubTtsImageContentService(
    private val delegate: ContentService,
    private val settings: EpubTtsImageSettings
) : ContentService {
    override fun content(start: Locator?): Content =
        EpubTtsImageContent(delegate.content(start), settings)

    override fun close() = delegate.close()
}

private class EpubTtsImageContent(
    private val delegate: Content,
    private val settings: EpubTtsImageSettings
) : Content {
    override fun iterator(): Content.Iterator =
        EpubTtsImageIterator(delegate.iterator(), settings)

    override suspend fun elements(): List<Content.Element> = buildList {
        val iterator = this@EpubTtsImageContent.iterator()
        while (iterator.hasNext()) add(iterator.next())
    }

    override suspend fun text(separator: String): String = elements()
        .filterIsInstance<Content.TextualElement>()
        .mapNotNull { it.text?.takeIf(String::isNotBlank) }
        .joinToString(separator)
}

private enum class IteratorDirection {
    FORWARD,
    BACKWARD
}

private data class PendingImageElement(
    val direction: IteratorDirection,
    val rawSteps: Int,
    val element: Content.Element?
)

internal class EpubTtsImageIterator(
    private val delegate: Content.Iterator,
    private val settings: EpubTtsImageSettings
) : Content.Iterator {
    private var pending: PendingImageElement? = null

    override suspend fun hasNext(): Boolean = prepare(IteratorDirection.FORWARD) != null

    override fun next(): Content.Element {
        consumePending(IteratorDirection.FORWARD)?.let { return it }
        while (true) {
            transformEpubTtsElement(delegate.next(), settings)?.let { return it }
        }
    }

    override suspend fun nextOrNull(): Content.Element? =
        if (hasNext()) next() else null

    override suspend fun hasPrevious(): Boolean = prepare(IteratorDirection.BACKWARD) != null

    override fun previous(): Content.Element {
        consumePending(IteratorDirection.BACKWARD)?.let { return it }
        while (true) {
            transformEpubTtsElement(delegate.previous(), settings)?.let { return it }
        }
    }

    override suspend fun previousOrNull(): Content.Element? =
        if (hasPrevious()) previous() else null

    private suspend fun prepare(direction: IteratorDirection): Content.Element? {
        pending?.let { current ->
            if (current.direction == direction) return current.element
            rollback(current)
            pending = null
        }
        var rawSteps = 0
        while (true) {
            val raw = when (direction) {
                IteratorDirection.FORWARD -> delegate.nextOrNull()
                IteratorDirection.BACKWARD -> delegate.previousOrNull()
            } ?: run {
                pending = PendingImageElement(direction, rawSteps, null)
                return null
            }
            rawSteps += 1
            val transformed = transformEpubTtsElement(raw, settings)
            if (transformed != null) {
                pending = PendingImageElement(direction, rawSteps, transformed)
                return transformed
            }
        }
    }

    private fun consumePending(direction: IteratorDirection): Content.Element? {
        val current = pending ?: return null
        if (current.direction != direction) {
            rollback(current)
            pending = null
            return null
        }
        pending = null
        return current.element ?: throw NoSuchElementException()
    }

    private fun rollback(current: PendingImageElement) {
        repeat(current.rawSteps) {
            when (current.direction) {
                IteratorDirection.FORWARD -> delegate.previous()
                IteratorDirection.BACKWARD -> delegate.next()
            }
        }
    }
}

internal fun transformEpubTtsElement(
    element: Content.Element,
    settings: EpubTtsImageSettings
): Content.Element? {
    if (element !is Content.ImageElement) return element
    val description = if (settings.readDescriptions) {
        selectEpubTtsImageDescription(element.caption, element.accessibilityLabel)
    } else {
        null
    }
    val resourceName = if (settings.readResourceNames) {
        epubTtsResourceName(element.embeddedLink.href.toString())
    } else {
        null
    }
    val spoken = when {
        description == null && resourceName == null -> return null
        description == null -> "Resource: $resourceName"
        resourceName == null -> description
        normalizedImageSpeech(description) == normalizedImageSpeech(resourceName) -> description
        else -> "$description. Resource: $resourceName"
    }
    return element.copy(caption = spoken)
}

internal fun selectEpubTtsImageDescription(vararg candidates: String?): String? =
    candidates.firstNotNullOfOrNull { candidate ->
        sanitizeEpubTtsImageText(candidate, MAX_IMAGE_DESCRIPTION_CODE_POINTS)
            ?.takeUnless(::isEpubTtsImageResourceDescription)
    }

internal fun epubTtsResourceName(rawHref: String?): String? {
    val raw = rawHref?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (raw.startsWith("//") || raw.startsWith("data:", ignoreCase = true)) return null
    val withoutQuery = raw.substringBefore('#').substringBefore('?')
    val uri = runCatching { URI(withoutQuery) }.getOrNull() ?: return null
    if (uri.isAbsolute || uri.host != null || uri.userInfo != null) return null
    val decoded = runCatching {
        URLDecoder.decode(withoutQuery.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrNull() ?: return null
    val normalizedSegments = mutableListOf<String>()
    decoded.replace('\\', '/').split('/').forEach { segment ->
        when (segment.trim()) {
            "", "." -> Unit
            ".." -> if (normalizedSegments.isEmpty()) {
                return null
            } else {
                normalizedSegments.removeAt(normalizedSegments.lastIndex)
            }
            else -> normalizedSegments += segment.trim()
        }
    }
    val path = normalizedSegments.joinToString("/").takeIf(String::isNotBlank) ?: return null
    return sanitizeEpubTtsImageText(path, MAX_IMAGE_RESOURCE_CODE_POINTS)
}

internal fun sanitizeEpubTtsImageText(value: String?, maximumCodePoints: Int): String? {
    val normalized = value
        ?.replace(Regex("[\\p{Cc}\\p{Cf}\\s]+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val count = normalized.codePointCount(0, normalized.length)
    if (count <= maximumCodePoints) return normalized
    val end = normalized.offsetByCodePoints(0, maximumCodePoints.coerceAtLeast(0))
    return normalized.substring(0, end).trimEnd() + "..."
}

internal fun isEpubTtsImageResourceDescription(value: String): Boolean {
    val candidate = value.trim()
        .replace(Regex("(?i)^description\\s*:\\s*"), "")
        .trim()
    if (candidate.isBlank()) return false
    if (Regex("(?i)^[a-z]:[\\\\/].+").matches(candidate)) return true
    if (candidate.startsWith('/') || candidate.startsWith("../") || candidate.startsWith("./")) {
        return true
    }
    return !candidate.any(Char::isWhitespace) &&
        Regex("(?i).+\\.(?:avif|bmp|gif|jpe?g|png|svg|tiff?|webp)$").matches(candidate)
}

private fun normalizedImageSpeech(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

private const val MAX_IMAGE_DESCRIPTION_CODE_POINTS = 1_000
private const val MAX_IMAGE_RESOURCE_CODE_POINTS = 512
