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
    val uiState by viewModel.uiState.collectAsState()
    val qaQuestions by viewModel.qaQuestions.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    
    // Refresh data when screen is visible
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }
    
    // Dialog states
    var showCreateQuestionDialog by remember { mutableStateOf(false) }
    
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
                PartnerHubLoading()
            }
            uiState.linkStatus == LinkStatus.PENDING_RECEIVED && pendingRequests.isNotEmpty() -> {
                PendingRequestsScreenFirebase(
                    requests = pendingRequests,
                    onAccept = { viewModel.acceptLinkRequest(it) },
                    onReject = { viewModel.rejectLinkRequest(it) }
                )
            }
            uiState.linkStatus == LinkStatus.NOT_LINKED -> {
                NotConnectedScreenFirebase(
                    myLinkCode = uiState.myLinkCode,
                    onNavigateToLinkPartner = onNavigateToLinkPartner
                )
            }
            uiState.linkStatus == LinkStatus.LINKED -> {
                LinkedPartnerScreenFirebase(
                    currentUser = uiState.currentUser,
                    partner = uiState.partner,
                    shortcuts = viewModel.shortcuts,
                    qaQuestions = qaQuestions,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToQA = onNavigateToQA,
                    onNavigateToShortcut = onNavigateToShortcut,
                    onAddQuestion = { showCreateQuestionDialog = true },
                    onAnswerQuestion = { question, answer ->
                        viewModel.answerQuestion(question.id, answer)
                    },
                    onApproveAnswer = { viewModel.approveAnswer(it) },
                    onRejectAnswer = { questionId, comment ->
                        viewModel.rejectAnswer(questionId, comment)
                    },
                    onNavigateToLinkPartner = onNavigateToLinkPartner,
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToMoments = onNavigateToMoments,
                    onNavigateToProfile = onNavigateToProfile,
                    onUnlinkPartner = { viewModel.unlinkPartner() }
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
    onNavigateToLinkPartner: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        PartnerScreenHeader(
            title = "Partner",
            onAddFriendClick = null
        )
        
        // Not connected content
        NotConnectedContent(
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

/**
 * Screen khi đã kết nối với partner - Firebase version dùng HouseCard như mock
 */
@Composable
private fun LinkedPartnerScreenFirebase(
    currentUser: FirebaseUser?,
    partner: FirebaseUser?,
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
    // Convert FirebaseUser to PartnerUser for HouseCard
    val partnerUser = partner?.let {
        PartnerUser(
            id = it.id,
            name = it.displayName,
            nickname = it.bio ?: "",
            avatarUrl = it.profileImageUrl,
            linkCode = it.linkCode,
            locationName = "Unknown"
        )
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
                HouseCard(
                    partner = partnerUser,
                    partnerDistance = "Unknown",
                    partnerLocation = partnerUser.locationName,
                    shortcuts = shortcuts,
                    onChatClick = onNavigateToChat,
                    onShortcutClick = onNavigateToShortcut
                )
            }
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
