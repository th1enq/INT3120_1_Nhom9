package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.coupleapp.data.model.LocationType
import com.example.coupleapp.data.model.SharedPlace
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.Base64ImageDecoder
import com.example.coupleapp.util.LocationUtils
import java.time.format.DateTimeFormatter

/**
 * Grid view of shared places where both users visited together
 */
@Composable
fun SharedPlacesGrid(
    places: List<SharedPlace>,
    onPlaceClick: (SharedPlace) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = places,
            key = { _, place -> place.id }
        ) { index, place ->
            SharedPlaceCard(
                place = place,
                onClick = { onPlaceClick(place) },
                animationDelay = index * 80
            )
        }
    }
}

/**
 * Horizontal scrollable list of shared places for bottom sheet
 */
@Composable
fun SharedPlacesHorizontalList(
    places: List<SharedPlace>,
    onPlaceClick: (SharedPlace) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💕",
                    fontSize = 18.sp
                )
                Text(
                    text = "Places Together",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            
            TextButton(onClick = onSeeAllClick) {
                Text(
                    text = "See all →",
                    fontSize = 13.sp,
                    color = SoftPink
                )
            }
        }
        
        // Horizontal list
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = places.take(5),
                key = { _, place -> place.id }
            ) { index, place ->
                SharedPlaceCardCompact(
                    place = place,
                    onClick = { onPlaceClick(place) },
                    animationDelay = index * 100
                )
            }
        }
    }
}

@Composable
private fun SharedPlaceCard(
    place: SharedPlace,
    onClick: () -> Unit,
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
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.8f)
    ) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .aspectRatio(0.85f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() },
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Place image/icon area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Show representative photo if available
                    val context = LocalContext.current
                    if (Base64ImageDecoder.isValidImageUrl(place.representativePhotoUrl)) {
                        AsyncImage(
                            model = place.representativePhotoUrl,
                            contentDescription = place.placeName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            imageLoader = Coil.imageLoader(context)
                        )
                    } else {
                        // Fallback to gradient with emoji
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            LocationUtils.getLocationTypeColor(place.locationType).copy(alpha = 0.3f),
                                            LocationUtils.getLocationTypeColor(place.locationType).copy(alpha = 0.1f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Representative emoji/icon
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = LocationUtils.getLocationTypeEmoji(place.locationType),
                                    fontSize = 40.sp
                                )
                            }
                        }
                    }
                    
                    // Photos count badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = SoftPink,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${place.photosCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                    
                    // Heart decoration
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(text = "💕", fontSize = 16.sp)
                    }
                }
                
                // Place info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = place.placeName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = place.visitDate.format(DateTimeFormatter.ofPattern("dd MMM")),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedPlaceCardCompact(
    place: SharedPlace,
    onClick: () -> Unit,
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
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 2 }
    ) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .width(140.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    LocationUtils.getLocationTypeColor(place.locationType).copy(alpha = 0.4f),
                                    LocationUtils.getLocationTypeColor(place.locationType).copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocationUtils.getLocationTypeEmoji(place.locationType),
                        fontSize = 32.sp
                    )
                    
                    // Photos badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "${place.photosCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftPink,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                // Info
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(
                        text = place.placeName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = place.visitDate.format(DateTimeFormatter.ofPattern("dd MMM")),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet for shared place with photos preview
 * Shows when clicking on a shared place marker on the map
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlaceBottomSheet(
    place: SharedPlace,
    onDismiss: () -> Unit,
    onViewAllPhotos: (SharedPlace) -> Unit,
    onAddPhoto: (SharedPlace) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        dragHandle = {
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
                .padding(bottom = 24.dp)
        ) {
            // Place header
            SharedPlaceHeader(place = place)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Photos preview section
            Text(
                text = "📸 Memories Together",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Photo preview grid (horizontal scroll)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mock photos preview
                items(minOf(place.photosCount, 5)) { index ->
                    PhotoPreviewItem(
                        index = index,
                        onClick = { onViewAllPhotos(place) }
                    )
                }
                
                // Add photo button
                item {
                    AddPhotoButton(onClick = { onAddPhoto(place) })
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // View all photos
                Button(
                    onClick = { onViewAllPhotos(place) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SoftPink
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View All (${place.photosCount})",
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Add photo
                OutlinedButton(
                    onClick = { onAddPhoto(place) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SoftPink
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(SoftPink, SoftPink))
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Photo",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedPlaceHeader(place: SharedPlace) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Place icon or representative photo
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            if (Base64ImageDecoder.isValidImageUrl(place.representativePhotoUrl)) {
                AsyncImage(
                    model = place.representativePhotoUrl,
                    contentDescription = place.placeName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    imageLoader = Coil.imageLoader(context)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    LocationUtils.getLocationTypeColor(place.locationType).copy(alpha = 0.4f),
                                    LocationUtils.getLocationTypeColor(place.locationType).copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocationUtils.getLocationTypeEmoji(place.locationType),
                        fontSize = 26.sp
                    )
                }
            }
        }
        
        // Place info
        Column(modifier = Modifier.weight(1f)) {
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
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = "💕", fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = place.address,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = place.visitDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${place.durationMinutes / 60}h ${place.durationMinutes % 60}m",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoPreviewItem(
    index: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "photoScale"
    )
    
    Surface(
        modifier = Modifier
            .scale(scale)
            .size(100.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PastelPink.copy(alpha = 0.5f),
                            PastelBlue.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = SoftPink.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Photo ${index + 1}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AddPhotoButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "addScale"
    )
    
    Surface(
        modifier = Modifier
            .scale(scale)
            .size(100.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = PastelPink.copy(alpha = 0.3f),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(listOf(SoftPink.copy(alpha = 0.5f), SoftPink.copy(alpha = 0.5f)))
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Photo",
                    tint = SoftPink,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Add",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SoftPink
                )
            }
        }
    }
}
