package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.landing.FeatureLayout.Stacked
import com.rossomak.flashcards.web.theme.WEB_FAINT_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions
import org.jetbrains.compose.resources.painterResource

/** Width over height of the framed screenshots. */
private const val SCREENSHOT_ASPECT_RATIO = 578f / 1238f

/** Share of the space under the header the phone takes next to the text. */
private const val PHONE_HEIGHT_SHARE = 0.94f

/** The type of a feature's three blocks of text. */
private class FeatureTextStyles(val headline: TextStyle, val subline: TextStyle, val detail: TextStyle)

/**
 * One feature: its headline, subline and detail paragraph next to the app screenshot, on the side
 * [LandingFeature.phoneOnEnd] picks, or stacked above it in a narrow window. Content stays clear of
 * the header logo. [textModifier] carries the text's scroll motion and scrim; the phone moves with the page.
 */
@Composable
fun FeaturePage(modifier: Modifier = Modifier, frame: LandingFrame, feature: LandingFeature, screenshotVariant: ScreenshotVariant, textModifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (frame.layout) {
            SideBySide -> Row(
                modifier = Modifier
                    .widthIn(max = WebDimensions.featureRowMaxWidth)
                    .fillMaxSize()
                    .padding(
                        start = WebDimensions.featureSidePadding,
                        end = WebDimensions.featureSidePadding,
                        top = frame.headerClearance,
                        bottom = MaterialTheme.spacing.large,
                    ),
                horizontalArrangement = Arrangement.spacedBy(WebDimensions.featureGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val styles = FeatureTextStyles(
                    headline = MaterialTheme.typography.displayMedium,
                    subline = MaterialTheme.typography.titleLarge,
                    detail = MaterialTheme.typography.bodyLarge,
                )
                val text: @Composable () -> Unit = {
                    FeatureText(
                        feature = feature,
                        styles = styles,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = WebDimensions.featureTextMaxWidth).then(textModifier),
                    )
                }
                val phone: @Composable () -> Unit = {
                    Screenshot(
                        feature = feature,
                        variant = screenshotVariant,
                        modifier = Modifier.fillMaxHeight(PHONE_HEIGHT_SHARE).heightIn(max = WebDimensions.phoneMaxHeight).aspectRatio(SCREENSHOT_ASPECT_RATIO),
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
            Stacked -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = MaterialTheme.spacing.medium,
                        end = WebDimensions.stackedEndPadding,
                        top = frame.headerClearance,
                        bottom = MaterialTheme.spacing.medium,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FeatureText(
                    feature = feature,
                    styles = FeatureTextStyles(
                        headline = MaterialTheme.typography.headlineMedium,
                        subline = MaterialTheme.typography.bodyLarge,
                        detail = MaterialTheme.typography.bodyMedium,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().then(textModifier),
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = MaterialTheme.spacing.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Screenshot(
                        feature = feature,
                        variant = screenshotVariant,
                        modifier = Modifier.fillMaxHeight().aspectRatio(SCREENSHOT_ASPECT_RATIO, matchHeightConstraintsFirst = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureText(feature: LandingFeature, styles: FeatureTextStyles, textAlign: TextAlign, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = feature.headline,
                style = styles.headline,
                textAlign = textAlign,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = feature.subline,
                style = styles.subline,
                textAlign = textAlign,
                color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                modifier = Modifier.padding(top = MaterialTheme.spacing.normal),
            )
            Text(
                text = feature.detail,
                style = styles.detail,
                textAlign = textAlign,
                color = LocalContentColor.current.copy(alpha = WEB_FAINT_CONTENT_ALPHA),
                modifier = Modifier.padding(top = MaterialTheme.spacing.normal),
            )
        }
    }
}

/** The framed app screenshot. The pager composes only the pages on and next to the screen, so it decodes only near its page. */
@Composable
private fun Screenshot(feature: LandingFeature, variant: ScreenshotVariant, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(feature.screenshot(variant)),
        contentDescription = "The Flashcards app: ${feature.headline.lowercase()}",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
