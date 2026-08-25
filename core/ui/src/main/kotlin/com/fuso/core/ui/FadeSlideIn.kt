package com.fuso.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.fuso.core.designsystem.motion.FusoMotion
import kotlinx.coroutines.delay

@Composable
fun FadeSlideIn(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val step = index.coerceAtMost(FusoMotion.MaxStaggerSteps)
        delay(step * FusoMotion.StaggerStepMillis)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FusoMotion.DurationMedium,
                easing = FusoMotion.EmphasizedDecelerate,
            ),
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28.dp.toPx()
        },
    ) {
        content()
    }
}
