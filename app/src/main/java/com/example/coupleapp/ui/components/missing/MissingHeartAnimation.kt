package com.example.coupleapp.ui.components.missing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.example.coupleapp.R

/**
 * Heart animation component using Lottie animation
 * Displays on background_missing.png with Heart beating.json animation
 */
@Composable
fun MissingHeartAnimation(
    isAnimating: Boolean,
    clickCount: Int,
    onHeartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Lottie composition
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.heart_beating)
    )
    
    // Use clickCount as key to restart animation on each click
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = isAnimating,
        speed = 3f, // Faster animation
        restartOnPlay = true,
        cancellationBehavior = LottieCancellationBehavior.Immediately
    )
    
    // Quick bounce scale animation that resets on each click
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium // Faster spring
        ),
        label = "heartScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onHeartClick() },
        contentAlignment = Alignment.Center
    ) {
        // Background image - full width
        Image(
            painter = painterResource(id = R.drawable.background_missing),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Heart Lottie animation - bigger size
        LottieAnimation(
            composition = composition,
            progress = { if (isAnimating) progress else 0f },
            modifier = Modifier
                .size(280.dp) // Bigger heart
                .scale(scale)
        )
    }
}
