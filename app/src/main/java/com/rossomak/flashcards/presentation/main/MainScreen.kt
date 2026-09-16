package com.rossomak.flashcards.presentation.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.rossomak.flashcards.R
import com.rossomak.flashcards.feature.browse.BrowseScreen
import com.rossomak.flashcards.feature.home.HomeScreen
import com.rossomak.flashcards.feature.settings.SettingsScreen
import com.rossomak.flashcards.ui.navigation.HomeGraph
import com.rossomak.flashcards.ui.navigation.HomeRoot
import com.rossomak.flashcards.ui.navigation.SettingsGraph
import com.rossomak.flashcards.ui.navigation.SettingsRoot
import com.rossomak.flashcards.ui.navigation.StudyGraph
import com.rossomak.flashcards.ui.navigation.StudyRoot

@Composable
private fun mainTabs(): List<TabItem> = buildList {
    add(
        TabItem(
            stringResource(R.string.main_home_tab_label),
            Icons.Filled.Home,
            Icons.Outlined.Home,
            HomeGraph,
        ),
    )
    add(
        TabItem(
            stringResource(R.string.main_study_tab_label),
            Icons.AutoMirrored.Filled.MenuBook,
            Icons.AutoMirrored.Outlined.MenuBook,
            StudyGraph,
        ),
    )
    add(
        TabItem(
            stringResource(R.string.main_settings_tab_label),
            Icons.Filled.Settings,
            Icons.Outlined.Settings,
            SettingsGraph,
        ),
    )
    // The debug tools hub comes from feature:debug — see app/src/debug vs app/src/release
    // MainScreenDebugTabs.kt (never present in release builds).
    addAll(debugTabs())
}

/**
 * A tab is selected when the current destination sits anywhere under its graph, so a nested
 * destination still keeps its parent tab highlighted.
 */
@Composable
private fun MainBottomBar(
    tabs: List<TabItem>,
    currentDestination: NavDestination?,
    onTabSelect: (TabItem) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        tabs.forEach { tab ->
            val isSelected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(text = tab.label) }
            )
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onNavigateToCategoryDetails: (String, String) -> Unit,
    onNavigateToSubcategoryDetails: (String, String, String, String) -> Unit,
    onNavigateToPreviewStudySession: (String, String, String, String) -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
) {
    val tabs = mainTabs()
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        // No topBar here — each tab root owns its own top app bar and handles the status-bar
        // inset itself (see e.g. SettingsScreen). Left at the Scaffold default, contentWindowInsets
        // would reserve the top inset too (Scaffold only excludes an edge once its matching slot is
        // present), consuming it here and leaving every tab's own top bar with nothing left to pad
        // by — its container color would then stop short of the status bar instead of painting
        // behind it.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        bottomBar = {
            MainBottomBar(
                tabs = tabs,
                currentDestination = currentDestination,
                onTabSelect = { tab ->
                    tabNavController.navigate(tab.route) {
                        popUpTo(tabNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = HomeGraph,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Declares the bottom-bar inset already spent, so any descendant applying a
                // window-inset padding of its own adds only the part beyond the bar rather than
                // double-counting it.
                .consumeWindowInsets(innerPadding)
        ) {
            navigation<HomeGraph>(startDestination = HomeRoot) {
                composable<HomeRoot> {
                    HomeScreen(
                        onNavigateToCategoryDetails = onNavigateToCategoryDetails,
                        onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
                    )
                }
            }
            navigation<StudyGraph>(startDestination = StudyRoot) {
                composable<StudyRoot> {
                    BrowseScreen(
                        onNavigateToCategoryDetails = onNavigateToCategoryDetails,
                        onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = onNavigateToPreviewStudySession,
                    )
                }
            }
            navigation<SettingsGraph>(startDestination = SettingsRoot) {
                composable<SettingsRoot> {
                    SettingsScreen(
                        onNavigateToLogin = onNavigateToLogin,
                    )
                }
            }
            // Onboarding replay is a debug affordance, so it hangs off the debug hub rather than
            // off Settings, which ships in release.
            debugNavGraphEntries(
                navController = tabNavController,
                onNavigateToOnboarding = onNavigateToOnboarding,
            )
        }
    }
}
