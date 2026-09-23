package com.rossomak.flashcards.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.repository.PermissionRepository
import com.rossomak.flashcards.core.ui.animation.LocalNavAnimatedVisibilityScope
import com.rossomak.flashcards.core.ui.animation.LocalSharedTransitionScope
import com.rossomak.flashcards.core.ui.animation.SHARED_ELEMENT_DURATION_MS
import com.rossomak.flashcards.feature.auth.AuthRoute
import com.rossomak.flashcards.feature.auth.LoginScreen
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsScreen
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsScreen
import com.rossomak.flashcards.feature.onboarding.OnboardingRoute
import com.rossomak.flashcards.feature.onboarding.OnboardingScreen
import com.rossomak.flashcards.feature.study.FastStudySessionRoute
import com.rossomak.flashcards.feature.study.PreviewStudySessionRoute
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.feature.study.fast.FastStudySessionScreen
import com.rossomak.flashcards.feature.study.preview.PreviewStudySessionScreen
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionScreen
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScreen
import com.rossomak.flashcards.presentation.main.MainScreen
import com.rossomak.flashcards.presentation.splash.SplashScreen
import com.rossomak.flashcards.ui.permission.PermissionLauncherHost
import kotlinx.serialization.Serializable

/**
 * [pendingRoute] is the raw route string off a launcher-shortcut Intent
 * (`AppShortcutsRepository.EXTRA_ROUTE`), forwarded here as [FlashcardsNavGraph]'s start-destination
 * argument so [SplashViewModel][com.rossomak.flashcards.presentation.splash.SplashViewModel] can
 * decode it the same testable way any other route argument is decoded. Null on every ordinary
 * app launch.
 */
@Serializable data class Splash(val pendingRoute: String? = null)

@Serializable object Main

@Serializable object HomeGraph

@Serializable object StudyGraph

@Serializable object SettingsGraph

@Serializable object HomeRoot

@Serializable object StudyRoot

@Serializable object SettingsRoot

/**
 * Reached from three places — Category Details, and both a search result's row and its play button
 * on the Study tab — so the route construction lives here once rather than in each `composable`
 * block.
 */
private fun NavHostController.navigateToSubcategoryDetails(
    categoryId: String,
    categoryName: String,
    subcategoryId: String,
    subcategoryName: String,
) {
    navigate(SubcategoryDetailsRoute(categoryId, categoryName, subcategoryId, subcategoryName))
}

/**
 * Study Creation takes a list of subcategories; every entry point that starts from a single
 * Subcategory wraps it in a one-element list here.
 */
private fun NavHostController.navigateToPreviewStudySession(
    categoryId: String,
    categoryName: String,
    subcategoryId: String,
    subcategoryName: String,
) {
    navigate(
        PreviewStudySessionRoute(
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = listOf(subcategoryId),
            subcategoryNames = listOf(subcategoryName),
        )
    )
}

/**
 * Subcategory Details is the one entry point that has a browsed card list behind it, so it is the
 * only one that carries a selection: the filters the user applied and the order they were looking
 * at, which the Preview screen honours over their saved default (ADR-0038).
 */
private fun NavHostController.navigateToPreviewStudySessionWithSelection(
    categoryId: String,
    categoryName: String,
    subcategoryId: String,
    subcategoryName: String,
    filterTagIds: List<String>,
    difficultyRange: IntRange,
    sortOrder: FlashcardSortOrder,
) {
    navigate(
        PreviewStudySessionRoute(
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = listOf(subcategoryId),
            subcategoryNames = listOf(subcategoryName),
            filterTagIds = filterTagIds,
            difficultyMin = difficultyRange.first,
            difficultyMax = difficultyRange.last,
            sortOrder = sortOrder,
        )
    )
}

/**
 * Category Details' two session CTAs both land here. **Quick** hands over every Subcategory the
 * category lists as the *candidate pool*, and the Preview screen samples a bounded subset from it
 * (ADR-0040) rather than starting a session across all of them. **Custom** hands over exactly the
 * Subcategories the user selected, and [isQuickSession] is `false`, so the Preview screen honours
 * them literally rather than sampling. Either way — unlike [navigateToPreviewStudySession] — the
 * subcategories pass through as the lists the route already models rather than wrapping a single
 * one.
 *
 * It also passes no sort order: there is no browsed card list behind either entry point, so the
 * route carries null and the Preview screen falls back to the user's saved default (ADR-0038).
 */
private fun NavHostController.navigateToPreviewStudySessionForCategory(
    categoryId: String,
    categoryName: String,
    subcategoryIds: List<String>,
    subcategoryNames: List<String>,
    isQuickSession: Boolean,
) {
    navigate(
        PreviewStudySessionRoute(
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            isQuickSession = isQuickSession,
        )
    )
}

/**
 * Terminating a Rated or Fast session lands here — natural end or a confirmed exit alike.
 * One `popUpTo` does both jobs that would otherwise be handled separately: the just-finished session
 * sits above [Main] on the back stack, so popping up to [Main] removes it (the session can never be
 * swiped or backed into again) *and* leaves the Summary sitting directly on top of [Main] — so both
 * the Summary's own action and a plain system back land on the tab the user started from, with no
 * second `popUpTo` needed at the Summary screen itself.
 */
