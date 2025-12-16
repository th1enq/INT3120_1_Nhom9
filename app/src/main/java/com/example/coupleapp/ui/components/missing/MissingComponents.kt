package com.example.coupleapp.ui.components.missing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.MissingSummary
import com.example.coupleapp.data.model.UserMissCount

/**
 * Top bar for Missing screen with streak indicator
 */
@Composable
fun MissingTopBar(
    title: String,
    streakCount: Int,
    isStreakActive: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = Color(0xFF2D2D2D)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Streak indicator (like TikTok)
        StreakIndicator(
            streakCount = streakCount,
            isActive = isStreakActive
        )
    }
}

/**
 * Streak indicator component (fire icon like TikTok)
 */
@Composable
fun StreakIndicator(
    streakCount: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    
    // Flame flicker animation when active
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Fire emoji/icon
        Text(
            text = "🔥",
            fontSize = 18.sp,
            modifier = Modifier
                .scale(if (isActive) flameScale else 1f)
                .alpha(if (isActive) 1f else 0.4f)
        )
        
        // Streak count
        Text(
            text = "$streakCount",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = if (isActive) Color(0xFFFF6D00) else Color(0xFFB0B0B0)
        )
    }
}

/**
 * Today's miss count card for both users
 */
@Composable
fun TodayMissCountCard(
    myCount: UserMissCount,
    partnerCount: UserMissCount,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) +
                slideInVertically(animationSpec = tween(400, delayMillis = 200)) { it / 4 },
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Today's Love 💕",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF2D2D2D)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // My count
                    UserMissCountItem(
                        userName = myCount.userName,
                        count = myCount.todayCount,
                        isCurrentUser = true
                    )
                    
                    // Heart divider
                    Box(
                        modifier = Modifier
                            .height(60.dp)
                            .width(1.dp)
                            .background(Color(0xFFE0E0E0))
                    )
                    
                    // Partner count
                    UserMissCountItem(
                        userName = partnerCount.userName,
                        count = partnerCount.todayCount,
                        isCurrentUser = false
                    )
                }
            }
        }
    }
}

@Composable
private fun UserMissCountItem(
    userName: String,
    count: Int,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isCurrentUser) Color(0xFFFFE8F5) else Color(0xFFE8F5FF)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (isCurrentUser) Color(0xFFFF6B9D) else Color(0xFF6B9DFF),
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Name
        Text(
            text = if (isCurrentUser) "You" else userName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF757575)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Count with heart
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF6B9D),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "x$count",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF2D2D2D)
            )
        }
    }
}

/**
 * Missing button component
 */
@Composable
fun MissingButton(
    onClick: () -> Unit,
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )
    
    Button(
        onClick = onClick,
        enabled = !isAnimating,
        modifier = modifier
            .scale(scale)
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF6B9D),
            disabledContainerColor = Color(0xFFFFB8D6)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White
            )
            Text(
                text = "Miss You",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}
