package com.vangeaux.lagrange

import org.json.JSONObject
import java.time.Instant

internal fun buildReadingSessionPayload(item: ReadingSessionPayload): JSONObject = JSONObject()
    .put("sessionId", item.sessionId)
    .put("startedAt", Instant.ofEpochMilli(item.startedAtMillis).toString())
    .put("endedAt", Instant.ofEpochMilli(item.endedAtMillis).toString())
    .put("durationSeconds", item.durationSeconds)
    .apply {
        item.progressDelta?.let { put("progressDelta", it) }
        item.endProgress?.let { put("endProgress", it) }
    }

internal fun buildCreateAnnotationPayload(item: AnnotationMutationPayload): JSONObject = JSONObject().apply {
    item.cfi?.let { put("cfi", it) }
    item.bookFileId?.toIntOrNull()?.let { put("bookFileId", it) }
    put("text", item.text.orEmpty())
    put("color", item.color.orEmpty())
    put("style", item.style.orEmpty())
    put("note", item.note ?: JSONObject.NULL)
    put("chapterTitle", item.chapterTitle ?: JSONObject.NULL)
}

internal fun buildUpdateAnnotationPayload(item: AnnotationMutationPayload): JSONObject = JSONObject().apply {
    if (item.noteSpecified) put("note", item.note ?: JSONObject.NULL)
    item.color?.let { put("color", it) }
    item.style?.let { put("style", it) }
}

internal fun overlayPendingAnnotations(
    page: BookAnnotationsPage,
    pending: List<AnnotationMutationPayload>,
    localToServerIds: Map<String, String>
): BookAnnotationsPage {
    val byId = page.items.associateBy { it.id }.toMutableMap()
    pending.sortedBy { it.updatedAtMillis }.forEach { mutation ->
        val resolvedId = localToServerIds[mutation.annotationId] ?: mutation.annotationId
        when (mutation.op) {
            AnnotationMutationOp.CREATE -> if (byId[resolvedId] == null) {
                byId[resolvedId] = BookAnnotation(
                    id = mutation.annotationId,
                    bookId = mutation.bookId,
                    cfi = mutation.cfi,
                    jumpFileId = mutation.bookFileId,
                    text = mutation.text,
                    color = mutation.color,
                    style = mutation.style,
                    note = mutation.note,
                    chapterTitle = mutation.chapterTitle,
                    createdAt = Instant.ofEpochMilli(mutation.updatedAtMillis).toString()
                )
            }
            AnnotationMutationOp.UPDATE -> byId[resolvedId]?.let { current ->
                byId[resolvedId] = current.copy(
                    note = if (mutation.noteSpecified) mutation.note else current.note,
                    color = mutation.color ?: current.color,
                    style = mutation.style ?: current.style
                )
            }
            AnnotationMutationOp.DELETE -> byId.remove(resolvedId)
        }
    }
    return page.copy(items = byId.values.toList())
}
