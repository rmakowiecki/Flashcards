package com.rossomak.flashcards

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
import com.rossomak.flashcards.core.domain.repository.PermissionRepository
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.presentation.startup.AppStartViewModel
import com.rossomak.flashcards.presentation.startup.AppStartupState
import com.rossomak.flashcards.ui.navigation.FlashcardsNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appStartViewModel: AppStartViewModel by viewModels()

    @Inject lateinit var permissionRepository: PermissionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            appStartViewModel.startupState.value is AppStartupState.Loading
        }
        splashScreen.setOnExitAnimationListener { splashView ->
            // Deliberately no animation to avoid jank - the goal is a seamless transition
            // to the secondary splash screen in the SplashScreen composable
            splashView.remove()
        }

        // Only a launcher-shortcut's own Intent carries this action; an ordinary launcher-icon tap
        // (or a warm-started MainActivity handing this same Intent back on process recreation)
        // does not, so this stays null on every other cold start.
        val launchRoute = intent.takeIf { it.action == AppShortcutsRepository.ACTION_OPEN_ROUTE }
            ?.getStringExtra(AppShortcutsRepository.EXTRA_ROUTE)

        enableEdgeToEdge()
        setContent {
            FlashcardsTheme(dynamicColor = false) {
                val navController = rememberNavController()
                FlashcardsNavGraph(
                    navController = navController,
                    permissionRepository = permissionRepository,
                    modifier = Modifier.fillMaxSize(),
                    launchRoute = launchRoute,
                )
            }
        }
    }
}
