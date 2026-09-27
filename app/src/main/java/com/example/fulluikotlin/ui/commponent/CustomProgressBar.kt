package com.example.fulluikotlin.ui.commponent

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CustomProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 12.dp,
    trackColor: Color = Color.LightGray,
    progressColor: Color = Color(0xFF4CAF50),
    roundedCorners: Dp = 6.dp,
    animate: Boolean = true
) {
    var targetProgress by remember { mutableStateOf(progress) }
    var animatedProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(progress) {
        targetProgress = progress.coerceIn(0f, 1f)
        if (animate) {
            animate(
                initialValue = animatedProgress,
                targetValue = targetProgress,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing
                )
            ) { value, _ ->
                animatedProgress = value
            }
        } else {
            animatedProgress = targetProgress
        }
    }

    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(roundedCorners))
            .background(trackColor)
            .onGloballyPositioned { coordinates: LayoutCoordinates ->
                widthPx = coordinates.size.width
            }
    ) {

        val progressWidth = (animatedProgress * widthPx).roundToInt()
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { progressWidth.toDp() })
                .clip(RoundedCornerShape(roundedCorners))
                .background(progressColor)
        )

    }
}
