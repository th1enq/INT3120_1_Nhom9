package com.example.coupleapp.ui.screens.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.data.model.*
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.ui.components.partner.*
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.viewmodel.PartnerHubViewModel
import com.example.coupleapp.viewmodel.PartnerHubViewModelFirebase
import kotlinx.coroutines.delay


@Composable
fun PartnerHubScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToLinkPartner: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToQA: () -> Unit,
    onNavigateToShortcut: (String) -> Unit,
    viewModel: PartnerHubViewModelFirebase = viewModel()
) {
    android.util.Log.d("PartnerHubScreen", "[PARTNER] Screen composing")
    
    val uiState by viewModel.uiState.collectAsState()
    val qaQuestions by viewModel.qaQuestions.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    
    android.util.Log.d("PartnerHubScreen", "[PARTNER] UI State: linkStatus=${uiState.linkStatus}, isLoading=${uiState.isLoading}")
    android.util.Log.d("PartnerHubScreen", "[PARTNER] QA Questions count: ${qaQuestions.size}")
    android.util.Log.d("PartnerHubScreen", "[PARTNER] Pending requests count: ${pendingRequests.size}")
    
    // Refresh data when screen is visible
    LaunchedEffect(Unit) {
        android.util.Log.d("PartnerHubScreen", "[PARTNER] LaunchedEffect triggered, refreshing data")
        viewModel.refreshData()
    }
    
    // Dialog states
    var showCreateQuestionDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF5FFF5),
                        Color(0xFFFFFBF5),
                        Color(0xFFFFFAF0)
                    )
                )
            )
    ) {
        when {
            uiState.isLoading -> {
                android.util.Log.d("PartnerHubScreen", "[PARTNER] Showing loading state")
                PartnerHubLoading()
            }
            uiState.linkStatus == LinkStatus.NOT_LINKED -> {
                android.util.Log.d("PartnerHubScreen", "[PARTNER] Showing not connected screen")
                NotConnectedScreenFirebase(
                    myLinkCode = uiState.myLinkCode,
                    pendingRequestCount = pendingRequests.size,
                    onNavigateToLinkPartner = onNavigateToLinkPartner,
                    onShowNotifications = { showNotificationDialog = true }
                )
            }
            uiState.linkStatus == LinkStatus.LINKED -> {
                android.util.Log.d("PartnerHubScreen", "[PARTNER] Showing linked partner screen")
                android.util.Log.d("PartnerHubScreen", "[PARTNER] Current user: ${uiState.currentUser?.displayName}")
                android.util.Log.d("PartnerHubScreen", "[PARTNER] Partner: ${uiState.partner?.displayName}")
                LinkedPartnerScreenFirebase(
                    currentUser = uiState.currentUser,
                    partner = uiState.partner,
                    partnerLocationName = uiState.partnerLocationName,
                    partnerDistance = uiState.partnerDistance,
                    shortcuts = viewModel.shortcuts,
                    qaQuestions = qaQuestions,
                    onNavigateToChat = {
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Navigate to chat")
                        onNavigateToChat()
                    },
                    onNavigateToQA = {
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Navigate to QA")
                        onNavigateToQA()
                    },
                    onNavigateToShortcut = { route ->
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Navigate to shortcut: $route")
                        onNavigateToShortcut(route)
                    },
                    onAddQuestion = { 
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Add question clicked")
                        showCreateQuestionDialog = true 
                    },
                    onAnswerQuestion = { question, answer ->
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Answer question: ${question.id}")
                        viewModel.answerQuestion(question.id, answer)
                    },
                    onApproveAnswer = { 
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Approve answer: $it")
                        viewModel.approveAnswer(it) 
                    },
                    onRejectAnswer = { questionId, comment ->
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Reject answer: $questionId")
                        viewModel.rejectAnswer(questionId, comment)
                    },
                    onNavigateToLinkPartner = onNavigateToLinkPartner,
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToMoments = onNavigateToMoments,
                    onNavigateToProfile = onNavigateToProfile,
                    onUnlinkPartner = { 
                        android.util.Log.d("PartnerHubScreen", "[PARTNER] Unlink partner")
                        viewModel.unlinkPartner() 
                    }
                )
            }
        }
        
        // Create Question Dialog
        if (showCreateQuestionDialog) {
            CreateQuestionDialog(
                partnerName = uiState.partner?.displayName ?: "Partner",
                onDismiss = { showCreateQuestionDialog = false },
                onCreate = { question ->
                    viewModel.createQuestion(question)
                    showCreateQuestionDialog = false
                }
            )
        }
        
        // Notification Dialog for pending link requests
        if (showNotificationDialog) {
            PendingRequestsDialog(
                requests = pendingRequests,
                onDismiss = { showNotificationDialog = false },
                onAccept = { request ->
                    android.util.Log.d("PartnerHubScreen", "[PARTNER] Accepting request: ${request.id}")
                    viewModel.acceptLinkRequest(request)
                    showNotificationDialog = false
                },
                onReject = { request ->
                    android.util.Log.d("PartnerHubScreen", "[PARTNER] Rejecting request: ${request.id}")
                    viewModel.rejectLinkRequest(request)
                }
            )
        }
    }
}

