package com.rossomak.flashcards.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
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
import com.rossomak.flashcards.R
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
import com.rossomak.flashcards.core.ui.animation.LocalNavAnimatedVisibilityScope
import com.rossomak.flashcards.core.ui.animation.LocalSharedTransitionScope
import com.rossomak.flashcards.core.ui.animation.SHARED_ELEMENT_DURATION_MS
import com.rossomak.flashcards.feature.account.AccountRoute
import com.rossomak.flashcards.feature.account.AccountScreen
import com.rossomak.flashcards.feature.account.OpenSourceLicensesRoute
import com.rossomak.flashcards.feature.account.OpenSourceLicensesScreen
import com.rossomak.flashcards.feature.account.ReportBugRoute
import com.rossomak.flashcards.feature.account.ReportBugScreen
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
private fun EntryNavigator.navigateToSubcategoryDetails(
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
private fun EntryNavigator.navigateToPreviewStudySession(
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
            sourceType = SingleSubcategory,
        )
    )
}

/**
 * A Quick Session started from a Category alone, with no Subcategory list in hand. The route carries
 * empty lists and the Preview screen resolves the candidate pool itself before sampling (ADR-0056).
 */
private fun EntryNavigator.navigateToPreviewQuickSession(categoryId: String, categoryName: String) {
    navigate(
        PreviewStudySessionRoute(
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = emptyList(),
            subcategoryNames = emptyList(),
            sourceType = Quick,
        )
    )
}

/**
 * Studying a finished session's thing again, from its Recent on Home or from its Session Summary: Preview
 * opens with the past Study Mode and delivery as starting values. It carries no filters or sort, so Preview
 * uses the saved defaults (ADR-0038).
 *
 * `popUpTo(Main)` is a no-op from Home, where [Main] is already on top, and from the Summary it removes the
 * Summary, so back from Preview returns to the tab the user started from.
 */
@Suppress("LongParameterList") // one primitive per route field: feature modules never build each other's routes.
private fun EntryNavigator.navigateToPreviewReplay(
    categoryId: String,
    categoryName: String,
    sourceType: SessionSourceType,
    subcategoryIds: List<String>,
    subcategoryNames: List<String>,
    studyMode: StudyMode,
    voiceAnsweringEnabled: Boolean?,
    readAloudEnabled: Boolean?,
) {
    navigate(
        PreviewStudySessionRoute(
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            sourceType = sourceType,
            studyMode = studyMode,
            voiceAnsweringEnabled = voiceAnsweringEnabled,
            readAloudEnabled = readAloudEnabled,
        )
    ) {
        popUpTo(Main) { inclusive = false }
    }
}

/**
 * Subcategory Details is the one entry point that has a browsed card list behind it, so it is the
 * only one that carries a selection: the filters the user applied and the order they were looking
 * at, which the Preview screen honours over their saved default (ADR-0038).
 */
private fun EntryNavigator.navigateToPreviewStudySessionWithSelection(
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
            sourceType = SingleSubcategory,
        )
    )
}

/**
 * Category Details' two session CTAs both land here. **Quick** hands over the Category's complete
 * Subcategory list as the *candidate pool*, and the Preview screen samples a bounded subset from it
 * (ADR-0040) rather than starting a session across all of them. A caller without the list can
 * leave the ids empty and let the Preview screen fetch the pool itself (ADR-0056). **Custom** hands
 * over exactly the Subcategories the user selected, as [sourceType] Custom (or SingleSubcategory
 * for a one-Subcategory selection), so the Preview screen honours them literally rather than
 * sampling. Either way — unlike [navigateToPreviewStudySession] — the subcategories pass through as
 * the lists the route already models rather than wrapping a single one.
 *
 * It also passes no sort order: there is no browsed card list behind either entry point, so the
 * route carries null and the Preview screen falls back to the user's saved default (ADR-0038).
 */
