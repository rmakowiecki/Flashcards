package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.android
import com.rossomak.flashcards.debugtools.showcase.fixtures.backgroundWork
import com.rossomak.flashcards.debugtools.showcase.fixtures.compose
import com.rossomak.flashcards.debugtools.showcase.fixtures.navigation
import com.rossomak.flashcards.feature.study.preview.PreviewStudySessionContent
import com.rossomak.flashcards.feature.study.preview.PreviewStudySessionScreenState

private const val SESSION_CARD_COUNT = 20
private const val SESSION_ESTIMATED_MINUTES = 14

/** A drawn Quick Session in Android: Rated with Voice Answering, microphone granted. */
private fun ShowcaseFixtures.previewState(): PreviewStudySessionScreenState {
    val sampledSubcategories = listOf(compose, navigation, backgroundWork)
    return PreviewStudySessionScreenState(
        categoryName = android.name,
        categoryIconSvg = android.iconSvg,
        subcategoryNames = sampledSubcategories.map { it.name },
        sourceType = SessionSourceType.Quick,
        isLoading = false,
        config = StudySessionConfig(subcategoryIds = sampledSubcategories.map { it.id }, mode = StudyMode.Rated, voiceAnsweringEnabled = true),
        hasDrawnSession = true,
        selectedCardCount = SESSION_CARD_COUNT,
        estimatedMinutes = SESSION_ESTIMATED_MINUTES,
        quickSessionSampledSubcategoryIds = sampledSubcategories.map { it.id },
        micPermissionStatus = PermissionStatus.Granted,
    )
}

@Composable
internal fun PreviewStudySessionShowcase(isSettingsSheetOpen: Boolean, onNavigateBack: () -> Unit) {
    val fixtures = LocalShowcaseFixtures.current
    var state by remember(fixtures) { mutableStateOf(fixtures.previewState()) }
    PreviewStudySessionContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = { event -> state = state.copy(activeDialog = showcaseDialogAfter(event)) },
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
        initiallySettingsSheetOpen = isSettingsSheetOpen,
    )
}
