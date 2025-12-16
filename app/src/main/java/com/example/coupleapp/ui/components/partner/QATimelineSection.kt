package com.example.coupleapp.ui.components.partner

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.QAQuestion
import com.example.coupleapp.data.model.QAStatus
import com.example.coupleapp.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val TAG = "QATimelineSection"

/**
 * Section hiển thị Q&A với timeline theo ngày
 */
@Composable
fun QATimelineSection(
    questions: List<QAQuestion>,
    currentUserId: String,
    onAddQuestion: () -> Unit,
    onAnswerQuestion: (QAQuestion, String) -> Unit,
    onApproveAnswer: (String) -> Unit,
    onRejectAnswer: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "[QA] QATimelineSection composing, questions: ${questions.size}, userId: $currentUserId")
    
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        Log.d(TAG, "[QA] LaunchedEffect triggered, setting visible")
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(500, delayMillis = 300)) +
                slideInVertically(animationSpec = tween(500, delayMillis = 300)) { it / 4 }
    ) {
        Log.d(TAG, "[QA] AnimatedVisibility visible, rendering Column")
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header: Title + Add button
            Log.d(TAG, "[QA] Rendering QAHeader")
            QAHeader(onAddClick = onAddQuestion)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Timeline content
            if (questions.isEmpty()) {
                Log.d(TAG, "[QA] No questions, showing empty content")
                EmptyQAContent()
            } else {
                Log.d(TAG, "[QA] Rendering timeline with ${questions.size} questions")
                QATimelineContent(
                    questions = questions,
                    currentUserId = currentUserId,
                    onAnswerQuestion = onAnswerQuestion,
                    onApproveAnswer = onApproveAnswer,
                    onRejectAnswer = onRejectAnswer
                )
            }
        }
    }
}

@Composable
private fun QAHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Q & A",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Get to know each other better",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        // Add question button
        AddQuestionButton(onClick = onAddClick)
    }
}

