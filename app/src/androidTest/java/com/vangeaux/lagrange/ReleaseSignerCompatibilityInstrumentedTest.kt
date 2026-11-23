package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseSignerCompatibilityInstrumentedTest {
    @Test
    fun debugBuildSignerIsBlockedFromDirectReleaseDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val status = ReleaseSignerCompatibility.currentInstalledStatus(context)

        assertEquals(ReleaseSignerStatus.MISMATCH, status)

        var downloadStarted = false
        var manualSwitchRequested = false
        ReleaseSignerCompatibility.routeUpdateAction(
            status = status,
            onDownload = { downloadStarted = true },
            onManualSwitch = { manualSwitchRequested = true }
        )

        assertEquals(false, downloadStarted)
        assertEquals(true, manualSwitchRequested)
    }
}
