package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.komga.komgaDownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KomgaDownloadContractTest {
    @Test
    fun `known response length reports bounded progress`() {
        assertEquals(0.25f, komgaDownloadProgress(25L, 100L))
        assertEquals(1f, komgaDownloadProgress(125L, 100L))
        assertEquals(0f, komgaDownloadProgress(-10L, 100L))
    }

    @Test
    fun `unknown response length leaves progress indeterminate`() {
        assertNull(komgaDownloadProgress(25L, null))
        assertNull(komgaDownloadProgress(25L, 0L))
    }
}
