package com.rossomak.flashcards.core.domain.repository

import java.time.LocalDate

class FakeTodayProvider(var today: LocalDate = LocalDate.of(2026, 9, 6)) : TodayProvider {
    override fun today(): LocalDate = today
}
