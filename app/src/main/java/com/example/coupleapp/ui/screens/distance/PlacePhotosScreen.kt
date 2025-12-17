package com.example.coupleapp.ui.screens.distance

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.coupleapp.data.model.SharedPlacePhoto
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.util.Base64ImageDecoder
import com.example.coupleapp.util.LocationUtils
import com.example.coupleapp.viewmodel.DistanceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Screen showing photos from a specific shared place
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PlacePhotosScreen(
    placeId: String,
    onBackClick: () -> Unit,
    viewModel: DistanceViewModel = viewModel()
) {
    val photosState by viewModel.photosState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = uiState.currentUserId
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPhotoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    android.util.Log.d("PlacePhotosScreen", "=== Screen rendered for placeId: $placeId ===")
    android.util.Log.d("PlacePhotosScreen", "Photos count: ${photosState.photos.size}")
    android.util.Log.d("PlacePhotosScreen", "Is loading: ${photosState.isLoading}")
    
    // Camera permission
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.addPhotoToPlace(placeId, it.toString())
            showAddPhotoDialog = false
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            viewModel.addPhotoToPlace(placeId, tempCameraUri.toString())
            showAddPhotoDialog = false
        }
    }
    
    // Function to create temp file for camera
    fun createTempImageUri(): Uri {
        val tempFile = File.createTempFile(
            "place_photo_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        ).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
    
    // Function to launch camera
    fun launchCamera() {
        if (cameraPermissionState.status.isGranted) {
            tempCameraUri = createTempImageUri()
            cameraLauncher.launch(tempCameraUri!!)
        } else {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Function to launch gallery
    fun launchGallery() {
        galleryLauncher.launch("image/*")
    }
    
    LaunchedEffect(placeId) {
        android.util.Log.d("PlacePhotosScreen", "LaunchedEffect - Loading photos for placeId: $placeId")
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
                        onBackClick = if (selectionMode) {
                            {
                                selectionMode = false
                                selectedPhotoIds = emptySet()
                            }
                        } else {
                            onBackClick
                        },
                        selectionMode = selectionMode,
                        selectedCount = selectedPhotoIds.size,
                        onEnterSelectionMode = {
                            selectionMode = true
                        },
                        onDeleteSelected = {
                            if (selectedPhotoIds.isNotEmpty()) {
                                showDeleteConfirmation = true
                            }
                        }
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
                        
                        android.util.Log.d("PlacePhotosScreen", "Photos list size: ${photosState.photos.size}")
                        photosState.photos.forEachIndexed { index, photo ->
                            android.util.Log.d("PlacePhotosScreen", "Photo $index: id=${photo.id}, url=${photo.photoUrl}")
                        }
                        
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
                                    currentUserId = currentUserId,
                                    placeId = placeId,
                                    selectionMode = selectionMode,
                                    selectedPhotoIds = selectedPhotoIds,
                                    onPhotoSelectionChanged = { photoId, isSelected ->
                                        selectedPhotoIds = if (isSelected) {
                                            selectedPhotoIds + photoId
                                        } else {
                                            selectedPhotoIds - photoId
                                        }
                                    },
                                    onDeletePhoto = { photoId ->
                                        viewModel.deletePhotoFromPlace(photoId, placeId)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                    
                    // Loading overlay when adding photo
                    if (photosState.isAddingPhoto) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = SoftPink)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Adding photo...",
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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
            onTakePhoto = {
                showAddPhotoDialog = false
                launchCamera()
            },
            onChooseFromGallery = {
                showAddPhotoDialog = false
                launchGallery()
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Delete ${selectedPhotoIds.size} photo${if (selectedPhotoIds.size > 1) "s" else ""}?",
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This action cannot be undone. These photos will be permanently removed from this place.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMultiplePhotosFromPlace(selectedPhotoIds.toList(), placeId)
                        selectionMode = false
                        selectedPhotoIds = emptySet()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    // Show error toast
    LaunchedEffect(photosState.error) {
        photosState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePhotosTopBar(
    placeName: String,
    onBackClick: () -> Unit,
    selectionMode: Boolean = false,
    selectedCount: Int = 0,
    onEnterSelectionMode: () -> Unit = {},
    onDeleteSelected: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = if (selectionMode) "$selectedCount selected" else placeName,
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
            if (selectionMode && selectedCount > 0) {
                // Delete button when in selection mode
                IconButton(
                    onClick = onDeleteSelected,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFF44336))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (!selectionMode) {
                // Selection mode button
                IconButton(
                    onClick = onEnterSelectionMode,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Select Photos",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    currentUserId: String,
    placeId: String,
    selectionMode: Boolean = false,
    selectedPhotoIds: Set<String> = emptySet(),
    onPhotoSelectionChanged: (String, Boolean) -> Unit = { _, _ -> },
    onDeletePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("PhotosGrid", "Rendering grid with ${photos.size} photos, currentUserId: $currentUserId, selectionMode: $selectionMode")
    
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
            android.util.Log.d("PhotosGrid", "Rendering item $index: ${photo.photoUrl}")
            PhotoCard(
                photo = photo,
                currentUserId = currentUserId,
                animationDelay = index * 80,
                selectionMode = selectionMode,
                isSelected = selectedPhotoIds.contains(photo.id),
                onSelectionChanged = { isSelected ->
                    onPhotoSelectionChanged(photo.id, isSelected)
                },
                onDeleteClick = { onDeletePhoto(photo.id) }
            )
        }
    }
}

@Composable
private fun PhotoCard(
    photo: SharedPlacePhoto,
    currentUserId: String,
    animationDelay: Int,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChanged: (Boolean) -> Unit = {},
    onDeleteClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
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
    
    // Delete confirmation dialog (only in normal mode)
    if (showDeleteDialog && !selectionMode) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFE5E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Delete Photo?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This photo will be permanently removed from this memory. This action cannot be undone.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
    
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
                ) { 
                    if (selectionMode) {
                        onSelectionChanged(!isSelected)
                    }
                    /* TODO: Open full screen in normal mode */ 
                },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Display actual photo if URL exists, otherwise show placeholder
                val isValidUrl = Base64ImageDecoder.isValidImageUrl(photo.photoUrl)
                val context = LocalContext.current
                
                android.util.Log.d("PhotoCard", "=== Photo Debug ===")
                android.util.Log.d("PhotoCard", "Photo ID: ${photo.id}")
                android.util.Log.d("PhotoCard", "Photo URL preview: ${photo.photoUrl.take(100)}...")
                android.util.Log.d("PhotoCard", "Is valid URL: $isValidUrl")
                
                if (isValidUrl) {
                    // Use AsyncImage with custom ImageLoader from Coil singleton
                    val imageLoader = Coil.imageLoader(context)
                    
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photo.photoUrl)
                            .crossfade(true)
                            .memoryCacheKey(photo.id) // Use photo ID as cache key
                            .build(),
                        contentDescription = "Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        imageLoader = imageLoader, // Explicitly use custom loader
                        onError = { error ->
                            android.util.Log.e("PhotoCard", "!!! Error loading image !!!")
                            android.util.Log.e("PhotoCard", "Error: ${error.result.throwable.message}")
                            android.util.Log.e("PhotoCard", "URL: ${photo.photoUrl.take(200)}")
                            error.result.throwable.printStackTrace()
                        },
                        onSuccess = {
                            android.util.Log.d("PhotoCard", "✓ Image loaded successfully")
                        }
                    )
                } else {
                    // Placeholder for photos without valid URL
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
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Photo",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                // User indicator - compare with actual currentUserId
                val isMyPhoto = photo.takenByUserId == currentUserId
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (isMyPhoto) PastelPink else PastelGreen
                        )
                        .border(
                            1.5.dp,
                            if (isMyPhoto) SoftPink else Color(0xFF98E4C8),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isMyPhoto) "🌸" else "🍋",
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
                
                // Delete button (bottom right) - only show in normal mode
                if (!selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete photo",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Selection checkbox overlay - only show in selection mode
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSelected) Color.Black.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) PastelPink
                                else Color.White.copy(alpha = 0.9f)
                            )
                            .border(
                                2.dp,
                                if (isSelected) SoftPink else Color.Gray.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = SoftPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit
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
                    onClick = onTakePhoto
                )
                
                // Gallery option
                AddPhotoOption(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "From Gallery",
                    subtitle = "Choose from album",
                    onClick = onChooseFromGallery
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


