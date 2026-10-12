package com.rossomak.flashcards.feature.debug

import com.rossomak.flashcards.core.domain.model.AppVersion

/** [appVersion] is null only until the first load completes, which lands before the first frame. */
data class DebugScreenState(
    val appVersion: AppVersion? = null,
)
