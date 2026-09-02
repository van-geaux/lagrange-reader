package com.vangeaux.lagrange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class AudiobookSeekConfirmationPlacement {
    ABOVE_ANCHOR,
    OVER_ANCHOR
}

internal fun audiobookSeekConfirmationPosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    marginPx: Int,
    placement: AudiobookSeekConfirmationPlacement
): IntOffset {
    val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val x = centeredX.coerceIn(0, maxX)
    val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
    val y = when (placement) {
        AudiobookSeekConfirmationPlacement.ABOVE_ANCHOR -> {
            val aboveY = anchorBounds.top - popupContentSize.height - marginPx
            val belowY = anchorBounds.bottom + marginPx
            if (aboveY >= 0) aboveY else min(belowY, maxY)
        }
        AudiobookSeekConfirmationPlacement.OVER_ANCHOR -> {
            (anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2)
                .coerceIn(0, maxY)
        }
    }
    return IntOffset(x, max(y, 0))
}

@Composable
internal fun AudiobookSeekConfirmationOverlay(
    onKeepPosition: () -> Unit,
    onReturnToPrevious: () -> Unit,
    onDontShowAgainChange: (Boolean) -> Unit,
    anchorBounds: IntRect?,
    placement: AudiobookSeekConfirmationPlacement,
    modifier: Modifier = Modifier
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { 12.dp.toPx().roundToInt() }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val windowSize = with(LocalDensity.current) {
            IntSize(
                (LocalConfiguration.current.screenWidthDp.dp.toPx()).roundToInt(),
                (LocalConfiguration.current.screenHeightDp.dp.toPx()).roundToInt()
            )
        }
        Box(modifier = Modifier.fillMaxSize().testTag("audiobook-seek-confirmation-modal")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f))
                    .clickable(onClick = {})
                    .testTag("audiobook-seek-confirmation-scrim")
            )
            val cardPosition = anchorBounds?.takeIf { cardSize != IntSize.Zero }?.let {
                audiobookSeekConfirmationPosition(
                    anchorBounds = it,
                    windowSize = windowSize,
                    popupContentSize = cardSize,
                    marginPx = marginPx,
                    placement = placement
                )
            }
            Surface(
                modifier = modifier
                    .widthIn(max = 360.dp)
                    .onGloballyPositioned { cardSize = it.size }
                    .then(cardPosition?.let { Modifier.offset { it } } ?: Modifier.align(Alignment.Center))
                    .testTag("audiobook-seek-confirmation"),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Did you tap that?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep this new audiobook position?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = {
                                dontShowAgain = it
                                onDontShowAgainChange(it)
                            },
                            modifier = Modifier.testTag("audiobook-seek-dont-show-again")
                        )
                        Text(
                            "Don't show confirmations again",
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        "You can re-enable confirmations in Options.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = onKeepPosition,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audiobook-seek-keep-position")
                    ) { Text("Yes, keep position") }
                    TextButton(
                        onClick = onReturnToPrevious,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audiobook-seek-go-back")
                    ) { Text("No, go back") }
                }
            }
        }
    }
}
