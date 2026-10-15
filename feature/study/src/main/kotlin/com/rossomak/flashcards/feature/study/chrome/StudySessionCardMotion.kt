package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.TransformOrigin
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import kotlin.math.roundToInt

private const val CARD_SWAP_SCALE = 0.85f
private const val CARD_SWAP_DRIFT_FRACTION = 0.15f

/** Scale cards about their top edge, which is where they sit. */
private val CardTransformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)

/** The session card's reveal and swap motion. Sessions use the defaults; a provider of [LocalStudySessionCardMotion] can swap in its own. */
data class StudySessionCardMotion(
    val revealSpec: FiniteAnimationSpec<Float> = tween(durationMillis = FlashcardsMotion.DURATION_LONG_MS, easing = FlashcardsMotion.EmphasizedEasing),
    val swapTransform: (isStepBack: Boolean) -> ContentTransform = ::cardSwapTransform,
)

val LocalStudySessionCardMotion = compositionLocalOf { StudySessionCardMotion() }

/** Forward: the old card shrinks and drifts down, the new one grows in from above. Stepping back mirrors the drift. */
private fun cardSwapTransform(isStepBack: Boolean): ContentTransform {
    val drift = if (isStepBack) -CARD_SWAP_DRIFT_FRACTION else CARD_SWAP_DRIFT_FRACTION
    val enter = fadeIn(arriveSpec()) +
        scaleIn(arriveSpec(), initialScale = CARD_SWAP_SCALE, transformOrigin = CardTransformOrigin) +
        slideInVertically(arriveSpec()) { fullHeight -> -(fullHeight * drift).roundToInt() }
    val exit = fadeOut(leaveSpec()) +
        scaleOut(leaveSpec(), targetScale = CARD_SWAP_SCALE, transformOrigin = CardTransformOrigin) +
        slideOutVertically(leaveSpec()) { fullHeight -> (fullHeight * drift).roundToInt() }
    return ContentTransform(targetContentEnter = enter, initialContentExit = exit, sizeTransform = SizeTransform(clip = false))
}

private fun <T> leaveSpec(): TweenSpec<T> = tween(durationMillis = FlashcardsMotion.DURATION_MEDIUM_MS, easing = FlashcardsMotion.StandardEasing)

private fun <T> arriveSpec(): TweenSpec<T> = tween(
    durationMillis = FlashcardsMotion.DURATION_MEDIUM_MS,
    delayMillis = FlashcardsMotion.DURATION_SHORT_MS,
    easing = FlashcardsMotion.EmphasizedEasing,
)
