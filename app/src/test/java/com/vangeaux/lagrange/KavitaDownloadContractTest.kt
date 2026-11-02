package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaDownloadEndpoint
import com.vangeaux.lagrange.provider.kavita.kavitaDownloadFilesForBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KavitaDownloadContractTest {
    @Test
    fun downloadEndpointUsesChapterQueryParameter() {
        assertEquals(
            "https://demo.kavitareader.com/api/Download/chapter?chapterId=42",
            kavitaDownloadEndpoint("https://demo.kavitareader.com", "42")
        )
    }

    @Test
    fun audiobookDownloadExpansionIsExplicitlyUnavailable() {
        val book = BookSummary(
            libraryId = "1",
            id = "chapter:42",
            fileId = "99",
            title = "Audiobook",
            mediaKind = MediaKind.AUDIO
        )

        val error = assertThrows(
            UserFacingException::class.java
        ) { kavitaDownloadFilesForBook(book) }
        assertEquals(UserFacingReason.CAPABILITY_UNAVAILABLE, error.reason)
    }
}
