package com.vangeaux.lagrange.provider.opds

import org.junit.Assert.assertEquals
import org.junit.Test

class OpdsAtomParserAndroidTest {
    @Test
    fun parsesAtomFeedUsingTheAndroidPlatformFactory() {
        val url = "https://books.example/api/v1/opds"
        val parsed = OpdsAtomParser.parse(
            """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Android platform catalog</title>
                </feed>
            """.trimIndent(),
            url
        )

        assertEquals("Android platform catalog", parsed.title)
    }
}
