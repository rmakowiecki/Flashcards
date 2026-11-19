package com.rossomak.flashcards.web.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.browser.WindowPointerDownHandler
import com.rossomak.flashcards.web.browser.openInNewTab
import com.rossomak.flashcards.web.browser.rememberPrefersReducedMotion
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.github_mark
import com.rossomak.flashcards.web.resources.play_triangle
import com.rossomak.flashcards.web.theme.WebColors
import com.rossomak.flashcards.web.theme.WebDimensions
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val CLOSED_TESTING_HINT = "In closed testing on Google Play. Public release coming soon."
private const val HINT_MILLIS = 150
private const val SOLID_BUTTON_REST_ALPHA = 0.88f
private const val DISABLED_CONTENT_ALPHA = 0.55f
private const val DISABLED_BORDER_ALPHA = 0.4f

/** A button's height, padding, icon and label style: smaller in a compact window. */
private class ProjectButtonMetrics(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp, val iconGap: Dp, val label: TextStyle, val gap: Dp)

@Composable
private fun projectButtonMetrics(compact: Boolean): ProjectButtonMetrics = if (compact) {
    ProjectButtonMetrics(
        height = 40.dp,
        horizontalPadding = MaterialTheme.spacing.small,
        iconSize = 16.dp,
        iconGap = MaterialTheme.spacing.xsmall,
        label = MaterialTheme.typography.labelLarge,
        gap = MaterialTheme.spacing.xsmall,
    )
} else {
    ProjectButtonMetrics(
        height = 48.dp,
        horizontalPadding = MaterialTheme.spacing.normal,
        iconSize = 20.dp,
        iconGap = MaterialTheme.spacing.xsmall,
        label = MaterialTheme.typography.titleMedium,
        gap = MaterialTheme.spacing.small,
    )
}

/**
 * The GitHub button, which opens the source code in a new tab, next to the Google Play button. While the app is in
 * closed testing the Google Play button is disabled, and a hint under the row explains it on hover, tap or keyboard
 * focus. The hint's space is kept while it is hidden, so it never covers what lies below. The two buttons always sit
 * side by side; [compact] shrinks them.
 */
@Composable
fun ProjectButtons(modifier: Modifier = Modifier, compact: Boolean) {
    val metrics = projectButtonMetrics(compact)
    val playInteractionSource = remember { MutableInteractionSource() }
    val playHovered by playInteractionSource.collectIsHoveredAsState()
    val playFocused by playInteractionSource.collectIsFocusedAsState()
    var playTapped by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap), verticalAlignment = Alignment.CenterVertically) {
            SourceButton(metrics = metrics, label = "View on GitHub")
            if (ProjectLinks.PLAY_STORE_AVAILABLE) {
                PlayStoreButton(metrics = metrics)
            } else {
                // Runs before the button's own tap, which shows the hint again.
                WindowPointerDownHandler(onPointerDown = { playTapped = false })
                ClosedTestingPlayStoreButton(metrics = metrics, interactionSource = playInteractionSource, onTap = { playTapped = true })
            }
        }
        if (!ProjectLinks.PLAY_STORE_AVAILABLE) ClosedTestingHint(shown = playHovered || playFocused || playTapped)
    }
}

@Composable
private fun SourceButton(metrics: ProjectButtonMetrics, label: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val hover by interactionSource.hoverProgress()
    val shape = RoundedCornerShape(percent = 50)
    ButtonContent(
        modifier = Modifier
            .focusRing(shape)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button) { openInNewTab(ProjectLinks.SOURCE_URL) }
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .background(WebColors.solidButtonFill.copy(alpha = lerp(SOLID_BUTTON_REST_ALPHA, 1f, hover)), shape),
        metrics = metrics,
        icon = Res.drawable.github_mark,
        label = label,
        contentColor = WebColors.solidButtonContent,
    )
}

/** The Google Play button once the app is public: an outlined link to the store listing. */
@Composable
private fun PlayStoreButton(metrics: ProjectButtonMetrics) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    HoverBrightens(restAlpha = SOLID_BUTTON_REST_ALPHA) {
        val contentColor = LocalContentColor.current
        ButtonContent(
            modifier = Modifier
                .focusRing(shape)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button) { openInNewTab(ProjectLinks.PLAY_STORE_URL) }
                .pointerHoverIcon(PointerIcon.Hand)
                .border(WebDimensions.outlinedButtonBorderWidth, contentColor, shape),
            metrics = metrics,
            icon = Res.drawable.play_triangle,
            label = "Google Play",
            contentColor = contentColor,
        )
    }
}

/**
 * The Google Play button during closed testing: muted, never brightening, with a not-allowed cursor.
 * It takes focus so a keyboard reaches the hint, but Enter does nothing. Hover, focus and taps on [interactionSource] and
 * [onTap] drive the hint.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ClosedTestingPlayStoreButton(metrics: ProjectButtonMetrics, interactionSource: MutableInteractionSource, onTap: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    val mutedColor = LocalContentColor.current.copy(alpha = DISABLED_CONTENT_ALPHA)
    ButtonContent(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
                contentDescription = "Google Play. $CLOSED_TESTING_HINT"
            }
            .focusRing(shape)
            .focusable(interactionSource = interactionSource)
            .hoverable(interactionSource)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .pointerHoverIcon(PointerIcon.fromKeyword("not-allowed"))
            .border(WebDimensions.outlinedButtonBorderWidth, LocalContentColor.current.copy(alpha = DISABLED_BORDER_ALPHA), shape),
        metrics = metrics,
        icon = Res.drawable.play_triangle,
        label = "Google Play",
        contentColor = mutedColor,
    )
}

@Composable
private fun ButtonContent(modifier: Modifier, metrics: ProjectButtonMetrics, icon: DrawableResource, label: String, contentColor: Color) {
    Row(
        modifier = modifier.height(metrics.height).padding(horizontal = metrics.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.iconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(metrics.iconSize))
        Text(text = label, style = metrics.label, color = contentColor, maxLines = 1)
    }
}

/**
 * The closed-testing hint, centered under the button row. It keeps its space while hidden, so showing it never moves
 * the page. It sits on the page dots' frosted backing. The button already announces it, so screen readers skip it.
 */
@Composable
private fun ClosedTestingHint(shown: Boolean) {
    val reducedMotion by rememberPrefersReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(HINT_MILLIS, easing = FlashcardsMotion.StandardEasing),
        label = "closed testing hint",
    )
    val shape = RoundedCornerShape(WebDimensions.focusRingCornerRadius)
    Text(
        text = CLOSED_TESTING_HINT,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clearAndSetSemantics {}
            .padding(top = MaterialTheme.spacing.small)
            .widthIn(max = WebDimensions.hintMaxWidth)
            .graphicsLayer { this.alpha = alpha }
            .blurBackdrop(backdrop = LocalBlurBackdrop.current, shape = shape, radius = WebDimensions.pageDotsBlurRadius)
            .background(WebColors.frostedFill, shape)
            .border(width = Dp.Hairline, color = WebColors.frostedBorder, shape = shape)
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xsmall),
    )
}
