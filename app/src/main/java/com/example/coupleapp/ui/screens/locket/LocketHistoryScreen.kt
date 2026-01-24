package com.example.coupleapp.ui.screens.locket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.LocketPost
import com.example.coupleapp.data.model.LocketType
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.viewmodel.LocketViewModelFirebase
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocketHistoryScreen(
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LocketViewModelFirebase = viewModel()
) {
    // Load all lockets for history view
    LaunchedEffect(Unit) {
        viewModel.loadAllLockets()
    }
    val uiState by viewModel.uiState.collectAsState()
    
    var visible by remember { mutableStateOf(false) }
    var selectedBottomNavItem by remember { mutableStateOf<BottomNavItem?>(null) }
    var selectedPost by remember { mutableStateOf<LocketPost?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Show snackbar for delete success/error
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            snackbarHostState.showSnackbar("Đã xóa Locket thành công")
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
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
    
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                delay(100)
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
        }
    }
    
    Scaffold(
        bottomBar = {
            if (uiState.isSelectionMode) {
                // Selection mode bottom bar
                SelectionModeBottomBar(
                    selectedCount = uiState.selectedForDeletion.size,
                    onCancelClick = { viewModel.toggleSelectionMode(false) },
                    onDeleteClick = { showDeleteConfirmDialog = true }
                )
            } else {
                CoupleBottomNavigation(
                    selectedItem = selectedBottomNavItem ?: BottomNavItem.HOME,
                    onItemSelected = { item ->
                        selectedBottomNavItem = item
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 300),
            label = "LoadingCrossfade",
            modifier = modifier.fillMaxSize()
        ) { loading ->
            if (loading) {
                LoadingScreen(message = stringResource(R.string.loading_history))
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
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // Top Bar
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(animationSpec = tween(300)) { -it }
                        ) {
                            HistoryTopBar(
                                partnerName = uiState.partnerUser.name,
                                isSelectionMode = uiState.isSelectionMode,
                                onBackClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleSelectionMode(false)
                                    } else {
                                        onBackClick()
                                    }
                                },
                                onToggleSelectionMode = { viewModel.toggleSelectionMode(!uiState.isSelectionMode) }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Stats row
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300, delayMillis = 50))
                        ) {
                            HistoryStatsRow(
                                totalPosts = uiState.locketHistory.size,
                                photoCount = uiState.locketHistory.count { it.type == LocketType.PHOTO },
                                emojiCount = uiState.locketHistory.count { it.type == LocketType.EMOJI },
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // History grid
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300, delayMillis = 100))
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.locketHistory,
                                    key = { it.id }
                                ) { post ->
                                    val isSelected = post.id in uiState.selectedForDeletion
                                    val canDelete = post.senderId == uiState.currentUser.id
                                    
                                    HistoryGridItem(
                                        post = post,
                                        currentUserId = uiState.currentUser.id,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isSelected = isSelected,
                                        canDelete = canDelete,
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                if (canDelete) {
                                                    viewModel.toggleLocketSelection(post.id)
                                                }
                                            } else {
                                                selectedPost = post
                                            }
                                        },
                                        onLongClick = {
                                            if (canDelete && !uiState.isSelectionMode) {
                                                viewModel.toggleSelectionMode(true)
                                                viewModel.toggleLocketSelection(post.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Post detail dialog
        selectedPost?.let { post ->
            val canDelete = post.senderId == uiState.currentUser.id
            LocketPostDetailDialog(
                post = post,
                currentUserId = uiState.currentUser.id,
                canDelete = canDelete,
                onDismiss = { selectedPost = null },
                onDelete = {
                    viewModel.deleteLocket(post)
                    selectedPost = null
                }
            )
        }
        
        // Delete confirmation dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Xác nhận xóa") },
                text = { 
                    Text("Bạn có chắc muốn xóa ${uiState.selectedForDeletion.size} Locket đã chọn?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedPosts = uiState.locketHistory.filter { 
                                it.id in uiState.selectedForDeletion 
                            }
                            viewModel.deleteMultipleLockets(selectedPosts)
                            showDeleteConfirmDialog = false
                        }
                    ) {
                        Text("Xóa", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Hủy")
                    }
                }
            )
        }
        
        // Loading overlay when deleting
        if (uiState.isDeleting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun HistoryTopBar(
    partnerName: String,
    isSelectionMode: Boolean,
    onBackClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isSelectionMode) "Cancel" else "Back",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (isSelectionMode) {
            Text(
                text = "Chọn để xóa",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF2D2D2D),
                modifier = Modifier.weight(1f)
            )
        } else {
            // Partner avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9ED9FF).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = partnerName,
                    tint = Color(0xFF9ED9FF),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF2D2D2D)
                )
                
                Text(
                    text = stringResource(R.string.message_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575)
                )
            }
        }
        
        // Selection mode toggle button
        if (!isSelectionMode) {
            IconButton(
                onClick = onToggleSelectionMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Select to delete",
                    tint = Color(0xFF757575),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryStatsRow(
    totalPosts: Int,
    photoCount: Int,
    emojiCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF9ECE).copy(alpha = 0.2f),
                        Color(0xFF9ED9FF).copy(alpha = 0.2f),
                        Color(0xFF4CAF50).copy(alpha = 0.2f)
                    )
                )
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            icon = Icons.Default.Collections,
            value = totalPosts.toString(),
            label = "Total",
            tintColor = Color(0xFFFF9ECE)
        )
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(Color(0xFFE0E0E0))
        )
        
        StatItem(
            icon = Icons.Default.PhotoCamera,
            value = photoCount.toString(),
            label = "Photos",
            tintColor = Color(0xFF4D96FF)
        )
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(Color(0xFFE0E0E0))
        )
        
        StatItem(
            icon = Icons.Default.EmojiEmotions,
            value = emojiCount.toString(),
            label = "Emoji",
            tintColor = Color(0xFF4CAF50)
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tintColor: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(24.dp)
        )
        
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF2D2D2D)
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF757575)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryGridItem(
    post: LocketPost,
    currentUserId: String,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    canDelete: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFromCurrentUser = post.senderId == currentUserId
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM") }
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelectionMode && isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFFFF6B9D),
                        shape = RoundedCornerShape(20.dp)
                    )
                } else Modifier
            )
            .background(getPostBackground(post.type))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Content based on type
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (post.type) {
                LocketType.PHOTO -> {
                    // Load photo from Firebase (Base64 or URL)
                    if (post.content.isNotEmpty()) {
                        com.example.coupleapp.ui.components.locket.LocketImageFromUrl(
                            imageUrl = post.content,
                            contentDescription = "Photo from ${post.senderName}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Placeholder for photo
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                LocketType.EMOJI -> {
                    Text(
                        text = post.content,
                        fontSize = 56.sp
                    )
                }
                
                LocketType.DRAWING -> {
                    // Load drawing from Firebase (Base64 or URL)
                    if (post.content.isNotEmpty()) {
                        com.example.coupleapp.ui.components.locket.LocketImageFromUrl(
                            imageUrl = post.content,
                            contentDescription = "Drawing from ${post.senderName}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                LocketType.TEXT -> {
                    Text(
                        text = post.content,
                        color = Color(0xFF2D2D2D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        // Selection checkbox overlay (when in selection mode)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFFFF6B9D)
                        else if (canDelete) Color.White.copy(alpha = 0.9f)
                        else Color.Gray.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (!canDelete) {
                    // Show lock icon for items that can't be deleted
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Cannot delete",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // Sender avatar (bottom left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isFromCurrentUser) Color(0xFFFF9ECE)
                    else Color(0xFF9ED9FF)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = post.senderName,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        
        // Time badge (top right)
        if (!isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = post.timestamp.format(timeFormatter),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = post.timestamp.format(dateFormatter),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun getPostBackground(type: LocketType): Brush {
    return when (type) {
        LocketType.PHOTO -> Brush.verticalGradient(
            colors = listOf(Color(0xFF4A4A4A), Color(0xFF2D2D2D))
        )
        LocketType.EMOJI -> Brush.verticalGradient(
            colors = listOf(Color(0xFF4A7C59), Color(0xFF3D6B4C))
        )
        LocketType.DRAWING -> Brush.verticalGradient(
            colors = listOf(Color(0xFFFF9ECE), Color(0xFFFF7EB3))
        )
        LocketType.TEXT -> Brush.verticalGradient(
            colors = listOf(Color(0xFFE8F0FF), Color(0xFFDEE8FF))
        )
    }
}

/**
 * Detail dialog for a locket post
 */
@Composable
fun LocketPostDetailDialog(
    post: LocketPost,
    currentUserId: String,
    canDelete: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {}
) {
    val isFromCurrentUser = post.senderId == currentUserId
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xác nhận xóa") },
            text = { Text("Bạn có chắc muốn xóa Locket này?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Xóa", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Hủy")
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Content preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(getPostBackground(post.type)),
                    contentAlignment = Alignment.Center
                ) {
                    when (post.type) {
                        LocketType.PHOTO -> {
                            // Load photo from Firebase (Base64 or URL)
                            if (post.content.isNotEmpty()) {
                                com.example.coupleapp.ui.components.locket.LocketImageFromUrl(
                                    imageUrl = post.content,
                                    contentDescription = "Photo from ${post.senderName}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                        LocketType.EMOJI -> {
                            Text(text = post.content, fontSize = 80.sp)
                        }
                        LocketType.DRAWING -> {
                            // Load drawing from Firebase (Base64 or URL)
                            if (post.content.isNotEmpty()) {
                                com.example.coupleapp.ui.components.locket.LocketImageFromUrl(
                                    imageUrl = post.content,
                                    contentDescription = "Drawing from ${post.senderName}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Brush,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                        LocketType.TEXT -> {
                            Text(
                                text = post.content,
                                color = Color(0xFF2D2D2D),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sender info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFromCurrentUser) Color(0xFFFF9ECE).copy(alpha = 0.2f)
                                else Color(0xFF9ED9FF).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isFromCurrentUser) Color(0xFFFF9ECE) else Color(0xFF9ED9FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = if (isFromCurrentUser) "Bạn" else post.senderName,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF2D2D2D)
                        )
                        
                        Text(
                            text = post.timestamp.format(timeFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canDelete) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Xóa",
                            color = Color.Red,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.close),
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

/**
 * Bottom bar for selection mode
 */
@Composable
private fun SelectionModeBottomBar(
    selectedCount: Int,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelClick) {
                Text(
                    text = "Hủy",
                    color = Color(0xFF757575),
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "Đã chọn $selectedCount",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF2D2D2D)
            )
            
            Button(
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B9D),
                    disabledContainerColor = Color(0xFFE0E0E0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Xóa")
            }
        }
    }
}
