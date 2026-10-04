package com.rossomak.flashcards.feature.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId

/** Not a lazy list: at most 15 Recents, scrolling with the rest of Home. */
@Composable
internal fun RecentSessionsSection(
    items: List<RecentItem>,
    now: Instant,
    zoneId: ZoneId,
    onRecentClick: (RecentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlashcardsOverlineLabel(text = stringResource(R.string.recents_section_title))
        FlashcardsListGroup(
            items = items,
            key = { item -> item.session.id },
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
        ) { item, rowModifier ->
            RecentSessionRow(
                item = item,
                now = now,
                zoneId = zoneId,
                onClick = { onRecentClick(item) },
                modifier = rowModifier,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun RecentSessionsSectionPreview() {
    RecentSessionsSectionPreviewHost()
}

@Preview(name = "Font scale 1.3 - Light", fontScale = 1.3f)
@Preview(name = "Font scale 1.3 - Dark", fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun RecentSessionsSectionLargeFontPreview() {
    RecentSessionsSectionPreviewHost()
}

@Preview(name = "Right to left - Light", locale = "ar")
@Preview(name = "Right to left - Dark", locale = "ar", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun RecentSessionsSectionRtlPreview() {
    RecentSessionsSectionPreviewHost()
}

@Composable
private fun RecentSessionsSectionPreviewHost() {
    FlashcardsTheme {
        Surface {
            RecentSessionsSection(
                items = previewRecentItems,
                now = previewRecentsNow,
                zoneId = previewRecentsZoneId,
                onRecentClick = {},
            )
        }
    }
}
