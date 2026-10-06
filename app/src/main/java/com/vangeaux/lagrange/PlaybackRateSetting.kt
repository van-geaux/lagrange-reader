package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal const val PLAYBACK_RATE_FINE_STEP_HUNDREDTHS = 5
internal const val PLAYBACK_RATE_COARSE_STEP_HUNDREDTHS = 10

internal fun parsePlaybackRate(
    value: String,
    minHundredths: Int,
    maxHundredths: Int
): Float? {
    val parsed = value.trim().replace(',', '.').toFloatOrNull() ?: return null
    if (!parsed.isFinite()) return null
    val rawHundredths = parsed * 100f
    if (rawHundredths < minHundredths || rawHundredths > maxHundredths) return null
    val hundredths = rawHundredths.roundToInt()
    return hundredths / 100f
}

internal fun adjustPlaybackRate(
    value: Float,
    deltaHundredths: Int,
    minHundredths: Int,
    maxHundredths: Int
): Float {
    val currentHundredths = ((value.takeIf(Float::isFinite) ?: 1f) * 100f).roundToInt()
    return (currentHundredths + deltaHundredths)
        .coerceIn(minHundredths, maxHundredths) / 100f
}

@Composable
internal fun PlaybackRateSetting(
    title: String,
    value: String,
    minHundredths: Int,
    maxHundredths: Int,
    isError: Boolean,
    rateDescription: String,
    presets: List<Float> = emptyList(),
    onValueChange: (String) -> Unit
) {
    val parsedValue = parsePlaybackRate(value, minHundredths, maxHundredths)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        PlaybackRateAdjustmentRow(
            value = parsedValue ?: 1f,
            minHundredths = minHundredths,
            maxHundredths = maxHundredths,
            rateDescription = rateDescription,
            enabled = parsedValue != null,
            onValueChange = { onValueChange(formatEditablePlaybackRate(it)) }
        )
        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = parsedValue == preset,
                        onClick = { onValueChange(formatEditablePlaybackRate(preset)) },
                        label = { Text("${formatEditablePlaybackRate(preset)}\u00d7") }
                    )
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom ${title.lowercase()}") },
            suffix = { Text("×") },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
                val range = "${formatEditablePlaybackRate(minHundredths / 100f)} to " +
                    formatEditablePlaybackRate(maxHundredths / 100f)
                Text(if (isError) "Enter a value from $range" else range)
            }
        )
    }
}

@Composable
internal fun PlaybackRateAdjustmentSetting(
    title: String,
    value: Float,
    minHundredths: Int,
    maxHundredths: Int,
    rateDescription: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        PlaybackRateAdjustmentRow(
            value = value,
            minHundredths = minHundredths,
            maxHundredths = maxHundredths,
            rateDescription = rateDescription,
            onValueChange = onValueChange
        )
    }
}

@Composable
internal fun PlaybackRateAdjustmentRow(
    value: Float,
    minHundredths: Int,
    maxHundredths: Int,
    rateDescription: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    val valueHundredths = (value * 100f).roundToInt().coerceIn(minHundredths, maxHundredths)
    val applyAdjustment: (Int) -> Unit = { delta ->
        onValueChange(adjustPlaybackRate(value, delta, minHundredths, maxHundredths))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaybackRateAdjustmentButton(
            label = "−0.10",
            description = "Decrease $rateDescription by 0.10",
            modifier = Modifier.weight(1f),
            enabled = enabled &&
                valueHundredths - PLAYBACK_RATE_COARSE_STEP_HUNDREDTHS >= minHundredths,
            onClick = { applyAdjustment(-PLAYBACK_RATE_COARSE_STEP_HUNDREDTHS) }
        )
        PlaybackRateAdjustmentButton(
            label = "−0.05",
            description = "Decrease $rateDescription by 0.05",
            modifier = Modifier.weight(1f),
            enabled = enabled &&
                valueHundredths - PLAYBACK_RATE_FINE_STEP_HUNDREDTHS >= minHundredths,
            onClick = { applyAdjustment(-PLAYBACK_RATE_FINE_STEP_HUNDREDTHS) }
        )
        Text(
            text = "${formatPlaybackSpeed(valueHundredths / 100.0)}×",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.headlineSmall
        )
        PlaybackRateAdjustmentButton(
            label = "+0.05",
            description = "Increase $rateDescription by 0.05",
            modifier = Modifier.weight(1f),
            enabled = enabled &&
                valueHundredths + PLAYBACK_RATE_FINE_STEP_HUNDREDTHS <= maxHundredths,
            onClick = { applyAdjustment(PLAYBACK_RATE_FINE_STEP_HUNDREDTHS) }
        )
        PlaybackRateAdjustmentButton(
            label = "+0.10",
            description = "Increase $rateDescription by 0.10",
            modifier = Modifier.weight(1f),
            enabled = enabled &&
                valueHundredths + PLAYBACK_RATE_COARSE_STEP_HUNDREDTHS <= maxHundredths,
            onClick = { applyAdjustment(PLAYBACK_RATE_COARSE_STEP_HUNDREDTHS) }
        )
    }
}

@Composable
private fun PlaybackRateAdjustmentButton(
    label: String,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .semantics { contentDescription = description }
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun formatEditablePlaybackRate(value: Float): String =
    formatPlaybackSpeed(value.toDouble())
        .trimEnd('0')
        .trimEnd('.')
