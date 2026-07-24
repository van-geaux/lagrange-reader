package com.bookorbit.android

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class AudiobookSessionEventType {
    PLAY,
    PAUSE
}

internal data class AudiobookSessionKey(
    val serverUrl: String,
    val bookId: String,
    val fileId: String?
)

internal data class AudiobookSessionEvent(
    val serverUrl: String,
    val bookId: String,
    val fileId: String?,
    val title: String,
    val type: AudiobookSessionEventType,
    val occurredAtMillis: Long,
    val positionMs: Long
) {
    val key: AudiobookSessionKey
        get() = AudiobookSessionKey(serverUrl, bookId, fileId)
}

internal fun trimAudiobookSessionHistory(
    events: List<AudiobookSessionEvent>,
    maxPerBook: Int
): List<AudiobookSessionEvent> {
    if (maxPerBook <= 0) return emptyList()
    return events
        .groupBy(AudiobookSessionEvent::key)
        .values
        .flatMap { group -> group.sortedByDescending { it.occurredAtMillis }.take(maxPerBook) }
        .sortedByDescending { it.occurredAtMillis }
}

@Entity(
    tableName = "audiobook_session_history",
    indices = [
        Index(value = ["serverUrl", "bookId", "fileId", "occurredAtMillis"])
    ]
)
internal data class AudiobookSessionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverUrl: String,
    val bookId: String,
    val fileId: String?,
    val title: String,
    val type: String,
    val occurredAtMillis: Long,
    val positionMs: Long
)

@Dao
internal interface AudiobookSessionHistoryDao {
    @Query(
        """
        SELECT * FROM audiobook_session_history
        WHERE serverUrl = :serverUrl AND bookId = :bookId AND fileId IS :fileId
        ORDER BY occurredAtMillis DESC
        """
    )
    suspend fun readForBook(
        serverUrl: String,
        bookId: String,
        fileId: String?
    ): List<AudiobookSessionEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AudiobookSessionEventEntity): Long

    @Query("DELETE FROM audiobook_session_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM audiobook_session_history WHERE serverUrl = :serverUrl")
    suspend fun clearServer(serverUrl: String)

    @Query(
        """
        DELETE FROM audiobook_session_history
        WHERE serverUrl = :serverUrl AND bookId = :bookId AND fileId IS :fileId
        """
    )
    suspend fun clearBook(serverUrl: String, bookId: String, fileId: String?)
}

@Database(
    entities = [AudiobookSessionEventEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class AudiobookSessionHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): AudiobookSessionHistoryDao
}

internal class AudiobookSessionHistoryStore(context: Context) {
    private val dao = database(context).historyDao()
    private val mutex = Mutex()

    suspend fun read(serverUrl: String, book: BookSummary): List<AudiobookSessionEvent> = mutex.withLock {
        dao.readForBook(
            serverUrl = serverUrl,
            bookId = book.id,
            fileId = book.fileId
        ).map(AudiobookSessionEventEntity::toModel)
    }

    suspend fun record(
        serverUrl: String,
        book: BookSummary,
        type: AudiobookSessionEventType,
        occurredAtMillis: Long = System.currentTimeMillis(),
        positionMs: Long
    ) = mutex.withLock {
        dao.insert(
            AudiobookSessionEventEntity(
                serverUrl = serverUrl,
                bookId = book.id,
                fileId = book.fileId,
                title = book.title,
                type = type.name,
                occurredAtMillis = occurredAtMillis,
                positionMs = positionMs.coerceAtLeast(0L)
            )
        )
        val events = dao.readForBook(serverUrl, book.id, book.fileId)
        val retainedIds = events
            .sortedByDescending { it.occurredAtMillis }
            .take(MAX_EVENTS_PER_BOOK)
            .mapTo(mutableSetOf()) { it.id }
        events.map { it.id }.filterNot { it in retainedIds }.takeIf { it.isNotEmpty() }?.let { ids ->
            dao.deleteByIds(ids)
        }
    }

    suspend fun clearServer(serverUrl: String) = mutex.withLock {
        dao.clearServer(serverUrl)
    }

    suspend fun clearBook(serverUrl: String, book: BookSummary) = mutex.withLock {
        dao.clearBook(serverUrl, book.id, book.fileId)
    }


    private companion object {
        const val MAX_EVENTS_PER_BOOK = 20

        @Volatile
        private var instance: AudiobookSessionHistoryDatabase? = null

        fun database(context: Context): AudiobookSessionHistoryDatabase = instance
            ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudiobookSessionHistoryDatabase::class.java,
                    "audiobook-session-history.db"
                ).build().also { instance = it }
            }
    }
}

private fun AudiobookSessionEventEntity.toModel(): AudiobookSessionEvent = AudiobookSessionEvent(
    serverUrl = serverUrl,
    bookId = bookId,
    fileId = fileId,
    title = title,
    type = runCatching { AudiobookSessionEventType.valueOf(type) }
        .getOrDefault(AudiobookSessionEventType.PAUSE),
    occurredAtMillis = occurredAtMillis,
    positionMs = positionMs
)
