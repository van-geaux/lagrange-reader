package com.bookorbit.android

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(
    tableName = "library_catalog_books",
    primaryKeys = ["serverUrl", "libraryId", "bookId"],
    indices = [Index(value = ["serverUrl", "libraryId", "catalogPosition"])]
)
internal data class LibraryCatalogBookEntity(
    val serverUrl: String,
    val libraryId: String,
    val bookId: String,
    val catalogPosition: Int,
    val fileId: String?,
    val title: String,
    val author: String?,
    val format: String?,
    val mediaKind: String,
    val streamUrl: String?,
    val downloadUrl: String?,
    val coverUrl: String?,
    val localPath: String?,
    val progressLabel: String?,
    val progressPercent: Float?,
    val progressPositionMs: Long?,
    val progressPageIndex: Int?,
    val seriesId: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
    val readStatus: String? = null,
    val isRead: Boolean,
    val addedAtMillis: Long?,
    val updatedAtMillis: Long?,
    val lastReadAtMillis: Long?,
    val readerPageIndex: Int?,
    val readerPageCount: Int?,
    @ColumnInfo(defaultValue = "'2/3'")
    val coverAspectRatio: String = CoverAspectRatio.PORTRAIT.wireValue
)

@Entity(
    tableName = "library_catalog_metadata",
    primaryKeys = ["serverUrl", "libraryId"]
)
internal data class LibraryCatalogMetadataEntity(
    val serverUrl: String,
    val libraryId: String,
    val total: Int,
    val seriesTotal: Int?,
    val pageSize: Int,
    val refreshedAtMillis: Long
)

@Entity(
    tableName = "library_catalog_jump_buckets",
    primaryKeys = ["serverUrl", "libraryId", "bucketKey"],
    indices = [Index(value = ["serverUrl", "libraryId", "displayOrder"])]
)
internal data class LibraryCatalogJumpBucketEntity(
    val serverUrl: String,
    val libraryId: String,
    val bucketKey: String,
    val label: String,
    val catalogIndex: Int,
    val displayOrder: Int
)

@Dao
internal interface LibraryCatalogDao {
    @Query(
        """
        SELECT * FROM library_catalog_books
        WHERE serverUrl = :serverUrl AND libraryId = :libraryId
        ORDER BY catalogPosition ASC
        """
    )
    suspend fun readBooks(serverUrl: String, libraryId: String): List<LibraryCatalogBookEntity>

    @Query(
        """
        SELECT * FROM library_catalog_books
        WHERE serverUrl = :serverUrl
        ORDER BY libraryId ASC, catalogPosition ASC
        """
    )
    suspend fun readAllBooks(serverUrl: String): List<LibraryCatalogBookEntity>

    @Query(
        """
        SELECT * FROM library_catalog_metadata
        WHERE serverUrl = :serverUrl AND libraryId = :libraryId
        """
    )
    suspend fun readMetadata(serverUrl: String, libraryId: String): LibraryCatalogMetadataEntity?

    @Query(
        """
        SELECT * FROM library_catalog_jump_buckets
        WHERE serverUrl = :serverUrl AND libraryId = :libraryId
        ORDER BY displayOrder ASC
        """
    )
    suspend fun readJumpBuckets(serverUrl: String, libraryId: String): List<LibraryCatalogJumpBucketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<LibraryCatalogBookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: LibraryCatalogMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJumpBuckets(buckets: List<LibraryCatalogJumpBucketEntity>)

    @Query(
        """
        DELETE FROM library_catalog_books
        WHERE serverUrl = :serverUrl AND libraryId = :libraryId AND bookId IN (:bookIds)
        """
    )
    suspend fun deleteBooksById(serverUrl: String, libraryId: String, bookIds: List<String>)

    @Query("DELETE FROM library_catalog_jump_buckets WHERE serverUrl = :serverUrl AND libraryId = :libraryId")
    suspend fun deleteJumpBuckets(serverUrl: String, libraryId: String)

    @Query(
        """
        UPDATE library_catalog_books
        SET progressLabel = NULL,
            progressPercent = NULL,
            progressPositionMs = NULL,
            progressPageIndex = NULL,
            readStatus = 'unread',
            isRead = 0,
            lastReadAtMillis = NULL,
            readerPageIndex = NULL,
            readerPageCount = NULL
        WHERE serverUrl = :serverUrl AND bookId = :bookId
        """
    )
    suspend fun resetBookReadingState(serverUrl: String, bookId: String)

