package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaDownloadEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaDownloadContractTest {
    @Test
    fun downloadEndpointUsesChapterQueryParameter() {
        assertEquals(
            "https://demo.kavitareader.com/api/Download/chapter?chapterId=42",
            kavitaDownloadEndpoint("https://demo.kavitareader.com", "42")
        )
    }
}
