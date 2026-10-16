package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.Browse
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.CategoryDetails
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.CategoryDetailsSelectionMode
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.FastReadAloud
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.FastTapThrough
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.Home
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.Onboarding
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.PreviewStudySession
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedAnswerRevealed
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedQuestion
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.RatedVoiceAnswering
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SessionSummary
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry.SubcategoryDetails

/**
 * Draws one showcase entry full screen. [onNavigateBack] is wired only where a screen's own back or close
 * control would otherwise do nothing; every other callback is a no-op.
 */
@Composable
fun ScreenShowcaseEntryContent(entry: ScreenShowcaseEntry, onNavigateBack: () -> Unit) {
    when (entry) {
        Onboarding -> OnboardingShowcase()
        Home -> HomeShowcase()
        Browse -> BrowseShowcase()
        CategoryDetails -> CategoryDetailsShowcase(isSelectionMode = false, onNavigateBack = onNavigateBack)
        CategoryDetailsSelectionMode -> CategoryDetailsShowcase(isSelectionMode = true, onNavigateBack = onNavigateBack)
        SubcategoryDetails -> SubcategoryDetailsShowcase(onNavigateBack = onNavigateBack)
        PreviewStudySession -> PreviewStudySessionShowcase(onNavigateBack = onNavigateBack)
        RatedQuestion -> RatedStudySessionShowcase(RatedShowcaseState.Question)
        RatedAnswerRevealed -> RatedStudySessionShowcase(RatedShowcaseState.AnswerRevealed)
        RatedVoiceAnswering -> RatedStudySessionShowcase(RatedShowcaseState.VoiceAnswering)
        FastTapThrough -> FastStudySessionShowcase(isReadAloud = false)
        FastReadAloud -> FastStudySessionShowcase(isReadAloud = true)
        SessionSummary -> StudySessionSummaryShowcase(onNavigateBack = onNavigateBack)
    }
}
