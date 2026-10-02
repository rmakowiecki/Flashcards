package com.rossomak.flashcards.feature.home

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val MINUTES_PER_HOUR = 60L
private const val MAX_DAYS_AGO = 7L

/** How long ago a Recent started, as its row words it. */
internal sealed interface RecentRelativeTime {
    data object JustNow : RecentRelativeTime
    data class MinutesAgo(val minutes: Int) : RecentRelativeTime
    data class HoursAgo(val hours: Int) : RecentRelativeTime
    data object Yesterday : RecentRelativeTime
    data class DaysAgo(val days: Int) : RecentRelativeTime
    data class ThisYearDate(val date: LocalDate) : RecentRelativeTime
    data class OtherYearDate(val date: LocalDate) : RecentRelativeTime
}

/**
 * Minutes and hours are elapsed time, so DST never skews them; days are calendar days in [zoneId].
 * A [startedAt] after [now] (clock skew) reads as just now.
 */
internal fun recentRelativeTime(startedAt: Instant, now: Instant, zoneId: ZoneId): RecentRelativeTime {
    if (!startedAt.isBefore(now)) return RecentRelativeTime.JustNow
    val elapsed = Duration.between(startedAt, now)
    val elapsedMinutes = elapsed.toMinutes()
    if (elapsedMinutes < MINUTES_PER_HOUR) {
        return if (elapsedMinutes == 0L) RecentRelativeTime.JustNow else RecentRelativeTime.MinutesAgo(elapsedMinutes.toInt())
    }
    val startedDate = startedAt.atZone(zoneId).toLocalDate()
    val today = now.atZone(zoneId).toLocalDate()
    val calendarDaysAgo = ChronoUnit.DAYS.between(startedDate, today)
    return when {
        calendarDaysAgo == 0L -> RecentRelativeTime.HoursAgo(elapsed.toHours().coerceAtLeast(1L).toInt())
        calendarDaysAgo == 1L -> RecentRelativeTime.Yesterday
        calendarDaysAgo <= MAX_DAYS_AGO -> RecentRelativeTime.DaysAgo(calendarDaysAgo.toInt())
        startedDate.year == today.year -> RecentRelativeTime.ThisYearDate(startedDate)
        else -> RecentRelativeTime.OtherYearDate(startedDate)
    }
}