/**
 * Dialog hiển thị danh sách lời mời kết nối
 */
@Composable
private fun PendingRequestsDialog(
    requests: List<FirebaseLinkRequest>,
    onDismiss: () -> Unit,
    onAccept: (FirebaseLinkRequest) -> Unit,
    onReject: (FirebaseLinkRequest) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Lời mời kết nối",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            if (requests.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = Color(0xFFBDBDBD),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Không có lời mời nào",
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    requests.forEach { request ->
                        PendingRequestItem(
                            request = request,
                            onAccept = { onAccept(request) },
                            onReject = { onReject(request) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Đóng",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

/**
 * Item hiển thị một lời mời kết nối
 */
@Composable
private fun PendingRequestItem(
    request: FirebaseLinkRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = request.fromUserName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.fromUserName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Muốn kết nối với bạn",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    )
                ) {
                    Text("Từ chối", fontSize = 13.sp)
                }
                
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Chấp nhận", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PartnerHubLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Color(0xFF4CAF50),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Đang tải...",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Screen khi chưa kết nối với ai
 */
@Composable
private fun NotConnectedScreen(
    currentUser: PartnerUser?,
    onNavigateToLinkPartner: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header (không có nút add)
        PartnerScreenHeader(
            title = "Bạn bè",
            onAddFriendClick = null
        )
        
        // Not connected content
        NotConnectedContent(
            myLinkCode = currentUser?.linkCode ?: "",
            onAddByCode = { code ->
                // TODO: Search and add by code
                onNavigateToLinkPartner()
            },
            onShareLink = {
                // TODO: Share link
            }
        )
    }
}

/**
 * Pending Requests Screen - Firebase
 */
@Composable
private fun PendingRequestsScreenFirebase(
    requests: List<FirebaseLinkRequest>,
    onAccept: (FirebaseLinkRequest) -> Unit,
    onReject: (FirebaseLinkRequest) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PartnerScreenHeader(
            title = "Lời mời kết nối",
            onAddFriendClick = null
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        requests.forEach { request ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "${request.fromUserName} muốn kết nối với bạn",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onReject(request) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Từ chối")
                        }
                        
                        Button(
                            onClick = { onAccept(request) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B9D)
                            )
                        ) {
                            Text("Chấp nhận")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Not Connected Screen với Firebase
 */
@Composable
private fun NotConnectedScreenFirebase(
    myLinkCode: String,
    pendingRequestCount: Int = 0,
    onNavigateToLinkPartner: () -> Unit,
    onShowNotifications: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with notification bell
            NotConnectedHeader(
                title = "Partner",
                pendingRequestCount = pendingRequestCount,
                onNotificationClick = onShowNotifications
            )
            
            // Not connected content with green theme
            NotConnectedContentGreen(
                myLinkCode = myLinkCode,
                onAddByCode = { code ->
                    // Navigate to link partner screen to search
                    onNavigateToLinkPartner()
                },
                onShareLink = {
                    // TODO: Share link functionality
                }
            )
        }
    }
}

/**
 * Header với nút thông báo cho màn hình chưa kết nối
 */
@Composable
private fun NotConnectedHeader(
    title: String,
    pendingRequestCount: Int,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(top = 30.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        // Notification bell with badge
        Box {
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9))
            ) {
                Icon(
                    imageVector = if (pendingRequestCount > 0) 
                        Icons.Default.Notifications 
                    else 
                        Icons.Default.NotificationsNone,
                    contentDescription = "Thông báo",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Badge for pending requests
            if (pendingRequestCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (pendingRequestCount > 9) "9+" else pendingRequestCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Giao diện kết nối với theme xanh lá (unified green theme)
 */
@Composable
private fun NotConnectedContentGreen(
    myLinkCode: String,
    onAddByCode: (String) -> Unit,
    onShareLink: () -> Unit
) {
    var linkCode by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(2000)
            showCopied = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Welcome section
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF66BB6A), Color(0xFF4CAF50))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Kết nối với người ấy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Text(
            text = "Chia sẻ mã của bạn hoặc nhập mã của người ấy",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // My link code section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE8F5E9)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Mã của bạn",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Link code display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    myLinkCode.forEach { char ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Copy button
                AnimatedVisibility(visible = showCopied) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Đã sao chép!",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(myLinkCode))
                        showCopied = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sao chép mã")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
            Text(
                text = "hoặc",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = TextSecondary,
                fontSize = 14.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Enter partner code button
        Button(
            onClick = { onAddByCode("") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Nhập mã người ấy",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Screen khi đã kết nối với partner - Firebase version dùng HouseCard như mock
 */
@Composable
private fun LinkedPartnerScreenFirebase(
    currentUser: FirebaseUser?,
    partner: FirebaseUser?,
    partnerLocationName: String,
    partnerDistance: Double?,
    shortcuts: List<PartnerShortcut>,
    qaQuestions: List<QAQuestion>,
    onNavigateToChat: () -> Unit,
    onNavigateToQA: () -> Unit,
    onNavigateToShortcut: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onAnswerQuestion: (QAQuestion, String) -> Unit,
    onApproveAnswer: (String) -> Unit,
    onRejectAnswer: (String, String) -> Unit,
    onNavigateToLinkPartner: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToMoments: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onUnlinkPartner: () -> Unit
) {
    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Composing LinkedPartnerScreenFirebase")
    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Current user null? ${currentUser == null}")
    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Partner null? ${partner == null}")
    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] QA questions: ${qaQuestions.size}")
    
    // Convert FirebaseUser to PartnerUser for HouseCard
    val partnerUser = partner?.let {
        android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Converting partner to PartnerUser: ${it.displayName}")
        PartnerUser(
            id = it.id,
            name = it.displayName,
            nickname = it.bio ?: "",
            avatarUrl = it.profileImageUrl,
            linkCode = it.linkCode,
            locationName = partnerLocationName.ifEmpty { "Unknown" }
        )
    }
    
    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Partner user created: ${partnerUser?.name}")

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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            
            // Header
            PartnerScreenHeader(
                title = "Partner",
                onAddFriendClick = null
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // House Card như mock data
            if (partnerUser != null) {
                android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Rendering HouseCard for: ${partnerUser.name}")
                val distanceText = when {
                    partnerDistance == null -> ""
                    partnerDistance < 1.0 -> String.format("%.0f m", partnerDistance * 1000)
                    else -> String.format("%.1f km", partnerDistance)
                }
                HouseCard(
                    partner = partnerUser,
                    partnerDistance = distanceText,
                    partnerLocation = partnerUser.locationName,
                    shortcuts = shortcuts,
                    onChatClick = {
                        android.util.Log.d("LinkedPartnerScreen", "[PARTNER] HouseCard chat clicked")
                        onNavigateToChat()
                    },
                    onShortcutClick = { route ->
                        android.util.Log.d("LinkedPartnerScreen", "[PARTNER] HouseCard shortcut clicked: $route")
                        onNavigateToShortcut(route)
                    }
                )
            } else {
                android.util.Log.e("LinkedPartnerScreen", "[PARTNER] ❌ Partner user is null")
                Text(
                    text = "Partner data not available",
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Q&A Timeline Section
            android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Rendering Q&A Timeline, questions: ${qaQuestions.size}")
            QATimelineSection(
                questions = qaQuestions,
                currentUserId = currentUser?.id ?: "",
                onAddQuestion = {
                    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Add question from timeline")
                    onAddQuestion()
                },
                onAnswerQuestion = { question, answer ->
                    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Answer question from timeline: ${question.id}")
                    onAnswerQuestion(question, answer)
                },
                onApproveAnswer = { questionId ->
                    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Approve from timeline: $questionId")
                    onApproveAnswer(questionId)
                },
                onRejectAnswer = { questionId, reason ->
                    android.util.Log.d("LinkedPartnerScreen", "[PARTNER] Reject from timeline: $questionId")
                    onRejectAnswer(questionId, reason)
                }
            )
        }
        
        // Bottom Navigation
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CoupleBottomNavigation(
                selectedItem = BottomNavItem.FRIENDS,
                onItemSelected = { item ->
                    when (item) {
                        BottomNavItem.HOME -> onNavigateToHome()
                        BottomNavItem.FRIENDS -> { /* Already here */ }
                        BottomNavItem.ACTIVITIES -> onNavigateToMoments()
                        BottomNavItem.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        }
    }
}

/**
 * House Card with Firebase User data
 */
@Composable
private fun HouseCardFirebase(
    partner: FirebaseUser?,
    onNavigateToChat: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Partner info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFF6B9D), Color(0xFFFFA8D5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partner?.displayName?.firstOrNull()?.toString()?.uppercase() ?: "P",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partner?.displayName ?: "Partner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    partner?.bio?.takeIf { it.isNotEmpty() }?.let { bio ->
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                
                // Chat button
                IconButton(
                    onClick = onNavigateToChat,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B9D))
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Chat",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Screen khi đã kết nối với partner - Old version with mock data
 */
@Composable
private fun LinkedPartnerScreen(
    uiState: PartnerHubState,
    shortcuts: List<PartnerShortcut>,
    qaQuestions: List<QAQuestion>,
    onNavigateToChat: () -> Unit,
    onNavigateToShortcut: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onAnswerQuestion: (QAQuestion, String) -> Unit,
    onApproveAnswer: (String) -> Unit,
    onRejectAnswer: (String, String) -> Unit,
    onNavigateToLinkPartner: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToMoments: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
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
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            // Header (no add button)
            PartnerScreenHeader(
                title = "Friends",
                onAddFriendClick = null
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // House Card - Fixed section
            HouseCard(
                partner = uiState.partner,
                partnerDistance = uiState.partnerDistance,
                partnerLocation = uiState.partnerLocationName,
                shortcuts = shortcuts,
                onChatClick = onNavigateToChat,
                onShortcutClick = onNavigateToShortcut
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Q&A Timeline Section - Scrollable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp)
            ) {
                QATimelineSection(
                    questions = qaQuestions,
                    currentUserId = uiState.currentUser?.id ?: "",
                    onAddQuestion = onAddQuestion,
                    onAnswerQuestion = onAnswerQuestion,
                    onApproveAnswer = onApproveAnswer,
                    onRejectAnswer = onRejectAnswer
                )
            }
        }
        
        // Bottom Navigation
        CoupleBottomNavigation(
            selectedItem = BottomNavItem.FRIENDS,
            onItemSelected = { item ->
                when (item) {
                    BottomNavItem.HOME -> onNavigateToHome()
                    BottomNavItem.FRIENDS -> { /* Already here */ }
                    BottomNavItem.ACTIVITIES -> onNavigateToMoments()
                    BottomNavItem.PROFILE -> onNavigateToProfile()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Header chung cho màn hình Partner
 */
@Composable
private fun PartnerScreenHeader(
    title: String,
    onAddFriendClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
            .padding(top = 15.dp, bottom = 8.dp),
        horizontalArrangement = if (onAddFriendClick != null) Arrangement.SpaceBetween else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        // Chỉ hiển thị nút add khi có callback
        onAddFriendClick?.let {
            AddFriendButton(onClick = it)
        }
    }
}

@Composable
private fun PendingRequestSentContent(partnerName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Loading animation
        val infiniteTransition = rememberInfiniteTransition(label = "waiting")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ),
            label = "rotation"
        )
        
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(100.dp),
                color = Color(0xFF4CAF50),
                strokeWidth = 4.dp
            )
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(40.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Waiting for Response",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Link request sent to\n$partnerName",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PendingRequestReceivedContent(
    request: LinkRequest?,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    request ?: return
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF81C784), Color(0xFF4CAF50))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = request.senderName.first().toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Yêu cầu liên kết",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "${request.senderName} muốn liên kết với bạn",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        if (request.message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8F5E9)
            ) {
                Text(
                    text = "\"${request.message}\"",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { onReject(request.id) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "Reject",
                    fontWeight = FontWeight.Medium
                )
            }
            
            Button(
                onClick = { onAccept(request.id) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = "Accept",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Simple shortcut item for Firebase version
 */
@Composable
private fun ShortcutItemSimple(
    shortcut: PartnerShortcut,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(shortcut.backgroundColor))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when (shortcut.iconName) {
                "bedtime" -> Icons.Default.Bedtime
                "photo_camera" -> Icons.Default.PhotoCamera
                "favorite" -> Icons.Default.Favorite
                "location_on" -> Icons.Default.LocationOn
                "event" -> Icons.Default.Event
                "local_florist" -> Icons.Default.LocalFlorist
                "assignment" -> Icons.Default.Assignment
                "store" -> Icons.Default.Store
                else -> Icons.Default.Star
            },
            contentDescription = shortcut.name,
            tint = Color(shortcut.iconColor),
            modifier = Modifier.size(28.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = shortcut.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color(shortcut.iconColor),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
