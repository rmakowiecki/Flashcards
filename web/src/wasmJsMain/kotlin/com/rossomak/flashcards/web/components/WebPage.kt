package com.rossomak.flashcards.web.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.lightFlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.browser.rememberPrefersDarkColorScheme
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.flashcards_logo
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions
import org.jetbrains.compose.resources.vectorResource

/** A legal page's reveal slots. */
private object LegalSlots {
    const val LOGO = 0
    const val CARD = 1
    const val LINKS = 2
    const val COUNT = 3
}

/**
 * A legal page's scrolling column: the home logo, [content] on a selectable card in the app's light or
 * dark card colors, and the site's links, revealed in turn.
 */
@Composable
fun WebPage(modifier: Modifier = Modifier, onNavigate: OnNavigate, content: @Composable ColumnScope.() -> Unit) {
    val prefersDark by rememberPrefersDarkColorScheme()
    val cardColors = if (prefersDark) darkFlashcardsColors.colorScheme else lightFlashcardsColors.colorScheme
    val reveal = rememberRevealSequence(slotCount = LegalSlots.COUNT, shown = true)
    val rise = LocalRevealMotion.current.rise
    val logoInteractionSource = remember { MutableInteractionSource() }
    val logoHover by logoInteractionSource.hoverProgress()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val logoWidth = subpageLogoWidth(maxWidth)
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
                    .reveal(progress = { reveal.progress(LegalSlots.LOGO) }, rise = rise, startScale = LOGO_REVEAL_START_SCALE)
                    .graphicsLayer { alpha = lerp(WEB_MUTED_CONTENT_ALPHA, 1f, logoHover) }
                    .hoverable(logoInteractionSource)
                    .focusRing(RoundedCornerShape(WebDimensions.focusRingCornerRadius))
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onNavigate(WebRoute.Home) }
                    .pointerHoverIcon(PointerIcon.Hand),
            )
            MaterialTheme(colorScheme = cardColors, typography = MaterialTheme.typography) {
                CompositionLocalProvider(LocalContentColor provides cardColors.onSurface) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .padding(top = MaterialTheme.spacing.medium)
                                .reveal(progress = { reveal.progress(LegalSlots.CARD) }, rise = rise)
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
                modifier = Modifier
                    .padding(top = MaterialTheme.spacing.large)
                    .reveal(progress = { reveal.progress(LegalSlots.LINKS) }, rise = rise),
                showContact = true,
                onNavigate = onNavigate,
            )
        }
    }
}

/** The legal pages' logo: larger than the landing page's pinned one, a share of the window within bounds. */
private fun subpageLogoWidth(windowWidth: Dp): Dp =
    (windowWidth * WebDimensions.SUBPAGE_LOGO_WIDTH_SHARE).coerceIn(WebDimensions.subpageLogoMinWidth, WebDimensions.subpageLogoMaxWidth)