    @Query(
        """
        UPDATE library_catalog_books
        SET readStatus = 'read',
            isRead = 1,
            lastReadAtMillis = :markedAtMillis
        WHERE serverUrl = :serverUrl AND bookId = :bookId
        """
    )
    suspend fun markBookAsRead(serverUrl: String, bookId: String, markedAtMillis: Long)

    @Query(
        """
        UPDATE library_catalog_books
        SET localPath = :localPath
        WHERE serverUrl = :serverUrl AND bookId = :bookId
        """
    )
    suspend fun updateLocalPath(serverUrl: String, bookId: String, localPath: String?)

    @Transaction
    suspend fun reconcileLibrary(
        metadata: LibraryCatalogMetadataEntity,
        changedBooks: List<LibraryCatalogBookEntity>,
        removedBookIds: List<String>,
        jumpBuckets: List<LibraryCatalogJumpBucketEntity>,
        replaceJumpBuckets: Boolean
    ) {
        removedBookIds.chunked(900).forEach { ids ->
            deleteBooksById(metadata.serverUrl, metadata.libraryId, ids)
        }
        if (changedBooks.isNotEmpty()) insertBooks(changedBooks)
        if (replaceJumpBuckets) {
            deleteJumpBuckets(metadata.serverUrl, metadata.libraryId)
            if (jumpBuckets.isNotEmpty()) insertJumpBuckets(jumpBuckets)
        }
        insertMetadata(metadata)
    }
}

@Database(
    entities = [
        LibraryCatalogBookEntity::class,
        LibraryCatalogMetadataEntity::class,
        LibraryCatalogJumpBucketEntity::class
    ],
    version = 3,
    exportSchema = false
)
internal abstract class LibraryCatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): LibraryCatalogDao
}

internal data class CachedLibraryCatalog(
    val books: List<BookSummary>,
    val total: Int,
    val seriesTotal: Int?,
    val pageSize: Int,
    val refreshedAtMillis: Long,
    val jumpBuckets: List<LibraryJumpBucket>
)

internal class LibraryCatalogStore(context: Context) {
    private val dao = database(context).catalogDao()
    private val reconcileMutex = Mutex()

    suspend fun read(serverUrl: String, libraryId: String): CachedLibraryCatalog? = reconcileMutex.withLock {
        val metadata = dao.readMetadata(serverUrl, libraryId) ?: return@withLock null
        CachedLibraryCatalog(
            books = dao.readBooks(serverUrl, libraryId).map(LibraryCatalogBookEntity::toBookSummary),
            total = metadata.total,
            seriesTotal = metadata.seriesTotal,
            pageSize = metadata.pageSize,
            refreshedAtMillis = metadata.refreshedAtMillis,
            jumpBuckets = dao.readJumpBuckets(serverUrl, libraryId).map { bucket ->
                LibraryJumpBucket(
                    key = bucket.bucketKey,
                    label = bucket.label,
                    index = bucket.catalogIndex
                )
            }
        )
    }

    suspend fun readAllBooks(serverUrl: String): List<BookSummary> =
        reconcileMutex.withLock {
            dao.readAllBooks(serverUrl).map(LibraryCatalogBookEntity::toBookSummary)
        }

    suspend fun resetBookReadingState(serverUrl: String, bookId: String) = reconcileMutex.withLock {
        dao.resetBookReadingState(serverUrl, bookId)
    }

    suspend fun markBookAsRead(serverUrl: String, bookId: String, markedAtMillis: Long) = reconcileMutex.withLock {
        dao.markBookAsRead(serverUrl, bookId, markedAtMillis)
    }

    suspend fun updateLocalPath(serverUrl: String, bookId: String, localPath: String?) =
        reconcileMutex.withLock {
            dao.updateLocalPath(serverUrl, bookId, localPath)
        }

