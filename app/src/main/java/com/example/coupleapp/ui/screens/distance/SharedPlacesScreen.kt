package com.example.coupleapp.ui.screens.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.data.model.SharedPlace
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.LocationUtils
import com.example.coupleapp.viewmodel.DistanceViewModel
import java.time.format.DateTimeFormatter

/**
 * Screen showing all shared places where both users visited together
 * with modern Vertical Timeline UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlacesScreen(
    onBackClick: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onNavigateToMapWithPlace: (String) -> Unit = {},
    viewModel: DistanceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
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
                        Color(0xFFFFFAF0)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Custom Top Bar
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it }
            ) {
                SharedPlacesTopBar(
                    onBackClick = onBackClick,
                    placesCount = uiState.sharedPlaces.size
                )
            }
            
            if (uiState.sharedPlaces.isEmpty()) {
                // Empty state
                EmptySharedPlacesState(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                // Stats header
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 4 }
                ) {
                    SharedPlacesStatsCard(
                        placesCount = uiState.sharedPlaces.size,
                        photosCount = uiState.sharedPlaces.sumOf { it.photosCount }
                    )
                }
                
                // Vertical Timeline
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(600, delayMillis = 200)),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    VerticalTimelineList(
                        places = uiState.sharedPlaces.sortedByDescending { it.visitDate },
                        onPlaceClick = onPlaceClick,
                        onNavigateToMap = onNavigateToMapWithPlace
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedPlacesTopBar(
    onBackClick: () -> Unit,
    placesCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Title with heart animation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "heartPulse")
                val heartScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "heartScale"
                )
                
                Text(
                    text = "💕",
                    fontSize = 24.sp,
                    modifier = Modifier.scale(heartScale)
                )
                
                Column {
                    Text(
                        text = "Our Journey",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$placesCount places together",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedPlacesStatsCard(
    placesCount: Int,
    photosCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineStatItem(
                value = placesCount.toString(),
                label = "Places",
                emoji = "📍",
                color = SoftPink
            )
            
            // Decorative divider with heart
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0xFFFFE4EC))
                )
                Text(text = "💝", fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0xFFFFE4EC))
                )
            }
            
            TimelineStatItem(
                value = photosCount.toString(),
                label = "Photos",
                emoji = "📸",
                color = SoftLavender
            )
            
            // Decorative divider with heart
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0xFFFFE4EC))
                )
                Text(text = "💝", fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0xFFFFE4EC))
                )
            }
            
            TimelineStatItem(
                value = "∞",
                label = "Memories",
                emoji = "💕",
                color = PastelPink
            )
        }
    }
}

@Composable
private fun TimelineStatItem(
    value: String,
    label: String,
    emoji: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun VerticalTimelineList(
    places: List<SharedPlace>,
    onPlaceClick: (String) -> Unit,
    onNavigateToMap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 100.dp
        )
    ) {
        itemsIndexed(
            items = places,
            key = { _, place -> place.id }
        ) { index, place ->
            TimelineItem(
                place = place,
                isFirst = index == 0,
                isLast = index == places.lastIndex,
                onPlaceClick = { onPlaceClick(place.id) },
                onNavigateToMap = { onNavigateToMap(place.id) },
                animationDelay = index * 100
            )
        }
    }
}

@Composable
private fun TimelineItem(
    place: SharedPlace,
    isFirst: Boolean,
    isLast: Boolean,
    onPlaceClick: () -> Unit,
    onNavigateToMap: () -> Unit,
    animationDelay: Int
) {
    var visible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        visible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { -it / 3 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onPlaceClick() }
        ) {
            // Timeline connector
            TimelineConnector(
                isFirst = isFirst,
                isLast = isLast,
                locationType = place.locationType
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Place card
            TimelinePlaceCard(
                place = place,
                onNavigateToMap = onNavigateToMap,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimelineConnector(
    isFirst: Boolean,
    isLast: Boolean,
    locationType: com.example.coupleapp.data.model.LocationType
) {
    val lineColor = SoftPink.copy(alpha = 0.4f)
    val dotColor = LocationUtils.getLocationTypeColor(locationType)
    
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(IntrinsicSize.Max),
        contentAlignment = Alignment.TopCenter
    ) {
        // Vertical line
        Canvas(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
        ) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            
            // Top line (if not first)
            if (!isFirst) {
                drawLine(
                    color = lineColor,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, 40.dp.toPx()),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect
                )
            }
            
            // Bottom line (if not last)
            if (!isLast) {
                drawLine(
                    color = lineColor,
                    start = Offset(size.width / 2, 40.dp.toPx()),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect
                )
            }
        }
        
        // Timeline dot with icon
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(40.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                dotColor.copy(alpha = 0.3f),
                                dotColor.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = LocationUtils.getLocationTypeEmoji(locationType),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TimelinePlaceCard(
    place: SharedPlace,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isMapPressed by interactionSource.collectIsPressedAsState()
    
    val mapButtonScale by animateFloatAsState(
        targetValue = if (isMapPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mapButtonScale"
    )
    
    Surface(
        modifier = modifier.padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Date badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PastelPink.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = SoftPink,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = place.visitDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SoftPink
                        )
                    }
                }
                
                // Navigate to map button
                Surface(
                    modifier = Modifier
                        .scale(mapButtonScale)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onNavigateToMap() },
                    shape = CircleShape,
                    color = Color(0xFFE8F8F0)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "View on Map",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Place name with heart
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = place.placeName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "💕", fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Address
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = place.address,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PastelBlue.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF5B9BD5),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "${place.durationMinutes / 60}h ${place.durationMinutes % 60}m",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                // Photos count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PastelPurple.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = SoftLavender,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "${place.photosCount} photos",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            // View photos hint
            if (place.photosCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF0F5)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "📸", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap to view memories",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SoftPink
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = SoftPink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySharedPlacesState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Animated heart with map
            val infiniteTransition = rememberInfiniteTransition(label = "emptyHeart")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "heartScale"
            )
            
            val rotation by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation"
            )
            
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                PastelPink.copy(alpha = 0.5f),
                                PastelPink.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗺️💕",
                    fontSize = 48.sp,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = rotation
                        }
                )
            }
            
            Text(
                text = "No Places Together Yet",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Text(
                text = "When you both visit a place together\nfor 15+ minutes, it will appear here!",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PastelPink
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🌸", fontSize = 18.sp)
                    Text(
                        text = "Go explore together!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SoftPink
                    )
                }
            }
        }
    }
}
