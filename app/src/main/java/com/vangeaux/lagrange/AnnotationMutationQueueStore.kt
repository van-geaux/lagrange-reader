package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal const val LOCAL_ANNOTATION_ID_PREFIX = "local:"

internal enum class AnnotationMutationOp {
    CREATE,
    UPDATE,
    DELETE
}

internal data class AnnotationMutationPayload(
    val annotationId: String,
    val serverUrl: String = "",
    val bookId: String = "",
    val op: AnnotationMutationOp,
    val cfi: String? = null,
    val bookFileId: String? = null,
    val text: String? = null,
    val color: String? = null,
    val style: String? = null,
    val note: String? = null,
    val noteSpecified: Boolean = false,
    val chapterTitle: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * A pending create is collapsed with any later edit/delete for the same [AnnotationMutationPayload.annotationId]
 * instead of appending a second entry, since the create has not reached the server yet and there is no
 * server-assigned id to reference. This keeps every queued item addressable by a single stable id and avoids
 * ever inventing a fake server id.
 */
internal class AnnotationMutationQueueStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    constructor(context: Context) : this(
        file = File(context.filesDir, "pending_annotation_mutations.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "pending_annotation_mutations.json"),
        directFileConstructor = true
    )

    suspend fun readAll(): List<AnnotationMutationPayload> = mutex.withLock {
        readUnlocked()
    }

    suspend fun enqueue(payload: AnnotationMutationPayload) = mutex.withLock {
        writeUnlocked(collapse(readUnlocked(), payload))
    }

    suspend fun acknowledge(annotationIds: Set<String>) = mutex.withLock {
        if (annotationIds.isEmpty()) return@withLock
        writeUnlocked(readUnlocked().filterNot { it.annotationId in annotationIds })
    }

    suspend fun countFor(serverUrl: String): Int = mutex.withLock {
        readUnlocked().count { it.serverUrl == serverUrl }
    }

    suspend fun clearServer(serverUrl: String) = mutex.withLock {
        writeUnlocked(readUnlocked().filterNot { it.serverUrl == serverUrl })
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    private fun collapse(
        current: List<AnnotationMutationPayload>,
        incoming: AnnotationMutationPayload
    ): List<AnnotationMutationPayload> {
        val existing = current.find { it.annotationId == incoming.annotationId }
        val withoutExisting = current.filterNot { it.annotationId == incoming.annotationId }
        if (existing == null) return withoutExisting + incoming
        val merged = when {
            existing.op == AnnotationMutationOp.CREATE && incoming.op == AnnotationMutationOp.DELETE ->
                null
            existing.op == AnnotationMutationOp.CREATE && incoming.op == AnnotationMutationOp.UPDATE ->
                existing.copy(
                    text = incoming.text ?: existing.text,
                    color = incoming.color ?: existing.color,
                    style = incoming.style ?: existing.style,
                    note = if (incoming.noteSpecified) incoming.note else existing.note,
                    noteSpecified = existing.noteSpecified || incoming.noteSpecified,
                    chapterTitle = incoming.chapterTitle ?: existing.chapterTitle,
                    updatedAtMillis = incoming.updatedAtMillis
                )
            else -> incoming
        }
        return if (merged == null) withoutExisting else withoutExisting + merged
    }

    private fun readUnlocked(): List<AnnotationMutationPayload> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toPayload()?.let(::add)
                }
            }.sortedBy { it.updatedAtMillis }
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(items: List<AnnotationMutationPayload>) {
        val array = JSONArray()
        items.sortedBy { it.updatedAtMillis }.forEach { payload ->
            array.put(
                JSONObject().apply {
                    put("annotationId", payload.annotationId)
                    put("serverUrl", payload.serverUrl)
                    put("bookId", payload.bookId)
                    put("op", payload.op.name)
                    put("cfi", payload.cfi ?: JSONObject.NULL)
                    put("bookFileId", payload.bookFileId ?: JSONObject.NULL)
                    put("text", payload.text ?: JSONObject.NULL)
                    put("color", payload.color ?: JSONObject.NULL)
                    put("style", payload.style ?: JSONObject.NULL)
                    put("note", payload.note ?: JSONObject.NULL)
                    put("noteSpecified", payload.noteSpecified)
                    put("chapterTitle", payload.chapterTitle ?: JSONObject.NULL)
                    put("updatedAtMillis", payload.updatedAtMillis)
                }
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun JSONObject.toPayload(): AnnotationMutationPayload? {
        val annotationId = optString("annotationId").takeIf { it.isNotBlank() } ?: return null
        val serverUrl = optString("serverUrl")
        val bookId = optString("bookId")
        val op = runCatching { AnnotationMutationOp.valueOf(optString("op")) }.getOrNull() ?: return null
        return AnnotationMutationPayload(
            annotationId = annotationId,
            serverUrl = serverUrl,
            bookId = bookId,
            op = op,
            cfi = optNullableString("cfi"),
            bookFileId = optNullableString("bookFileId"),
            text = optNullableString("text"),
            color = optNullableString("color"),
            style = optNullableString("style"),
            note = optNullableString("note"),
            noteSpecified = optBoolean("noteSpecified", false),
            chapterTitle = optNullableString("chapterTitle"),
            updatedAtMillis = optLong("updatedAtMillis")
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private companion object {
        val mutex = Mutex()
    }
}

/**
 * Once a queued CREATE reaches the server, the durable local annotation is still addressed by its
 * [LOCAL_ANNOTATION_ID_PREFIX]-prefixed id everywhere else in the app (the UI, any freshly-queued
 * UPDATE/DELETE). This store remembers the local-id -> server-id association so later mutations can be
 * resolved to the real id before they are ever sent, instead of a local id silently being posted to the
 * server as though it were a server-assigned one.
 */
internal class AnnotationLocalIdMappingStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    constructor(context: Context) : this(
        file = File(context.filesDir, "annotation_local_id_mappings.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "annotation_local_id_mappings.json"),
        directFileConstructor = true
    )

    suspend fun resolve(id: String): String = mutex.withLock {
        if (!id.startsWith(LOCAL_ANNOTATION_ID_PREFIX)) return@withLock id
        readUnlocked()[id] ?: id
    }

    suspend fun readAll(): Map<String, String> = mutex.withLock { readUnlocked() }

    suspend fun remember(localId: String, serverId: String) = mutex.withLock {
        if (!localId.startsWith(LOCAL_ANNOTATION_ID_PREFIX) || serverId.isBlank()) return@withLock
        val current = readUnlocked().toMutableMap()
        current[localId] = serverId
        writeUnlocked(current)
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    private fun readUnlocked(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val obj = JSONObject(file.readText())
            buildMap {
                obj.keys().forEach { key -> put(key, obj.optString(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeUnlocked(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        file.parentFile?.mkdirs()
        file.writeText(obj.toString())
    }

    private companion object {
        val mutex = Mutex()
    }
}
