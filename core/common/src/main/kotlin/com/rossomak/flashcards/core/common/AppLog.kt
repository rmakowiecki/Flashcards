package com.rossomak.flashcards.core.common

import timber.log.Timber

inline fun logv(message: () -> String) {
    Timber.v(message())
}

inline fun logd(message: () -> String) {
    Timber.d(message())
}

inline fun logi(message: () -> String) {
    Timber.i(message())
}

inline fun logw(
    throwable: Throwable? = null,
    message: () -> String,
) {
    Timber.w(throwable, message())
}

inline fun loge(
    throwable: Throwable? = null,
    message: () -> String,
) {
    Timber.e(throwable, message())
}
