package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseSignerCompatibilityTest {
    @Test
    fun `matching release signer permits direct update`() {
        val status = ReleaseSignerCompatibility.evaluate(
            installedFingerprint = "da44867c9bfb83f8f8a1fcc1cd8d3aeac2ca515f4dd0c95b58cf5369f6f14681",
            expectedFingerprint = "da44867c9bfb83f8f8a1fcc1cd8d3aeac2ca515f4dd0c95b58cf5369f6f14681"
        )

        assertEquals(ReleaseSignerStatus.MATCH, status)
    }

    @Test
    fun `different signer routes to manual path without starting download`() {
        val actions = mutableListOf<String>()
        val status = ReleaseSignerCompatibility.evaluate(
            installedFingerprint = "f277b8b568569986bfb4f2fbd7a35a09eddfa10349568c5f0a8264ccb409b4c0",
            expectedFingerprint = "da44867c9bfb83f8f8a1fcc1cd8d3aeac2ca515f4dd0c95b58cf5369f6f14681"
        )

        ReleaseSignerCompatibility.routeUpdateAction(
            status = status,
            onDownload = { actions += "download" },
            onManualSwitch = { actions += "manual" }
        )

        assertEquals(listOf("manual"), actions)
    }

    @Test
    fun `unknown signer fails closed to manual path`() {
        val actions = mutableListOf<String>()

        ReleaseSignerCompatibility.routeUpdateAction(
            status = ReleaseSignerStatus.UNKNOWN,
            onDownload = { actions += "download" },
            onManualSwitch = { actions += "manual" }
        )

        assertEquals(listOf("manual"), actions)
    }

    @Test
    fun `malformed fingerprints are unknown`() {
        assertEquals(
            ReleaseSignerStatus.UNKNOWN,
            ReleaseSignerCompatibility.evaluate(
                installedFingerprint = "not-a-fingerprint",
                expectedFingerprint = "da44867c9bfb83f8f8a1fcc1cd8d3aeac2ca515f4dd0c95b58cf5369f6f14681"
            )
        )
    }
}