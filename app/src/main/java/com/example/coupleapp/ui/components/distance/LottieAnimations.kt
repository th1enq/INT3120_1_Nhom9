package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lottie Animation Components for Distance Feature
 * 
 * Recommended Lottie animations to download from LottieFiles.com:
 * 
 * 1. Location Pin Pulse Animation
 *    - Search: "location pin", "map marker pulse"
 *    - Use for: Map markers with cute bouncy effect
 *    - Example: https://lottiefiles.com/animations/location-pin
 * 
 * 2. Heart Connection Animation  
 *    - Search: "heart connection", "love link"
 *    - Use for: Showing connection between two users on map
 *    - Example: https://lottiefiles.com/animations/heart-connection
 * 
 * 3. Cute Walking Character
 *    - Search: "walking kawaii", "cute character walk"
 *    - Use for: Location loading states
 *    - Example: https://lottiefiles.com/animations/walking-cute
 * 
 * 4. GPS/Location Loading
 *    - Search: "gps loading", "location searching"
 *    - Use for: While fetching location data
 *    - Example: https://lottiefiles.com/animations/gps-location
 * 
 * 5. Photo Gallery Animation
 *    - Search: "photo gallery", "pictures kawaii"
 *    - Use for: Shared places photo sections
 *    - Example: https://lottiefiles.com/animations/photo-gallery
 * 
 * 6. Sparkle/Stars Animation
 *    - Search: "sparkle", "stars cute"
 *    - Use for: Decorative effects on avatars
 *    - Example: https://lottiefiles.com/animations/sparkle-stars
 * 
 * Place downloaded .json files in: res/raw/ folder
 * Name them like: lottie_location_pin.json, lottie_heart_connection.json, etc.
 */

/**
 * Location loading animation
 * Shows a cute GPS/location searching animation
 */
@Composable
fun LocationLoadingAnimation(
    modifier: Modifier = Modifier
) {
    // When you have the lottie file, uncomment this:
    /*
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_location_loading)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(120.dp)
    )
    */
    
    // Placeholder until lottie is added - use animated emoji
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "locationLoading")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        androidx.compose.material3.Text(
            text = "📍",
            fontSize = (48 * scale).toInt().sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Heart connection animation between two users
 */
@Composable
fun HeartConnectionAnimation(
    modifier: Modifier = Modifier
) {
    // When you have the lottie file:
    /*
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_heart_connection)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
    */
    
    // Placeholder animation
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "heart")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        
        androidx.compose.material3.Text(
            text = "💕",
            fontSize = 32.sp,
            modifier = Modifier
                .padding(8.dp)
                .graphicsLayer { this.alpha = alpha }
        )
    }
}

/**
 * Sparkle decoration animation for avatars
 */
@Composable
fun SparkleAnimation(
    modifier: Modifier = Modifier
) {
    // When you have the lottie file:
    /*
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_sparkle)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
    */
    
    // Placeholder animation
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        
        androidx.compose.material3.Text(
            text = "✨",
            fontSize = 20.sp,
            modifier = Modifier
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

/**
 * Empty state animation for no shared places
 */
@Composable
fun EmptyPlacesAnimation(
    modifier: Modifier = Modifier
) {
    // When you have the lottie file:
    /*
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_empty_places)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(200.dp)
    )
    */
    
    // Placeholder animation
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "empty")
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
        
        androidx.compose.material3.Text(
            text = "🗺️",
            fontSize = 64.sp,
            modifier = Modifier
                .offset(y = offsetY.dp)
        )
    }
}

/**
 * Success checkmark animation for added photos
 */
@Composable
fun SuccessAnimation(
    modifier: Modifier = Modifier,
    onAnimationEnd: () -> Unit = {}
) {
    // When you have the lottie file:
    /*
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_success)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        cancellationBehavior = LottieCancellationBehavior.OnIterationFinish
    )
    
    LaunchedEffect(progress) {
        if (progress == 1f) {
            onAnimationEnd()
        }
    }
    
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(100.dp)
    )
    */
    
    // Placeholder
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val scale = remember { Animatable(0f) }
        
        LaunchedEffect(Unit) {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            kotlinx.coroutines.delay(500)
            onAnimationEnd()
        }
        
        androidx.compose.material3.Text(
            text = "✅",
            fontSize = 48.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        )
    }
}
