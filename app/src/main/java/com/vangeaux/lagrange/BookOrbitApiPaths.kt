package com.vangeaux.lagrange

internal const val ANNOTATIONS_PAGE_SIZE = 30

internal fun bookReadingSessionsPath(bookId: String, page: Int, pageSize: Int): String {
    val encodedBookId = java.net.URLEncoder.encode(bookId, Charsets.UTF_8.name())
    return "/api/v1/books/$encodedBookId/sessions" +
        "?page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceAtLeast(1)}"
}

internal fun bookReadingAttemptsPath(bookId: String, page: Int, pageSize: Int): String {
    val encodedBookId = java.net.URLEncoder.encode(bookId, Charsets.UTF_8.name())
    return "/api/v1/books/$encodedBookId/reading-attempts" +
        "?page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceAtLeast(1)}"
}

internal fun readingSessionPath(fileId: String): String {
    val encodedFileId = java.net.URLEncoder.encode(fileId, Charsets.UTF_8.name())
    return "/api/v1/books/files/$encodedFileId/sessions"
}

internal fun annotationsPath(filter: AnnotationsFilter, page: Int, pageSize: Int): String {
    fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    return buildString {
        append("/api/v1/annotations?page=")
        append(page.coerceAtLeast(1))
        append("&pageSize=")
        append(pageSize.coerceAtLeast(1))
        filter.search?.trim()?.takeIf { it.isNotBlank() }?.let { append("&search=").append(encode(it)) }
        filter.bookId?.trim()?.takeIf { it.isNotBlank() }?.let { append("&bookId=").append(encode(it)) }
        filter.chapter?.trim()?.takeIf { it.isNotBlank() }?.let { append("&chapter=").append(encode(it)) }
        filter.colors.takeIf { it.isNotEmpty() }?.let { append("&colors=").append(encode(it.joinToString(","))) }
        filter.styles.takeIf { it.isNotEmpty() }?.let { append("&styles=").append(encode(it.joinToString(","))) }
        filter.origins.takeIf { it.isNotEmpty() }?.let { append("&origins=").append(encode(it.joinToString(","))) }
        filter.dateFrom?.trim()?.takeIf { it.isNotBlank() }?.let { append("&dateFrom=").append(encode(it)) }
        filter.dateTo?.trim()?.takeIf { it.isNotBlank() }?.let { append("&dateTo=").append(encode(it)) }
        filter.hasNote?.let { append("&hasNote=").append(it) }
        append("&status=").append(encode(filter.status))
        append("&sortBy=").append(encode(filter.sortBy))
        append("&sortDir=").append(encode(filter.sortDir))
    }
}

internal fun annotationsPath(bookId: String): String {
    val encodedBookId = java.net.URLEncoder.encode(bookId, Charsets.UTF_8.name()).replace("+", "%20")
    return "/api/v1/books/$encodedBookId/annotations"
}

internal fun annotationPath(bookId: String, annotationId: String): String {
    val encodedBookId = java.net.URLEncoder.encode(bookId, Charsets.UTF_8.name()).replace("+", "%20")
    val encodedAnnotationId = java.net.URLEncoder.encode(annotationId, Charsets.UTF_8.name()).replace("+", "%20")
    return "/api/v1/books/$encodedBookId/annotations/$encodedAnnotationId"
}

internal fun annotationRestorePath(annotationId: String): String {
    val encodedAnnotationId = java.net.URLEncoder.encode(annotationId, Charsets.UTF_8.name()).replace("+", "%20")
    return "/api/v1/annotations/$encodedAnnotationId/restore"
}

internal fun annotationPurgePath(annotationId: String): String {
    val encodedAnnotationId = java.net.URLEncoder.encode(annotationId, Charsets.UTF_8.name()).replace("+", "%20")
    return "/api/v1/annotations/$encodedAnnotationId"
}
