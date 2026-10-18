package com.rossomak.flashcards.presentation.debug

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.feature.study.chrome.StudySessionCardMotion
import kotlin.math.roundToInt

private val CardTransformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)

/** Every tunable part of the session card motion. Defaults mirror the production [StudySessionCardMotion]. */
internal data class CardAnimationsDebugMotion(
    val revealDurationMs: Int = FlashcardsMotion.DURATION_LONG_MS,
    val revealEasing: Easing = FlashcardsMotion.EmphasizedEasing,
    val swapDurationMs: Int = FlashcardsMotion.DURATION_MEDIUM_MS,
    val swapStaggerMs: Int = FlashcardsMotion.DURATION_SHORT_MS,
    val swapLeaveEasing: Easing = FlashcardsMotion.StandardEasing,
    val swapArriveEasing: Easing = FlashcardsMotion.EmphasizedEasing,
    val swapScales: Boolean = true,
    val outgoingScale: Float = 0.85f,
    val incomingScale: Float = 0.85f,
    val swapFades: Boolean = true,
    val swapDrifts: Boolean = true,
    val driftFraction: Float = 0.15f,
) {
    fun toCardMotion(): StudySessionCardMotion = StudySessionCardMotion(
        revealSpec = tween(durationMillis = revealDurationMs, easing = revealEasing),
        swapTransform = ::swapTransform,
    )

    private fun swapTransform(isStepBack: Boolean): ContentTransform {
        val (leavingScale, arrivingScale) = if (isStepBack) incomingScale to outgoingScale else outgoingScale to incomingScale
        val driftSign = if (isStepBack) -1 else 1
        var enter = EnterTransition.None
        var exit = ExitTransition.None
        if (swapFades) {
            enter += fadeIn(arriveSpec())
            exit += fadeOut(leaveSpec())
        }
        if (swapScales) {
            enter += scaleIn(arriveSpec(), initialScale = arrivingScale, transformOrigin = CardTransformOrigin)
            exit += scaleOut(leaveSpec(), targetScale = leavingScale, transformOrigin = CardTransformOrigin)
        }
        if (swapDrifts) {
            enter += slideInVertically(arriveSpec()) { fullHeight -> -driftSign * (fullHeight * driftFraction).roundToInt() }
            exit += slideOutVertically(leaveSpec()) { fullHeight -> driftSign * (fullHeight * driftFraction).roundToInt() }
        }
        return ContentTransform(targetContentEnter = enter, initialContentExit = exit, sizeTransform = SizeTransform(clip = false))
    }

    private fun <T> leaveSpec(): TweenSpec<T> = tween(durationMillis = swapDurationMs, easing = swapLeaveEasing)

    private fun <T> arriveSpec(): TweenSpec<T> = tween(durationMillis = swapDurationMs, delayMillis = swapStaggerMs, easing = swapArriveEasing)
}
