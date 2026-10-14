package com.rossomak.flashcards.feature.debug.backdropblur

data class BackdropBlurDebugScreenState(
    val isBlobsOn: Boolean = true,
    val isBlurOn: Boolean = true,
    val blurRadiusDp: Float,
)
