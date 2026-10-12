package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.feature.home.RecentRelativeTime.DaysAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.HoursAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.JustNow
import com.rossomak.flashcards.feature.home.RecentRelativeTime.MinutesAgo
import com.rossomak.flashcards.feature.home.RecentRelativeTime.OtherYearDate
import com.rossomak.flashcards.feature.home.RecentRelativeTime.ThisYearDate
import com.rossomak.flashcards.feature.home.RecentRelativeTime.Yesterday
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
private val NOW: Instant = localInstant("2026-09-15T12:00")

private fun localInstant(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(WARSAW).toInstant()

private fun relativeTime(startedAt: Instant, now: Instant = NOW): RecentRelativeTime = recentRelativeTime(startedAt, now, WARSAW)

class RecentRelativeTimeTest {

    @Test
    fun `a start time after now reads as just now`() {
        relativeTime(startedAt = NOW.plusSeconds(90)) shouldBe JustNow
    }

    @Test
    fun `a start time equal to now reads as just now`() {
        relativeTime(startedAt = NOW) shouldBe JustNow
    }

    @Test
    fun `59 seconds ago reads as just now`() {
        relativeTime(startedAt = NOW.minusSeconds(59)) shouldBe JustNow
    }

    @Test
    fun `60 seconds ago reads as one minute ago`() {
        relativeTime(startedAt = NOW.minusSeconds(60)) shouldBe MinutesAgo(1)
    }

    @Test
    fun `59 minutes ago reads in minutes`() {
        relativeTime(startedAt = localInstant("2026-09-15T11:01")) shouldBe MinutesAgo(59)
    }

    @Test
    fun `under an hour across midnight reads in minutes, not as yesterday`() {
        relativeTime(startedAt = localInstant("2026-09-14T23:30"), now = localInstant("2026-09-15T00:10")) shouldBe MinutesAgo(40)
    }

    @Test
    fun `earlier the same calendar day reads in elapsed hours`() {
        relativeTime(startedAt = localInstant("2026-09-15T07:00")) shouldBe HoursAgo(5)
    }

    @Test
    fun `the previous calendar day reads as yesterday even when fewer than 24 hours passed`() {
        relativeTime(startedAt = localInstant("2026-09-14T23:00"), now = localInstant("2026-09-15T08:00")) shouldBe Yesterday
    }

    @Test
    fun `two calendar days ago reads in days`() {
        relativeTime(startedAt = localInstant("2026-09-13T18:00")) shouldBe DaysAgo(2)
    }

    @Test
    fun `seven calendar days ago reads in days`() {
        relativeTime(startedAt = localInstant("2026-09-08T09:00")) shouldBe DaysAgo(7)
    }

    @Test
    fun `eight calendar days ago in the same year reads as a date without the year`() {
        val startedDate = LocalDate.parse("2026-09-07")

        relativeTime(startedAt = startedDate.atTime(9, 0).atZone(WARSAW).toInstant()) shouldBe ThisYearDate(startedDate)
    }

    @Test
    fun `a date in a previous year reads as a date with the year`() {
        val startedDate = LocalDate.parse("2025-12-20")

        relativeTime(startedAt = startedDate.atTime(9, 0).atZone(WARSAW).toInstant()) shouldBe OtherYearDate(startedDate)
    }

    @Test
    fun `hours across a DST change count elapsed time, not wall-clock time`() {
        // Clocks jump from 02:00 to 03:00 on 2026-03-29 in Warsaw: 01:30 to 05:30 is four wall-clock hours, three elapsed.
        relativeTime(startedAt = localInstant("2026-03-29T01:30"), now = localInstant("2026-03-29T05:30")) shouldBe HoursAgo(3)
    }
}
