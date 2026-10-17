package com.rossomak.flashcards.web.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.PageLink
import com.rossomak.flashcards.web.components.WebPage
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.flashcards_logo
import com.rossomak.flashcards.web.theme.WebDimensions
import org.jetbrains.compose.resources.vectorResource

/** `/`: the app's logo and tagline, with links to the legal pages. */
@Composable
fun HomePage(modifier: Modifier = Modifier, onNavigate: OnNavigate) {
    WebPage(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                imageVector = vectorResource(Res.drawable.flashcards_logo),
                contentDescription = "Flashcards",
                modifier = Modifier.width(WebDimensions.homeLogoWidth),
            )
            Text(
                text = "Stay sharp, study smart",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
            )
            Column(
                modifier = Modifier.padding(top = MaterialTheme.spacing.xlarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                PageLink(label = "Privacy Policy", route = WebRoute.PrivacyPolicy, style = MaterialTheme.typography.bodyLarge, onNavigate = onNavigate)
                PageLink(label = "Delete your account", route = WebRoute.DeleteAccount, style = MaterialTheme.typography.bodyLarge, onNavigate = onNavigate)
            }
        }
    }
}
