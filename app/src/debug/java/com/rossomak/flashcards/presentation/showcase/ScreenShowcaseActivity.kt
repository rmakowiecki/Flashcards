package com.rossomak.flashcards.presentation.showcase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntryContent
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseListScreen
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.ui.navigation.guardedNavComposable
import kotlinx.serialization.Serializable

@Serializable object ScreenShowcaseList

/** [entryName] is a [ScreenShowcaseEntry] name. */
@Serializable data class ScreenShowcaseEntryRoute(val entryName: String)

/**
 * Production screens in sample states, full screen. An activity of its own so Main's bottom bar is not
 * drawn; it injects nothing, so no repository, network or sign-in is involved.
 */
class ScreenShowcaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fixtures = ShowcaseFixtures.load(resources)
        setContent {
            FlashcardsTheme(dynamicColor = false) {
                ScreenShowcaseNavHost(fixtures = fixtures, onExit = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ScreenShowcaseActivity::class.java)
    }
}

@Composable
private fun ScreenShowcaseNavHost(fixtures: ShowcaseFixtures, onExit: () -> Unit) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ScreenShowcaseList,
        modifier = Modifier.fillMaxSize(),
    ) {
        guardedNavComposable<ScreenShowcaseList>(navController) { navigator ->
            ScreenShowcaseListScreen(
                onNavigateBack = onExit,
                onNavigateToEntry = { entry -> navigator.navigate(ScreenShowcaseEntryRoute(entry.name)) },
            )
        }
        guardedNavComposable<ScreenShowcaseEntryRoute>(navController) { navigator ->
            // This destination's own entry, not the controller's current one, so it stays right during transitions.
            val backStackEntry = LocalViewModelStoreOwner.current as NavBackStackEntry
            val entry = remember(backStackEntry) { ScreenShowcaseEntry.valueOf(backStackEntry.toRoute<ScreenShowcaseEntryRoute>().entryName) }
            ScreenShowcaseEntryContent(entry = entry, fixtures = fixtures, onNavigateBack = { navigator.popBackStack() })
        }
    }
}
