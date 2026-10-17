package com.rossomak.flashcards.feature.study.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconCircle
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.rememberFlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import kotlinx.coroutines.launch

/**
 * The Premium offer, docked like the settings sheet as a plain sibling in the screen's outer `Box`
 * (see [FlashcardsBottomSheet] on placement). Composed only while [PreviewDialog.PremiumOffer] is
 * active, so it slides up on entry. An Upgrade action joins once there is a screen to set up
 * Premium on; until then the way forward is manual rating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PremiumOfferSheet(
    modifier: Modifier = Modifier,
    onSwitchToManualAnswering: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberFlashcardsBottomSheetState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { sheetState.sheetState.show() }
    // Slides away before the offer closes, like a swipe does.
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch {
            sheetState.sheetState.hide()
            action()
        }
    }

    FlashcardsBottomSheet(
        state = sheetState,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        FlashcardsIconCircle(
            icon = Icons.Default.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        Text(
            text = stringResource(R.string.preview_session_premium_offer_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        Text(
            text = stringResource(R.string.preview_session_premium_offer_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        FlashcardsFilledButton(
            text = stringResource(R.string.preview_session_premium_offer_switch_to_manual_button),
            onClick = { hideThen(onSwitchToManualAnswering) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        FlashcardsTonalButton(
            text = stringResource(R.string.preview_session_premium_offer_not_now_button),
            onClick = { hideThen(onDismiss) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
