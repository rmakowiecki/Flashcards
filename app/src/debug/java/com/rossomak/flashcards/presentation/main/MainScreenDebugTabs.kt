package com.rossomak.flashcards.presentation.main

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import com.rossomak.flashcards.BuildConfig
import com.rossomak.flashcards.debugtools.DebugBackdropBlurRoot
import com.rossomak.flashcards.debugtools.DebugGraph
import com.rossomak.flashcards.debugtools.DebugMorpheusGraphRoot
import com.rossomak.flashcards.debugtools.DebugRoot
import com.rossomak.flashcards.debugtools.DebugScreen
import com.rossomak.flashcards.debugtools.DebugVoiceIndicatorRoot
import com.rossomak.flashcards.debugtools.DebugVoiceRoot
import com.rossomak.flashcards.debugtools.R as DebugR
import com.rossomak.flashcards.debugtools.backdropblur.BackdropBlurDebugScreen
import com.rossomak.flashcards.debugtools.morpheus.MorpheusGraphPrototypeScreen
import com.rossomak.flashcards.debugtools.voice.VoiceDebugScreen
import com.rossomak.flashcards.debugtools.voiceindicator.VoiceIndicatorDebugScreen
import com.rossomak.flashcards.presentation.debug.CardAnimationsDebugActivity
import com.rossomak.flashcards.ui.navigation.guardedNavComposable

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
    onNavigateToDebugTool: (route: Any) -> Unit,
) {
    navigation<DebugGraph>(startDestination = DebugRoot) {
        guardedNavComposable<DebugRoot>(navController) { navigator ->
            val context = LocalContext.current
            DebugScreen(
                gitShortSha = BuildConfig.GIT_SHORT_SHA,
                onNavigateToOnboarding = onNavigateToOnboarding,
                onNavigateToVoiceDebug = { navigator.navigate(DebugVoiceRoot) },
                onNavigateToVoiceIndicatorDebug = { navigator.navigate(DebugVoiceIndicatorRoot) },
                onNavigateToMorpheusGraphPrototype = { onNavigateToDebugTool(DebugMorpheusGraphRoot) },
                onNavigateToBackdropBlurDebug = { navigator.navigate(DebugBackdropBlurRoot) },
                onNavigateToCardAnimationsDebug = {
                    context.startActivity(Intent(context, CardAnimationsDebugActivity::class.java))
                },
            )
        }
        guardedNavComposable<DebugVoiceRoot>(navController) { navigator ->
            VoiceDebugScreen(onNavigateBack = { navigator.popBackStack() })
        }
        guardedNavComposable<DebugBackdropBlurRoot>(navController) { navigator ->
            BackdropBlurDebugScreen(onNavigateBack = { navigator.popBackStack() })
        }
        guardedNavComposable<DebugVoiceIndicatorRoot>(navController) { navigator ->
            VoiceIndicatorDebugScreen(onNavigateBack = { navigator.popBackStack() })
        }
    }
}

/**
 * Debug tools that want the whole screen rather than a tab's content area, registered on the app's
 * outer graph so they render without the bottom bar. [DebugScreen] reaches them through
 * `onNavigateToDebugTool`, which is how the route symbols stay inside the debug source set.
 */
internal fun NavGraphBuilder.debugFullScreenDestinations(navController: NavHostController) {
    guardedNavComposable<DebugMorpheusGraphRoot>(navController) { navigator ->
        MorpheusGraphPrototypeScreen(onNavigateBack = { navigator.popBackStack() })
    }
}
