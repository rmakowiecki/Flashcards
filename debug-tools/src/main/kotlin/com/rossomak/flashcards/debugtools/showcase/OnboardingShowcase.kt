package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseUser
import com.rossomak.flashcards.debugtools.showcase.fixtures.compose
import com.rossomak.flashcards.debugtools.showcase.fixtures.connectivity
import com.rossomak.flashcards.debugtools.showcase.fixtures.coreTypes
import com.rossomak.flashcards.debugtools.showcase.fixtures.coroutines
import com.rossomak.flashcards.debugtools.showcase.fixtures.functions
import com.rossomak.flashcards.debugtools.showcase.fixtures.memoryManagement
import com.rossomak.flashcards.debugtools.showcase.fixtures.navigation
import com.rossomak.flashcards.debugtools.showcase.fixtures.swiftConcurrency
import com.rossomak.flashcards.debugtools.showcase.fixtures.testing
import com.rossomak.flashcards.debugtools.showcase.fixtures.typingAndInterfaces
import com.rossomak.flashcards.feature.onboarding.FavoriteSubcategoriesState
import com.rossomak.flashcards.feature.onboarding.OnboardingActions
import com.rossomak.flashcards.feature.onboarding.OnboardingContent
import com.rossomak.flashcards.feature.onboarding.OnboardingMessage
import com.rossomak.flashcards.feature.onboarding.OnboardingScreenState
import com.rossomak.flashcards.feature.onboarding.model.FavoriteSubcategoryOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

private val noOpActions = OnboardingActions(
    onStudyModeSelect = {},
    onDailyGoalDecrement = {},
    onDailyGoalIncrement = {},
    onFavoriteSubcategoryToggle = {},
    onFavoritesStepEntered = {},
    onFavoriteSubcategoriesRetry = {},
    onVoiceDemoStart = {},
    onVoiceDemoFinish = {},
    onVoiceDemoPlay = {},
    onVoiceDemoStop = {},
    onFinish = {},
)

private val silentVoiceBars: StateFlow<ImmutableList<Float>> = MutableStateFlow(persistentListOf())

private val noMessages: SharedFlow<OnboardingMessage> = MutableSharedFlow()

/** Microphone granted, so the voice test shows its normal card, and two Topics picked. */
private fun ShowcaseFixtures.onboardingState(): OnboardingScreenState {
    val options = listOf(coroutines, functions, compose, navigation, typingAndInterfaces, coreTypes, swiftConcurrency, memoryManagement, connectivity, testing)
        .map { FavoriteSubcategoryOption(id = it.id, name = it.name, categoryName = it.categoryName, iconSvg = category(it.categoryId).iconSvg) }
    return OnboardingScreenState(
        userName = ShowcaseUser.DISPLAY_NAME,
        favoriteSubcategories = FavoriteSubcategoriesState.Content(options.toImmutableList()),
        selectedFavoriteSubcategoriesIds = persistentSetOf(coroutines.id, compose.id),
        micPermissionStatus = PermissionStatus.Granted,
    )
}

@Composable
internal fun OnboardingShowcase() {
    val fixtures = LocalShowcaseFixtures.current
    OnboardingContent(
        state = remember(fixtures) { fixtures.onboardingState() },
        voiceBarsLevels = silentVoiceBars,
        actions = noOpActions,
        messages = noMessages,
    )
}
