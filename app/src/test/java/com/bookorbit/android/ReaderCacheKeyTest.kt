package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderCacheKeyTest {
    @Test
    fun `reader cache key is stable for the same server and file`() {
        val first = ReaderCacheKey.build("https://example.test/", "123", "epub")
        val second = ReaderCacheKey.build("https://example.test", "123", "epub")

        assertEquals(first, second)
    }

    @Test
    fun `reader cache key changes across servers`() {
        val first = ReaderCacheKey.build("https://one.example", "123", "epub")
        val second = ReaderCacheKey.build("https://two.example", "123", "epub")

        assertNotEquals(first, second)
    }
}
