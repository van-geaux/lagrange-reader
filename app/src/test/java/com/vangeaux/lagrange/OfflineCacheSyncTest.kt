package com.vangeaux.lagrange

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCacheSyncTest {
    @Test
    fun `only transient network and server failures are retried`() {
        assertTrue(isRetryableOfflineCacheError(IOException("offline")))
        assertTrue(isRetryableOfflineCacheError(HttpRequestException(408, "refresh cache")))
        assertTrue(isRetryableOfflineCacheError(HttpRequestException(429, "refresh cache")))
        assertTrue(isRetryableOfflineCacheError(HttpRequestException(503, "refresh cache")))
        assertFalse(isRetryableOfflineCacheError(HttpRequestException(404, "refresh cache")))
        assertFalse(isRetryableOfflineCacheError(HttpRequestException(403, "refresh cache")))
        assertFalse(isRetryableOfflineCacheError(IllegalStateException("bad state")))
    }
}
