package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.coupleapp.R
import com.example.coupleapp.ui.components.GradientButton
import com.example.coupleapp.ui.components.OutlinedCustomButton
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onGoogleLoginClick: () -> Unit,
    onPhoneLoginClick: () -> Unit,
    onSignUpClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF5F8),
                        Color(0xFFFFFBF5),
                        Color(0xFFF5F8FF)
                    )
                )
            )
    ) {
        // Floating decorative elements
        FloatingShapes()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Logo and Title Section
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800)) + 
                        slideInVertically(animationSpec = tween(800)) { -it }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    // Logo - Heart Shape
                    HeartLogo()
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = stringResource(R.string.app_title),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp
                        ),
                        color = Color(0xFF2D2D2D)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF757575),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                }
            }
            
            // Login Options
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 300)) + 
                        slideInVertically(animationSpec = tween(800, delayMillis = 300)) { it }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Google Login Button
                    OutlinedCustomButton(
                        text = stringResource(R.string.continue_with_google),
                        onClick = onGoogleLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        borderColor = Color(0xFFE8E8E8),
                        textColor = Color(0xFF2D2D2D),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEA4335)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Phone Login Button
                    GradientButton(
                        text = stringResource(R.string.login_with_phone),
                        onClick = onPhoneLoginClick,
                        modifier = Modifier.fillMaxWidth(),
                        gradient = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFFB8D6),
                                Color(0xFFFFD6E8)
                            )
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Sign up text
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.no_account_signup) + " ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575)
                        )
                        Text(
                            text = stringResource(R.string.sign_up_now),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFFFF9ECE),
                            modifier = Modifier.clickable { onSignUpClick() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun HeartLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "heartBeat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartBeatScale"
    )
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(0f)
        ) {
            val width = size.width
            val height = size.height
            
            val path = Path().apply {
                val heartCenterX = width / 2
                val heartCenterY = height / 3
                
                moveTo(heartCenterX, heartCenterY + height / 3)
                
                cubicTo(
                    heartCenterX, heartCenterY + height / 6,
                    heartCenterX - width / 4, heartCenterY - height / 6,
                    heartCenterX - width / 3, heartCenterY - height / 6
                )
                
                cubicTo(
                    heartCenterX - width / 2, heartCenterY - height / 6,
                    heartCenterX - width / 2, heartCenterY + height / 8,
                    heartCenterX - width / 2, heartCenterY + height / 8
                )
                
                cubicTo(
                    heartCenterX - width / 2, heartCenterY + height / 4,
                    heartCenterX - width / 4, heartCenterY + height / 2.5f,
                    heartCenterX, heartCenterY + height / 2
                )
                
                cubicTo(
                    heartCenterX + width / 4, heartCenterY + height / 2.5f,
                    heartCenterX + width / 2, heartCenterY + height / 4,
                    heartCenterX + width / 2, heartCenterY + height / 8
                )
                
                cubicTo(
                    heartCenterX + width / 2, heartCenterY + height / 8,
                    heartCenterX + width / 2, heartCenterY - height / 6,
                    heartCenterX + width / 3, heartCenterY - height / 6
                )
                
                cubicTo(
                    heartCenterX + width / 4, heartCenterY - height / 6,
                    heartCenterX, heartCenterY + height / 6,
                    heartCenterX, heartCenterY + height / 3
                )
                
                close()
            }
            
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF9ECE),
                        Color(0xFFFFB8D6),
                        Color(0xFFFFD6E8)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                )
            )
        }
    }
}

@Composable
fun FloatingShapes() {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    
    // Shape 1
    val offsetY1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY1"
    )
    
    // Shape 2
    val offsetY2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY2"
    )
    
    // Shape 3
    val offsetY3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY3"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Top left circle
        Box(
            modifier = Modifier
                .offset(x = 40.dp, y = 100.dp + offsetY1.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD6E8).copy(alpha = 0.4f),
                            Color(0xFFFFD6E8).copy(alpha = 0.1f)
                        )
                    )
                )
        )
        
        // Top right circle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-30).dp, y = 150.dp + offsetY2.dp)
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD6EFFF).copy(alpha = 0.4f),
                            Color(0xFFD6EFFF).copy(alpha = 0.1f)
                        )
                    )
                )
        )
        
        // Bottom left rounded square
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 30.dp, y = (-200).dp + offsetY3.dp)
                .size(70.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD6FFE8).copy(alpha = 0.3f),
                            Color(0xFFD6FFE8).copy(alpha = 0.1f)
                        )
                    )
                )
        )
        
        // Bottom right circle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-50).dp, y = (-150).dp + offsetY1.dp)
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEDD6FF).copy(alpha = 0.3f),
                            Color(0xFFEDD6FF).copy(alpha = 0.1f)
                        )
                    )
                )
        )
    }
}
