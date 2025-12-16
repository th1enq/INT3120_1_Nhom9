package com.example.coupleapp.ui.components.calendar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.CalendarUserProfile
import com.example.coupleapp.ui.theme.*

@Composable
fun CoupleProfileSection(
    user1: CalendarUserProfile,
    user2: CalendarUserProfile,
    showHeartbeat: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatars Row with Heartbeat in center
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User 1 Avatar and Info
            UserAvatarCard(user = user1)
            
            // Heartbeat Animation
            if (showHeartbeat) {
                HeartbeatAnimation(
                    modifier = Modifier.size(60.dp)
                )
            } else {
                Box(modifier = Modifier.size(60.dp)) {
                    StaticHeart()
                }
            }
            
            // User 2 Avatar and Info
            UserAvatarCard(user = user2)
        }
    }
}

@Composable
private fun UserAvatarCard(
    user: CalendarUserProfile
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(110.dp)
    ) {
        // Avatar with gradient border
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFFFB8D6),
                            Color(0xFFFFD6E8)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE8F5).copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            // TODO: Load actual avatar image
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = Color(0xFFFF6B9D)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Nickname
        Text(
            text = user.nickname,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(
                    Color(0xFFFF6B9D).copy(alpha = 0.85f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Age and Zodiac
        Text(
            text = "${user.age} tuổi",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        
        Text(
            text = "${user.zodiacSign.symbol} ${user.zodiacSign.displayName}",
            fontSize = 11.sp,
            color = TextLight
        )
    }
}

@Composable
private fun HeartbeatAnimation(
    modifier: Modifier = Modifier
) {
    // Heartbeat scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                1f at 0
                1.3f at 100 using FastOutSlowInEasing
                1.1f at 200
                1.25f at 300 using FastOutSlowInEasing
                1f at 400
                1f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "heart_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.8f at 0
                1f at 100
                0.9f at 200
                1f at 300
                0.8f at 400
                0.8f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "heart_alpha"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale * 1.2f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF6B9D).copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2
            )
        }
        
        // Heart
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
        ) {
            drawHeart(color = Color(0xFFFF6B9D).copy(alpha = alpha))
        }
    }
}

@Composable
private fun StaticHeart() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawHeart(color = Color(0xFFFF6B9D))
    }
}

private fun DrawScope.drawHeart(color: Color) {
    val path = Path().apply {
        val width = size.width
        val height = size.height
        
        // Heart shape path
        moveTo(width / 2, height * 0.35f)
        
        // Left curve
        cubicTo(
            width * 0.2f, height * 0.1f,
            -width * 0.25f, height * 0.6f,
            width / 2, height
        )
        
        // Right curve
        moveTo(width / 2, height * 0.35f)
        cubicTo(
            width * 0.8f, height * 0.1f,
            width * 1.25f, height * 0.6f,
            width / 2, height
        )
    }
    
    drawPath(
        path = path,
        color = color
    )
}
