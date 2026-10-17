package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseUser
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

private val favoriteOptions: ImmutableList<FavoriteSubcategoryOption> = with(ShowcaseTaxonomy) {
    listOf(coroutines, functions, compose, navigation, typingAndInterfaces, coreTypes, swiftConcurrency, memoryManagement, connectivity, testing)
        .map { subcategory ->
            FavoriteSubcategoryOption(
                id = subcategory.id,
                name = subcategory.name,
                categoryName = subcategory.categoryName,
                iconSvg = categoryById(subcategory.categoryId).iconSvg,
            )
        }
        .toImmutableList()
}

/** Ross mid-flow: microphone granted, so the voice test shows its normal card, and two Topics picked. */
private val onboardingState = OnboardingScreenState(
    userName = ShowcaseUser.DISPLAY_NAME,
    favoriteSubcategories = FavoriteSubcategoriesState.Content(favoriteOptions),
    selectedFavoriteSubcategoriesIds = persistentSetOf(ShowcaseTaxonomy.coroutines.id, ShowcaseTaxonomy.compose.id),
    micPermissionStatus = PermissionStatus.Granted,
)

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

/** The onboarding pager on its cover. Swipe or use the button to reach each step. */
@Composable
internal fun OnboardingShowcase() {
    OnboardingContent(
        state = onboardingState,
        voiceBarsLevels = silentVoiceBars,
        actions = noOpActions,
        messages = noMessages,
    )
}
