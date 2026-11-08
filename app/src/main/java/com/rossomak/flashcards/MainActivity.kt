package com.rossomak.flashcards

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.window.lockPortraitOnPhones
import com.rossomak.flashcards.presentation.startup.AppStartViewModel
import com.rossomak.flashcards.presentation.startup.AppStartupState
import com.rossomak.flashcards.ui.navigation.FlashcardsNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appStartViewModel: AppStartViewModel by viewModels()

    @Inject lateinit var permissionGateway: PermissionGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        lockPortraitOnPhones()

        splashScreen.setKeepOnScreenCondition {
            appStartViewModel.startupState.value is AppStartupState.Loading
        }
        splashScreen.setOnExitAnimationListener { splashView ->
            // Deliberately no animation to avoid jank - the goal is a seamless transition
            // to the secondary splash screen in the SplashScreen composable
            splashView.remove()
        }

        val launchRoute = intent.shortcutRoute()

        enableEdgeToEdge()
        setContent {
            FlashcardsTheme(dynamicColor = false) {
                val navController = rememberNavController()
                FlashcardsNavGraph(
                    navController = navController,
                    permissionGateway = permissionGateway,
                    modifier = Modifier.fillMaxSize(),
                    launchRoute = launchRoute,
                    shortcutEvents = appStartViewModel.shortcutEvents,
                    onNavigationStateChange = appStartViewModel::onNavigationStateChange,
                )
            }
        }
    }

    // singleTop: a shortcut tapped while this activity is on top arrives here instead of starting a second copy.
    // Any other intent, such as the session notification's tap, only brings the app forward.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.shortcutRoute()?.let(appStartViewModel::onShortcutTap)
    }

    // Only a launcher shortcut's own intent carries this action; an ordinary launcher-icon tap does not.
    private fun Intent.shortcutRoute(): String? =
        takeIf { it.action == AppShortcutsRepository.ACTION_OPEN_ROUTE }?.getStringExtra(AppShortcutsRepository.EXTRA_ROUTE)
}
