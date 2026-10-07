package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.rossomak.flashcards.core.ui.theme.spacing

/** A short snackbar with an optional leading [icon], which is decorative: [message] carries the meaning. */
internal class ReportBugSnackbarVisuals(
    override val message: String,
    val icon: ImageVector? = null,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** Draws the icon of a [ReportBugSnackbarVisuals], and every other snackbar as the default one. */
@Composable
internal fun ReportBugSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState) { data ->
        val icon = (data.visuals as? ReportBugSnackbarVisuals)?.icon
        if (icon == null) {
            Snackbar(snackbarData = data)
        } else {
            Snackbar {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = icon, contentDescription = null)
                    Text(text = data.visuals.message)
                }
            }
        }
    }
}
