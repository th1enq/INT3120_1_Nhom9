package com.example.coupleapp.ui.components.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.Plant
import com.example.coupleapp.data.model.PlantFlowerColor
import com.example.coupleapp.data.model.PlantStage
import com.example.coupleapp.data.model.PlantStatusType
import kotlin.math.sin

/**
 * Animated plant display on shelf
 */
@Composable
fun AnimatedPlantDisplay(
    plant: Plant,
    plantThought: PlantStatusType?,
    isExcited: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PlantAnimation")
    
    // Gentle swaying animation for leaves only
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PlantSway"
    )

    // Breathing animation for leaves (scale up and down slightly)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PlantBreathe"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Thought bubble
        AnimatedVisibility(
            visible = plantThought != null,
            enter = fadeIn(tween(300)) + scaleIn(tween(300)),
            exit = fadeOut(tween(300)) + scaleOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-20).dp)
        ) {
            ThoughtBubble(
                statusType = plantThought,
                modifier = Modifier.size(60.dp)
            )
        }

        // Plant pot and plant
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Plant image - animate only leaves area with transform origin at bottom
            Box(
                contentAlignment = Alignment.BottomCenter
            ) {
                // Apply color filter for blooming flowers
                val colorMatrix = if (plant.stage == PlantStage.BLOOMING) {
                    createHueRotationMatrix(plant.flowerColor.colorHue)
                } else null

                Image(
                    painter = painterResource(id = plant.stage.imageRes),
                    contentDescription = plant.stage.displayName,
                    modifier = Modifier
                        .size(500.dp)
                        .graphicsLayer {
                            // Apply sway animation only to top part (leaves)
                            // Transform origin at bottom center so pot stays stable
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            rotationZ = swayAngle
                            scaleX = breatheScale
                            scaleY = breatheScale
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(it) }
                )

                // Sparkle effect for blooming plants
                if (plant.stage == PlantStage.BLOOMING) {
                    SparkleEffect(
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}

/**
 * Thought bubble showing what plant needs
 */
@Composable
fun ThoughtBubble(
    statusType: PlantStatusType?,
    modifier: Modifier = Modifier
) {
    if (statusType == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "ThoughtBubble")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ThoughtFloat"
    )

    Box(
        modifier = modifier
            .offset(y = (-floatOffset).dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val icon = when (statusType) {
            PlantStatusType.SUNLIGHT -> "☀️"
            PlantStatusType.WATER -> "💧"
            PlantStatusType.HEALTH -> "🌿"
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 24.sp
            )
            Text(
                "💭",
                fontSize = 10.sp,
                modifier = Modifier.offset(y = (-4).dp)
            )
        }
    }
}

/**
 * Sparkle effect for blooming plants
 */
@Composable
fun SparkleEffect(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Sparkle")
    
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleAlpha"
    )

    Box(modifier = modifier) {
        // Multiple sparkles at different positions
        listOf(
            Pair(0.2f, 0.3f),
            Pair(0.8f, 0.2f),
            Pair(0.5f, 0.1f),
            Pair(0.3f, 0.5f),
            Pair(0.7f, 0.4f)
        ).forEachIndexed { index, (x, y) ->
            val delay = index * 200
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = sparkleAlpha,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "SparkleItem$index"
            )

            Text(
                "✨",
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.TopStart)
                    .offset(
                        x = (x * 150).dp,
                        y = (y * 100).dp
                    )
                    .graphicsLayer { this.alpha = alpha }
            )
        }
    }
}

/**
 * Stage name label only (no shelf image - uses background)
 */
@Composable
fun PlantStageLabel(
    stageName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF8D6E63),
                        Color(0xFF6D4C41)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "+",
                color = Color(0xFFD7CCC8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                stageName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "+",
                color = Color(0xFFD7CCC8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

/**
 * Create color matrix for hue rotation (for flower colors)
 */
fun createHueRotationMatrix(hue: Float): ColorMatrix {
    val hueDegrees = hue
    val hueRadians = Math.toRadians(hueDegrees.toDouble()).toFloat()
    val cosH = kotlin.math.cos(hueRadians)
    val sinH = kotlin.math.sin(hueRadians)

    return ColorMatrix(
        floatArrayOf(
            0.213f + cosH * 0.787f - sinH * 0.213f,
            0.715f - cosH * 0.715f - sinH * 0.715f,
            0.072f - cosH * 0.072f + sinH * 0.928f,
            0f, 0f,

            0.213f - cosH * 0.213f + sinH * 0.143f,
            0.715f + cosH * 0.285f + sinH * 0.140f,
            0.072f - cosH * 0.072f - sinH * 0.283f,
            0f, 0f,

            0.213f - cosH * 0.213f - sinH * 0.787f,
            0.715f - cosH * 0.715f + sinH * 0.715f,
            0.072f + cosH * 0.928f + sinH * 0.072f,
            0f, 0f,

            0f, 0f, 0f, 1f, 0f
        )
    )
}
