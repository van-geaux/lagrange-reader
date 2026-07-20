package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesBookGroupingTest {
    private val libraries = listOf(
        LibrarySummary(id = "library-main", name = "Main library"),
        LibrarySummary(id = "library-comics", name = "Comics")
    )

    private fun book(
        id: String,
        libraryId: String,
        format: String?,
        index: Double,
        mediaKind: MediaKind = MediaKind.UNKNOWN
    ) = BookSummary(
        libraryId = libraryId,
        id = id,
        fileId = "file-$id",
        title = "Book $id",
        format = format,
        mediaKind = mediaKind,
        seriesId = "series",
        seriesName = "Series",
        seriesIndex = index
    )

    @Test
    fun `library grouping follows global library order and keeps series order`() {
        val sections = seriesBookSections(
            books = listOf(
                book("three", "library-main", "pdf", 3.0),
                book("one", "library-comics", "cbz", 1.0),
                book("two", "library-main", "epub", 2.0)
            ),
            libraries = libraries,
            groupingMode = SeriesGroupingMode.LIBRARY
        )

        assertEquals(listOf("Main library", "Comics"), sections.map { it.title })
        assertEquals(listOf("two", "three"), sections[0].books.map { it.id })
        assertEquals(listOf("one"), sections[1].books.map { it.id })
    }

    @Test
    fun `format grouping normalizes labels and orders sections alphabetically`() {
        val sections = seriesBookSections(
            books = listOf(
                book("pdf", "library-main", ".pdf", 2.0),
                book("epub", "library-main", "epub", 1.0),
                book("comic", "library-comics", null, 3.0, MediaKind.COMIC)
            ),
            libraries = libraries,
            groupingMode = SeriesGroupingMode.FORMAT
        )

        assertEquals(listOf("COMIC", "EPUB", "PDF"), sections.map { it.title })
    }

    @Test
    fun `inactive grouping returns one unlabelled section in series order`() {
        val sections = seriesBookSections(
            books = listOf(
                book("later", "library-main", "epub", 2.0),
                book("earlier", "library-comics", "pdf", 1.0)
            ),
            libraries = libraries,
            groupingMode = SeriesGroupingMode.NONE
        )

        assertEquals(1, sections.size)
        assertNull(sections.single().title)
        assertEquals(listOf("earlier", "later"), sections.single().books.map { it.id })
    }

    @Test
    fun `selecting active grouping turns it off and another grouping replaces it`() {
        assertEquals(
            SeriesGroupingMode.NONE,
            toggledSeriesGroupingMode(SeriesGroupingMode.LIBRARY, SeriesGroupingMode.LIBRARY)
        )
        assertEquals(
            SeriesGroupingMode.FORMAT,
            toggledSeriesGroupingMode(SeriesGroupingMode.LIBRARY, SeriesGroupingMode.FORMAT)
        )
    }
}
