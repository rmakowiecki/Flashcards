package com.rossomak.flashcards.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rossomak.flashcards.R
import com.rossomak.flashcards.core.ui.animation.SharedElementKey
import com.rossomak.flashcards.core.ui.animation.sharedElementByKey
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LogoWidth = 240.dp
private val LogoHeight = LogoWidth * (1000f / 1800f)
private const val GRADIENT_SLIDE_START_DELAY_MS = 500
private const val GRADIENT_SLIDE_MS = 750
private const val TEXT_REVEAL_DELAY_MS = 750
private const val TEXT_REVEAL_DURATION_MS = 500
private const val ANIMATION_COMPLETE_MS = 2000L

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateToMain: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToCategoryDetails: (CategoryDetailsRoute) -> Unit,
    onNavigateToSubcategoryDetails: (SubcategoryDetailsRoute) -> Unit,
) {
    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            SplashDestination.Main -> onNavigateToMain()
            SplashDestination.Onboarding -> onNavigateToOnboarding()
            SplashDestination.Login -> onNavigateToLogin()
            is SplashDestination.ToCategoryDetails -> onNavigateToCategoryDetails(destination.route)
            is SplashDestination.ToSubcategoryDetails -> onNavigateToSubcategoryDetails(destination.route)
        }
    }

    SplashContent(modifier = modifier, onAnimationCompleted = viewModel::onAnimationCompleted)
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun SplashContent(
    modifier: Modifier = Modifier,
    onAnimationCompleted: () -> Unit,
) {
    val gradientSlide = remember { Animatable(0f) }
    val textReveal = remember { Animatable(0f) }
    var logoAtEnd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            gradientSlide.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GRADIENT_SLIDE_MS,
                    easing = FastOutSlowInEasing,
                    delayMillis = GRADIENT_SLIDE_START_DELAY_MS
                )
            )
        }
        launch {
            textReveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TEXT_REVEAL_DURATION_MS,
                    easing = FastOutSlowInEasing,
                    delayMillis = TEXT_REVEAL_DELAY_MS
                )
            )
        }
        logoAtEnd = true
        delay(ANIMATION_COMPLETE_MS)
        onAnimationCompleted()
    }

    val logoPainter = rememberAnimatedVectorPainter(
        animatedImageVector = AnimatedImageVector.animatedVectorResource(R.drawable.ic_flashcards_logo_anim),
        atEnd = logoAtEnd
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradientBase),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = gradientSlide.value
                    translationY = (1f - gradientSlide.value) * size.height
                }
                .background(MaterialTheme.brandColors.screenGradient)
        )
        Image(
            painter = logoPainter,
            contentDescription = null,
            // Handed to the onboarding cover as a shared element, so the mark flies into its new
            // size and position instead of the two screens cross-fading two copies of it.
            modifier = Modifier
                .sharedElementByKey(SharedElementKey.APP_LOGO)
                .size(width = LogoWidth, height = LogoHeight)
        )
        Text(
            text = stringResource(R.string.splash_tagline).uppercase(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .offset(y = LogoHeight / 2)
                .graphicsLayer {
                    alpha = textReveal.value
                    translationY = (1f - textReveal.value) * 12.dp.toPx()
                }
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun SplashContentPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(com.rossomak.flashcards.core.ui.R.drawable.flashcards_white),
            contentDescription = null,
            modifier = Modifier.size(width = LogoWidth, height = LogoHeight)
        )
        Text(
            text = stringResource(R.string.splash_tagline).uppercase(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.offset(y = LogoHeight / 2)
        )
    }
}