    suspend fun replace(
        serverUrl: String,
        libraryId: String,
        pageSize: Int,
        total: Int,
        seriesTotal: Int?,
        books: List<BookSummary>,
        jumpBuckets: List<LibraryJumpBucket>,
        refreshedAtMillis: Long = System.currentTimeMillis()
    ) = reconcileMutex.withLock {
        val targetBooks = books.mapIndexed { index, book ->
            book.toCatalogEntity(serverUrl, libraryId, index)
        }
        val existingBooks = dao.readBooks(serverUrl, libraryId).associateBy { it.bookId }
        val changedBooks = targetBooks.filter { target -> existingBooks[target.bookId] != target }
        val targetBookIds = targetBooks.mapTo(mutableSetOf()) { it.bookId }
        val removedBookIds = existingBooks.keys.filterNot { it in targetBookIds }
        val targetJumpBuckets = jumpBuckets.mapIndexed { index, bucket ->
            LibraryCatalogJumpBucketEntity(
                serverUrl = serverUrl,
                libraryId = libraryId,
                bucketKey = bucket.key,
                label = bucket.label,
                catalogIndex = bucket.index,
                displayOrder = index
            )
        }
        val existingJumpBuckets = dao.readJumpBuckets(serverUrl, libraryId)
        dao.reconcileLibrary(
            metadata = LibraryCatalogMetadataEntity(
                serverUrl = serverUrl,
                libraryId = libraryId,
                total = total,
                seriesTotal = seriesTotal,
                pageSize = pageSize,
                refreshedAtMillis = refreshedAtMillis
            ),
            changedBooks = changedBooks,
            removedBookIds = removedBookIds,
            jumpBuckets = targetJumpBuckets,
            replaceJumpBuckets = existingJumpBuckets != targetJumpBuckets
        )
    }

    private companion object {
        @Volatile
        private var instance: LibraryCatalogDatabase? = null

        fun database(context: Context): LibraryCatalogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LibraryCatalogDatabase::class.java,
                "library-catalog.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE library_catalog_books " +
                        "ADD COLUMN coverAspectRatio TEXT NOT NULL DEFAULT '2/3'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_catalog_books ADD COLUMN readStatus TEXT")
            }
        }
    }
}

private fun BookSummary.toCatalogEntity(
    serverUrl: String,
    fallbackLibraryId: String,
    catalogPosition: Int
): LibraryCatalogBookEntity = LibraryCatalogBookEntity(
    serverUrl = serverUrl,
    libraryId = libraryId.ifBlank { fallbackLibraryId },
    bookId = id,
    catalogPosition = catalogPosition,
    fileId = fileId,
    title = title,
    author = author,
    format = format,
    mediaKind = mediaKind.name,
    streamUrl = streamUrl,
    downloadUrl = downloadUrl,
    coverUrl = coverUrl,
    localPath = localPath,
    progressLabel = progressLabel,
    progressPercent = progressPercent,
    progressPositionMs = progressPositionMs,
    progressPageIndex = progressPageIndex,
    seriesId = seriesId,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    readStatus = readStatus?.wireValue,
    isRead = isRead,
    addedAtMillis = addedAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastReadAtMillis = lastReadAtMillis,
    readerPageIndex = readerPageIndex,
    readerPageCount = readerPageCount,
    coverAspectRatio = coverAspectRatio.wireValue
)

private fun LibraryCatalogBookEntity.toBookSummary(): BookSummary = BookSummary(
    libraryId = libraryId,
    id = bookId,
    fileId = fileId,
    title = title,
    author = author,
    format = format,
    mediaKind = runCatching { MediaKind.valueOf(mediaKind) }.getOrDefault(MediaKind.UNKNOWN),
    streamUrl = streamUrl,
    downloadUrl = downloadUrl,
    coverUrl = coverUrl,
    localPath = localPath,
    progressLabel = progressLabel,
    progressPercent = normalizeStoredProgressPercent(progressPercent),
    progressPositionMs = progressPositionMs,
    progressPageIndex = progressPageIndex,
    seriesId = seriesId,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    readStatus = BookReadStatus.fromWireValue(readStatus),
    isRead = isRead,
    addedAtMillis = addedAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastReadAtMillis = lastReadAtMillis,
    readerPageIndex = readerPageIndex,
    readerPageCount = readerPageCount,
    coverAspectRatio = CoverAspectRatio.fromWireValue(coverAspectRatio)
)
