package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.UserFacingReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryCapabilityContractTest {
    @Test
    fun statusOptionsExposeOnlyMappedGrimmoryStatuses() {
        val features = grimmoryFeatureAvailability()

        assertFalse(features.userRating)
        assertTrue(features.extendedReadingStatuses)
        assertEquals(
            listOf(
                com.vangeaux.lagrange.BookReadStatus.UNREAD,
                com.vangeaux.lagrange.BookReadStatus.READING,
                com.vangeaux.lagrange.BookReadStatus.REREADING,
                com.vangeaux.lagrange.BookReadStatus.READ,
                com.vangeaux.lagrange.BookReadStatus.ABANDONED
            ),
            features.readingStatusOptions
        )
    }

    @Test
    fun unsupportedProviderActionFailsWithUnavailableReason() {
        val error = runCatching { grimmoryUnavailable("Author browsing") }.exceptionOrNull()

        assertTrue(error is UserFacingException)
        assertEquals(UserFacingReason.CAPABILITY_UNAVAILABLE, (error as UserFacingException).reason)
    }
}
