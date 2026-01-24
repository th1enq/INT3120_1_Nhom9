package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
 * - Click on location card to navigate to that location on map
 */
@Composable
fun LocationHistoryTimeline(
    locationHistory: List<LocationHistory>,
    userName: String,
    onLocationClick: (LocationHistory) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Group locations by date and merge consecutive same-location entries
    // Only show last 3 days of history
    val groupedHistory = remember(locationHistory) {
        val today = LocalDate.now()
        val threeDaysAgo = today.minusDays(3)
        
        // First, filter to only last 3 days and fix time issues
        val processedHistory = locationHistory
            .filter { it.arrivalTime.toLocalDate() >= threeDaysAgo }
            .sortedByDescending { it.arrivalTime }
            .mapIndexed { index, entry ->
                var fixedEntry = entry
                
                // Fix: If departureTime is before arrivalTime (bug), swap them
                if (entry.departureTime != null && entry.departureTime.isBefore(entry.arrivalTime)) {
                    fixedEntry = entry.copy(
                        arrivalTime = entry.departureTime,
                        departureTime = entry.arrivalTime,
                        durationMinutes = java.time.Duration.between(entry.departureTime, entry.arrivalTime).toMinutes().toInt().coerceAtLeast(0)
                    )
                }
                
                if (index == 0) {
                    // Most recent entry - can keep departureTime as is
                    fixedEntry
                } else if (fixedEntry.departureTime == null) {
                    // Not the most recent, but has no departure time - fix it
                    val entryDate = fixedEntry.arrivalTime.toLocalDate()
                    val endOfDay = entryDate.atTime(23, 59, 59)
                    fixedEntry.copy(
                        departureTime = endOfDay,
                        durationMinutes = java.time.Duration.between(fixedEntry.arrivalTime, endOfDay).toMinutes().toInt().coerceAtLeast(0)
                    )
                } else {
                    fixedEntry
                }
            }
        
        processedHistory.groupBy { it.arrivalTime.toLocalDate() }
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
                            showTopDottedLine = !isFirstDate,
                            onLocationClick = onLocationClick
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
 * Merges consecutive entries at the same location within the same day
 */
@Composable
private fun DateSection(
    date: LocalDate,
    locations: List<LocationHistory>,
    showTopDottedLine: Boolean,
    onLocationClick: (LocationHistory) -> Unit = {}
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    // Merge consecutive entries at the same location (within ~300m)
    val mergedLocations = remember(locations) {
        mergeConsecutiveLocations(locations)
    }
    
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
                if (mergedLocations.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VerticalTimelineLine(itemCount = mergedLocations.size)
                }
            }
            
            // RIGHT: Location cards column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mergedLocations.forEachIndexed { index, location ->
                    LocationCard(
                        location = location,
                        animationDelay = index * 80,
                        onClick = { onLocationClick(location) }
                    )
                }
            }
        }
    }
}

/**
 * Merge consecutive locations that are at the same place (within ~300m)
 * This prevents showing multiple cards for the same location
 */
private fun mergeConsecutiveLocations(locations: List<LocationHistory>): List<LocationHistory> {
    if (locations.isEmpty()) return emptyList()
    
    val sorted = locations.sortedByDescending { it.arrivalTime }
    val result = mutableListOf<LocationHistory>()
    
    for (location in sorted) {
        if (result.isEmpty()) {
            result.add(location)
            continue
        }
        
        val lastEntry = result.last()
        val distance = calculateDistanceForMerge(location.coordinate, lastEntry.coordinate)
        
        // Consider same location if within 300 meters
        if (distance <= 300.0) {
            // Merge: extend the time range
            val mergedEntry = lastEntry.copy(
                arrivalTime = location.arrivalTime, // Use earlier arrival
                departureTime = lastEntry.departureTime ?: location.departureTime,
                durationMinutes = calculateMergedDuration(location.arrivalTime, lastEntry.departureTime ?: location.departureTime)
            )
            result[result.lastIndex] = mergedEntry
        } else {
            result.add(location)
        }
    }
    
    return result
}

/**
 * Calculate distance between two coordinates for merging check
 */
private fun calculateDistanceForMerge(coord1: com.example.coupleapp.data.model.LocationCoordinate, coord2: com.example.coupleapp.data.model.LocationCoordinate): Double {
    val earthRadius = 6371000.0 // meters
    val lat1 = Math.toRadians(coord1.latitude)
    val lat2 = Math.toRadians(coord2.latitude)
    val deltaLat = Math.toRadians(coord2.latitude - coord1.latitude)
    val deltaLon = Math.toRadians(coord2.longitude - coord1.longitude)
    
    val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(deltaLon / 2) * kotlin.math.sin(deltaLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    
    return earthRadius * c
}

/**
 * Calculate merged duration in minutes
 */
private fun calculateMergedDuration(arrivalTime: java.time.LocalDateTime, departureTime: java.time.LocalDateTime?): Int {
    val endTime = departureTime ?: java.time.LocalDateTime.now()
    return java.time.Duration.between(arrivalTime, endTime).toMinutes().toInt()
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
 * Click to navigate to this location on the map
 */
@Composable
private fun LocationCard(
    location: LocationHistory,
    animationDelay: Int,
    onClick: () -> Unit = {}
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
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
                    
                    // Current location indicator - only show if:
                    // 1. departureTime is null AND
                    // 2. arrival date is today
                    val isCurrentlyHere = location.departureTime == null && 
                        location.arrivalTime.toLocalDate() == LocalDate.now()
                    
                    if (isCurrentlyHere) {
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
 * Handles edge cases:
 * - Same day: "HH:mm → HH:mm"
 * - Still here today: "HH:mm → hiện tại"
 * - Past day without proper departure: "HH:mm → 23:59"
 * - Swapped times (departure before arrival): fixes automatically
 */
private fun formatTimeRange(location: LocationHistory): String {
    // Handle potential time swap bug: if departure is before arrival, swap them for display
    val (effectiveArrival, effectiveDeparture) = if (location.departureTime != null && 
        location.departureTime.isBefore(location.arrivalTime)) {
        // Bug: times are swapped, fix for display
        Pair(location.departureTime, location.arrivalTime)
    } else {
        Pair(location.arrivalTime, location.departureTime)
    }
    
    val arrivalTime = effectiveArrival.format(DateTimeFormatter.ofPattern("HH:mm"))
    val today = LocalDate.now()
    val arrivalDate = effectiveArrival.toLocalDate()
    
    return when {
        // Still at this location (only valid if it's today and departureTime is null)
        effectiveDeparture == null && arrivalDate == today -> {
            "$arrivalTime → hiện tại"
        }
        // Has departure time
        effectiveDeparture != null -> {
            val departureTime = effectiveDeparture.format(DateTimeFormatter.ofPattern("HH:mm"))
            "$arrivalTime → $departureTime"
        }
        // Past day but no departure time (shouldn't happen after processing, but handle gracefully)
        else -> {
            "$arrivalTime → 23:59"
        }
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
