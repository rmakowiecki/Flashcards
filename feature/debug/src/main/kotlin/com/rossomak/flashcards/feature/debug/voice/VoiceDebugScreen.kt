package com.rossomak.flashcards.feature.debug.voice

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicator
import com.rossomak.flashcards.feature.debug.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

@Composable
fun VoiceDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: VoiceDebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionDeniedMessage = stringResource(R.string.voice_debug_mic_permission_message)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            pendingMicAction?.invoke()
        } else {
            Toast.makeText(context, micPermissionDeniedMessage, Toast.LENGTH_SHORT).show()
        }
        pendingMicAction = null
    }

    fun withMicPermission(action: () -> Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    VoiceDebugContent(
        modifier = modifier,
        state = state,
        voiceBarsLevels = viewModel.voiceBarsLevels,
        onNavigateBack = onNavigateBack,
        onVadToggle = { withMicPermission(viewModel::onVadToggle) },
        onPlayCapturedUtterance = viewModel::onPlayCapturedUtterance,
        onRecordClip = { withMicPermission(viewModel::onRecordClip) },
        onPlayRawClip = viewModel::onPlayRawClip,
        onPlayObfuscatedClip = viewModel::onPlayObfuscatedClip,
        onRerandomizeObfuscation = viewModel::onRerandomizeObfuscation,
        onTranscribeClip = viewModel::onTranscribeClip,
        onCheckEntitlement = viewModel::onCheckEntitlement,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDebugContent(
    modifier: Modifier = Modifier,
    state: VoiceDebugScreenState,
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
    onNavigateBack: () -> Unit,
    onVadToggle: () -> Unit,
    onPlayCapturedUtterance: () -> Unit,
    onRecordClip: () -> Unit,
    onPlayRawClip: () -> Unit,
    onPlayObfuscatedClip: () -> Unit,
    onRerandomizeObfuscation: () -> Unit,
    onTranscribeClip: () -> Unit,
    onCheckEntitlement: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.voice_debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.debug_voice_harness_back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AudioRouteBanner(
                micLabel = state.micRouteLabel,
                playbackLabel = state.playbackRouteLabel,
            )

            DebugBlock(title = stringResource(R.string.voice_debug_vad_title)) {
                LevelIndicator(
                    voiceBarsLevels = voiceBarsLevels,
                    isPlayback = state.isPlayingLastAnswer,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onVadToggle) {
                        Text(
                            stringResource(
                                if (state.isVadListening) {
                                    R.string.voice_debug_vad_stop_button
                                } else {
                                    R.string.voice_debug_vad_start_button
                                }
                            )
                        )
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    SpeechStatusLabel(isSpeechDetected = state.isSpeechDetected)
                }
                Text(
                    text = stringResource(R.string.voice_debug_vad_probability_label, state.vadSpeechProbability),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onPlayCapturedUtterance, enabled = state.hasCapturedUtterance) {
                        Text(stringResource(R.string.voice_debug_vad_play_utterance_button))
                    }
                    if (state.hasCapturedUtterance) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(
                                R.string.voice_debug_vad_utterance_ready_label,
                                state.capturedUtteranceDurationMs,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                state.vadEventLog.forEach { line -> RawDataText(line) }
            }

            DebugBlock(title = stringResource(R.string.voice_debug_capture_title)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRecordClip, enabled = !state.isRecordingClip) {
                        Text(
                            stringResource(
                                if (state.isRecordingClip) {
                                    R.string.voice_debug_capture_recording_label
                                } else {
                                    R.string.voice_debug_capture_record_button
                                }
                            )
                        )
                    }
                    OutlinedButton(onClick = onPlayRawClip, enabled = state.hasRawClip) {
                        Text(stringResource(R.string.voice_debug_capture_play_raw_button))
                    }
                }
                if (state.hasRawClip) {
                    Text(
                        text = stringResource(R.string.voice_debug_capture_clip_ready_label, state.rawClipDurationMs),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            DebugBlock(title = stringResource(R.string.voice_debug_obfuscation_title)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPlayRawClip, enabled = state.hasRawClip) {
                        Text(stringResource(R.string.voice_debug_capture_play_raw_button))
                    }
                    Button(onClick = onPlayObfuscatedClip, enabled = state.hasRawClip) {
                        Text(stringResource(R.string.voice_debug_obfuscation_play_obfuscated_button))
                    }
                }
                OutlinedButton(onClick = onRerandomizeObfuscation) {
                    Text(stringResource(R.string.voice_debug_obfuscation_rerandomize_button))
                }
                if (!state.hasRawClip) {
                    Text(
                        text = stringResource(R.string.voice_debug_obfuscation_no_clip_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            DebugBlock(title = stringResource(R.string.voice_debug_transcription_title)) {
                Button(onClick = onTranscribeClip, enabled = state.hasRawClip && !state.isTranscribing) {
                    Text(
                        stringResource(
                            if (state.isTranscribing) {
                                R.string.voice_debug_transcription_running_label
                            } else {
                                R.string.voice_debug_transcription_send_button
                            }
                        )
                    )
                }
                state.transcriptionResult?.let { RawDataText(it) }
            }

            DebugBlock(title = stringResource(R.string.voice_debug_entitlement_title)) {
                Button(onClick = onCheckEntitlement, enabled = !state.isCheckingEntitlement) {
                    Text(
                        stringResource(
                            if (state.isCheckingEntitlement) {
                                R.string.voice_debug_entitlement_checking_label
                            } else {
                                R.string.voice_debug_entitlement_check_button
                            }
                        )
                    )
                }
                state.entitlementResult?.let { RawDataText(it) }
            }
        }
    }
}

/** Persistent mic/playback route indicator — visible above every block, independent of which one is active. */
@Composable
private fun AudioRouteBanner(micLabel: String, playbackLabel: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_debug_mic_route_label, micLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.voice_debug_playback_route_label, playbackLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DebugBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

/**
 * Collects the level here so each new level recomposes only the indicator. A speaker replaces the
 * microphone while the last answer plays back.
 */
@Composable
private fun LevelIndicator(voiceBarsLevels: StateFlow<ImmutableList<Float>>, isPlayback: Boolean, modifier: Modifier = Modifier) {
    val currentVoiceBarsLevels by voiceBarsLevels.collectAsStateWithLifecycle()
    FlashcardsVoiceCaptureIndicator(
        levels = currentVoiceBarsLevels,
        contentDescription = stringResource(
            if (isPlayback) CoreUiR.string.common_voice_capture_playing_cd else CoreUiR.string.common_voice_capture_listening_cd,
        ),
        modifier = modifier,
        icon = if (isPlayback) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Mic,
    )
}

/**
 * Silence / speech label reserving the width of the longer "speech detected" text, so swapping
 * between the two never shifts the row.
 */
@Composable
private fun SpeechStatusLabel(isSpeechDetected: Boolean) {
    val speechText = stringResource(R.string.voice_debug_vad_speech_label)
    Box {
        Text(
            text = speechText,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Text(
            text = if (isSpeechDetected) speechText else stringResource(R.string.voice_debug_vad_silence_label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun RawDataText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
            .horizontalScroll(rememberScrollState()),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}
