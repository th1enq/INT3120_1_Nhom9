package com.example.coupleapp.ui.components.store

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.PurchaseResult
import kotlinx.coroutines.delay

/**
 * Purchase result dialog showing success or error
 */
@Composable
fun PurchaseResultDialog(
    result: PurchaseResult,
    onDismiss: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    val (isSuccess, title, message, iconColor, bgColor) = when (result) {
        is PurchaseResult.Success -> {
            Quintuple(
                true,
                "Purchase Successful! 🎉",
                "You have received ${result.item.name}!\nNew balance: ${result.newBalance} coins",
                Color(0xFF4CAF50),
                Color(0xFFE8F5E9)
            )
        }
        is PurchaseResult.InsufficientFunds -> {
            Quintuple(
                false,
                "Insufficient Funds 💰",
                "You need ${result.required} coins but only have ${result.current} coins.\nPlease earn more coins first!",
                Color(0xFFF44336),
                Color(0xFFFFEBEE)
            )
        }
        is PurchaseResult.OnCooldown -> {
            Quintuple(
                false,
                "On Cooldown ⏳",
                "This item is still on cooldown.\nPlease wait before claiming again!",
                Color(0xFFFF9800),
                Color(0xFFFFF3E0)
            )
        }
        is PurchaseResult.AdNotAvailable -> {
            Quintuple(
                false,
                "Ad Not Available 📺",
                "No ads available at the moment.\nPlease try again later!",
                Color(0xFF9C27B0),
                Color(0xFFF3E5F5)
            )
        }
        is PurchaseResult.Error -> {
            Quintuple(
                false,
                "Error Occurred ⚠️",
                result.message,
                Color(0xFFF44336),
                Color(0xFFFFEBEE)
            )
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        shape = RoundedCornerShape(32.dp),
        title = null,
        text = {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                label = "dialog_content"
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Animated icon
                    AnimatedResultIcon(
                        isSuccess = isSuccess,
                        iconColor = iconColor
                    )
                    
                    // Title
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        textAlign = TextAlign.Center
                    )
                    
                    // Divider
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        iconColor.copy(alpha = 0.3f),
                                        iconColor,
                                        iconColor.copy(alpha = 0.3f)
                                    )
                                )
                            )
                    )
                    
                    // Message
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = Color(0xFF5D4037),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            BeautifulResultButton(
                isSuccess = isSuccess,
                iconColor = iconColor,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
            )
        }
    )
}

/**
 * Animated result icon (success/error)
 */
@Composable
fun AnimatedResultIcon(
    isSuccess: Boolean,
    iconColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = if (isSuccess) -5f else 0f,
        targetValue = if (isSuccess) 5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .graphicsLayer { rotationZ = rotation }
    ) {
        // Outer glow circle
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        iconColor.copy(alpha = 0.3f),
                        iconColor.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
        }
        
        // Inner solid circle
        Box(
            modifier = Modifier
                .size(84.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            iconColor.copy(alpha = 0.2f),
                            iconColor.copy(alpha = 0.15f)
                        )
                    )
                )
                .border(3.dp, iconColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSuccess) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = iconColor,
                    modifier = Modifier.size(50.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Failed",
                    tint = iconColor,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }
}

/**
 * Beautiful result button for dialog
 */
@Composable
fun BeautifulResultButton(
    isSuccess: Boolean,
    iconColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        iconColor,
                        iconColor.copy(alpha = 0.8f)
                    )
                )
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSuccess) "Great!" else "Got it",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// Helper data class for quintuple values
internal data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
