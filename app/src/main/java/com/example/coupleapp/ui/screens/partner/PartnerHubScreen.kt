package com.example.coupleapp.ui.screens.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
    viewModel: PartnerHubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val qaQuestions by viewModel.qaQuestions.collectAsState()
    
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
            uiState.linkStatus == LinkStatus.NOT_LINKED -> {
                NotConnectedScreen(
                    currentUser = uiState.currentUser,
                    onNavigateToLinkPartner = onNavigateToLinkPartner
                )
            }
            uiState.linkStatus == LinkStatus.PENDING_SENT -> {
                PendingRequestSentContent(
                    partnerName = uiState.pendingLinkRequest?.receiverName ?: ""
                )
            }
            uiState.linkStatus == LinkStatus.PENDING_RECEIVED -> {
                PendingRequestReceivedContent(
                    request = uiState.pendingLinkRequest,
                    onAccept = { viewModel.acceptLinkRequest(it) },
                    onReject = { viewModel.rejectLinkRequest(it) }
                )
            }
            uiState.linkStatus == LinkStatus.LINKED -> {
                LinkedPartnerScreen(
                    uiState = uiState,
                    shortcuts = viewModel.shortcuts,
                    qaQuestions = qaQuestions,
                    onNavigateToChat = onNavigateToChat,
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
                    onNavigateToProfile = onNavigateToProfile
                )
            }
        }
        
        // Create Question Dialog
        if (showCreateQuestionDialog) {
            CreateQuestionDialog(
                partnerName = uiState.partner?.name ?: "Partner",
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
 * Screen khi đã kết nối với partner
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
