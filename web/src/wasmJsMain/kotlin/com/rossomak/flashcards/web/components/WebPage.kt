package com.rossomak.flashcards.web.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.lightFlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.browser.rememberPrefersDarkColorScheme
import com.rossomak.flashcards.web.landing.LandingGraphs
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.flashcards_logo
import com.rossomak.flashcards.web.theme.WebDimensions
import com.rossomak.morpheus.morpheusGraphBackground
import org.jetbrains.compose.resources.vectorResource

/**
 * The frame of every page below the landing page: the brand gradient with the closing page's calm
 * graph fixed behind, and a scrolling column of the large Flashcards logo (linking home), the
 * [content] on an opaque card and the site's links. The card is the app's own card in the app's
 * light or dark colors, following the browser's color scheme live. Its text is selectable, since the
 * page is drawn on a canvas the browser can't select from.
 */
@Composable
fun WebPage(modifier: Modifier = Modifier, onNavigate: OnNavigate, content: @Composable ColumnScope.() -> Unit) {
    val prefersDark by rememberPrefersDarkColorScheme()
    val cardColors = if (prefersDark) darkFlashcardsColors.colorScheme else lightFlashcardsColors.colorScheme
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.brandColors.screenGradient)) {
        val logoWidth = subpageLogoWidth(maxWidth)
        Box(modifier = Modifier.fillMaxSize().morpheusGraphBackground(LandingGraphs.closing))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                imageVector = vectorResource(Res.drawable.flashcards_logo),
                contentDescription = "Flashcards home",
                modifier = Modifier
                    .width(logoWidth)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onNavigate(WebRoute.Home) }
                    .pointerHoverIcon(PointerIcon.Hand),
            )
            MaterialTheme(colorScheme = cardColors, typography = MaterialTheme.typography) {
                CompositionLocalProvider(LocalContentColor provides cardColors.onSurface) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .padding(top = MaterialTheme.spacing.medium)
                                .widthIn(max = WebDimensions.contentMaxWidth)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MaterialTheme.cornerRadius.large))
                                .background(cardColors.surfaceContainerLowest)
                                .padding(MaterialTheme.spacing.large),
                            content = content,
                        )
                    }
                }
            }
            SiteLinks(
                showContact = true,
                onNavigate = onNavigate,
                modifier = Modifier.padding(top = MaterialTheme.spacing.large),
            )
        }
    }
}

/** The legal pages' logo: larger than the landing page's pinned one, a share of the window within bounds. */
private fun subpageLogoWidth(windowWidth: Dp): Dp =
    (windowWidth * WebDimensions.SUBPAGE_LOGO_WIDTH_SHARE).coerceIn(WebDimensions.subpageLogoMinWidth, WebDimensions.subpageLogoMaxWidth)