private fun NavHostController.navigateToStudySessionSummary(route: StudySessionSummaryRoute) {
    navigate(route) {
        popUpTo(Main) { inclusive = false }
    }
}

/**
 * Splash, Login and Onboarding — the three screens a user passes through before reaching [Main].
 * Each pops itself off the back stack on the way out, so [Main] is never reachable "back" into a
 * launch screen. Extracted from [FlashcardsNavGraph] to keep that function readable as the flow
 * grew a third pre-Main step.
 */
private fun NavGraphBuilder.launchDestinations(navController: NavHostController) {
    composable<Splash>(
        // Every launch handoff is one opaque full-screen gradient replacing another, so there is
        // nothing to cross-fade. Fading them would briefly leave both around half-opaque and let the
        // white window background bleed through as a flash. Instead the splash holds at full opacity
        // until the shared logo has landed — it stays composed that long so the element has both
        // ends to animate between — and is then cut in a single frame, hidden the whole time under
        // the incoming screen, which enters with no transition at all.
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 1, delayMillis = SHARED_ELEMENT_DURATION_MS))
        },
    ) {
        // Splash and Onboarding hand the brand mark between them as a shared element, so both
        // publish their AnimatedVisibilityScope for the element to animate against.
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            SplashScreen(
                onNavigateToMain = {
                    navController.navigate(Main) {
                        popUpTo<Splash> { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(OnboardingRoute) {
                        popUpTo<Splash> { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(AuthRoute) {
                        popUpTo<Splash> { inclusive = true }
                    }
                },
                onNavigateToCategoryDetails = { route ->
                    // Pushed on top of Main rather than replacing it outright, so back from
                    // Details returns to Browse instead of exiting the app.
                    navController.navigate(Main) {
                        popUpTo<Splash> { inclusive = true }
                    }
                    navController.navigate(route)
                },
                onNavigateToSubcategoryDetails = { route ->
                    navController.navigate(Main) {
                        popUpTo<Splash> { inclusive = true }
                    }
                    navController.navigate(route)
                },
            )
        }
    }
    composable<AuthRoute>(enterTransition = { EnterTransition.None }) {
        LoginScreen(
            onNavigateToMain = {
                navController.navigate(Main) {
                    popUpTo(AuthRoute) { inclusive = true }
                }
            },
            onNavigateToOnboarding = {
                navController.navigate(OnboardingRoute) {
                    popUpTo(AuthRoute) { inclusive = true }
                }
            },
        )
    }
    composable<OnboardingRoute>(enterTransition = { EnterTransition.None }) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            OnboardingScreen(
                onNavigateToMain = {
                    navController.navigate(Main) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(AuthRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
fun FlashcardsNavGraph(
    navController: NavHostController,
    permissionRepository: PermissionRepository,
    modifier: Modifier = Modifier,
    launchRoute: String? = null,
) {
    // One SharedTransitionLayout around the whole NavHost: a shared element is matched between the
    // outgoing and the incoming destination, so both have to sit inside the same scope.
    SharedTransitionLayout(modifier = modifier) {
        PermissionLauncherHost(permissionRepository)
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Splash(pendingRoute = launchRoute),
                modifier = Modifier.fillMaxSize()
            ) {
                launchDestinations(navController)
                // Only ever reached from one of the three launch screens, all of which hand over
                // opaquely — see the comment on Splash.
                composable<Main>(enterTransition = { EnterTransition.None }) {
                    MainScreen(
                        onNavigateToLogin = {
                            navController.navigate(AuthRoute) {
                                popUpTo(Main) { inclusive = true }
                            }
                        },
                        onNavigateToOnboarding = {
                            navController.navigate(OnboardingRoute) {
                                popUpTo(Main) { inclusive = true }
                            }
                        },
                        onNavigateToCategoryDetails = { categoryId, categoryName ->
                            navController.navigate(CategoryDetailsRoute(categoryId, categoryName))
                        },
                        onNavigateToSubcategoryDetails = navController::navigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = navController::navigateToPreviewStudySession,
                    )
                }
                composable<CategoryDetailsRoute> {
                    CategoryDetailsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSubcategoryDetails = navController::navigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = navController::navigateToPreviewStudySession,
                        onNavigateToPreviewStudySessionForCategory = navController::navigateToPreviewStudySessionForCategory,
                    )
                }
                composable<SubcategoryDetailsRoute> {
                    SubcategoryDetailsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPreviewStudySession = navController::navigateToPreviewStudySessionWithSelection,
                    )
                }
                composable<PreviewStudySessionRoute> {
                    PreviewStudySessionScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToFastStudySession = { route -> navController.navigate(route) },
                        onNavigateToRatedStudySession = { route -> navController.navigate(route) }
                    )
                }
                composable<FastStudySessionRoute> {
                    FastStudySessionScreen(
                        onNavigateToSummary = navController::navigateToStudySessionSummary
                    )
                }
                composable<RatedStudySessionRoute> {
                    RatedStudySessionScreen(
                        onNavigateToSummary = navController::navigateToStudySessionSummary
                    )
                }
                composable<StudySessionSummaryRoute> {
                    StudySessionSummaryScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
