package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.navigation.OnNavigate

/** The last page: the tagline again under the large logo ([LandingLogo]), the legal links and the contact address. */
@Composable
fun ClosingPage(modifier: Modifier = Modifier, frame: LandingFrame, textModifier: Modifier = Modifier, onNavigate: OnNavigate) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacing.medium)) {
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = frame.heroTextTop).then(textModifier)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Stay sharp, study smart",
                    style = if (frame.layout == SideBySide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                SiteLinks(showContact = true, onNavigate = onNavigate, modifier = Modifier.padding(top = MaterialTheme.spacing.xlarge))
            }
        }
    }
}
