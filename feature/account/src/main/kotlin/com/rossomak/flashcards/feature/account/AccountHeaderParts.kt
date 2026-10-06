package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlin.math.roundToInt

private const val MANAGE_LINK_TAG = "manage"

/** The height of the back arrow's `IconButton` container, so the two ripples match. */
private val SIGN_OUT_CONTAINER_HEIGHT = 40.dp

/**
 * Sign out in the error color: an icon with a label that shrinks away as [labelProgress] falls from
 * 1 to 0, leaving the icon alone. The ripple and the click area are a pill 40dp tall, which is a
 * circle once the label is gone, like the back arrow's; the 48dp touch target around it is kept.
 *
 * It is one clickable node named by the label's text, so a screen reader announces "Sign out" in both
 * states even once the label has no width left to draw in.
 */
@Composable
internal fun AccountSignOutAction(
    modifier: Modifier = Modifier,
    labelProgress: Float,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.account_sign_out_button)
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(SIGN_OUT_CONTAINER_HEIGHT)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = MaterialTheme.spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            tint = color,
        )
        Text(
            text = label,
            modifier = Modifier
                .clearAndSetSemantics {}
                .graphicsLayer {
                    alpha = labelProgress
                    clip = true
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout((placeable.width * labelProgress).roundToInt(), placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .padding(start = MaterialTheme.spacing.xsmall, end = MaterialTheme.spacing.xxsmall),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** One string with a clickable span, so a screen reader announces a single line with a link in it. */
@Composable
internal fun AccountSignedInWithGoogleLine(
    modifier: Modifier = Modifier,
    onManageAccountClick: () -> Unit,
) {
    val signedInLabel = stringResource(R.string.account_signed_in_with_google_label)
    val separator = stringResource(CoreUiR.string.common_middle_dot_separator)
    val manageLabel = stringResource(R.string.account_manage_button)
    val manageStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = buildAnnotatedString {
            append(signedInLabel)
            append(separator)
            withLink(
                LinkAnnotation.Clickable(
                    tag = MANAGE_LINK_TAG,
                    styles = TextLinkStyles(style = manageStyle),
                    linkInteractionListener = { onManageAccountClick() },
                ),
            ) {
                append(manageLabel)
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
