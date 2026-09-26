package com.rossomak.flashcards.feature.debug.voiceindicator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicator
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.debug.R
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun VoiceIndicatorDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: VoiceIndicatorDebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VoiceIndicatorDebugContent(
        modifier = modifier,
        state = state,
        voiceBarsLevels = viewModel.voiceBarsLevels,
        onNavigateBack = onNavigateBack,
        onSpeechSimulatedChange = viewModel::onSpeechSimulatedChange,
        onLevelIntervalChange = viewModel::onLevelIntervalChange,
    )
}

/** [voiceBarsLevels] is a flow so only [LiveVoiceCaptureIndicator] recomposes per snapshot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceIndicatorDebugContent(
    modifier: Modifier = Modifier,
    state: VoiceIndicatorDebugScreenState,
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
    onNavigateBack: () -> Unit,
    onSpeechSimulatedChange: (Boolean) -> Unit,
    onLevelIntervalChange: (Int) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.voice_indicator_debug_title)) },
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
                .padding(MaterialTheme.spacing.normal),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.xlarge),
                contentAlignment = Alignment.Center,
            ) {
                LiveVoiceCaptureIndicator(voiceBarsLevels = voiceBarsLevels, levelIntervalMillis = state.levelIntervalMillis)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.voice_indicator_debug_speech_label))
                Switch(checked = state.isSpeechSimulated, onCheckedChange = onSpeechSimulatedChange)
            }
            Column {
                Text(text = stringResource(R.string.voice_indicator_debug_interval_label, state.levelIntervalMillis))
                Slider(
                    value = state.levelIntervalMillis.toFloat(),
                    onValueChange = { interval -> onLevelIntervalChange(interval.roundToInt()) },
                    valueRange = MIN_LEVEL_INTERVAL_MILLIS..MAX_LEVEL_INTERVAL_MILLIS,
                )
            }
        }
    }
}

@Composable
private fun LiveVoiceCaptureIndicator(voiceBarsLevels: StateFlow<ImmutableList<Float>>, levelIntervalMillis: Int) {
    val currentVoiceBarsLevels by voiceBarsLevels.collectAsStateWithLifecycle()
    FlashcardsVoiceCaptureIndicator(
        levels = currentVoiceBarsLevels,
        contentDescription = stringResource(CoreUiR.string.common_voice_capture_listening_cd),
        levelIntervalMillis = levelIntervalMillis,
    )
}

private const val MIN_LEVEL_INTERVAL_MILLIS = 40f
private const val MAX_LEVEL_INTERVAL_MILLIS = 150f

private val PreviewWaveLevels = persistentListOf(0.35f, 0.9f, 0.6f, 0.2f, 0.05f)

@PreviewLightDark
@Composable
private fun VoiceIndicatorDebugContentPreview() {
    FlashcardsTheme {
        VoiceIndicatorDebugContent(
            state = VoiceIndicatorDebugScreenState(),
            voiceBarsLevels = MutableStateFlow(PreviewWaveLevels),
            onNavigateBack = {},
            onSpeechSimulatedChange = {},
            onLevelIntervalChange = {},
        )
    }
}
