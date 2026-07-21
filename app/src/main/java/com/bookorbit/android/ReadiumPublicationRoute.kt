package com.bookorbit.android

import java.util.Locale

internal enum class ReadiumPublicationRoute {
    EPUB,
    PDF,
    AUDIO,
    COMIC,
    NORMALIZE_COMIC_TO_CBZ,
    NORMALIZE_EBOOK_TO_EPUB,
    UNSUPPORTED
}

internal fun readiumPublicationRoute(format: String?, title: String?): ReadiumPublicationRoute {
    val token = listOfNotNull(format, title).joinToString(" ").lowercase(Locale.US)
    val parts = token.split(Regex("[^a-z0-9]+"))
    return when {
        parts.any { it in setOf("mobi", "azw", "azw3", "fb2") } ->
            ReadiumPublicationRoute.NORMALIZE_EBOOK_TO_EPUB
        parts.any { it == "cbr" || it == "cb7" } || token.contains("rar") || token.contains("7z") ->
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
    ReadiumPublicationRoute.NORMALIZE_EBOOK_TO_EPUB,
    ReadiumPublicationRoute.UNSUPPORTED -> MediaKind.UNKNOWN
}
