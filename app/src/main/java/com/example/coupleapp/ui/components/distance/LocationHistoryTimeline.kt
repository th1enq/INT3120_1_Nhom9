package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.LocationHistory
import com.example.coupleapp.data.model.LocationType
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.LocationUtils
import java.time.format.DateTimeFormatter

/**
 * Vertical Timeline UI for location history
 * Displays locations visited in the last 1-2 days
 */
@Composable
fun LocationHistoryTimeline(
    locationHistory: List<LocationHistory>,
    userName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = SoftPink,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Location History",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        
        if (locationHistory.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOff,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "No location history yet",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Locations will appear after staying\n5+ minutes at a place",
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Timeline items
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = locationHistory,
                    key = { index, item -> 
                        // Use ID if not empty, otherwise use index to avoid duplicate key error
                        if (item.id.isNotEmpty()) item.id else "history_$index"
                    }
                ) { index, location ->
                    TimelineItem(
                        location = location,
                        isFirst = index == 0,
                        isLast = index == locationHistory.lastIndex,
                        animationDelay = index * 100
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    location: LocationHistory,
    isFirst: Boolean,
    isLast: Boolean,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { -it / 4 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Timeline line and dot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(32.dp)
            ) {
                // Top line
                if (!isFirst) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        SoftPink.copy(alpha = 0.3f),
                                        SoftPink
                                    )
                                )
                            )
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Location type icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(LocationUtils.getLocationTypeColor(location.locationType))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LocationUtils.getLocationTypeIcon(location.locationType),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Bottom line
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        SoftPink,
                                        SoftPink.copy(alpha = 0.3f)
                                    )
                                )
                            )
                    )
                }
            }
            
            // Location info card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = if (!isLast) 8.dp else 0.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Location name
                    Text(
                        text = location.locationName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Address
                    Text(
                        text = location.address,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Time info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time range
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
                                text = formatTimeRange(location),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        
                        // Duration badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PastelPink.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = formatDuration(location.durationMinutes),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = SoftPink,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    // Current location indicator
                    if (location.departureTime == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Pulsing dot
                            val infiniteTransition = rememberInfiniteTransition(label = "currentPulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 0.4f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = alpha))
                            )
                            Text(
                                text = "Currently here",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeRange(location: LocationHistory): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val arrival = location.arrivalTime.format(formatter)
    val departure = location.departureTime?.format(formatter) ?: "now"
    return "$arrival - $departure"
}

private fun formatDuration(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 120 -> "1h ${minutes - 60}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
