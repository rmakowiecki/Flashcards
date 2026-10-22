package com.rossomak.flashcards.core.domain.repository

import java.time.LocalDate

/**
 * Today's calendar day in the device's local timezone, the same day boundary the Streak and Daily Goal
 * use (a session's study day). Read fresh on every call, so a midnight or a timezone change shows on
 * the next read.
 */
fun interface TodayProvider {

    fun today(): LocalDate
}
