package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.LocationHistory
import com.example.coupleapp.data.model.UserLocation
import com.example.coupleapp.ui.theme.*
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet showing user details and location history
 * Improved with smoother animation and limited to half screen height
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoBottomSheet(
    user: UserLocation?,
    locationHistory: List<LocationHistory>,
    isMe: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (user == null) return
    
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.5f // Limit to half screen
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        dragHandle = {
            // Custom drag handle
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E0E0))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // User profile header
            UserProfileHeader(
                user = user,
                isMe = isMe
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Current location card
            CurrentLocationCard(user = user)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = Color(0xFFF5F5F5),
                thickness = 1.dp
            )
            
            // Location history timeline (compact)
            LocationHistoryTimeline(
                locationHistory = locationHistory.take(3), // Show only last 3
                userName = user.userName
            )
        }
    }
}

@Composable
private fun UserProfileHeader(
    user: UserLocation,
    isMe: Boolean
) {
    val borderColor = if (isMe) SoftPink else Color(0xFF98E4C8)
    val backgroundColor = if (isMe) PastelPink else PastelGreen
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(3.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isMe) "🌸" else "🍋",
                fontSize = 28.sp
            )
        }
        
        // User info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = user.userName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                // Online status
                if (user.isOnline) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "Online",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Battery and last update
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = when {
                            user.batteryLevel > 80 -> Icons.Default.BatteryFull
                            user.batteryLevel > 50 -> Icons.Default.Battery5Bar
                            user.batteryLevel > 20 -> Icons.Default.Battery3Bar
                            else -> Icons.Default.Battery1Bar
                        },
                        contentDescription = null,
                        tint = when {
                            user.batteryLevel > 50 -> Color(0xFF4CAF50)
                            user.batteryLevel > 20 -> Color(0xFFFFC107)
                            else -> Color(0xFFFF5252)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${user.batteryLevel}%",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                // Last update
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatLastUpdate(user.lastUpdated),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(user: UserLocation) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = BackgroundCard,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Location icon with pulse
            Box(
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "locationPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                
                // Pulse background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SoftPink.copy(alpha = pulseAlpha))
                )
                
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SoftPink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            // Location details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Location",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = user.address,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Quick actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip(
                        icon = Icons.Outlined.Navigation,
                        label = "Directions",
                        onClick = { /* TODO: Open maps */ }
                    )
                    QuickActionChip(
                        icon = Icons.Outlined.Share,
                        label = "Share",
                        onClick = { /* TODO: Share location */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SoftPink,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

private fun formatLastUpdate(dateTime: java.time.LocalDateTime): String {
    val now = java.time.LocalDateTime.now()
    val minutes = java.time.Duration.between(dateTime, now).toMinutes()
    
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM HH:mm"))
    }
}
