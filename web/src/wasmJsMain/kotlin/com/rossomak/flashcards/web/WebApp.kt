package com.rossomak.flashcards.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.rossomak.flashcards.web.navigation.WebRoute.DeleteAccount
import com.rossomak.flashcards.web.navigation.WebRoute.Home
import com.rossomak.flashcards.web.navigation.WebRoute.PrivacyPolicy
import com.rossomak.flashcards.web.navigation.rememberBrowserNavigator
import com.rossomak.flashcards.web.pages.DeleteAccountPage
import com.rossomak.flashcards.web.pages.HomePage
import com.rossomak.flashcards.web.pages.PrivacyPolicyPage
import com.rossomak.flashcards.web.theme.WebTheme
import kotlinx.browser.document

/** The whole site: one page at a time, chosen by the browser's URL path. */
@Composable
fun WebApp() {
    val navigator = rememberBrowserNavigator()
    val route = navigator.route

    LaunchedEffect(route) { document.title = route.title }

    WebTheme {
        when (route) {
            Home -> HomePage(onNavigate = navigator::navigate)
            PrivacyPolicy -> PrivacyPolicyPage(onNavigate = navigator::navigate)
            DeleteAccount -> DeleteAccountPage(onNavigate = navigator::navigate)
        }
    }
}
