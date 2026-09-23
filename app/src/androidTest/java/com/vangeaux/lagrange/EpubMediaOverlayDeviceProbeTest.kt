package com.vangeaux.lagrange

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubMediaOverlayDeviceProbeTest {
    @Test
    fun parserFindsClipsInDownloadedMynoghraVolumeTwo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epubFile = File(
            context.filesDir,
            "downloads/Apocalypse_Bringer_Mynoghra_World_Conquest_Starts_with_the_Civilization_of_Ruin_Volume_2-51186.epub"
        )
        assertTrue("Downloaded test EPUB is missing: ${epubFile.absolutePath}", epubFile.isFile)

        val playlist = EpubMediaOverlayParser.parse(epubFile)
        Log.i(
            TAG,
            "Production parser returned ${playlist.items.size} clips from ${epubFile.name}; " +
                "first=${playlist.items.firstOrNull()}"
        )
        assertTrue("Production parser returned no media-overlay clips", playlist.items.isNotEmpty())
    }

    private companion object {
        const val TAG = "EpubOverlayProbe"
    }
}
