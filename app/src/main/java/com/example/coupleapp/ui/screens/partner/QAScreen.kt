package com.example.coupleapp.ui.screens.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.QAQuestion
import com.example.coupleapp.data.model.QAStatus
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.viewmodel.QAViewModelFirebase
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QAScreen(
    onBackClick: () -> Unit,
    viewModel: QAViewModelFirebase = viewModel()
) {
    val questions by viewModel.filteredQuestions.collectAsState()
    val partner by viewModel.partner.collectAsState()
    val newQuestion by viewModel.newQuestion.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val currentUserId = viewModel.currentUserId
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAnswerDialog by remember { mutableStateOf<QAQuestion?>(null) }
    
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
            // Header
            QAHeader(
                onBackClick = onBackClick,
                onAddClick = { showCreateDialog = true }
            )
            
            // Tabs
            QATabs(
                selectedTab = selectedTab,
                onTabSelected = viewModel::onTabSelected
            )
            
            // Questions List
            if (questions.isEmpty()) {
                EmptyQAState(selectedTab = selectedTab)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(questions, key = { it.id }) { question ->
                        QACard(
                            question = question,
                            currentUserId = currentUserId,
                            onAnswerClick = { showAnswerDialog = question },
                            onApproveClick = { viewModel.approveAnswer(question.id) },
                            onRejectClick = { viewModel.rejectAnswer(question.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        
        // Create Question Dialog
        if (showCreateDialog) {
            CreateQuestionDialog(
                question = newQuestion,
                partnerName = partner?.displayName ?: "Partner",
                onQuestionChanged = viewModel::onNewQuestionChanged,
                onDismiss = { 
                    showCreateDialog = false
                    viewModel.onNewQuestionChanged("")
                },
                onCreate = {
                    viewModel.createQuestion()
                    showCreateDialog = false
                },
                isLoading = isLoading
            )
        }
        
        // Answer Question Dialog
        showAnswerDialog?.let { question ->
            AnswerQuestionDialog(
                question = question,
                answerText = viewModel.answerText.collectAsState().value,
                onAnswerChanged = viewModel::onAnswerTextChanged,
                onDismiss = { 
                    showAnswerDialog = null
                    viewModel.onAnswerTextChanged("")
                },
                onSubmit = {
                    viewModel.answerQuestion(question.id)
                    showAnswerDialog = null
                },
                isLoading = isLoading
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QAHeader(
    onBackClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BackgroundWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = TextPrimary
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.qa_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.qa_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            FilledTonalIconButton(
                onClick = onAddClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PastelPink
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Question",
                    tint = AccentPink
                )
            }
        }
    }
}

@Composable
private fun QATabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("All", "My Questions", "Answer Me")
    
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = AccentPink,
        indicator = { tabPositions ->
            if (selectedTab < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = tabPositions[selectedTab].left)
                        .width(tabPositions[selectedTab].width),
                    color = AccentPink
                )
            }
        },
        divider = {}
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selectedContentColor = AccentPink,
                unselectedContentColor = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyQAState(selectedTab: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.QuestionAnswer,
            contentDescription = null,
            tint = TextLight,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = when (selectedTab) {
                1 -> "You haven't asked any questions"
                2 -> "No questions to answer"
                else -> "No questions yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.start_asking_questions),
            style = MaterialTheme.typography.bodyMedium,
            color = TextLight,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QACard(
    question: QAQuestion,
    currentUserId: String,
    onAnswerClick: () -> Unit,
    onApproveClick: () -> Unit,
    onRejectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMyQuestion = question.askerId == currentUserId
    val needsMyAnswer = question.responderId == currentUserId && question.status == QAStatus.PENDING
    val needsMyApproval = question.askerId == currentUserId && question.status == QAStatus.ANSWERED
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BackgroundWhite,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Asker info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMyQuestion) AccentPink.copy(alpha = 0.2f)
                                else SoftBlue.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = question.askerName.first().toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isMyQuestion) AccentPink else AccentBlue
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Column {
                        Text(
                            text = if (isMyQuestion) "Bạn hỏi" else question.askerName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = formatTimeAgo(question.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLight
                        )
                    }
                }
                
                // Status chip
                StatusChip(status = question.status)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Question
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8F0FF)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = SoftLavender,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Answer section
            if (question.answer != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = question.responderName + " answered:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = question.answer!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
                
                // Approval buttons for the asker
                if (needsMyApproval) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.are_you_satisfied),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRejectClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.not_correct))
                        }
                        
                        Button(
                            onClick = onApproveClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.accept))
                        }
                    }
                }
                
                // Approval result
                if (question.status == QAStatus.APPROVED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.answer_approved),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen
                        )
                    }
                } else if (question.status == QAStatus.REJECTED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = ErrorColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.answer_not_satisfactory),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorColor
                        )
                    }
                }
            }
            
            // Answer button for responder
            if (needsMyAnswer) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onAnswerClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPink
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.answer_this_question))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: QAStatus) {
    val (text, backgroundColor, textColor) = when (status) {
        QAStatus.PENDING -> Triple("Waiting", Color(0xFFFFF3E0), Color(0xFFFF9800))
        QAStatus.ANSWERED -> Triple("Answered", Color(0xFFE3F2FD), Color(0xFF2196F3))
        QAStatus.APPROVED -> Triple("Approved", Color(0xFFE8F5E9), AccentGreen)
        QAStatus.REJECTED -> Triple("Rejected", Color(0xFFFFEBEE), ErrorColor)
        QAStatus.EXPIRED -> Triple("Expired", Color(0xFFF5F5F5), TextLight)
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun CreateQuestionDialog(
    question: String,
    partnerName: String,
    onQuestionChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = BackgroundWhite,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.ask_question_to, partnerName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.ask_interesting_questions),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.question_example),
                        color = TextLight
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = question.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPink
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.send_question))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AnswerQuestionDialog(
    question: QAQuestion,
    answerText: String,
    onAnswerChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = BackgroundWhite,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.answer_question_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PastelPurple.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = question.question,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        },
        text = {
            OutlinedTextField(
                value = answerText,
                onValueChange = onAnswerChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.enter_your_answer),
                        color = TextLight
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = answerText.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPink
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.send_answer))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        }
    )
}

private fun formatTimeAgo(dateTime: java.time.LocalDateTime): String {
    val now = java.time.LocalDateTime.now()
    val minutes = ChronoUnit.MINUTES.between(dateTime, now)
    val hours = ChronoUnit.HOURS.between(dateTime, now)
    val days = ChronoUnit.DAYS.between(dateTime, now)
    
    return when {
        minutes < 1 -> "Vừa xong"
        minutes < 60 -> "$minutes phút trước"
        hours < 24 -> "$hours giờ trước"
        days < 7 -> "$days ngày trước"
        else -> dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}
