package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.ui.composables.FlashcardsInlineCategoryGlyph
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.dialogs.label
import com.rossomak.flashcards.core.ui.composables.icon
import com.rossomak.flashcards.core.ui.format.AppLocale
import com.rossomak.flashcards.core.ui.format.formatSignedXp
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.rememberCategoryColor
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.home.RecentRelativeTime.DaysAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.HoursAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.JustNow
import com.rossomak.flashcards.feature.home.RecentRelativeTime.MinutesAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.OtherYearDate
import com.rossomak.flashcards.feature.home.RecentRelativeTime.ThisYearDate
import com.rossomak.flashcards.feature.home.RecentRelativeTime.Yesterday
import java.time.Instant
import java.time.ZoneId

private const val SECONDS_PER_MINUTE = 60

/** No play button: a history row has nothing separate to browse. */
@Composable
internal fun RecentSessionRow(
    item: RecentItem,
    now: () -> Instant,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // Reads now() inside derivedStateOf, so a tick recomposes this row only when its RecentRelativeTime changes.
    val currentNow by rememberUpdatedState(now)
    val relativeTime by remember(item.session.startedAt, zoneId) {
        derivedStateOf { recentRelativeTime(item.session.startedAt, currentNow(), zoneId) }
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .heightIn(min = MaterialTheme.sizes.listRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            RecentCategoryOverline(
                categoryName = item.session.categoryName,
                category = item.category,
                modifier = Modifier.weight(1f),
            )
            RecentModeAndTime(session = item.session, relativeTime = relativeTime)
        }
        Text(
            text = recentTitle(item),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        RecentStatPills(session = item.session)
    }
}

/** [category] only styles the overline; without it the name shows in the primary color with no glyph. */
@Composable
private fun RecentCategoryOverline(categoryName: String, category: Category?, modifier: Modifier = Modifier) {
    val categoryColor = rememberCategoryColor(hex = category?.color, fallback = MaterialTheme.colorScheme.primary)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        if (category != null) {
            FlashcardsInlineCategoryGlyph(
                iconSvg = category.iconSvg,
                tint = categoryColor,
                contentDescription = null,
            )
        }
        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = categoryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun recentTitle(item: RecentItem): String {
    val topicCount = item.session.subcategoryIds.size
    return when (item.session.sourceType) {
        SingleSubcategory -> item.session.subcategoryNames.first()
        Quick -> pluralStringResource(R.plurals.recents_quick_session_topics, topicCount, topicCount)
        Custom -> pluralStringResource(R.plurals.recents_custom_session_topics, topicCount, topicCount)
    }
}

@Composable
private fun RecentStatPills(session: RecentSession) {
    val durationLabel = if (session.durationSeconds < SECONDS_PER_MINUTE) {
        stringResource(R.string.recents_duration_under_minute_label)
    } else {
        stringResource(R.string.recents_duration_label, session.durationSeconds / SECONDS_PER_MINUTE)
    }
    val signedXp = remember(session.xpTotal) { formatSignedXp(session.xpTotal, AppLocale) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        FlashcardsMetadataBadge(
            label = pluralStringResource(R.plurals.recents_card_count, session.studiedCount, session.studiedCount),
            size = FlashcardsComponentSize.Small,
        )
        FlashcardsMetadataBadge(label = durationLabel, size = FlashcardsComponentSize.Small)
        FlashcardsMetadataBadge(
            label = stringResource(R.string.recents_xp_label, signedXp),
            size = FlashcardsComponentSize.Small,
        )
    }
}

@Composable
private fun RecentModeAndTime(session: RecentSession, relativeTime: RecentRelativeTime) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        Icon(
            imageVector = session.mode.icon,
            contentDescription = session.mode.label(),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MaterialTheme.sizes.metadataBadgeIconCompact),
        )
        Text(
            text = recentRelativeTimeText(relativeTime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun recentRelativeTimeText(relativeTime: RecentRelativeTime): String = when (relativeTime) {
    JustNow -> stringResource(R.string.recents_time_just_now_label)
    is MinutesAgo -> stringResource(R.string.recents_time_minutes_ago_label, relativeTime.minutes)
    is HoursAgo -> stringResource(R.string.recents_time_hours_ago_label, relativeTime.hours)
    Yesterday -> stringResource(R.string.recents_time_yesterday_label)
    is DaysAgo -> stringResource(R.string.recents_time_days_ago_label, relativeTime.days)
    is ThisYearDate -> recentDateText(relativeTime.date, includesYear = false)
    is OtherYearDate -> recentDateText(relativeTime.date, includesYear = true)
}

@PreviewLightDark
@Composable
private fun RecentSessionRowSingleSubcategoryPreview() {
    RecentSessionRowPreviewHost(item = previewRecentItems.first { it.session.sourceType == SingleSubcategory })
}

@PreviewLightDark
@Composable
private fun RecentSessionRowQuickPreview() {
    RecentSessionRowPreviewHost(item = previewRecentItems.first { it.session.sourceType == Quick })
}

@PreviewLightDark
@Composable
private fun RecentSessionRowCustomPreview() {
    RecentSessionRowPreviewHost(item = previewRecentItems.first { it.session.sourceType == Custom })
}

@Composable
private fun RecentSessionRowPreviewHost(item: RecentItem) {
    FlashcardsTheme {
        RecentSessionRow(
            item = item,
            now = { previewRecentsNow },
            zoneId = previewRecentsZoneId,
            onClick = {},
            modifier = Modifier,
        )
    }
}
