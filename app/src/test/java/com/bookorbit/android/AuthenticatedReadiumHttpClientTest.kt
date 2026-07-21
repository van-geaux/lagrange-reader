package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpStatus

class AuthenticatedReadiumHttpClientTest {
    @Test
    fun `authentication failure policy accepts only unauthorized and forbidden`() {
        assertTrue(
            isReadiumAuthenticationFailure(
                HttpError.ErrorResponse(status = HttpStatus.Unauthorized)
            )
        )
        assertTrue(
            isReadiumAuthenticationFailure(
                HttpError.ErrorResponse(status = HttpStatus.Forbidden)
            )
        )
        assertFalse(
            isReadiumAuthenticationFailure(
                HttpError.ErrorResponse(status = HttpStatus.InternalServerError)
            )
        )
        assertFalse(isReadiumAuthenticationFailure(null))
    }

    @Test
    fun `current authentication replaces stale values without dropping range headers`() {
        val merged = mergeReadiumRequestHeaders(
            existing = mapOf(
                "Range" to listOf("bytes=100-199"),
                "authorization" to listOf("Bearer stale"),
                "Accept" to listOf("audio/mp4")
            ),
            currentAuthentication = mapOf(
                "Authorization" to "Bearer current",
                "Cookie" to "session=abc"
            )
        )

        assertEquals(listOf("bytes=100-199"), merged["Range"])
        assertEquals(listOf("audio/mp4"), merged["Accept"])
        assertEquals(listOf("Bearer current"), merged["Authorization"])
        assertEquals(listOf("session=abc"), merged["Cookie"])
        assertFalse("authorization" in merged)
    }

    @Test
    fun `empty authentication leaves request headers unchanged`() {
        val existing = mapOf("Range" to listOf("bytes=0-99"))

        assertEquals(existing, mergeReadiumRequestHeaders(existing, emptyMap()))
    }

    @Test
    fun `range probe accepts only a partial response that advertises byte ranges`() {
        assertEquals(
            RemoteByteRangeSupport.SUPPORTED,
            remoteByteRangeSupport(statusCode = 206, acceptsByteRanges = true)
        )
        assertEquals(
            RemoteByteRangeSupport.UNSUPPORTED,
            remoteByteRangeSupport(statusCode = 200, acceptsByteRanges = true)
        )
        assertEquals(
            RemoteByteRangeSupport.UNSUPPORTED,
            remoteByteRangeSupport(statusCode = 206, acceptsByteRanges = false)
        )
    }
}
