package com.rossomak.flashcards.core.data.logging

import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logi
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.logging.DomainLogger
import javax.inject.Inject

class DefaultDomainLogger @Inject constructor() : DomainLogger {
    override fun info(message: () -> String) = logi(message)

    override fun warn(message: () -> String) = logw(message = message)

    override fun error(throwable: Throwable?, message: () -> String) = loge(throwable, message)
}
