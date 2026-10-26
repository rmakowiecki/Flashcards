package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.Browse
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.CategoryDetails
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.CategoryDetailsSelectionMode
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.FastReadAloud
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.FastTapThrough
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.Home
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.Onboarding
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.PreviewStudySession
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.PreviewStudySessionSettings
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedAnswerRevealed
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedQuestion
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedVoiceAnswering
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedVoiceGraded
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SessionSummary
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SessionSummaryConfetti
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SessionSummaryXpBreakdown
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SubcategoryDetails
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SubcategoryDetailsFilters
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures

/**
 * Draws [entry] on the theme background, which Main's scaffold paints under Home and Browse in the app.
 * Only [onNavigateBack] is wired; every other callback is a no-op.
 */
@Suppress("CyclomaticComplexMethod") // one branch per entry, exhaustive over the enum.
@Composable
fun ScreenShowcaseEntryContent(entry: ScreenShowcaseEntry, fixtures: ShowcaseFixtures, onNavigateBack: () -> Unit) {
    CompositionLocalProvider(LocalShowcaseFixtures provides fixtures) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (entry) {
                Onboarding -> OnboardingShowcase()
                Home -> HomeShowcase()
                Browse -> BrowseShowcase()
                CategoryDetails -> CategoryDetailsShowcase(isSelectionMode = false, onNavigateBack = onNavigateBack)
                CategoryDetailsSelectionMode -> CategoryDetailsShowcase(isSelectionMode = true, onNavigateBack = onNavigateBack)
                SubcategoryDetails -> SubcategoryDetailsShowcase(isFilterDialogOpen = false, onNavigateBack = onNavigateBack)
                SubcategoryDetailsFilters -> SubcategoryDetailsShowcase(isFilterDialogOpen = true, onNavigateBack = onNavigateBack)
                PreviewStudySession -> PreviewStudySessionShowcase(isSettingsSheetOpen = false, onNavigateBack = onNavigateBack)
                PreviewStudySessionSettings -> PreviewStudySessionShowcase(isSettingsSheetOpen = true, onNavigateBack = onNavigateBack)
                RatedQuestion -> RatedStudySessionShowcase(RatedShowcaseState.Question)
                RatedAnswerRevealed -> RatedStudySessionShowcase(RatedShowcaseState.AnswerRevealed)
                RatedVoiceAnswering -> RatedStudySessionShowcase(RatedShowcaseState.VoiceAnswering)
                RatedVoiceGraded -> RatedStudySessionShowcase(RatedShowcaseState.VoiceGraded)
                FastTapThrough -> FastStudySessionShowcase(isReadAloud = false)
                FastReadAloud -> FastStudySessionShowcase(isReadAloud = true)
                SessionSummary -> StudySessionSummaryShowcase(SummaryShowcaseState.Settled, onNavigateBack = onNavigateBack)
                SessionSummaryXpBreakdown -> StudySessionSummaryShowcase(SummaryShowcaseState.XpBreakdown, onNavigateBack = onNavigateBack)
                SessionSummaryConfetti -> StudySessionSummaryShowcase(SummaryShowcaseState.Confetti, onNavigateBack = onNavigateBack)
            }
        }
    }
}
