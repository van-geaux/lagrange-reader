package com.vangeaux.lagrange

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadiumAudioPlayerOverlayInstrumentedTest {
    @Test
    fun readerViewportTracksTheTallerOverlayAndReclaimsSpaceImmediatelyWhenOverlaysClose() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = FrameLayout(context)
        val readerViewport = FrameLayout(context).apply {
            setPadding(0, 0, 0, 72)
        }
        val footer = View(context)
        val compactPlayer = View(context)
        val readAlongPlayer = View(context)
        root.addView(
            readerViewport,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        readerViewport.addView(
            footer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30, Gravity.BOTTOM)
        )
        root.addView(
            compactPlayer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 240, Gravity.BOTTOM)
        )
        root.addView(
            readAlongPlayer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420, Gravity.BOTTOM)
        )

        val updateOverlaySpace = bindReaderViewportAboveOverlays(
            readerViewport,
            listOf(compactPlayer, readAlongPlayer)
        )
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        fun layoutReader() {
            root.measure(widthSpec, heightSpec)
            root.layout(0, 0, 1080, 2400)
            updateOverlaySpace()
            root.measure(widthSpec, heightSpec)
            root.layout(0, 0, 1080, 2400)
        }

        layoutReader()
        assertEquals(348, (readerViewport.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(readAlongPlayer.top, footer.bottom)

        readAlongPlayer.visibility = View.GONE
        updateOverlaySpace()
        layoutReader()
        assertEquals(168, (readerViewport.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(compactPlayer.top, footer.bottom)

        compactPlayer.visibility = View.GONE
        updateOverlaySpace()
        layoutReader()
        assertEquals(0, (readerViewport.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(2328, footer.bottom)
    }
}
