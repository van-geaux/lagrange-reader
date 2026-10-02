package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun EpubTtsControls(
    settings: EpubTtsSettings,
    isPlaying: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSettingsChange: (EpubTtsSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var settingsVisible by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Text to speech",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(
                    onClick = { settingsVisible = true },
                    modifier = Modifier
                        .testTag("epub-tts-settings")
                        .semantics { contentDescription = "Text-to-speech settings" }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }
                IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous spoken sentence")
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.testTag("epub-tts-play-pause")
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) {
                            "Pause text to speech"
                        } else {
                            "Play text to speech"
                        }
                    )
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next spoken sentence")
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("epub-tts-close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close text to speech")
                }
            }
        }
        if (settingsVisible) {
            EpubTtsSettingsDialog(
                settings = settings,
                onApply = onSettingsChange,
                onDismiss = { settingsVisible = false }
            )
        }
    }
}

@Composable
private fun EpubTtsSettingsDialog(
    settings: EpubTtsSettings,
    onApply: (EpubTtsSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val normalized = settings.normalized()
    var speedText by remember(normalized) {
        mutableStateOf(formatEpubTtsRate(normalized.speed))
    }
    var pitchText by remember(normalized) {
        mutableStateOf(formatEpubTtsRate(normalized.pitch))
    }
    var customPausesEnabled by remember(normalized) {
        mutableStateOf(normalized.pauses.enabled)
    }
    var showBookTitleOnLockScreen by remember(normalized) {
        mutableStateOf(normalized.showBookTitleOnLockScreen)
    }
    var commaText by remember(normalized) {
        mutableStateOf(normalized.pauses.commaMillis.toString())
    }
    var semicolonText by remember(normalized) {
        mutableStateOf(normalized.pauses.semicolonMillis.toString())
    }
    var colonText by remember(normalized) {
        mutableStateOf(normalized.pauses.colonMillis.toString())
    }
    var emDashText by remember(normalized) {
        mutableStateOf(normalized.pauses.emDashMillis.toString())
    }
    var ellipsisText by remember(normalized) {
        mutableStateOf(normalized.pauses.ellipsisMillis.toString())
    }
    var parenthesesText by remember(normalized) {
        mutableStateOf(normalized.pauses.parenthesesMillis.toString())
    }
    var showValidationErrors by remember { mutableStateOf(false) }

    val parsedSpeed = parseEpubTtsRate(speedText)
    val parsedPitch = parseEpubTtsRate(pitchText)
    val parsedComma = parseEpubTtsPauseMillis(commaText)
    val parsedSemicolon = parseEpubTtsPauseMillis(semicolonText)
    val parsedColon = parseEpubTtsPauseMillis(colonText)
    val parsedEmDash = parseEpubTtsPauseMillis(emDashText)
    val parsedEllipsis = parseEpubTtsPauseMillis(ellipsisText)
    val parsedParentheses = parseEpubTtsPauseMillis(parenthesesText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text-to-speech settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TtsRateSetting(
                    title = "Speed",
                    value = speedText,
                    presets = EPUB_TTS_PLAYBACK_SPEED_OPTIONS,
                    isError = showValidationErrors && parsedSpeed == null,
                    rateDescription = "text-to-speech speed",
                    onValueChange = { speedText = it }
                )
                TtsRateSetting(
                    title = "Pitch",
                    value = pitchText,
                    presets = EPUB_TTS_PITCH_OPTIONS,
                    isError = showValidationErrors && parsedPitch == null,
                    rateDescription = "text-to-speech pitch",
                    onValueChange = { pitchText = it }
                )
                Text("Extra punctuation pauses", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Use custom punctuation pauses",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = customPausesEnabled,
                        onCheckedChange = { customPausesEnabled = it }
                    )
                }
                Text(
                    "Values are at 1× and scale with reading speed. Grouping marks applies to " +
                        "opening and closing parentheses, square brackets, and braces. Turn " +
                        "custom pauses off to use only the Android speech engine's handling. " +
                        "Some voices add their own latency between chunks; use 0 for commas if " +
                        "small values sound too long.",
                    style = MaterialTheme.typography.bodySmall
                )
                TtsPauseSetting(
                    label = "Comma",
                    value = commaText,
                    isError = customPausesEnabled && showValidationErrors && parsedComma == null,
                    enabled = customPausesEnabled,
                    onValueChange = { commaText = it }
                )
                TtsPauseSetting(
                    label = "Semicolon",
                    value = semicolonText,
                    isError = customPausesEnabled && showValidationErrors && parsedSemicolon == null,
                    enabled = customPausesEnabled,
                    onValueChange = { semicolonText = it }
                )
                TtsPauseSetting(
                    label = "Colon",
                    value = colonText,
                    isError = customPausesEnabled && showValidationErrors && parsedColon == null,
                    enabled = customPausesEnabled,
                    onValueChange = { colonText = it }
                )
                TtsPauseSetting(
                    label = "Em dash",
                    value = emDashText,
                    isError = customPausesEnabled && showValidationErrors && parsedEmDash == null,
                    enabled = customPausesEnabled,
                    onValueChange = { emDashText = it }
                )
                TtsPauseSetting(
                    label = "Ellipsis (… or ...)",
                    value = ellipsisText,
                    isError = customPausesEnabled && showValidationErrors && parsedEllipsis == null,
                    enabled = customPausesEnabled,
                    onValueChange = { ellipsisText = it }
                )
                TtsPauseSetting(
                    label = "Grouping marks: ( ) [ ] { }",
                    value = parenthesesText,
                    isError = customPausesEnabled && showValidationErrors &&
                        parsedParentheses == null,
                    enabled = customPausesEnabled,
                    onValueChange = { parenthesesText = it }
                )
                Text("Lock-screen privacy", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Show book title on lock screen",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = showBookTitleOnLockScreen,
                        onCheckedChange = { showBookTitleOnLockScreen = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val invalidPauses = customPausesEnabled && (
                        parsedComma == null || parsedSemicolon == null || parsedColon == null ||
                            parsedEmDash == null || parsedEllipsis == null ||
                            parsedParentheses == null
                        )
                    if (parsedSpeed == null || parsedPitch == null || invalidPauses
                    ) {
                        showValidationErrors = true
                    } else {
                        onApply(
                            EpubTtsSettings(
                                speed = parsedSpeed,
                                pitch = parsedPitch,
                                pauses = EpubTtsPauseSettings(
                                    enabled = customPausesEnabled,
                                    commaMillis = parsedComma ?: normalized.pauses.commaMillis,
                                    semicolonMillis = parsedSemicolon
                                        ?: normalized.pauses.semicolonMillis,
                                    colonMillis = parsedColon ?: normalized.pauses.colonMillis,
                                    emDashMillis = parsedEmDash ?: normalized.pauses.emDashMillis,
                                    ellipsisMillis = parsedEllipsis
                                        ?: normalized.pauses.ellipsisMillis,
                                    parenthesesMillis = parsedParentheses
                                        ?: normalized.pauses.parenthesesMillis
                                ),
                                showBookTitleOnLockScreen = showBookTitleOnLockScreen
                            )
                        )
                        onDismiss()
                    }
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TtsRateSetting(
    title: String,
    value: String,
    presets: List<Float>,
    isError: Boolean,
    rateDescription: String,
    onValueChange: (String) -> Unit
) {
    PlaybackRateSetting(
        title = title,
        value = value,
        minHundredths = EPUB_TTS_RATE_MIN_HUNDREDTHS,
        maxHundredths = EPUB_TTS_RATE_MAX_HUNDREDTHS,
        isError = isError,
        rateDescription = rateDescription,
        presets = presets,
        onValueChange = onValueChange
    )
}

@Composable
private fun TtsPauseSetting(
    label: String,
    value: String,
    isError: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text("ms") },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            Text(if (isError) "Enter a whole number from 0 to 2000" else "0 to 2000 ms")
        }
    )
}

internal fun formatEpubTtsPlaybackSpeed(speed: Float): String = formatEpubTtsRate(speed)

internal fun formatEpubTtsRate(value: Float): String =
    formatEditablePlaybackRate(value)

internal enum class EpubListenChoice {
    PUBLISHER_NARRATION,
    TTS_FROM_HERE,
    TTS_KEEP_LISTENING
}

internal fun epubListenChoices(
    hasPublisherNarration: Boolean,
    hasTextToSpeech: Boolean,
    canKeepListening: Boolean
): List<EpubListenChoice> = buildList {
    if (hasPublisherNarration) add(EpubListenChoice.PUBLISHER_NARRATION)
    if (hasTextToSpeech) add(EpubListenChoice.TTS_FROM_HERE)
    if (hasTextToSpeech && canKeepListening) add(EpubListenChoice.TTS_KEEP_LISTENING)
}
