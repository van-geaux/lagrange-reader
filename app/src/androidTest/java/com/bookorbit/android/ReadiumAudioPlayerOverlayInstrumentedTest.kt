package com.bookorbit.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadiumAudioPlayerOverlayInstrumentedTest {
    @Test
    fun readerViewportTracksCompactPlayerHeightAndExpandsAfterClose() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val readerViewport = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val compactPlayer = View(context)

        bindReaderViewportAboveOverlay(readerViewport, compactPlayer)
        compactPlayer.layout(0, 0, 1080, 240)

        assertEquals(
            240,
            (readerViewport.layoutParams as FrameLayout.LayoutParams).bottomMargin
        )

        compactPlayer.layout(0, 0, 1080, 0)

        assertEquals(
            0,
            (readerViewport.layoutParams as FrameLayout.LayoutParams).bottomMargin
        )
    }
}
