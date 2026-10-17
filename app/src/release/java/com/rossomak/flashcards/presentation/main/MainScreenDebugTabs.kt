package com.rossomak.flashcards.presentation.main

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

@Composable
internal fun debugTabs(): List<TabItem> = emptyList()

internal fun NavGraphBuilder.debugNavGraphEntries(
    navController: NavHostController,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDebugTool: (route: Any) -> Unit,
) = Unit

internal fun NavGraphBuilder.debugFullScreenDestinations(navController: NavHostController) = Unit