private fun EntryNavigator.navigateToPreviewStudySessionForCategory(
    categoryId: String,
    categoryName: String,
    subcategoryIds: List<String>,
    subcategoryNames: List<String>,
    sourceType: SessionSourceType,
) {
    navigate(
        PreviewStudySessionRoute(
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            sourceType = sourceType,
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
private fun EntryNavigator.navigateToStudySessionSummary(route: StudySessionSummaryRoute) {
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
    guardedNavComposable<Splash>(
        navController = navController,
        // Every launch handoff is one opaque full-screen gradient replacing another, so there is
        // nothing to cross-fade. Fading them would briefly leave both around half-opaque and let the
        // white window background bleed through as a flash. Instead the splash holds at full opacity
        // until the shared logo has landed — it stays composed that long so the element has both
        // ends to animate between — and is then cut in a single frame, hidden the whole time under
        // the incoming screen, which enters with no transition at all.
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 1, delayMillis = SHARED_ELEMENT_DURATION_MS))
        },
    ) { navigator ->
        // Splash hands the brand mark to Onboarding or Login as a shared element, so all three
        // publish their AnimatedVisibilityScope for the element to animate against.
        ProvideNavAnimatedVisibilityScope {
            SplashScreen(
                onNavigateToMain = {
                    navigator.navigate(Main) {
                        popUpTo<Splash> { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navigator.navigate(OnboardingRoute) {
                        popUpTo<Splash> { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navigator.navigate(AuthRoute) {
                        popUpTo<Splash> { inclusive = true }
                    }
                },
                onNavigateToCategoryDetails = { route ->
                    // Pushed on top of Main rather than replacing it outright, so back from
                    // Details returns to Main's start tab (Home) instead of exiting the app.
                    navigator.navigateStacked(Main, { popUpTo<Splash> { inclusive = true } }, route)
                },
                onNavigateToSubcategoryDetails = { route ->
                    navigator.navigateStacked(Main, { popUpTo<Splash> { inclusive = true } }, route)
                },
            )
        }
    }
    guardedNavComposable<AuthRoute>(navController, enterTransition = { EnterTransition.None }) { navigator ->
        ProvideNavAnimatedVisibilityScope {
            LoginScreen(
                onNavigateToMain = {
                    navigator.navigate(Main) {
                        popUpTo(AuthRoute) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navigator.navigate(OnboardingRoute) {
                        popUpTo(AuthRoute) { inclusive = true }
                    }
                },
            )
        }
    }
    guardedNavComposable<OnboardingRoute>(navController, enterTransition = { EnterTransition.None }) { navigator ->
        ProvideNavAnimatedVisibilityScope {
            OnboardingScreen(
                onNavigateToMain = {
                    navigator.navigate(Main) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navigator.navigate(AuthRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}

private fun NavGraphBuilder.accountDestination(navController: NavHostController) {
    guardedNavComposable<AccountRoute>(navController) { navigator ->
        AccountScreen(
            onNavigateBack = { navigator.popBackStack() },
            onNavigateToReportBug = { navigator.navigate(ReportBugRoute) },
            onNavigateToOpenSourceLicenses = {
                navigator.navigate(OpenSourceLicensesRoute) { launchSingleTop = true }
            },
            onNavigateToLogin = {
                navigator.navigate(AuthRoute) {
                    popUpTo(Main) { inclusive = true }
                    // A second Login event during the exit transition must not stack another AuthRoute.
                    launchSingleTop = true
                }
            },
        )
    }
    guardedNavComposable<ReportBugRoute>(navController) { navigator ->
        ReportBugScreen(onNavigateBack = { navigator.popBackStack() })
    }
}

private fun NavGraphBuilder.openSourceLicensesDestination(navController: NavHostController) {
    guardedNavComposable<OpenSourceLicensesRoute>(navController) { navigator ->
        OpenSourceLicensesScreen(
            librariesResId = R.raw.aboutlibraries,
            onNavigateBack = { navigator.popBackStack() },
        )
    }
}

@Composable
fun FlashcardsNavGraph(
    navController: NavHostController,
    permissionGateway: PermissionGateway,
    modifier: Modifier = Modifier,
    launchRoute: String? = null,
) {
    // One SharedTransitionLayout around the whole NavHost: a shared element is matched between the
    // outgoing and the incoming destination, so both have to sit inside the same scope.
    SharedTransitionLayout(modifier = modifier) {
        PermissionLauncherHost(permissionGateway)
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Splash(pendingRoute = launchRoute),
                modifier = Modifier.fillMaxSize()
            ) {
                launchDestinations(navController)
                // Only ever reached from one of the three launch screens, all of which hand over
                // opaquely — see the comment on Splash.
                guardedNavComposable<Main>(navController, enterTransition = { EnterTransition.None }) { navigator ->
                    MainScreen(
                        onNavigateToAccount = {
                            navigator.navigate(AccountRoute) { launchSingleTop = true }
                        },
                        onNavigateToOnboarding = {
                            navigator.navigate(OnboardingRoute) {
                                popUpTo(Main) { inclusive = true }
                            }
                        },
                        onNavigateToCategoryDetails = { categoryId, categoryName ->
                            navigator.navigate(CategoryDetailsRoute(categoryId, categoryName))
                        },
                        onNavigateToSubcategoryDetails = navigator::navigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = navigator::navigateToPreviewStudySession,
                        onNavigateToPreviewQuickSession = navigator::navigateToPreviewQuickSession,
                        onNavigateToPreviewRecentSession = navigator::navigateToPreviewReplay,
                    )
                }
                accountDestination(navController)
                openSourceLicensesDestination(navController)
                guardedNavComposable<CategoryDetailsRoute>(navController) { navigator ->
                    CategoryDetailsScreen(
                        onNavigateBack = { navigator.popBackStack() },
                        onNavigateToSubcategoryDetails = navigator::navigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = navigator::navigateToPreviewStudySession,
                        onNavigateToPreviewStudySessionForCategory = navigator::navigateToPreviewStudySessionForCategory,
                    )
                }
                guardedNavComposable<SubcategoryDetailsRoute>(navController) { navigator ->
                    SubcategoryDetailsScreen(
                        onNavigateBack = { navigator.popBackStack() },
                        onNavigateToPreviewStudySession = navigator::navigateToPreviewStudySessionWithSelection,
                    )
                }
                guardedNavComposable<PreviewStudySessionRoute>(navController) { navigator ->
                    PreviewStudySessionScreen(
                        onNavigateBack = { navigator.popBackStack() },
                        onNavigateToFastStudySession = { route -> navigator.navigate(route) },
                        onNavigateToRatedStudySession = { route -> navigator.navigate(route) }
                    )
                }
                guardedNavComposable<FastStudySessionRoute>(navController) { navigator ->
                    FastStudySessionScreen(
                        onNavigateBack = { navigator.popBackStack() },
                        onNavigateToSummary = navigator::navigateToStudySessionSummary,
                    )
                }
                guardedNavComposable<RatedStudySessionRoute>(navController) { navigator ->
                    RatedStudySessionScreen(
                        onNavigateBack = { navigator.popBackStack() },
                        onNavigateToSummary = navigator::navigateToStudySessionSummary,
                    )
                }
                guardedNavComposable<StudySessionSummaryRoute>(navController) { navigator ->
                    StudySessionSummaryScreen(
                        onNavigateBack = { navigator.popBackStack() },
                        onNavigateToPreviewStudySession = navigator::navigateToPreviewReplay,
                    )
                }
            }
        }
    }
}

/** Publishes this destination's scope, so a shared element inside it has an end to animate against. */
@Composable
private fun AnimatedVisibilityScope.ProvideNavAnimatedVisibilityScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this, content = content)
}
