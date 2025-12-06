package com.example.coupleapp.ui.screens.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.coupleapp.data.model.ChatMessage
import com.example.coupleapp.data.model.MessageType
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val partner by viewModel.partner.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    
    val listState = rememberLazyListState()
    val currentUserId = viewModel.currentUserId
    
    // Scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8F9FA),
                        Color(0xFFFFFBF5)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            ChatHeader(
                partnerName = partner?.name ?: "Partner",
                partnerAvatar = partner?.avatarUrl,
                onBackClick = onBackClick
            )
            
            // Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group messages by date
                val groupedMessages = messages.groupBy { 
                    it.createdAt.toLocalDate() 
                }
                
                groupedMessages.forEach { (date, dayMessages) ->
                    // Date separator
                    item {
                        DateSeparator(date = date.atStartOfDay())
                    }
                    
                    // Messages for this day
                    items(dayMessages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            isFromMe = message.senderId == currentUserId,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
            
            // Input Area
            ChatInputArea(
                messageText = messageText,
                onMessageChanged = viewModel::onMessageChanged,
                onSendClick = viewModel::sendMessage,
                onEmojiClick = viewModel::sendEmoji,
                isSending = isSending
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHeader(
    partnerName: String,
    partnerAvatar: String?,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BackgroundWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 50.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = TextPrimary
                )
            }
            
            // Partner avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(SoftBlue, AccentBlue)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = partnerName.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Đang hoạt động",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            IconButton(onClick = { /* More options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Tùy chọn",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DateSeparator(date: LocalDateTime) {
    val now = LocalDateTime.now()
    val daysBetween = ChronoUnit.DAYS.between(date.toLocalDate(), now.toLocalDate())
    
    val dateText = when {
        daysBetween == 0L -> "Hôm nay"
        daysBetween == 1L -> "Hôm qua"
        daysBetween < 7 -> date.format(DateTimeFormatter.ofPattern("EEEE"))
        else -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE8E8E8).copy(alpha = 0.6f)
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isFromMe) {
        Brush.linearGradient(
            colors = listOf(AccentPink, SoftPink)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFF0F0F0), Color(0xFFE8E8E8))
        )
    }
    val textColor = if (isFromMe) Color.White else TextPrimary
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
        ) {
            // Message bubble
            Surface(
                shape = bubbleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .widthIn(max = 280.dp)
            ) {
                Box(
                    modifier = Modifier.background(bubbleColor)
                ) {
                    when (message.type) {
                        MessageType.EMOJI -> {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 32.sp
                            )
                        }
                        else -> {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }
                }
            }
            
            // Timestamp
            Text(
                text = message.createdAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextLight
            )
        }
    }
}

@Composable
private fun ChatInputArea(
    messageText: String,
    onMessageChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onEmojiClick: (String) -> Unit,
    isSending: Boolean
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    Column {
        // Emoji picker
        AnimatedVisibility(
            visible = showEmojiPicker,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            EmojiPicker(
                onEmojiSelected = { emoji ->
                    onEmojiClick(emoji)
                    showEmojiPicker = false
                }
            )
        }
        
        // Input row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BackgroundWhite,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Emoji button
                IconButton(
                    onClick = { showEmojiPicker = !showEmojiPicker }
                ) {
                    Icon(
                        imageVector = if (showEmojiPicker) Icons.Default.Keyboard else Icons.Outlined.EmojiEmotions,
                        contentDescription = "Emoji",
                        tint = if (showEmojiPicker) AccentPink else TextSecondary
                    )
                }
                
                // Text input
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = onMessageChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Nhập tin nhắn...",
                                color = TextLight
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )
                }
                
                // Send button
                val canSend = messageText.isNotBlank() && !isSending
                
                FilledIconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canSend) AccentPink else Color(0xFFE0E0E0),
                        contentColor = Color.White
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiPicker(
    onEmojiSelected: (String) -> Unit
) {
    val emojis = listOf(
        "❤️", "😍", "🥰", "😘", "💕",
        "💖", "💗", "💓", "💝", "💞",
        "😊", "🤗", "😇", "🥺", "😭",
        "😂", "🤣", "😁", "😄", "😃",
        "🌹", "🌸", "💐", "🌺", "🌷",
        "🎁", "🎂", "🎉", "🎊", "✨"
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8F8F8)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            emojis.chunked(10).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { emoji ->
                        TextButton(
                            onClick = { onEmojiSelected(emoji) },
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
