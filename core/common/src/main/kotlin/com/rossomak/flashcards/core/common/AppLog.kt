package com.rossomak.flashcards.core.common

import timber.log.Timber

fun logv(message: () -> String) {
    Timber.v(message())
}

fun logd(message: () -> String) {
    Timber.d(message())
}

fun logi(message: () -> String) {
    Timber.i(message())
}

fun logw(
    throwable: Throwable? = null,
    message: () -> String,
) {
    Timber.w(throwable, message())
}

fun loge(
    throwable: Throwable? = null,
    message: () -> String,
) {
    Timber.e(throwable, message())
}
