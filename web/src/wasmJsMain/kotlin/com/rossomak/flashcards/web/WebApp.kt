package com.rossomak.flashcards.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.web.navigation.WebRoute.DeleteAccount
import com.rossomak.flashcards.web.navigation.WebRoute.Home
import com.rossomak.flashcards.web.navigation.WebRoute.PrivacyPolicy
import com.rossomak.flashcards.web.navigation.rememberBrowserNavigator
import com.rossomak.flashcards.web.pages.DeleteAccountPage
import com.rossomak.flashcards.web.pages.HomePage
import com.rossomak.flashcards.web.pages.PrivacyPolicyPage
import com.rossomak.flashcards.web.theme.WebTheme
import kotlinx.browser.document

/** The whole site: one page at a time, chosen by the browser's URL path. The landing page's entrance plays once, on first load. */
@Composable
fun WebApp() {
    val navigator = rememberBrowserNavigator()
    val route = navigator.route
    var homeEntrancePending by remember { mutableStateOf(route == Home) }

    LaunchedEffect(route) { document.title = route.title }

    WebTheme {
        when (route) {
            Home -> HomePage(
                playEntrance = homeEntrancePending,
                onEntrancePlayed = { homeEntrancePending = false },
                onNavigate = navigator::navigate,
            )
            PrivacyPolicy -> PrivacyPolicyPage(onNavigate = navigator::navigate)
            DeleteAccount -> DeleteAccountPage(onNavigate = navigator::navigate)
        }
    }
}
