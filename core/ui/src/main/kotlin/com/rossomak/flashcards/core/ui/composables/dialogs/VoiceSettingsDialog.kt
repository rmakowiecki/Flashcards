package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.voiceLabel
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.theme.spacing

/** Slowest selectable speech rate. */
private const val MIN_SPEECH_RATE = 0.5f

/** Fastest selectable speech rate. */
private const val MAX_SPEECH_RATE = 2f

/**
 * Picks the TTS voice and speech rate.
 *
 * Deferred commit: [draftVoiceId] and [draftSpeechRate] are in-flight values and nothing is
 * persisted until [onConfirm]. Dismissing discards them. Note that *previewing* a voice is not
 * persistence — the caller is free to play a sample on every draft change.
 *
 * [seededVoiceLabel] names the saved voice until [availableVoices] loads.
 *
 * Pass [keepAsDefault] as non-null where the choice is session-scoped (the Preview Study Session
 * screen); `null` on the Settings screen, where the change is permanent by definition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsDialog(
    availableVoices: List<VoiceOption>,
    draftVoiceId: String?,
    onDraftVoiceChange: (String?) -> Unit,
    draftSpeechRate: Float,
    onDraftSpeechRateChange: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    seededVoiceLabel: VoiceLabel? = null,
    keepAsDefault: Boolean? = null,
    onKeepAsDefaultChange: (Boolean) -> Unit = {},
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedVoiceLabel = availableVoices.firstOrNull { it.id == draftVoiceId }?.voiceLabel
        ?: seededVoiceLabel.takeIf { availableVoices.isEmpty() }

    FlashcardsSingleActionDialog(
        title = stringResource(R.string.voice_settings_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        keepAsDefault = keepAsDefault,
        onKeepAsDefaultChange = onKeepAsDefaultChange,
    ) {
        Column {
            Text(
                text = stringResource(R.string.voice_settings_voice_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.xxsmall))
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedVoiceLabel?.label() ?: stringResource(R.string.voice_settings_voice_hint),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.label()) },
                            onClick = {
                                dropdownExpanded = false
                                onDraftVoiceChange(voice.id)
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
            SpeechRateSection(
                speechRate = draftSpeechRate,
                onSpeechRateChange = onDraftSpeechRateChange,
            )
        }
    }
}

/** The speed label, slider and its end labels — extracted to keep the dialog itself scannable. */
@Composable
private fun SpeechRateSection(
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.voice_settings_speed_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = speechRateLabel(speechRate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = speechRate,
            onValueChange = onSpeechRateChange,
            valueRange = MIN_SPEECH_RATE..MAX_SPEECH_RATE,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = speechRateLabel(MIN_SPEECH_RATE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = speechRateLabel(MAX_SPEECH_RATE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun VoiceSettingsDialogSessionPreview() {
    VoiceSettingsDialog(
        availableVoices = listOf(
            VoiceOption(id = "en-us-x-1", countryCode = "US", variantIndex = 1),
            VoiceOption(id = "en-gb-x-1", countryCode = "GB", variantIndex = 1),
        ),
        draftVoiceId = "en-us-x-1",
        draftSpeechRate = 1.25f,
        onDraftVoiceChange = {},
        onDraftSpeechRateChange = {},
        onConfirm = {},
        onDismiss = {},
        keepAsDefault = false,
    )
}

@Preview
@Composable
private fun VoiceSettingsDialogNoSelectionPreview() {
    VoiceSettingsDialog(
        availableVoices = emptyList(),
        draftVoiceId = null,
        draftSpeechRate = 1f,
        onDraftVoiceChange = {},
        onDraftSpeechRateChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}
