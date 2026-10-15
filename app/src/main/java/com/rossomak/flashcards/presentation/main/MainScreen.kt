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
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.rossomak.flashcards.R
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.feature.browse.BrowseScreen
import com.rossomak.flashcards.feature.home.HomeScreen
import com.rossomak.flashcards.feature.settings.SettingsScreen
import com.rossomak.flashcards.ui.navigation.HomeGraph
import com.rossomak.flashcards.ui.navigation.HomeRoot
import com.rossomak.flashcards.ui.navigation.SettingsGraph
import com.rossomak.flashcards.ui.navigation.SettingsRoot
import com.rossomak.flashcards.ui.navigation.StudyGraph
import com.rossomak.flashcards.ui.navigation.StudyRoot
import com.rossomak.flashcards.ui.navigation.guardedNavComposable

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
    // Debug-only: the release source set supplies no tabs.
    addAll(debugTabs())
}

/** A tab is selected while the current destination sits anywhere under its graph. */
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

@Suppress("LongParameterList")
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onNavigateToCategoryDetails: (categoryId: String, categoryName: String) -> Unit,
    onNavigateToSubcategoryDetails: (
        categoryId: String,
        categoryName: String,
        subcategoryId: String,
        subcategoryName: String,
    ) -> Unit,
    onNavigateToPreviewStudySession: (
        categoryId: String,
        categoryName: String,
        subcategoryId: String,
        subcategoryName: String,
    ) -> Unit,
    onNavigateToPreviewQuickSession: (categoryId: String, categoryName: String) -> Unit,
    onNavigateToPreviewRecentSession: (
        categoryId: String,
        categoryName: String,
        sourceType: SessionSourceType,
        subcategoryIds: List<String>,
        subcategoryNames: List<String>,
        studyMode: StudyMode,
        voiceAnsweringEnabled: Boolean?,
        readAloudEnabled: Boolean?,
    ) -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
) {
    val tabs = mainTabs()
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val tabNavOptions: NavOptionsBuilder.() -> Unit = {
        popUpTo(tabNavController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
    val navigateToTab: (route: Any) -> Unit = { route -> tabNavController.navigate(route, tabNavOptions) }

    Scaffold(
        modifier = modifier,
        // Top inset excluded: each tab root's own top app bar handles it.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        bottomBar = {
            MainBottomBar(
                tabs = tabs,
                currentDestination = currentDestination,
                onTabSelect = { tab -> navigateToTab(tab.route) },
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = HomeGraph,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            navigation<HomeGraph>(startDestination = HomeRoot) {
                guardedNavComposable<HomeRoot>(tabNavController) { navigator ->
                    HomeScreen(
                        onNavigateToCategoryDetails = onNavigateToCategoryDetails,
                        onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = onNavigateToPreviewStudySession,
                        onNavigateToPreviewQuickSession = onNavigateToPreviewQuickSession,
                        onNavigateToPreviewRecentSession = onNavigateToPreviewRecentSession,
                        // Guarded, unlike navigateToTab.
                        onNavigateToBrowse = { navigator.navigate(StudyGraph, tabNavOptions) },
                    )
                }
            }
            navigation<StudyGraph>(startDestination = StudyRoot) {
                guardedNavComposable<StudyRoot>(tabNavController) {
                    BrowseScreen(
                        onNavigateToCategoryDetails = onNavigateToCategoryDetails,
                        onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
                        onNavigateToPreviewStudySession = onNavigateToPreviewStudySession,
                    )
                }
            }
            navigation<SettingsGraph>(startDestination = SettingsRoot) {
                guardedNavComposable<SettingsRoot>(tabNavController) {
                    SettingsScreen(
                        onNavigateToAccount = onNavigateToAccount,
                    )
                }
            }
            // Debug-only onboarding replay.
            debugNavGraphEntries(
                navController = tabNavController,
                onNavigateToOnboarding = onNavigateToOnboarding,
            )
        }
    }
}