@Composable
private fun AddQuestionButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Surface(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF4CAF50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Questions",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyQAContent() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.QuestionAnswer,
                contentDescription = null,
                tint = TextLight,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "No questions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Create questions to get to know each other better!",
                style = MaterialTheme.typography.bodySmall,
                color = TextLight,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QATimelineContent(
    questions: List<QAQuestion>,
    currentUserId: String,
    onAnswerQuestion: (QAQuestion, String) -> Unit,
    onApproveAnswer: (String) -> Unit,
    onRejectAnswer: (String, String) -> Unit
) {
    // Group questions by date (chỉ lấy 3 ngày gần nhất)
    val today = LocalDate.now()
    val threeDaysAgo = today.minusDays(3)
    
    val recentQuestions = questions.filter { 
        it.createdAt.toLocalDate() >= threeDaysAgo 
    }
    
    val groupedQuestions = recentQuestions
        .groupBy { it.createdAt.toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .entries
        .take(3)
    
    Column(modifier = Modifier.fillMaxWidth()) {
        groupedQuestions.forEachIndexed { index, (date, dayQuestions) ->
            TimelineDaySection(
                date = date,
                questions = dayQuestions,
                currentUserId = currentUserId,
                onAnswerQuestion = onAnswerQuestion,
                onApproveAnswer = onApproveAnswer,
                onRejectAnswer = onRejectAnswer,
                isLast = index == groupedQuestions.size - 1
            )
        }
    }
}

@Composable
private fun TimelineDaySection(
    date: LocalDate,
    questions: List<QAQuestion>,
    currentUserId: String,
    onAnswerQuestion: (QAQuestion, String) -> Unit,
    onApproveAnswer: (String) -> Unit,
    onRejectAnswer: (String, String) -> Unit,
    isLast: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline indicator bên trái
        TimelineIndicator(
            date = date,
            isLast = isLast
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Questions cards bên phải
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
        ) {
            questions.forEach { question ->
                QAQuestionCard(
                    question = question,
                    currentUserId = currentUserId,
                    onAnswerQuestion = onAnswerQuestion,
                    onApproveAnswer = onApproveAnswer,
                    onRejectAnswer = onRejectAnswer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TimelineIndicator(
    date: LocalDate,
    isLast: Boolean
) {
    val today = LocalDate.now()
    val dateText = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("dd/MM"))
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        // Date circle
        Surface(
            shape = CircleShape,
            color = Color(0xFF4CAF50),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Date label
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
        
        // Vertical line
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(60.dp)
                    .background(Color(0xFFE0E0E0))
            )
            
            // Dots
            Text(
                text = "•••",
                color = TextLight,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun QAQuestionCard(
    question: QAQuestion,
    currentUserId: String,
    onAnswerQuestion: (QAQuestion, String) -> Unit,
    onApproveAnswer: (String) -> Unit,
    onRejectAnswer: (String, String) -> Unit
) {
    val isMyQuestion = question.askerId == currentUserId
    val needsMyAnswer = question.responderId == currentUserId && question.status == QAStatus.PENDING
    val needsMyApproval = question.askerId == currentUserId && question.status == QAStatus.ANSWERED
    
    var showAnswerInput by remember { mutableStateOf(false) }
    var answerText by remember { mutableStateOf("") }
    var showRejectComment by remember { mutableStateOf(false) }
    var rejectComment by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BackgroundWhite,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Sender info + time + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Surface(
                        shape = CircleShape,
                        color = if (isMyQuestion) Color(0xFFE8F5E9) else Color(0xFFE3F2FD),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = question.askerName.first().toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMyQuestion) Color(0xFF4CAF50) else Color(0xFF2196F3)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = if (isMyQuestion) "You" else question.askerName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = formatTimeAgo(question.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextLight,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Status chip
                QAStatusChip(status = question.status, isApproved = question.isApproved)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Question content
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8F0FF)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "❓",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
            
            // Answer section (if answered)
            if (question.answer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        question.status == QAStatus.REJECTED -> Color(0xFFFFEBEE)
                        question.status == QAStatus.APPROVED -> Color(0xFFE8F5E9)
                        else -> Color(0xFFF5F5F5)
                    }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "💬",
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = question.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        
                        // Show rejected message if applicable
                        if (question.status == QAStatus.REJECTED) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "❌ Answer not correct",
                                style = MaterialTheme.typography.labelSmall,
                                color = ErrorColor
                            )
                        }
                    }
                }
            }
            
            // Action buttons based on status
            when {
                // Câu hỏi của người khác, cần mình trả lời
                needsMyAnswer -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (showAnswerInput) {
                        AnswerInputField(
                            value = answerText,
                            onValueChange = { answerText = it },
                            onSubmit = {
                                onAnswerQuestion(question, answerText)
                                answerText = ""
                                showAnswerInput = false
                            },
                            onCancel = {
                                answerText = ""
                                showAnswerInput = false
                            }
                        )
                    } else {
                        TextButton(
                            onClick = { showAnswerInput = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Answer",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                // Câu hỏi của mình, cần đánh giá câu trả lời
                needsMyApproval -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (showRejectComment) {
                        RejectCommentField(
                            value = rejectComment,
                            onValueChange = { rejectComment = it },
                            onSubmit = {
                                onRejectAnswer(question.id, rejectComment)
                                rejectComment = ""
                                showRejectComment = false
                            },
                            onCancel = {
                                rejectComment = ""
                                showRejectComment = false
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            // Reject button
                            TextButton(
                                onClick = { showRejectComment = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = ErrorColor
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reject",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Approve button
                            TextButton(
                                onClick = { onApproveAnswer(question.id) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Accept",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QAStatusChip(
    status: QAStatus,
    isApproved: Boolean?
) {
    val (text, backgroundColor, textColor) = when (status) {
        QAStatus.PENDING -> Triple("Waiting", Color(0xFFFFF3E0), Color(0xFFFF9800))
        QAStatus.ANSWERED -> Triple("Answered", Color(0xFFE3F2FD), Color(0xFF2196F3))
        QAStatus.APPROVED -> Triple("Correct", Color(0xFFE8F5E9), Color(0xFF4CAF50))
        QAStatus.REJECTED -> Triple("Not Correct", Color(0xFFFFEBEE), Color(0xFFF44336))
        QAStatus.EXPIRED -> Triple("Expired", Color(0xFFF5F5F5), TextLight)
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AnswerInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (value.isNotBlank()) {
                            onSubmit()
                            focusManager.clearFocus()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(8.dp)) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Enter your answer...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextLight
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(
                onClick = {
                    if (value.isNotBlank()) {
                        onSubmit()
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier.size(32.dp),
                enabled = value.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) Color(0xFF4CAF50) else TextLight,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RejectCommentField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Enter the correct answer:",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF3E0),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSubmit()
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(8.dp)) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "The correct answer is...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextLight
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = {
                        onSubmit()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Format time as "x minutes/hours/days ago"
 */
private fun formatTimeAgo(dateTime: LocalDateTime): String {
    val now = LocalDateTime.now()
    val minutes = ChronoUnit.MINUTES.between(dateTime, now)
    val hours = ChronoUnit.HOURS.between(dateTime, now)
    val days = ChronoUnit.DAYS.between(dateTime, now)
    
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days < 7 -> "$days days ago"
        else -> dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}
