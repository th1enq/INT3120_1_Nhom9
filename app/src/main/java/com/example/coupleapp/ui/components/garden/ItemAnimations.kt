package com.example.coupleapp.ui.components.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.CareItem
import com.example.coupleapp.data.model.CareItemType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animation overlay for using care items
 */
@Composable
fun ItemUseAnimation(
    item: CareItem?,
    isAnimating: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isAnimating || item == null) return

    var animationPhase by remember { mutableStateOf(0) }
    
    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            animationPhase = 1
            delay(800) // Item flies to plant
            animationPhase = 2
            delay(1200) // Item performs action
            animationPhase = 0
            onAnimationEnd()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (item.type) {
            CareItemType.WATER -> WateringAnimation(
                phase = animationPhase,
                iconRes = item.iconRes
            )
            CareItemType.SUNLIGHT -> SunlightAnimation(
                phase = animationPhase,
                iconRes = item.iconRes
            )
            CareItemType.PESTICIDE -> PesticideAnimation(
                phase = animationPhase,
                iconRes = item.iconRes
            )
            CareItemType.SCISSORS -> ScissorsAnimation(
                phase = animationPhase,
                iconRes = item.iconRes
            )
            CareItemType.FERTILIZER_4H, CareItemType.FERTILIZER_8H, CareItemType.FERTILIZER_24H -> 
                FertilizerAnimation(
                    phase = animationPhase,
                    iconRes = item.iconRes
                )
            else -> {}
        }
    }
}

/**
 * Watering can animation - flies in and tilts to pour water
 * LOCATION: ItemAnimations.kt - WateringAnimation function
 * Adjust offsetX/offsetY values to change animation position
 * SIZE: Change .size(XXX.dp) to make item larger/smaller
 */
@Composable
fun WateringAnimation(
    phase: Int,
    iconRes: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaterDrops")
    
    // Item position animation - more to the right toward plant
    // EDIT offsetX values to move left/right (positive = right)
    val offsetX by animateFloatAsState(
        targetValue = when (phase) {
            1 -> 80f      // Right of center, toward plant
            2 -> 60f      // Tilting position, pouring toward plant
            else -> 200f  // Start from right
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "WateringX"
    )

    // EDIT offsetY values to move up/down (negative = up)
    val offsetY by animateFloatAsState(
        targetValue = when (phase) {
            1 -> -40f     // Above plant
            2 -> -60f     // Tilted position
            else -> 200f  // Start from bottom
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "WateringY"
    )

    // Tilt animation for pouring
    val rotation by animateFloatAsState(
        targetValue = when (phase) {
            2 -> -45f
            else -> 0f
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "WateringRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Watering can - SIZE: change 120.dp to adjust
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Watering",
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.Center)
                .offset(x = offsetX.dp, y = offsetY.dp)
                .graphicsLayer {
                    rotationZ = rotation
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.2f, 0.8f)
                },
            contentScale = ContentScale.Fit
        )

        // Water drops when pouring - positioned relative to can
        if (phase == 2) {
            WaterDropsEffect(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (offsetX - 60).dp, y = (offsetY + 35).dp)
            )
        }
    }
}

/**
 * Water drops particle effect
 */
@Composable
fun WaterDropsEffect(
    modifier: Modifier = Modifier
) {
    val drops = remember {
        List(8) {
            WaterDrop(
                startX = Random.nextFloat() * 40 - 20,
                startY = 0f,
                endY = 100f + Random.nextFloat() * 50,
                delay = Random.nextInt(200)
            )
        }
    }

    Box(modifier = modifier) {
        drops.forEach { drop ->
            AnimatedWaterDrop(drop = drop)
        }
    }
}

data class WaterDrop(
    val startX: Float,
    val startY: Float,
    val endY: Float,
    val delay: Int
)

@Composable
fun AnimatedWaterDrop(drop: WaterDrop) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaterDrop")
    
    val y by infiniteTransition.animateFloat(
        initialValue = drop.startY,
        targetValue = drop.endY,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = drop.delay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DropY"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = drop.delay),
            repeatMode = RepeatMode.Restart
        ),
        label = "DropAlpha"
    )

    Canvas(modifier = Modifier.size(60.dp)) {
        drawCircle(
            color = Color(0xFF64B5F6).copy(alpha = alpha),
            radius = 4.dp.toPx(),
            center = Offset(
                x = size.width / 2 + drop.startX.dp.toPx(),
                y = y.dp.toPx()
            )
        )
    }
}

