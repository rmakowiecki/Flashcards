package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.landing.FeatureLayout.Stacked
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions
import org.jetbrains.compose.resources.painterResource

private const val SCREENSHOT_ASPECT_RATIO = 578f / 1238f

/** The phone frame's outline in the screenshots. */
private val phoneFrameShape = RoundedCornerShape(percent = 11)

private const val PHONE_HEIGHT_SHARE = 0.94f

/** The phone's maximum share of a side-by-side row, leaving the text room. */
private const val PHONE_MAX_ROW_WIDTH_SHARE = 0.45f

/** The side-by-side phone's height: a share of the height left after [verticalPadding], capped by height and row width. */
private fun sideBySidePhoneHeight(frame: LandingFrame, verticalPadding: Dp, sidePadding: Dp): Dp {
    val rowWidth = minOf(frame.width, WebDimensions.featureRowMaxWidth) - sidePadding * 2
    val byWidth = rowWidth * PHONE_MAX_ROW_WIDTH_SHARE / SCREENSHOT_ASPECT_RATIO
    return minOf((frame.height - verticalPadding) * PHONE_HEIGHT_SHARE, WebDimensions.phoneMaxHeight, byWidth).coerceAtLeast(0.dp)
}

private class FeatureTextStyles(val headline: TextStyle, val body: TextStyle)

/** Compact windows share smaller type: the screenshot gets the room. */
@Composable
private fun featureTextStyles(frame: LandingFrame): FeatureTextStyles = when {
    frame.isCompact -> FeatureTextStyles(headline = MaterialTheme.typography.headlineSmall, body = MaterialTheme.typography.bodyMedium)
    frame.layout == SideBySide -> FeatureTextStyles(headline = MaterialTheme.typography.displayMedium, body = MaterialTheme.typography.titleMedium)
    else -> FeatureTextStyles(headline = MaterialTheme.typography.headlineMedium, body = MaterialTheme.typography.bodyLarge)
}

/** One feature's text and screenshot, side by side or stacked by [LandingFrame.layout]. [textModifier] moves only the text. */
@Composable
fun FeaturePage(modifier: Modifier = Modifier, frame: LandingFrame, feature: LandingFeature, screenshotVariant: ScreenshotVariant, textModifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (frame.layout) {
            SideBySide -> SideBySidePage(frame = frame, feature = feature, screenshotVariant = screenshotVariant, textModifier = textModifier)
            Stacked -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = MaterialTheme.spacing.medium,
                        end = frame.dots.contentClearance,
                        top = frame.headerClearance,
                        bottom = MaterialTheme.spacing.medium,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FeatureText(
                    feature = feature,
                    styles = featureTextStyles(frame),
                    bodyGap = MaterialTheme.spacing.normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = WebDimensions.featureTextMaxWidth).fillMaxWidth().then(textModifier),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = if (frame.isCompact) MaterialTheme.spacing.large else MaterialTheme.spacing.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Screenshot(
                        feature = feature,
                        variant = screenshotVariant,
                        // Fits the column's width first, then its height, so the phone never outgrows the column.
                        modifier = Modifier.aspectRatio(SCREENSHOT_ASPECT_RATIO),
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBySidePage(frame: LandingFrame, feature: LandingFeature, screenshotVariant: ScreenshotVariant, textModifier: Modifier) {
    // A compact window clears the page dots on both sides and packs a narrow text close to the phone, centred as one group.
    val sidePadding = if (frame.isCompact) frame.dots.contentClearance else WebDimensions.featureSidePadding
    val gap = if (frame.isCompact) MaterialTheme.spacing.large else WebDimensions.featureGap
    val textMaxWidth = if (frame.isCompact) WebDimensions.compactFeatureTextMaxWidth else WebDimensions.featureTextMaxWidth
    Row(
        modifier = Modifier
            .widthIn(max = WebDimensions.featureRowMaxWidth)
            .fillMaxSize()
            .padding(
                start = sidePadding,
                end = sidePadding,
                top = frame.headerClearance,
                bottom = MaterialTheme.spacing.large,
            ),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val styles = featureTextStyles(frame)
        val text: @Composable () -> Unit = {
            FeatureText(
                feature = feature,
                styles = styles,
                bodyGap = MaterialTheme.spacing.normal,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f, fill = false).widthIn(max = textMaxWidth).then(textModifier),
            )
        }
        val phoneHeight = sideBySidePhoneHeight(
            frame = frame,
            verticalPadding = frame.headerClearance + MaterialTheme.spacing.large,
            sidePadding = sidePadding,
        )
        val phone: @Composable () -> Unit = {
            // Above the text, whose halo reaches past it and would dim a phone drawn before it.
            Screenshot(
                feature = feature,
                variant = screenshotVariant,
                modifier = Modifier.zIndex(1f).height(phoneHeight).aspectRatio(SCREENSHOT_ASPECT_RATIO),
            )
        }
        if (feature.phoneOnEnd) {
            text()
            phone()
        } else {
            phone()
            text()
        }
    }
}

@Composable
private fun FeatureText(feature: LandingFeature, styles: FeatureTextStyles, bodyGap: Dp, textAlign: TextAlign, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            BalancedText(
                text = feature.headline,
                style = styles.headline,
                textAlign = textAlign,
                modifier = Modifier.semantics { heading() },
            )
            BalancedText(
                text = feature.body,
                style = styles.body,
                textAlign = textAlign,
                color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                modifier = Modifier.padding(top = bodyGap),
            )
        }
    }
}

/** The framed screenshot with a shadow in the frame's shape. */
@Composable
private fun Screenshot(feature: LandingFeature, variant: ScreenshotVariant, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(feature.screenshot(variant)),
        contentDescription = "The Flashcards app: ${feature.headline.lowercase()}",
        contentScale = ContentScale.Fit,
        modifier = modifier.shadow(elevation = WebDimensions.phoneShadowElevation, shape = phoneFrameShape, clip = false),
    )
}
