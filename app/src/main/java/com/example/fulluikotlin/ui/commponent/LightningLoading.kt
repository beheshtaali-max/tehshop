package com.example.fulluikotlin.ui.commponent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LightningLoading(
    modifier: Modifier = Modifier,
    lightningSize: Dp = 20.dp,
    primaryColor: Color = Color(0xFFFFFFFF),
    secondaryColor: Color = Color(0xFFCBC8C8)
) {

    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {

            // 1️⃣ بزرگ شدن
            scale.animateTo(
                targetValue = 1.3f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )

            // 2️⃣ کوچیک شدن
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )

            // 3️⃣ چرخش کامل
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(700, easing = LinearEasing)
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(lightningSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = rotation.value
                }
        ) {

            val w = size.width
            val h = size.height
            val minDim = size.minDimension

            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.05f)
                lineTo(w * 0.25f, h * 0.55f)
                lineTo(w * 0.45f, h * 0.55f)
                lineTo(w * 0.35f, h * 0.95f)
                lineTo(w * 0.75f, h * 0.4f)
                lineTo(w * 0.55f, h * 0.4f)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.6f), Color.Transparent),
                    center = center,
                    radius = minDim
                )
            )

            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor, secondaryColor)
                )
            )
        }
    }
}