/**
 * Sunlight animation - sun rays spreading
 * LOCATION: ItemAnimations.kt - SunlightAnimation function
 * Adjust offset values to change position
 */
@Composable
fun SunlightAnimation(
    phase: Int,
    iconRes: Int
) {
    val scale by animateFloatAsState(
        targetValue = when (phase) {
            1 -> 1f
            2 -> 1.3f
            else -> 0f
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "SunScale"
    )

    val alpha by animateFloatAsState(
        targetValue = when (phase) {
            1, 2 -> 1f
            else -> 0f
        },
        animationSpec = tween(500),
        label = "SunAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Sun rays - lowered, above plant
        if (phase == 2) {
            SunRaysEffect(
                modifier = Modifier
                    .size(180.dp)
                    .offset(y = (-70).dp)
            )
        }

        // Sun icon - SIZE: change 130.dp to adjust
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Sunlight",
            modifier = Modifier
                .size(130.dp)
                .offset(y = (-70).dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Sun rays effect
 */
@Composable
fun SunRaysEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "SunRays")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RaysRotation"
    )

    val rayAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RayAlpha"
    )

    Canvas(
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    ) {
        val rayCount = 12
        val centerX = size.width / 2
        val centerY = size.height / 2
        val innerRadius = size.minDimension / 4
        val outerRadius = size.minDimension / 2

        for (i in 0 until rayCount) {
            val angle = (i * 360f / rayCount) * (PI / 180f).toFloat()
            val startX = centerX + innerRadius * cos(angle)
            val startY = centerY + innerRadius * sin(angle)
            val endX = centerX + outerRadius * cos(angle)
            val endY = centerY + outerRadius * sin(angle)

            drawLine(
                color = Color(0xFFFFD54F).copy(alpha = rayAlpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}

/**
 * Pesticide spray animation
 * LOCATION: ItemAnimations.kt - PesticideAnimation function
 * Adjust offsetX/offsetY to change spray position
 * SIZE: Change .size(XXX.dp) to make item larger/smaller
 */
@Composable
fun PesticideAnimation(
    phase: Int,
    iconRes: Int
) {
    // EDIT offsetX to move left/right (positive = right)
    val offsetX by animateFloatAsState(
        targetValue = when (phase) {
            1 -> 100f      // Right of center, toward plant
            2 -> 90f      // Spray position near plant
            else -> 200f  // Start from right
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "SprayX"
    )

    // EDIT offsetY to move up/down (negative = up, positive = down)
    // Now at plant level (much lower)
    val offsetY by animateFloatAsState(
        targetValue = when (phase) {
            1 -> 80f      // At plant level (lower)
            2 -> 75f      // Spray position at plant
            else -> 150f
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "SprayY"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Spray bottle - SIZE: change 120.dp to adjust
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Pesticide",
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.Center)
                .offset(x = offsetX.dp, y = offsetY.dp),
            contentScale = ContentScale.Fit
        )

        // Spray mist - at nozzle height (same Y as bottle top)
        if (phase == 2) {
            SprayMistEffect(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (offsetX - 70).dp, y = (offsetY - 30).dp)
            )
        }
    }
}

/**
 * Spray mist particle effect
 */
@Composable
fun SprayMistEffect(modifier: Modifier = Modifier) {
    val particles = remember {
        List(15) {
            SprayParticle(
                angle = Random.nextFloat() * 60 - 30, // -30 to 30 degrees
                distance = 30f + Random.nextFloat() * 50,
                size = 3f + Random.nextFloat() * 5,
                delay = Random.nextInt(300)
            )
        }
    }

    Box(modifier = modifier) {
        particles.forEach { particle ->
            AnimatedSprayParticle(particle = particle)
        }
    }
}

data class SprayParticle(
    val angle: Float,
    val distance: Float,
    val size: Float,
    val delay: Int
)

@Composable
fun AnimatedSprayParticle(particle: SprayParticle) {
    val infiniteTransition = rememberInfiniteTransition(label = "SprayParticle")
    
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = particle.delay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SprayProgress"
    )

    val angleRad = particle.angle * (PI / 180f).toFloat()
    
    Canvas(modifier = Modifier.size(100.dp)) {
        val x = -progress * particle.distance * cos(angleRad)
        val y = progress * particle.distance * sin(angleRad) * 0.3f

        drawCircle(
            color = Color(0xFF81C784).copy(alpha = 1f - progress),
            radius = particle.size.dp.toPx() * (1f - progress * 0.5f),
            center = Offset(
                x = size.width / 2 + x.dp.toPx(),
                y = size.height / 2 + y.dp.toPx()
            )
        )
    }
}

/**
 * Scissors cutting animation
 * LOCATION: ItemAnimations.kt - ScissorsAnimation function
 * Adjust offsetX/offsetY to change scissors position
 * SIZE: Change .size(XXX.dp) to make item larger/smaller
 */
@Composable
fun ScissorsAnimation(
    phase: Int,
    iconRes: Int
) {
    // EDIT offsetX to move left/right (positive = right)
    // Now comes from right side and cuts into plant
    val offsetX by animateFloatAsState(
        targetValue = when (phase) {
            1 -> 85f       // Right side of plant
            2 -> 65f       // Cutting into plant
            else -> 200f   // Start from right
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ScissorsX"
    )

    // EDIT offsetY to move up/down - same level as pesticide (at plant)
    val offsetY by animateFloatAsState(
        targetValue = when (phase) {
            1 -> 20f       // At plant level (same as pesticide)
            2 -> 20f       // Stay at plant level while cutting
            else -> 150f
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ScissorsY"
    )

    // Snip animation
    val infiniteTransition = rememberInfiniteTransition(label = "Snip")
    val snipRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SnipRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Scissors - SIZE: change 120.dp to adjust
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Scissors",
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.Center)
                .offset(x = offsetX.dp, y = offsetY.dp)
                .graphicsLayer {
                    rotationZ = if (phase == 2) snipRotation else 0f
                },
            contentScale = ContentScale.Fit
        )

        // Leaf particles when cutting
        if (phase == 2) {
            LeafParticlesEffect(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (offsetX - 20).dp, y = (offsetY + 40).dp)
            )
        }
    }
}

