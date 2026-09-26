package com.rossomak.flashcards.presentation.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.rossomak.flashcards.BuildConfig
import com.rossomak.flashcards.feature.debug.BuildInfo
import com.rossomak.flashcards.feature.debug.DebugGraph
import com.rossomak.flashcards.feature.debug.DebugRoot
import com.rossomak.flashcards.feature.debug.DebugScreen
import com.rossomak.flashcards.feature.debug.DebugVoiceIndicatorRoot
import com.rossomak.flashcards.feature.debug.DebugVoiceRoot
import com.rossomak.flashcards.feature.debug.R as DebugR
import com.rossomak.flashcards.feature.debug.voice.VoiceDebugScreen
import com.rossomak.flashcards.feature.debug.voiceindicator.VoiceIndicatorDebugScreen

@Composable
internal fun debugTabs(): List<TabItem> = listOf(
    TabItem(
        stringResource(DebugR.string.main_debug_tab_label),
        Icons.Filled.BugReport,
        Icons.Outlined.BugReport,
        DebugGraph,
    ),
)

/**
 * The graph starts at the hub, not at the voice harness: every debug tool is reached from
 * [DebugScreen], so a release build loses all of them by dropping this source set's
 * `debugImplementation` rather than by hiding rows on a screen users can reach.
 *
 * The harness is nested in this graph rather than registered on the app's outer one so that
 * navigating to it — and the whole `DebugVoiceRoot` symbol — stays inside the debug source set.
 * It renders above the bottom bar as a result, which is what any tab's nested destination does.
 *
 * This source set also backs the profiling build type, so everything here must hold up in an
 * R8-optimized, non-debuggable build.
 */
internal fun NavGraphBuilder.debugNavGraphEntries(
    navController: NavHostController,
    onNavigateToOnboarding: () -> Unit,
) {
    navigation<DebugGraph>(startDestination = DebugRoot) {
        composable<DebugRoot> {
            DebugScreen(
                buildInfo = BuildInfo(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    gitShortSha = BuildConfig.GIT_SHORT_SHA,
                ),
                onNavigateToOnboarding = onNavigateToOnboarding,
                onNavigateToVoiceDebug = { navController.navigate(DebugVoiceRoot) },
                onNavigateToVoiceIndicatorDebug = { navController.navigate(DebugVoiceIndicatorRoot) },
            )
        }
        composable<DebugVoiceRoot> {
            VoiceDebugScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<DebugVoiceIndicatorRoot> {
            VoiceIndicatorDebugScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
