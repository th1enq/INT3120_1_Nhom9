package com.example.coupleapp.ui.screens.locket

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.data.model.LocketTab
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.ui.components.locket.*
import com.example.coupleapp.viewmodel.LocketViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocketScreen(
    onBackClick: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToDrawing: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LocketViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var visible by remember { mutableStateOf(false) }
    var selectedBottomNavItem by remember { mutableStateOf<BottomNavItem?>(null) }
    
    // Handle navigation
    LaunchedEffect(selectedBottomNavItem) {
        when (selectedBottomNavItem) {
            BottomNavItem.HOME -> {
                onNavigateToHome()
            }
            BottomNavItem.FRIENDS -> {
                onNavigateToPartnerHub()
            }
            BottomNavItem.ACTIVITIES -> {
                onNavigateToMoments()
            }
            BottomNavItem.PROFILE -> {
                onNavigateToProfile()
            }
            null -> { /* Initial state, do nothing */ }
        }
    }
    
    // Swipe detection state
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val tabs = remember { LocketTab.entries.toList() }
    
    val scrollState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onGalleryImageSelected(it.toString()) }
    }
    
    // Animation timing
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                delay(50)  // Minimal delay for smooth transition
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
        }
    }
    
    // Send success snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.sendSuccess) {
        if (uiState.sendSuccess) {
            snackbarHostState.showSnackbar(
                message = "Sent successfully! 💕",
                duration = SnackbarDuration.Short
            )
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Hide bottom nav when showing photo confirmation
            if (!uiState.showPreview) {
                CoupleBottomNavigation(
                    selectedItem = selectedBottomNavItem ?: BottomNavItem.HOME,
                    onItemSelected = { item ->
                        selectedBottomNavItem = item
                    }
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        
        // Photo confirmation screen
        AnimatedVisibility(
            visible = uiState.showPreview && uiState.capturedPhoto != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            LocketPhotoConfirmation(
                bitmap = uiState.capturedPhoto,
                partnerName = uiState.partnerUser.name,
                onSend = { viewModel.sendLocket() },
                onCancel = { viewModel.clearCapturedPhoto() },
                onSaveToGallery = { /* TODO: Save to gallery */ }
            )
        }
        
        // Main content (hidden when showing photo confirmation)
        AnimatedVisibility(
            visible = !uiState.showPreview,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Crossfade(
                targetState = uiState.isLoading,
                animationSpec = tween(durationMillis = 200),  // Faster transition
                label = "LoadingCrossfade",
                modifier = modifier.fillMaxSize()
            ) { loading ->
                if (loading) {
                    LoadingScreen()
                } else {
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
                            .pointerInput(uiState.selectedTab) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val currentIndex = tabs.indexOf(uiState.selectedTab)
                                        if (abs(swipeOffset) > 100) {
                                            val newIndex = if (swipeOffset > 0) {
                                                // Swiped right - go to previous
                                                (currentIndex - 1).coerceAtLeast(0)
                                            } else {
                                                // Swiped left - go to next
                                                (currentIndex + 1).coerceAtMost(tabs.size - 1)
                                            }
                                            viewModel.selectTab(tabs[newIndex])
                                        }
                                        swipeOffset = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        swipeOffset += dragAmount
                                    }
                                )
                            }
                    ) {
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            // Top Bar
                            item {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(300)) +
                                            slideInVertically(animationSpec = tween(300)) { -it }
                                ) {
                                    LocketTopBar(
                                        title = "Pin",
                                        onBackClick = onBackClick,
                                        onMenuClick = { viewModel.showSettings(true) }
                                    )
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                            
                            // Tab Bar
                            item {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(300, delayMillis = 50))
                                ) {
                                    LocketTabBar(
                                        selectedTab = uiState.selectedTab,
                                        onTabSelected = { viewModel.selectTab(it) }
                                    )
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                            
                            // Content based on selected tab
                            item {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(300, delayMillis = 100))
                                ) {
                                    AnimatedContent(
                                        targetState = uiState.selectedTab,
                                        transitionSpec = {
                                            if (targetState.ordinal > initialState.ordinal) {
                                                // Moving forward - slide left
                                                slideInHorizontally { width -> width } + fadeIn() togetherWith
                                                        slideOutHorizontally { width -> -width } + fadeOut()
                                            } else {
                                                // Moving backward - slide right
                                                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                                        slideOutHorizontally { width -> width } + fadeOut()
                                            }.using(SizeTransform(clip = false))
                                        },
                                        label = "TabContent"
                                    ) { tab ->
                                        when (tab) {
                                            LocketTab.PHOTO -> {
                                                LocketCameraPreview(
                                                    cameraState = uiState.cameraState,
                                                    onFlashToggle = { viewModel.toggleFlash() },
                                                    onCameraToggle = { viewModel.toggleCamera() },
                                                    onZoomToggle = { viewModel.cycleZoom() },
                                                    onCapture = { bitmap -> 
                                                        viewModel.onPhotoCaptured(bitmap)
                                                    },
                                                    onGalleryClick = { 
                                                        galleryLauncher.launch("image/*")
                                                    }
                                                )
                                            }
                                            
                                            LocketTab.EMOJI -> {
                                                LocketEmojiContent(
                                                    selectedEmoji = uiState.selectedEmoji,
                                                    onSelectEmoji = { viewModel.showEmojiPicker(true) },
                                                    onSendEmoji = { viewModel.sendLocket() }
                                                )
                                            }
                                            
                                            LocketTab.DRAWING -> {
                                                LocketDrawingContent(
                                                    hasDrawing = uiState.drawingBitmap != null,
                                                    onOpenDrawing = onNavigateToDrawing,
                                                    onSendDrawing = { viewModel.sendLocket() }
                                                )
                                            }
                                            
                                            LocketTab.TEXT -> {
                                                LocketTextContent(
                                                    textContent = uiState.textContent,
                                                    onTextChange = { viewModel.updateTextContent(it) },
                                                    onSendText = { viewModel.sendLocket() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Toggle Button (Pin/User)
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300, delayMillis = 150)) +
                                    slideInVertically(
                                        animationSpec = tween(300, delayMillis = 150),
                                        initialOffsetY = { it }
                                    ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = paddingValues.calculateBottomPadding() + 16.dp)
                        ) {
                            LocketToggleButton(
                                partnerUserName = uiState.partnerUser.name,
                                isPinMode = uiState.isPinMode,
                                onPinClick = { viewModel.togglePinMode(true) },
                                onUserClick = { 
                                    viewModel.togglePinMode(false)
                                    onNavigateToHistory()
                                }
                            )
                        }
                        
                        // Sending indicator
                        if (uiState.isSending) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF4CAF50),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Text(
                                            text = "Sending...",
                                            color = Color(0xFF2D2D2D),
                                            fontSize = 16.sp,
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
        
        // Emoji picker bottom sheet
        if (uiState.showEmojiPicker) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.showEmojiPicker(false) },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                EmojiPickerContent(
                    emojis = uiState.emojis,
                    onEmojiSelected = { emoji ->
                        viewModel.selectEmoji(emoji)
                        scope.launch {
                            sheetState.hide()
                            viewModel.showEmojiPicker(false)
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showEmojiPicker(false)
                        }
                    }
                )
            }
        }
        
        // Settings bottom sheet
        if (uiState.showSettings) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.showSettings(false) },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                LocketSettingsBottomSheet(
                    onDismiss = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showSettings(false)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LocketTopBar(
    title: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
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
        
        // Title next to back button
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = Color(0xFF2D2D2D)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Menu button only
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Settings bottom sheet for Locket
 */
@Composable
fun LocketSettingsBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.White)
            .padding(24.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0))
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Locket Settings",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF2D2D2D)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Settings options
        SettingsItem(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            subtitle = "Get notified when you receive a message"
        )
        
        SettingsItem(
            icon = Icons.Default.Save,
            title = "Auto Save",
            subtitle = "Save photos to gallery after sending"
        )
        
        SettingsItem(
            icon = Icons.Default.History,
            title = "History",
            subtitle = "View all sent messages"
        )
        
        SettingsItem(
            icon = Icons.Default.Info,
            title = "Help",
            subtitle = "How to use Locket"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* TODO */ }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF2D2D2D)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF757575)
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFB0B0B0)
        )
    }
}