/**
 * Leaf particles falling effect
 */
@Composable
fun LeafParticlesEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "LeafParticles")
    
    val leaves = remember {
        List(5) {
            LeafParticle(
                startX = Random.nextFloat() * 60 - 30,
                rotation = Random.nextFloat() * 360,
                delay = Random.nextInt(200)
            )
        }
    }

    Box(modifier = modifier) {
        leaves.forEachIndexed { index, leaf ->
            val y by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 80f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, delayMillis = leaf.delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "LeafY$index"
            )

            val rotation by infiniteTransition.animateFloat(
                initialValue = leaf.rotation,
                targetValue = leaf.rotation + 180f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, delayMillis = leaf.delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "LeafRotation$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, delayMillis = leaf.delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "LeafAlpha$index"
            )

            Text(
                "🍃",
                fontSize = 16.sp,
                modifier = Modifier
                    .offset(x = leaf.startX.dp, y = y.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                        this.alpha = alpha
                    }
            )
        }
    }
}

data class LeafParticle(
    val startX: Float,
    val rotation: Float,
    val delay: Int
)

/**
 * Fertilizer animation - sparkles and growth boost
 * LOCATION: ItemAnimations.kt - FertilizerAnimation function
 * Adjust offsetY to change drop position
 */
@Composable
fun FertilizerAnimation(
    phase: Int,
    iconRes: Int
) {
    // CENTERED - drops from top
    val offsetY by animateFloatAsState(
        targetValue = when (phase) {
            1 -> -120f   // From top
            2 -> -40f    // Into soil
            else -> -200f
        },
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "FertilizerY"
    )

    val scale by animateFloatAsState(
        targetValue = when (phase) {
            2 -> 0f
            else -> 1f
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "FertilizerScale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Fertilizer icon - SIZE: change 120.dp to adjust
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Fertilizer",
            modifier = Modifier
                .size(120.dp)
                .offset(y = offsetY.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentScale = ContentScale.Fit
        )

        // Growth boost effect - CENTERED at plant
        if (phase == 2) {
            GrowthBoostEffect(
                modifier = Modifier.size(180.dp)
            )
        }
    }
}

/**
 * Growth boost particle effect
 */
@Composable
fun GrowthBoostEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "GrowthBoost")
    
    // Rising sparkles
    val particles = remember {
        List(10) {
            GrowthParticle(
                x = Random.nextFloat() * 150 - 75,
                delay = Random.nextInt(400)
            )
        }
    }

    Box(modifier = modifier) {
        particles.forEachIndexed { index, particle ->
            val y by infiniteTransition.animateFloat(
                initialValue = 100f,
                targetValue = -50f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, delayMillis = particle.delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "GrowthY$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0
                        1f at 300
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "GrowthAlpha$index"
            )

            Text(
                "✨",
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = particle.x.dp, y = y.dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }
    }
}

data class GrowthParticle(
    val x: Float,
    val delay: Int
)
