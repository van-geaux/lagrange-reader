package com.vangeaux.lagrange

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val READER_SPREAD_POSTURE_DEBOUNCE_MILLIS = 400L

internal fun readerHingeFrom(
    isVertical: Boolean,
    isHalfOpened: Boolean,
    isSeparating: Boolean,
    leftPx: Int,
    rightPx: Int
): ReaderHinge = ReaderHinge(
    orientation = if (isVertical) ReaderHingeOrientation.VERTICAL else ReaderHingeOrientation.HORIZONTAL,
    isHalfOpened = isHalfOpened,
    isSeparating = isSeparating,
    leftPx = leftPx,
    rightPx = rightPx
)

internal fun FoldingFeature.toReaderHinge(): ReaderHinge = readerHingeFrom(
    isVertical = orientation == FoldingFeature.Orientation.VERTICAL,
    isHalfOpened = state == FoldingFeature.State.HALF_OPENED,
    isSeparating = isSeparating,
    leftPx = bounds.left,
    rightPx = bounds.right
)

/**
 * Activity-scoped window geometry for reader spread decisions. Size changes recreate reader
 * activities, so the initial configuration snapshot is authoritative for size; the WindowManager
 * flow adds hinge posture, which can change without recreation.
 */
internal class ReaderWindowTracker(private val activity: FragmentActivity) {
    // Seeded with the last hinge seen in this process so a recreated reader starts from the
    // current posture instead of "no hinge"; otherwise a posture-driven recreation could loop.
    private val mutableState = MutableStateFlow(
        ReaderWindowState.fromConfiguration(activity.resources.configuration, lastKnownHinge)
    )
    val state: StateFlow<ReaderWindowState> = mutableState.asStateFlow()

    fun start() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collect { info ->
                        val hinge = info.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                            ?.toReaderHinge()
                        lastKnownHinge = hinge
                        mutableState.value = ReaderWindowState.fromConfiguration(
                            activity.resources.configuration,
                            hinge
                        )
                    }
            }
        }
    }

    /**
     * Emits only when [decide] flips after the initial state, debounced so a hinge moving through
     * intermediate postures does not recreate the reader repeatedly.
     */
    @OptIn(FlowPreview::class)
    fun spreadChanges(decide: () -> Boolean): Flow<Boolean> = state
        .map { decide() }
        .distinctUntilChanged()
        .drop(1)
        .debounce(READER_SPREAD_POSTURE_DEBOUNCE_MILLIS)

    /**
     * Runs [onMaybeChanged] when the spread decision flips, but only while the activity is started:
     * a debounced posture change must not reach a fragment transaction after state is saved. Each
     * start re-checks once, so a posture change made while stopped is applied on return.
     */
    fun observeSpreadChanges(decide: () -> Boolean, onMaybeChanged: () -> Unit) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                onMaybeChanged()
                spreadChanges(decide).collect { onMaybeChanged() }
            }
        }
    }

    companion object {
        @Volatile
        private var lastKnownHinge: ReaderHinge? = null
    }
}
