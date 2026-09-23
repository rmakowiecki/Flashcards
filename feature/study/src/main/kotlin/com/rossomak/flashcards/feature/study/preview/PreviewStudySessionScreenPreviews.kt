package com.rossomak.flashcards.feature.study.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionLoadingPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = true,
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionErrorPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = false,
            error = "Could not load flashcards",
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionEmptyStatePreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = false,
            config = StudySessionConfig(
                subcategoryIds = listOf("compose"),
                tagIds = setOf("state"),
                difficultyRange = 9..10,
            ),
            selectedCardCount = 0,
            estimatedMinutes = 0,
            availableTags = listOf("state", "modifiers", "recomposition"),
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionSingleSubcategoryRatedManualPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = false,
            config = StudySessionConfig(
                subcategoryIds = listOf("compose"),
                tagIds = setOf("state", "modifiers"),
                difficultyRange = 2..8,
            ),
            selectedCardCount = 18,
            estimatedMinutes = 12,
            availableTags = listOf("state", "modifiers", "recomposition"),
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionSingleSubcategoryRatedVoicePreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = false,
            config = StudySessionConfig(
                subcategoryIds = listOf("compose"),
                voiceAnsweringEnabled = true,
                tagIds = setOf("state", "modifiers"),
                difficultyRange = 2..8,
            ),
            selectedCardCount = 18,
            estimatedMinutes = 12,
            availableTags = listOf("state", "modifiers", "recomposition"),
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionMicPermissionRejectedPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = false,
            config = StudySessionConfig(
                subcategoryIds = listOf("compose"),
                voiceAnsweringEnabled = true,
            ),
            selectedCardCount = 18,
            estimatedMinutes = 12,
            micPermissionStatus = PermissionStatus.PermanentlyDenied,
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionQuickSessionPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose", "Coroutines", "Architecture", "Testing", "Navigation"),
            isQuickSession = true,
            isLoading = false,
            selectedCardCount = 20,
            estimatedMinutes = 13,
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionCustomSessionPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose", "Coroutines", "Architecture"),
            isLoading = false,
            config = StudySessionConfig(subcategoryIds = listOf("a", "b", "c"), mode = StudyMode.Fast),
            selectedCardCount = 12,
            estimatedMinutes = 8,
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionFastReadAloudPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose", "Coroutines", "Architecture"),
            isLoading = false,
            config = StudySessionConfig(subcategoryIds = listOf("a", "b", "c"), mode = StudyMode.Fast, readAloudEnabled = true),
            selectedCardCount = 12,
            estimatedMinutes = 8,
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewStudySessionSheetOpenPreview() {
    PreviewStudySessionContent(
        state = PreviewStudySessionScreenState(
            categoryName = "Android",
            subcategoryNames = listOf("Compose"),
            isLoading = false,
            config = StudySessionConfig(
                subcategoryIds = listOf("compose"),
                tagIds = setOf("state", "modifiers"),
                difficultyRange = 2..8,
            ),
            selectedCardCount = 18,
            estimatedMinutes = 12,
            availableTags = listOf("state", "modifiers", "recomposition"),
        ),
        onNavigateBack = {},
        onRetry = {},
        onReshuffleSubcategories = {},
        onDialogEvent = {},
        onResetFilters = {},
        onOpenAppSettings = {},
        onSwitchToManualAnswering = {},
        onStartSession = {},
        initiallySettingsSheetOpen = true,
    )
}
