package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.feature.study.preview.PreviewStudySessionContent
import com.rossomak.flashcards.feature.study.preview.PreviewStudySessionScreenState

private const val SESSION_CARD_COUNT = 20

private const val SESSION_ESTIMATED_MINUTES = 14

/** A Quick Session in Android, drawn and ready: Rated with Voice Answering, microphone already granted. */
private val previewState = with(ShowcaseTaxonomy) {
    val sampledSubcategories = listOf(compose, navigation, backgroundWork)
    PreviewStudySessionScreenState(
        categoryName = android.name,
        categoryIconSvg = android.iconSvg,
        subcategoryNames = sampledSubcategories.map { it.name },
        sourceType = SessionSourceType.Quick,
        isLoading = false,
        config = StudySessionConfig(
            subcategoryIds = sampledSubcategories.map { it.id },
            mode = StudyMode.Rated,
            voiceAnsweringEnabled = true,
        ),
        hasDrawnSession = true,
        selectedCardCount = SESSION_CARD_COUNT,
        estimatedMinutes = SESSION_ESTIMATED_MINUTES,
        quickSessionSampledSubcategoryIds = sampledSubcategories.map { it.id },
        micPermissionStatus = PermissionStatus.Granted,
    )
}

@Composable
internal fun PreviewStudySessionShowcase(onNavigateBack: () -> Unit) {
    PreviewStudySessionContent(
        state = previewState,
        onNavigateBack = onNavigateBack,
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}
