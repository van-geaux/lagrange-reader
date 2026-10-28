package com.vangeaux.lagrange

import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
/** Maps configured physical tap zones to the owning library's logical reading order. */
internal class LibraryDirectionalNavigationAdapter(
    private val navigator: OverflowableNavigator,
    private val readingDirection: () -> LibraryReadingDirection,
    private val tapZoneLayout: () -> ReaderTapZoneLayout = { ReaderTapZoneLayout.CURRENT_EDGES },
    private val tapZoneInvertMode: () -> ReaderTapZoneInvertMode = { ReaderTapZoneInvertMode.NONE },
    private val onMenu: () -> Unit = {},
    /** Called before a tap-zone page turn and when a swipe starts: deliberate moves by the reader. */
    private val onPageTurn: () -> Unit = {},
    private val animatedTransition: Boolean = true
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        val width = navigator.publicationView.width.toFloat().takeIf { it > 0f } ?: return false
        val height = navigator.publicationView.height.toFloat().takeIf { it > 0f } ?: return false
        return when (
            readerTapZoneAction(
                x = event.point.x,
                y = event.point.y,
                width = width,
                height = height,
                layout = tapZoneLayout(),
                readingDirection = readingDirection(),
                invertMode = tapZoneInvertMode()
            )
        ) {
            ReaderTapZoneAction.PREVIOUS -> {
                onPageTurn()
                navigator.goBackward(animatedTransition)
            }
            ReaderTapZoneAction.NEXT -> {
                onPageTurn()
                navigator.goForward(animatedTransition)
            }
            ReaderTapZoneAction.MENU -> {
                onMenu()
                true
            }
        }
    }

    override fun onDrag(event: DragEvent): Boolean {
        if (event.type == DragEvent.Type.Start) onPageTurn()
        // Observed only: the navigator still performs the swipe itself.
        return false
    }
}
