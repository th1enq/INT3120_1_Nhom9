package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.coupleapp.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.LocationHistory
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.LocationUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Beautiful Vertical Timeline UI for location history
 * Design:
 * - Date on the LEFT column with vertical dotted line separator
 * - Location cards on the RIGHT
 * - Days separated by dotted vertical lines
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
            EmptyHistoryState()
        } else {
            // Beautiful vertical timeline
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                var isFirstDate = true
                
                groupedHistory.forEach { (date, locations) ->
                    // Date section with locations
                    item(key = "section_${date}") {
                        DateSection(
                            date = date,
                            locations = locations,
                            showTopDottedLine = !isFirstDate
                        )
                        isFirstDate = false
                    }
                }
            }
        }
    }
}

/**
 * Empty state when no location history
 */
@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated icon
            val infiniteTransition = rememberInfiniteTransition(label = "emptyIcon")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float"
            )
            
            Icon(
                imageVector = Icons.Outlined.LocationOff,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = offsetY.dp)
            )
            
            Text(
                text = stringResource(R.string.no_location_history),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
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
}

/**
 * A date section containing date label on left and location cards on right
 */
@Composable
private fun DateSection(
    date: LocalDate,
    locations: List<LocationHistory>,
    showTopDottedLine: Boolean
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val dateLabel = when (date) {
        today -> "Hôm nay"
        yesterday -> "Hôm qua"
        else -> {
            val daysBetween = ChronoUnit.DAYS.between(date, today)
            if (daysBetween <= 7) {
                "${daysBetween.toInt()} ngày trước"
            } else {
                date.format(DateTimeFormatter.ofPattern("dd/MM"))
            }
        }
    }
    
    val dayOfWeek = date.format(DateTimeFormatter.ofPattern("EEE", java.util.Locale("vi")))
    
    Column {
        // Dotted line separator between dates
        if (showTopDottedLine) {
            DottedLineSeparator()
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT: Date column
            Column(
                modifier = Modifier.width(70.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Date badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (date == today) SoftPink else PastelPink.copy(alpha = 0.7f),
                    shadowElevation = if (date == today) 4.dp else 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayOfWeek.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (date == today) Color.White else SoftPink,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (date == today) Color.White else TextPrimary
                        )
                        Text(
                            text = dateLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (date == today) Color.White.copy(alpha = 0.9f) else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
                
                // Vertical timeline line with dots
                if (locations.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VerticalTimelineLine(itemCount = locations.size)
                }
            }
            
            // RIGHT: Location cards column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                locations.forEachIndexed { index, location ->
                    LocationCard(
                        location = location,
                        animationDelay = index * 80
                    )
                }
            }
        }
    }
}

/**
 * Dotted line separator between date sections
 */
@Composable
private fun DottedLineSeparator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left spacing to align with date column center
        Spacer(modifier = Modifier.width(35.dp))
        
        // Dotted vertical line effect (horizontal for separator)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
        ) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            drawLine(
                color = Color(0xFFE0E0E0),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f,
                pathEffect = pathEffect,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Vertical timeline line with dots for multiple locations in a day
 */
@Composable
private fun VerticalTimelineLine(itemCount: Int) {
    val lineHeight = (itemCount - 1) * 100 // Approximate height per card
    
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(lineHeight.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Dotted vertical line
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            drawLine(
                color = SoftPink.copy(alpha = 0.4f),
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 2f,
                pathEffect = pathEffect,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Beautiful location card with time, place name, address and duration
 */
@Composable
private fun LocationCard(
    location: LocationHistory,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Top row: Location type icon + Time range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location type chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LocationUtils.getLocationTypeColor(location.locationType).copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = LocationUtils.getLocationTypeIcon(location.locationType),
                                contentDescription = null,
                                tint = LocationUtils.getLocationTypeColor(location.locationType),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = getLocationTypeName(location.locationType),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = LocationUtils.getLocationTypeColor(location.locationType)
                            )
                        }
                    }
                    
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
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Location name
                Text(
                    text = location.locationName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Address
                if (location.address.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = location.address,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Bottom row: Duration + Current status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Duration badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PastelPink.copy(alpha = 0.6f)
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SoftPink
                            )
                        }
                    }
                    
                    // Current location indicator
                    if (location.departureTime == null) {
                        CurrentLocationIndicator()
                    }
                }
            }
        }
    }
}

/**
 * Pulsing indicator for current location
 */
@Composable
private fun CurrentLocationIndicator() {
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
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = alpha))
        )
        Text(
            text = stringResource(R.string.currently_here),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4CAF50)
        )
    }
}

/**
 * Format time range (arrival → departure)
 */
private fun formatTimeRange(location: LocationHistory): String {
    val arrivalTime = location.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (location.departureTime != null) {
        val departureTime = location.departureTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        "$arrivalTime → $departureTime"
    } else {
        "$arrivalTime → hiện tại"
    }
}

/**
 * Format duration in human readable format
 */
private fun formatDuration(minutes: Int): String {
    return when {
        minutes < 1 -> "< 1 phút"
        minutes < 60 -> "$minutes phút"
        minutes < 120 -> "1 giờ ${minutes - 60} phút"
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "$hours giờ $mins phút" else "$hours giờ"
        }
    }
}

/**
 * Get Vietnamese name for location type
 */
private fun getLocationTypeName(type: com.example.coupleapp.data.model.LocationType): String {
    return when (type) {
        com.example.coupleapp.data.model.LocationType.HOME -> "Nhà"
        com.example.coupleapp.data.model.LocationType.WORK -> "Công ty"
        com.example.coupleapp.data.model.LocationType.RESTAURANT -> "Nhà hàng"
        com.example.coupleapp.data.model.LocationType.CAFE -> "Quán cafe"
        com.example.coupleapp.data.model.LocationType.SHOPPING -> "Mua sắm"
        com.example.coupleapp.data.model.LocationType.ENTERTAINMENT -> "Giải trí"
        com.example.coupleapp.data.model.LocationType.PARK -> "Công viên"
        com.example.coupleapp.data.model.LocationType.GYM -> "Phòng gym"
        com.example.coupleapp.data.model.LocationType.SCHOOL -> "Trường học"
        com.example.coupleapp.data.model.LocationType.HOSPITAL -> "Bệnh viện"
        com.example.coupleapp.data.model.LocationType.OTHER -> "Khác"
    }
}
