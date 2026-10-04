package com.rossomak.flashcards.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.core.ui.theme.toCategoryColorOrNull
import com.rossomak.flashcards.feature.home.RecentRelativeTime.DaysAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.HoursAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.JustNow
import com.rossomak.flashcards.feature.home.RecentRelativeTime.MinutesAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.OtherYearDate
import com.rossomak.flashcards.feature.home.RecentRelativeTime.ThisYearDate
import com.rossomak.flashcards.feature.home.RecentRelativeTime.Yesterday
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

private const val SECONDS_PER_MINUTE = 60
private const val MINUS_SIGN = '−'
private const val THIS_YEAR_DATE_SKELETON = "MMMd"
private const val OTHER_YEAR_DATE_SKELETON = "MMMdy"

/**
 * The whole row is one button with no play button: a history row has no separate browse target.
 *
 * @param modifier the row modifier from FlashcardsListGroup; it carries the row's width and shape.
 */
@Composable
internal fun RecentSessionRow(
    item: RecentItem,
    now: Instant,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .heightIn(min = MaterialTheme.sizes.listRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.normal, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
        ) {
            RecentCategoryOverline(categoryName = item.session.categoryName, category = item.category)
            Text(
                text = recentTitle(item),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RecentStatPills(session = item.session)
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
        ) {
            RecentDeliveryIcons(session = item.session)
            Text(
                text = recentRelativeTimeText(recentRelativeTime(item.session.startedAt, now, zoneId)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** [category] only styles the overline; without it the name shows in the primary color with no glyph. */
@Composable
private fun RecentCategoryOverline(categoryName: String, category: Category?) {
    val categoryColor = remember(category?.color) { category?.color?.toCategoryColorOrNull() }
        ?: MaterialTheme.colorScheme.primary
    Row(
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
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
    val locale = currentLocale()
    val durationLabel = if (session.durationSeconds < SECONDS_PER_MINUTE) {
        stringResource(R.string.recents_duration_under_minute_label)
    } else {
        stringResource(R.string.recents_duration_label, session.durationSeconds / SECONDS_PER_MINUTE)
    }
    val signedXp = remember(session.xpTotal, locale) { formatSignedXp(session.xpTotal, locale) }
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

/** `+N`, `−N` (real minus sign) or `0`. */
private fun formatSignedXp(xpTotal: Int, locale: Locale): String {
    val magnitude = NumberFormat.getIntegerInstance(locale).format(xpTotal.toLong().absoluteValue)
    return when {
        xpTotal > 0 -> "+$magnitude"
        xpTotal < 0 -> "$MINUS_SIGN$magnitude"
        else -> magnitude
    }
}

@Composable
private fun RecentDeliveryIcons(session: RecentSession) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall)) {
        when (session) {
            is RecentSession.Rated -> {
                RecentIcon(icon = Icons.Default.Checklist, contentDescription = stringResource(R.string.recents_rated_mode_cd))
                if (session.voiceAnsweringEnabled) {
                    RecentIcon(icon = Icons.Default.Mic, contentDescription = stringResource(R.string.recents_voice_answering_cd))
                }
            }
            is RecentSession.Fast -> {
                RecentIcon(icon = Icons.Default.Bolt, contentDescription = stringResource(R.string.recents_fast_mode_cd))
                if (session.readAloudEnabled) {
                    RecentIcon(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.recents_read_aloud_cd),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentIcon(icon: ImageVector, contentDescription: String) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(MaterialTheme.sizes.metadataBadgeIconCompact),
    )
}

@Composable
private fun recentRelativeTimeText(relativeTime: RecentRelativeTime): String {
    val locale = currentLocale()
    return when (relativeTime) {
        JustNow -> stringResource(R.string.recents_time_just_now_label)
        is MinutesAgo -> stringResource(R.string.recents_time_minutes_ago_label, relativeTime.minutes)
        is HoursAgo -> stringResource(R.string.recents_time_hours_ago_label, relativeTime.hours)
        Yesterday -> stringResource(R.string.recents_time_yesterday_label)
        is DaysAgo -> stringResource(R.string.recents_time_days_ago_label, relativeTime.days)
        is ThisYearDate -> remember(relativeTime.date, locale) {
            localizedDateFormatter(THIS_YEAR_DATE_SKELETON, locale).format(relativeTime.date)
        }
        is OtherYearDate -> remember(relativeTime.date, locale) {
            localizedDateFormatter(OTHER_YEAR_DATE_SKELETON, locale).format(relativeTime.date)
        }
    }
}

private fun localizedDateFormatter(skeleton: String, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

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
            now = previewRecentsNow,
            zoneId = previewRecentsZoneId,
            onClick = {},
            modifier = Modifier,
        )
    }
}
