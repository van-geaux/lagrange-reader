package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubRendererRecoveryTest {
    @Test
    fun `software renderer is limited to affected Bigme HiBreak devices`() {
        assertTrue(shouldUseSoftwareEpubRendering("Bigme", "HiBreak"))
        assertTrue(shouldUseSoftwareEpubRendering("BIGME", "HIBREAK"))
        assertFalse(shouldUseSoftwareEpubRendering("Sony", "XQ-CQ44"))
        assertFalse(shouldUseSoftwareEpubRendering("Bigme", "B751C"))
    }

    @Test
    fun `renderer probe requires a live laid out Readium document`() {
        val healthy = """"{\"marker\":\"lagrange-epub-renderer-v1\",\"readyState\":\"complete\",\"hasBody\":true,\"hasReadium\":true,\"viewportWidth\":1072,\"viewportHeight\":1360,\"contentWidth\":1032,\"contentHeight\":1360}""""
        assertTrue(isHealthyEpubRendererProbeResult(healthy))

        assertFalse(isHealthyEpubRendererProbeResult(null))
        assertFalse(isHealthyEpubRendererProbeResult("null"))
        assertFalse(isHealthyEpubRendererProbeResult("\"\""))
        assertFalse(
            isHealthyEpubRendererProbeResult(
                """"{\"marker\":\"lagrange-epub-renderer-v1\",\"readyState\":\"complete\",\"hasBody\":true,\"hasReadium\":true,\"viewportWidth\":0,\"viewportHeight\":1360,\"contentWidth\":1032,\"contentHeight\":1360}""""
            )
        )
    }

    @Test
    fun `renderer probe rejects spoofed and incomplete callback values`() {
        assertFalse(
            isHealthyEpubRendererProbeResult(
                """"{\"marker\":\"wrong\",\"readyState\":\"complete\",\"hasBody\":true,\"hasReadium\":true,\"viewportWidth\":1,\"viewportHeight\":1,\"contentWidth\":1,\"contentHeight\":1}""""
            )
        )
        assertFalse(
            isHealthyEpubRendererProbeResult(
                """"{\"marker\":\"lagrange-epub-renderer-v1\",\"readyState\":\"loading\",\"hasBody\":true,\"hasReadium\":true,\"viewportWidth\":1,\"viewportHeight\":1,\"contentWidth\":1,\"contentHeight\":1}""""
            )
        )
        assertFalse(isHealthyEpubRendererProbeResult("\"not-json\""))
    }
}
