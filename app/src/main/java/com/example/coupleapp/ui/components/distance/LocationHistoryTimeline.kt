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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.coupleapp.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.LocationHistory
import com.example.coupleapp.data.model.LocationType
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.LocationUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Vertical Timeline UI for location history
 * Displays locations visited in the last 1-2 days with detailed date/time info
 */
@Composable
fun LocationHistoryTimeline(
    locationHistory: List<LocationHistory>,
    userName: String,
    modifier: Modifier = Modifier
) {
    // Group locations by date for better organization
    val groupedHistory = remember(locationHistory) {
        locationHistory.groupBy { it.arrivalTime.toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    
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
                text = stringResource(R.string.location_history),
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
                        text = stringResource(R.string.no_location_history),
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.location_appear_message),
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Timeline with date sections
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                groupedHistory.forEach { (date, locations) ->
                    // Date header
                    item(key = "date_${date}") {
                        DateHeader(date = date)
                    }
                    
                    // Location items for this date
                    itemsIndexed(
                        items = locations,
                        key = { index, item -> 
                            if (item.id.isNotEmpty()) item.id else "history_${date}_$index"
                        }
                    ) { index, location ->
                        TimelineItemWithDate(
                            location = location,
                            isFirst = index == 0,
                            isLast = index == locations.lastIndex,
                            animationDelay = index * 100,
                            showDateOnLeft = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Date section header
 */
@Composable
private fun DateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val dateText = when (date) {
        today -> "Hôm nay"
        yesterday -> "Hôm qua"
        else -> {
            val daysBetween = ChronoUnit.DAYS.between(date, today)
            if (daysBetween <= 7) {
                "${daysBetween.toInt()} ngày trước"
            } else {
                date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        }
    }
    
    val fullDate = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", java.util.Locale("vi")))
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Decorative line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SoftPink.copy(alpha = 0.3f)
                        )
                    )
                )
        )
        
        // Date badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PastelPink.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftPink
                )
                Text(
                    text = fullDate,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
        
        // Decorative line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            SoftPink.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Timeline item with date/time on the left side
 */
@Composable
private fun TimelineItemWithDate(
    location: LocationHistory,
    isFirst: Boolean,
    isLast: Boolean,
    animationDelay: Int,
    showDateOnLeft: Boolean
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // LEFT SIDE: Date and Time info
            Column(
                modifier = Modifier.width(60.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // Time
                Text(
                    text = location.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftPink
                )
                
                // Duration or "Đang ở đây"
                if (location.departureTime == null) {
                    Text(
                        text = "Đang ở",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Text(
                        text = "→ ${location.departureTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
            
            // MIDDLE: Timeline line and dot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(32.dp)
            ) {
                // Top line
                if (!isFirst) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(12.dp)
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
                    Spacer(modifier = Modifier.height(12.dp))
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
                            .height(36.dp)
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
            
            // RIGHT SIDE: Location info card
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
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Address
                    Text(
                        text = location.address,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Duration badge and current indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Duration badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PastelPink.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = SoftPink,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = formatDuration(location.durationMinutes),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SoftPink
                                )
                            }
                        }
                        
                        // Current location indicator
                        if (location.departureTime == null) {
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
                                    text = stringResource(R.string.currently_here),
                                    fontSize = 10.sp,
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
}

private fun formatDuration(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 120 -> "1h ${minutes - 60}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
