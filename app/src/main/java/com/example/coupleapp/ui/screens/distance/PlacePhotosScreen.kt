package com.example.coupleapp.ui.screens.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.data.model.SharedPlacePhoto
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.LocationUtils
import com.example.coupleapp.viewmodel.DistanceViewModel
import java.time.format.DateTimeFormatter

/**
 * Screen showing photos from a specific shared place
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePhotosScreen(
    placeId: String,
    onBackClick: () -> Unit,
    viewModel: DistanceViewModel = viewModel()
) {
    val photosState by viewModel.photosState.collectAsState()
    var visible by remember { mutableStateOf(false) }
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(placeId) {
        viewModel.loadPlacePhotos(placeId)
        kotlinx.coroutines.delay(200)
        visible = true
    }
    
    Crossfade(
        targetState = photosState.isLoading,
        animationSpec = tween(500),
        label = "loadingCrossfade"
    ) { isLoading ->
        if (isLoading) {
            LoadingScreen()
        } else {
            Scaffold(
                topBar = {
                    PlacePhotosTopBar(
                        placeName = photosState.place?.placeName ?: "",
                        onBackClick = onBackClick
                    )
                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(600, delayMillis = 400)) +
                                scaleIn(tween(600, delayMillis = 400))
                    ) {
                        FloatingActionButton(
                            onClick = { showAddPhotoDialog = true },
                            containerColor = SoftPink,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.shadow(8.dp, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Photo",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
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
                        .padding(paddingValues)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Place info header
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 4 }
                        ) {
                            PlaceInfoHeader(
                                place = photosState.place,
                                photosCount = photosState.photos.size
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (photosState.photos.isEmpty()) {
                            // Empty state
                            EmptyPhotosState(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                onAddClick = { showAddPhotoDialog = true }
                            )
                        } else {
                            // Photos grid
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(tween(600, delayMillis = 200))
                            ) {
                                PhotosGrid(
                                    photos = photosState.photos,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add photo dialog
    if (showAddPhotoDialog) {
        AddPhotoDialog(
            onDismiss = { showAddPhotoDialog = false },
            onPhotoSelected = { photoUrl ->
                viewModel.addPhotoToPlace(placeId, photoUrl)
                showAddPhotoDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePhotosTopBar(
    placeName: String,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = placeName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = { /* TODO: Share */ },
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun PlaceInfoHeader(
    place: com.example.coupleapp.data.model.SharedPlace?,
    photosCount: Int,
    modifier: Modifier = Modifier
) {
    place ?: return
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Place emoji/icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
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
                    fontSize = 28.sp
                )
            }
            
            // Place details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = place.placeName,
                        fontSize = 16.sp,
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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = place.visitDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    
                    // Photos count
                    Row(
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
                            text = "$photosCount photos",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SoftPink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotosGrid(
    photos: List<SharedPlacePhoto>,
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
            items = photos,
            key = { _, photo -> photo.id }
        ) { index, photo ->
            PhotoCard(
                photo = photo,
                animationDelay = index * 80
            )
        }
    }
}

@Composable
private fun PhotoCard(
    photo: SharedPlacePhoto,
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
        label = "photoScale"
    )
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.8f)
    ) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .aspectRatio(1f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { /* TODO: Open full screen */ },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Photo placeholder
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
                    // Placeholder icon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = SoftPink.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Photo ${photo.id.takeLast(1)}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
                
                // User indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (photo.takenByUserId == "user_me") PastelPink else PastelGreen
                        )
                        .border(
                            1.5.dp,
                            if (photo.takenByUserId == "user_me") SoftPink else Color(0xFF98E4C8),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (photo.takenByUserId == "user_me") "🌸" else "🍋",
                        fontSize = 10.sp
                    )
                }
                
                // Caption overlay
                photo.caption?.let { caption ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f)
                                    )
                                )
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = caption,
                            fontSize = 11.sp,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Time
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = photo.takenAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPhotosState(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "camera")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cameraScale"
        )
        
        Text(
            text = "📸",
            fontSize = 64.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Photos Yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Text(
            text = "Add your favorite memories\nfrom this place!",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SoftPink
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Photos",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AddPhotoDialog(
    onDismiss: () -> Unit,
    onPhotoSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📸", fontSize = 24.sp)
                Text(
                    text = "Add Photo",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose how to add photos to this memory:",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                
                // Camera option
                AddPhotoOption(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Take Photo",
                    subtitle = "Use camera to capture",
                    onClick = {
                        onPhotoSelected("camera_photo_${System.currentTimeMillis()}")
                    }
                )
                
                // Gallery option
                AddPhotoOption(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "From Gallery",
                    subtitle = "Choose from album",
                    onClick = {
                        onPhotoSelected("gallery_photo_${System.currentTimeMillis()}")
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = TextSecondary
                )
            }
        }
    )
}

@Composable
private fun AddPhotoOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = BackgroundCard
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PastelPink),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SoftPink,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


