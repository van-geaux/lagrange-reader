package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesNavigationTest {
    @Test
    fun `neighbors follow numeric series index regardless of candidate order`() {
        val first = book("first", "First", 1.0)
        val current = book("current", "Current", 2.0)
        val otherVersion = book("current-audio", "Current Audio", 2.0)
        val next = book("next", "Next", 3.0)

        val neighbors = seriesBookNeighbors(current, listOf(next, otherVersion, current, first))

        assertEquals(first, neighbors.previous)
        assertEquals(next, neighbors.next)
        assertEquals(3, neighbors.total)
    }

    @Test
    fun `other versions contain only distinct books at the same series index`() {
        val current = book("current", "Current", 2.0)
        val printVersion = book("print", "Current Print", 2.0).copy(format = "epub")
        val audioVersion = book("audio", "Current Audio", 2.0).copy(format = "m4b")
        val next = book("next", "Next", 3.0)
        val unrelated = book("unrelated", "Unrelated", 2.0).copy(
            seriesId = "other-series",
            seriesName = "Other Series"
        )

        val versions = bookDetailOtherVersions(
            current,
            listOf(next, audioVersion, current, unrelated, printVersion, audioVersion)
        )

        assertEquals(listOf("audio", "print"), versions.map { it.id })
    }

    @Test
    fun `other versions require a series and a concrete index`() {
        val standalone = book("standalone", "Standalone", 1.0).copy(
            seriesId = null,
            seriesName = null
        )
        val unindexed = book("unindexed", "Unindexed", null)

        assertEquals(emptyList<BookSummary>(), bookDetailOtherVersions(standalone, listOf(book("match", "Match", 1.0))))
        assertEquals(emptyList<BookSummary>(), bookDetailOtherVersions(unindexed, listOf(book("other", "Other", null))))
    }

    @Test
    fun `series edges keep one stable disabled direction`() {
        val first = book("first", "First", 1.0)
        val next = book("next", "Next", 2.0)

        val firstNeighbors = seriesBookNeighbors(first, listOf(next))
        val lastNeighbors = seriesBookNeighbors(next, listOf(first))

        assertNull(firstNeighbors.previous)
        assertEquals(next, firstNeighbors.next)
        assertEquals(first, lastNeighbors.previous)
        assertNull(lastNeighbors.next)
    }

    @Test
    fun `unindexed books sort after indexed books and unrelated series are excluded`() {
        val indexed = book("indexed", "Indexed", 1.0)
        val current = book("current", "Current", null)
        val unrelated = book("unrelated", "Unrelated", 2.0).copy(
            seriesId = "other-series",
            seriesName = "Other Series"
        )

        val neighbors = seriesBookNeighbors(current, listOf(unrelated, indexed))

        assertEquals(indexed, neighbors.previous)
        assertNull(neighbors.next)
        assertEquals(2, neighbors.total)
    }

    @Test
    fun `prefers current library and current exact format when available`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "epub")
        val sameLibrarySameFormat = book("next-epub-a", "Next Epub A", 3.0, libraryId = "lib-a", format = "epub")
        val sameLibraryOtherFormat = book("next-pdf-a", "Next Pdf A", 3.0, libraryId = "lib-a", format = "pdf")
        val otherLibrarySameFormat = book("next-epub-b", "Next Epub B", 3.0, libraryId = "lib-b", format = "epub")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(sameLibraryOtherFormat, otherLibrarySameFormat, sameLibrarySameFormat),
            libraryOrder = listOf("lib-a", "lib-b")
        )

        assertEquals(sameLibrarySameFormat, neighbors.next)
    }

    @Test
    fun `falls back to exact format in another library ordered by user library order`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "epub")
        val thirdLibraryExactFormat = book("next-epub-c", "Next Epub C", 3.0, libraryId = "lib-c", format = "epub")
        val secondLibraryExactFormat = book("next-epub-b", "Next Epub B", 3.0, libraryId = "lib-b", format = "epub")
        val sameLibraryOtherFormat = book("next-pdf-a", "Next Pdf A", 3.0, libraryId = "lib-a", format = "pdf")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(thirdLibraryExactFormat, sameLibraryOtherFormat, secondLibraryExactFormat),
            libraryOrder = listOf("lib-a", "lib-b", "lib-c")
        )

        assertEquals(secondLibraryExactFormat, neighbors.next)
    }

    @Test
    fun `falls back to exact format in a third library when neither earlier library has a target`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "epub")
        val thirdLibraryExactFormat = book("next-epub-c", "Next Epub C", 3.0, libraryId = "lib-c", format = "epub")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(thirdLibraryExactFormat),
            libraryOrder = listOf("lib-a", "lib-b", "lib-c")
        )

        assertEquals(thirdLibraryExactFormat, neighbors.next)
    }

    @Test
    fun `falls back to format-family priority within the first library that has a target`() {
        // current's format (azw3) belongs to no known family, so format-family priority governs.
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "azw3")
        val secondLibraryAudio = book("next-audio-b", "Next Audio B", 3.0, libraryId = "lib-b", format = "m4b")
        val secondLibraryComic = book("next-comic-b", "Next Comic B", 3.0, libraryId = "lib-b", format = "cbz")
        val secondLibraryPdf = book("next-pdf-b", "Next Pdf B", 3.0, libraryId = "lib-b", format = "pdf")
        val thirdLibraryComic = book("next-comic-c", "Next Comic C", 3.0, libraryId = "lib-c", format = "cbr")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(secondLibraryAudio, secondLibraryComic, secondLibraryPdf, thirdLibraryComic),
            libraryOrder = listOf("lib-a", "lib-b", "lib-c")
        )

        // lib-a has no target at this index, so lib-b is the first library with a target;
        // within lib-b, PDF outranks comic and audio in the family priority order.
        assertEquals(secondLibraryPdf, neighbors.next)
    }

    @Test
    fun `treats epub and kepub as one fallback family and breaks ties deterministically`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "azw3")
        val kepub = book("next-kepub", "Zzz Kepub", 3.0, libraryId = "lib-b", format = "kepub")
        val epub = book("next-epub", "Aaa Epub", 3.0, libraryId = "lib-b", format = "epub")
        val audio = book("next-audio", "Audio", 3.0, libraryId = "lib-b", format = "m4b")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(kepub, epub, audio),
            libraryOrder = listOf("lib-a", "lib-b")
        )

        // current's format (azw3) is unavailable anywhere else, so family priority applies;
        // epub and kepub are the same family, tie-broken by title.
        assertEquals(epub, neighbors.next)
    }

    @Test
    fun `m4a current picks m4b neighbor as the same audiobook family in step 1`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "m4a")
        val sameLibraryAudio = book("next-m4b-a", "Next M4b A", 3.0, libraryId = "lib-a", format = "m4b")
        val sameLibraryEpub = book("next-epub-a", "Next Epub A", 3.0, libraryId = "lib-a", format = "epub")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(sameLibraryEpub, sameLibraryAudio),
            libraryOrder = listOf("lib-a")
        )

        assertEquals(sameLibraryAudio, neighbors.next)
    }

    @Test
    fun `mp3 current picks m4b neighbor as the same audiobook family in another library`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "mp3")
        val secondLibraryAudio = book("next-m4b-b", "Next M4b B", 3.0, libraryId = "lib-b", format = "m4b")
        val secondLibraryEpub = book("next-epub-b", "Next Epub B", 3.0, libraryId = "lib-b", format = "epub")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(secondLibraryEpub, secondLibraryAudio),
            libraryOrder = listOf("lib-a", "lib-b")
        )

        assertEquals(secondLibraryAudio, neighbors.next)
    }

    @Test
    fun `m4a current prefers m4b in the current library over epub in another library`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "m4a")
        val sameLibraryAudio = book("next-m4b-a", "Next M4b A", 3.0, libraryId = "lib-a", format = "m4b")
        val otherLibraryEpub = book("next-epub-b", "Next Epub B", 3.0, libraryId = "lib-b", format = "epub")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(otherLibraryEpub, sameLibraryAudio),
            libraryOrder = listOf("lib-a", "lib-b")
        )

        assertEquals(sameLibraryAudio, neighbors.next)
    }

    @Test
    fun `two m4b targets in the same library and index are tie-broken by title`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "m4a")
        val zzzAudio = book("next-m4b-zzz", "Zzz Audio", 3.0, libraryId = "lib-a", format = "m4b")
        val aaaAudio = book("next-m4b-aaa", "Aaa Audio", 3.0, libraryId = "lib-a", format = "m4b")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(zzzAudio, aaaAudio),
            libraryOrder = listOf("lib-a")
        )

        assertEquals(aaaAudio, neighbors.next)
    }

    @Test
    fun `known media kind takes precedence over a conflicting format string`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "epub")
            .copy(mediaKind = MediaKind.AUDIO)
        val audio = book("next-m4b", "Next Audio", 3.0, libraryId = "lib-a", format = "m4b")
            .copy(mediaKind = MediaKind.AUDIO)
        val epub = book("next-epub", "Next Epub", 3.0, libraryId = "lib-a", format = "epub")
            .copy(mediaKind = MediaKind.EPUB)

        val neighbors = seriesBookNeighbors(
            current,
            listOf(epub, audio),
            libraryOrder = listOf("lib-a")
        )

        assertEquals(audio, neighbors.next)
    }

    @Test
    fun `duplicate indexes within the target slot are resolved by selection policy`() {
        val current = book("current", "Current", 2.0, libraryId = "lib-a", format = "epub")
        val duplicateA = book("dup-a", "Dup A", 3.0, libraryId = "lib-a", format = "pdf")
        val duplicateB = book("dup-b", "Dup B", 3.0, libraryId = "lib-a", format = "epub")

        val neighbors = seriesBookNeighbors(
            current,
            listOf(duplicateA, duplicateB),
            libraryOrder = listOf("lib-a")
        )

        assertEquals(duplicateB, neighbors.next)
        assertEquals(2, neighbors.total)
    }

    @Test
    fun `unavailable previous and next targets remain null under the selection policy`() {
        val onlyBook = book("only", "Only", 1.0, libraryId = "lib-a", format = "epub")

        val neighbors = seriesBookNeighbors(onlyBook, emptyList(), libraryOrder = listOf("lib-a", "lib-b"))

        assertNull(neighbors.previous)
        assertNull(neighbors.next)
        assertEquals(1, neighbors.total)
    }

    private fun book(
        id: String,
        title: String,
        index: Double?,
        libraryId: String = "library",
        format: String? = null
    ) = BookSummary(
        libraryId = libraryId,
        id = id,
        fileId = "file-$id",
        title = title,
        format = format,
        seriesId = "series",
        seriesName = "Test Series",
        seriesIndex = index
    )
}
