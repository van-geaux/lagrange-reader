package com.vangeaux.lagrange

import java.util.Locale

internal enum class ReadiumPublicationRoute {
    EPUB,
    PDF,
    AUDIO,
    COMIC,
    NORMALIZE_COMIC_TO_CBZ,
    UNSUPPORTED_EBOOK,
    UNSUPPORTED
}

internal fun readiumPublicationRoute(format: String?, title: String?): ReadiumPublicationRoute {
    val formatRoute = format?.let { routeForToken(it) } ?: ReadiumPublicationRoute.UNSUPPORTED
    if (formatRoute != ReadiumPublicationRoute.UNSUPPORTED) return formatRoute
    val extension = title?.substringAfterLast('/')?.substringAfterLast('.', "")
    return extension?.takeIf { it.isNotEmpty() }?.let { routeForToken(it) } ?: ReadiumPublicationRoute.UNSUPPORTED
}

private fun routeForToken(value: String): ReadiumPublicationRoute {
    val token = value.lowercase(Locale.US)
    val parts = token.split(Regex("[^a-z0-9]+"))
    return when {
        parts.any { it in setOf("mobi", "azw", "azw3", "fb2") } ->
            ReadiumPublicationRoute.UNSUPPORTED_EBOOK
        parts.any { it in setOf("cbr", "cb7", "rar", "7z") } ->
            ReadiumPublicationRoute.NORMALIZE_COMIC_TO_CBZ
        token.contains("epub") || parts.any { it == "kepub" } -> ReadiumPublicationRoute.EPUB
        parts.any { it == "cbz" } || token.contains("comic") || token.contains("zip") ->
            ReadiumPublicationRoute.COMIC
        parts.any { it == "pdf" } -> ReadiumPublicationRoute.PDF
        token.contains("audio") || parts.any {
            it in setOf("mp3", "mpeg", "m4a", "m4b", "mp4", "aac", "aif", "aiff", "flac", "ogg", "oga", "opus", "wav", "webm")
        } -> ReadiumPublicationRoute.AUDIO
        else -> ReadiumPublicationRoute.UNSUPPORTED
    }
}

internal fun ReadiumPublicationRoute.mediaKind(): MediaKind = when (this) {
    ReadiumPublicationRoute.EPUB -> MediaKind.EPUB
    ReadiumPublicationRoute.PDF -> MediaKind.PDF
    ReadiumPublicationRoute.AUDIO -> MediaKind.AUDIO
    ReadiumPublicationRoute.COMIC,
    ReadiumPublicationRoute.NORMALIZE_COMIC_TO_CBZ -> MediaKind.COMIC
    ReadiumPublicationRoute.UNSUPPORTED_EBOOK,
    ReadiumPublicationRoute.UNSUPPORTED -> MediaKind.UNKNOWN
}
