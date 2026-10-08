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
import androidx.compose.material3.HorizontalDivider
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
import java.util.Locale

@Composable
internal fun EpubTtsControls(
    settings: EpubTtsSettings,
    isPlaying: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    voiceLanguageTag: String? = null,
    voices: List<EpubTtsVoice> = emptyList(),
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
                voiceLanguageTag = voiceLanguageTag,
                voices = voices,
                onApply = onSettingsChange,
                onDismiss = { settingsVisible = false }
            )
        }
    }
}

@Composable
private fun EpubTtsSettingsDialog(
    settings: EpubTtsSettings,
    voiceLanguageTag: String?,
    voices: List<EpubTtsVoice>,
    onApply: (EpubTtsSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val normalized = settings.normalized()
    var speedText by remember {
        mutableStateOf(formatEpubTtsRate(normalized.speed))
    }
    var pitchText by remember {
        mutableStateOf(formatEpubTtsRate(normalized.pitch))
    }
    var customPausesEnabled by remember {
        mutableStateOf(normalized.pauses.enabled)
    }
    var showBookTitleOnLockScreen by remember {
        mutableStateOf(normalized.showBookTitleOnLockScreen)
    }
    var voiceDialogVisible by remember { mutableStateOf(false) }
    var commaText by remember {
        mutableStateOf(normalized.pauses.commaMillis.toString())
    }
    var semicolonText by remember {
        mutableStateOf(normalized.pauses.semicolonMillis.toString())
    }
    var colonText by remember {
        mutableStateOf(normalized.pauses.colonMillis.toString())
    }
    var emDashText by remember {
        mutableStateOf(normalized.pauses.emDashMillis.toString())
    }
    var ellipsisText by remember {
        mutableStateOf(normalized.pauses.ellipsisMillis.toString())
    }
    var parenthesesText by remember {
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

    fun applyImmediateRate(speed: Float? = null, pitch: Float? = null) {
        speed?.let { speedText = formatEpubTtsRate(it) }
        pitch?.let { pitchText = formatEpubTtsRate(it) }
        onApply(normalized.copy(speed = speed ?: normalized.speed, pitch = pitch ?: normalized.pitch))
    }

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
                PlaybackRateAdjustmentSetting(
                    title = "Speed",
                    value = normalized.speed,
                    minHundredths = EPUB_TTS_RATE_MIN_HUNDREDTHS,
                    maxHundredths = EPUB_TTS_RATE_MAX_HUNDREDTHS,
                    rateDescription = "text-to-speech speed",
                    onValueChange = { applyImmediateRate(speed = it) }
                )
                PlaybackRateAdjustmentSetting(
                    title = "Pitch",
                    value = normalized.pitch,
                    minHundredths = EPUB_TTS_RATE_MIN_HUNDREDTHS,
                    maxHundredths = EPUB_TTS_RATE_MAX_HUNDREDTHS,
                    rateDescription = "text-to-speech pitch",
                    onValueChange = { applyImmediateRate(pitch = it) }
                )
                HorizontalDivider()
                Text("Voice", style = MaterialTheme.typography.titleSmall)
                val selectedVoiceId = voiceLanguageTag?.let(normalized.voiceIds::get)
                val selectedVoice = voices.firstOrNull { it.id == selectedVoiceId }
                TextButton(
                    onClick = { voiceDialogVisible = true },
                    enabled = voices.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            selectedVoice != null -> selectedVoice.displayName()
                            voices.isNotEmpty() -> "Default Android voice"
                            else -> "No compatible voices available"
                        }
                    )
                }
                Text(
                    "Voices are filtered to this EPUB's language. Network voices may send spoken text to their provider.",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                Text(
                    "Speed and pitch changes apply immediately. Settings below require Apply.",
                    style = MaterialTheme.typography.bodySmall
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
                        onCheckedChange = { customPausesEnabled = it },
                        modifier = Modifier.testTag("tts-custom-pauses")
                    )
                }
                if (customPausesEnabled) {
                    Text(
                        "Values are measured at 1× and scale with reading speed. Android voices " +
                            "may add their own latency between chunks.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TtsPauseSetting(
                        label = "Comma",
                        value = commaText,
                        isError = showValidationErrors && parsedComma == null,
                        enabled = true,
                        onValueChange = { commaText = it },
                        supportingText = "Use 0 if the pause sounds too long."
                    )
                    TtsPauseSetting(
                        label = "Semicolon",
                        value = semicolonText,
                        isError = showValidationErrors && parsedSemicolon == null,
                        enabled = true,
                        onValueChange = { semicolonText = it }
                    )
                    TtsPauseSetting(
                        label = "Colon",
                        value = colonText,
                        isError = showValidationErrors && parsedColon == null,
                        enabled = true,
                        onValueChange = { colonText = it }
                    )
                    TtsPauseSetting(
                        label = "Em dash",
                        value = emDashText,
                        isError = showValidationErrors && parsedEmDash == null,
                        enabled = true,
                        onValueChange = { emDashText = it }
                    )
                    TtsPauseSetting(
                        label = "Ellipsis (… or ...)",
                        value = ellipsisText,
                        isError = showValidationErrors && parsedEllipsis == null,
                        enabled = true,
                        onValueChange = { ellipsisText = it }
                    )
                    Text(
                        "Grouping marks applies to opening and closing parentheses, square " +
                            "brackets, and braces.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TtsPauseSetting(
                        label = "Grouping marks: ( ) [ ] { }",
                        value = parenthesesText,
                        isError = showValidationErrors && parsedParentheses == null,
                        enabled = true,
                        onValueChange = { parenthesesText = it }
                    )
                }
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
                Text(
                    "When enabled, the book title is shown in the lock-screen media controls.",
                    style = MaterialTheme.typography.bodySmall
                )
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
                                showBookTitleOnLockScreen = showBookTitleOnLockScreen,
                                voiceIds = normalized.voiceIds
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
    if (voiceDialogVisible) {
        AlertDialog(
            onDismissRequest = { voiceDialogVisible = false },
            title = { Text("Text-to-speech voice") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            onApply(normalized.copy(voiceIds = voiceLanguageTag?.let {
                                normalized.voiceIds - it
                            } ?: normalized.voiceIds))
                            voiceDialogVisible = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Default Android voice") }
                    voices.forEach { voice ->
                        TextButton(
                            onClick = {
                                onApply(normalized.copy(voiceIds = voiceLanguageTag?.let {
                                    normalized.voiceIds + (it to voice.id)
                                } ?: normalized.voiceIds))
                                voiceDialogVisible = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(voice.displayName()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { voiceDialogVisible = false }) { Text("Cancel") }
            }
        )
    }
}

internal fun EpubTtsVoice.displayName(): String = buildString {
    val localeName = Locale.forLanguageTag(languageTag)
        .getDisplayName(Locale.getDefault())
        .takeIf(String::isNotBlank)
        ?: languageTag
    append(localeName)
    append(" · ")
    append(if (requiresNetwork) "Network" else "Local")
    append(" · ")
    append(quality.lowercase().replaceFirstChar(Char::uppercase))
    append(" · ")
    append(voiceVariantLabel())
}

private fun EpubTtsVoice.voiceVariantLabel(): String {
    val normalizedId = id.lowercase(Locale.ROOT)
    val variantStart = normalizedId.indexOf("-x-")
    val variantEnd = listOf("-local", "-network")
        .mapNotNull { suffix -> normalizedId.indexOf(suffix, variantStart + 3).takeIf { it >= 0 } }
        .minOrNull()
    if (variantStart >= 0 && variantEnd != null && variantEnd > variantStart + 3) {
        return "Variant " + id.substring(variantStart + 3, variantEnd).uppercase(Locale.ROOT)
    }
    return "Voice " + id.substringAfterLast('-').ifBlank { id }
}

@Composable
private fun TtsPauseSetting(
    label: String,
    value: String,
    isError: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    supportingText: String? = null
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
            Text(
                when {
                    isError -> "Enter a whole number from 0 to 2000"
                    supportingText != null -> supportingText
                    else -> "0 to 2000 ms"
                }
            )
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
