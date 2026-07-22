package com.bookorbit.android

import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
/** Maps physical edge taps to the owning library's logical reading order. */
internal class LibraryDirectionalNavigationAdapter(
    private val navigator: OverflowableNavigator,
    private val readingDirection: LibraryReadingDirection,
    private val horizontalEdgeThresholdPercent: Float = 0.25f,
    private val animatedTransition: Boolean = true
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        val width = navigator.publicationView.width.toFloat().takeIf { it > 0f } ?: return false
        val threshold = width * horizontalEdgeThresholdPercent.coerceIn(0f, 0.5f)
        val isLeftEdge = event.point.x <= threshold
        val isRightEdge = event.point.x >= width - threshold
        if (!isLeftEdge && !isRightEdge) return false

        val isForward = when (readingDirection) {
            LibraryReadingDirection.LEFT_TO_RIGHT -> isRightEdge
            LibraryReadingDirection.RIGHT_TO_LEFT -> isLeftEdge
        }
        return if (isForward) {
            navigator.goForward(animatedTransition)
        } else {
            navigator.goBackward(animatedTransition)
        }
    }
}
