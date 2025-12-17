package com.example.coupleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.model.ChatMessage
import com.example.coupleapp.data.model.FirebaseChatMessage
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.model.MessageType
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.utils.NotificationHelper
import com.google.firebase.database.*
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/**
 * ViewModel for Chat Screen with Firebase integration
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    private val realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://coupleapp-69f4c-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
    private val notificationHelper = NotificationHelper(application.applicationContext)
    private var messagesListener: ValueEventListener? = null
    private var messagesRef: DatabaseReference? = null
    private var isInChatScreen = false

    companion object {
        private const val TAG = "ChatViewModel"
        private const val SEND_TIMEOUT_MS = 10000L // 10 seconds
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _partner = MutableStateFlow<FirebaseUser?>(null)
    val partner: StateFlow<FirebaseUser?> = _partner.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: ""

    init {
        Log.d(TAG, "ChatViewModel initialized")
        loadCurrentUserAndPartner()
    }

    override fun onCleared() {
        super.onCleared()
        // Remove realtime listener when ViewModel is cleared
        messagesListener?.let { listener ->
            messagesRef?.removeEventListener(listener)
        }
        isInChatScreen = false
        Log.d(TAG, "ChatViewModel cleared, realtime listener removed")
    }
    
    /**
     * Set whether user is currently viewing the chat screen
     */
    fun setInChatScreen(inChat: Boolean) {
        isInChatScreen = inChat
        if (inChat) {
            // Cancel notifications when user enters chat screen
            notificationHelper.cancelMessageNotification()
        }
    }

    /**
     * Load current user and partner info, then start listening to messages
     */
    private fun loadCurrentUserAndPartner() {
        viewModelScope.launch {
            _isLoading.value = true

            val currentUserId = authRepository.currentUser?.uid
            if (currentUserId == null) {
                Log.e(TAG, "No user logged in")
                _error.value = "Chưa đăng nhập"
                _isLoading.value = false
                return@launch
            }

            Log.d(TAG, "Loading user data for: $currentUserId")

            // Load current user to get partnerId
            firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                currentUserId,
                FirebaseUser::class.java
            ).onSuccess { currentUser ->
                Log.d(TAG, "Current user loaded: ${currentUser?.displayName}")
                Log.d(TAG, "Partner ID: ${currentUser?.partnerId}")

                val partnerId = currentUser?.partnerId
                if (partnerId != null) {
                    // Load partner info
                    firestoreRepository.getDocument(
                        FirebaseFirestoreRepository.USERS_COLLECTION,
                        partnerId,
                        FirebaseUser::class.java
                    ).onSuccess { partnerUser ->
                        Log.d(TAG, "Partner loaded: ${partnerUser?.displayName}")
                        _partner.value = partnerUser
                        _isLoading.value = false

                        // Start listening to messages
                        listenToMessages(currentUserId, partnerId)
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to load partner: ${error.message}", error)
                        _error.value = "Không thể tải thông tin partner"
                        _isLoading.value = false
                    }
                } else {
                    Log.w(TAG, "User has no partner")
                    _error.value = "Chưa liên kết với ai"
                    _isLoading.value = false
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to load current user: ${error.message}", error)
                _error.value = "Không thể tải thông tin người dùng"
                _isLoading.value = false
            }
        }
    }

    /**
     * Listen to messages realtime between current user and partner
     */
    private fun listenToMessages(userId: String, partnerId: String) {
        Log.d(TAG, "[CHAT] 🔥 Setting up realtime listener for messages")
        
        // Generate couple ID
        val coupleId = listOf(userId, partnerId).sorted().joinToString("_")
        Log.d(TAG, "[CHAT] Couple ID: $coupleId")

        // Reference to messages in Realtime Database
        messagesRef = realtimeDatabase.getReference("chats/$coupleId/messages")
        
        // Create realtime listener
        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "[CHAT] 📨 Realtime update received, ${snapshot.childrenCount} messages")
                
                val messagesList = mutableListOf<ChatMessage>()
                
                for (messageSnapshot in snapshot.children) {
                    try {
                        val messageId = messageSnapshot.key ?: continue
                        val senderId = messageSnapshot.child("senderId").getValue(String::class.java) ?: continue
                        val message = messageSnapshot.child("message").getValue(String::class.java) ?: ""
                        val messageType = messageSnapshot.child("messageType").getValue(String::class.java) ?: "text"
                        val timestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        val isRead = messageSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                        
                        val receiverId = if (senderId == userId) partnerId else userId
                        val createdAt = Instant.ofEpochMilli(timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                        
                        val chatMessage = ChatMessage(
                            id = messageId,
                            senderId = senderId,
                            receiverId = receiverId,
                            content = message,
                            type = MessageType.valueOf(messageType.uppercase()),
                            createdAt = createdAt,
                            isRead = isRead
                        )
                        
                        messagesList.add(chatMessage)
                    } catch (e: Exception) {
                        Log.e(TAG, "[CHAT] Error parsing message: ${e.message}", e)
                    }
                }
                
                // Sort by timestamp
                val sortedMessages = messagesList.sortedBy { it.createdAt }
                
                // Check for new messages from partner and show notification
                val previousMessages = _messages.value
                val newMessagesFromPartner = sortedMessages.filter { msg ->
                    msg.senderId == partnerId && 
                    !msg.isRead && 
                    previousMessages.none { it.id == msg.id }
                }
                
                // Show notification if:
                // 1. There are new messages from partner
                // 2. App is in background OR user is not in chat screen
                val shouldShowNotification = newMessagesFromPartner.isNotEmpty() && 
                    (!CoupleApplication.isAppInForeground || !isInChatScreen)
                
                if (shouldShowNotification) {
                    val partnerName = _partner.value?.displayName ?: "Người yêu"
                    val latestMessage = newMessagesFromPartner.last()
                    notificationHelper.showMessageNotification(
                        senderName = partnerName,
                        messageText = latestMessage.content,
                        messageCount = newMessagesFromPartner.size
                    )
                    Log.d(TAG, "[CHAT] 🔔 Notification shown: ${newMessagesFromPartner.size} new messages")
                }
                
                _messages.value = sortedMessages
                
                Log.d(TAG, "[CHAT] ✅ Updated ${sortedMessages.size} messages in UI")
                
                // Mark unread messages as read
                markUnreadMessagesAsRead(userId, sortedMessages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "[CHAT] ❌ Realtime listener cancelled: ${error.message}")
                _error.value = "Lỗi kết nối realtime: ${error.message}"
            }
        }
        
        // Attach listener
        messagesRef?.addValueEventListener(messagesListener!!)
        Log.d(TAG, "[CHAT] ✅ Realtime listener attached")
    }

    /**
     * Mark unread messages as read in Realtime Database
     */
    private fun markUnreadMessagesAsRead(currentUserId: String, messages: List<ChatMessage>) {
        val unreadMessages = messages.filter { it.senderId != currentUserId && !it.isRead }
        if (unreadMessages.isEmpty()) return
        
        viewModelScope.launch {
            val coupleId = listOf(currentUserId, _partner.value?.id ?: "").sorted().joinToString("_")
            val messagesRef = realtimeDatabase.getReference("chats/$coupleId/messages")
            
            unreadMessages.forEach { message ->
                messagesRef.child(message.id).child("isRead").setValue(true)
                    .addOnSuccessListener {
                        Log.d(TAG, "[CHAT] Marked message ${message.id} as read")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "[CHAT] Failed to mark message as read: ${e.message}")
                    }
            }
        }
    }



    /**
     * Send a new message via Realtime Database
     */
    fun sendMessage() {
        val text = _messageText.value.trim()
        if (text.isEmpty() || _isSending.value) {
            return
        }

        val currentUserId = authRepository.currentUser?.uid
        val partnerId = _partner.value?.id

        if (currentUserId == null || partnerId == null) {
            Log.e(TAG, "[CHAT] ❌ Cannot send message: missing user or partner")
            return
        }

        _isSending.value = true
        Log.d(TAG, "[CHAT] 📤 Sending message from $currentUserId to $partnerId: $text")

        // Check Firebase Realtime Database connection
        val connectedRef = realtimeDatabase.getReference(".info/connected")
        connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "[CHAT] Firebase connection status: $connected")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "[CHAT] Cannot check connection: ${error.message}")
            }
        })

        // Generate coupleId
        val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
        Log.d(TAG, "[CHAT] Generated coupleId: $coupleId")
        
        val messagesRef = realtimeDatabase.getReference("chats/$coupleId/messages")
        Log.d(TAG, "[CHAT] Firebase path: chats/$coupleId/messages")
        Log.d(TAG, "[CHAT] Database URL: ${realtimeDatabase.reference.toString()}")
        
        // Create new message with auto-generated key
        val newMessageRef = messagesRef.push()
        val messageId = newMessageRef.key
        Log.d(TAG, "[CHAT] Generated message ID: $messageId")
        
        val messageData = mapOf(
            "senderId" to currentUserId,
            "message" to text,
            "messageType" to "text",
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false
        )
        Log.d(TAG, "[CHAT] Message data prepared: $messageData")
        Log.d(TAG, "[CHAT] Calling setValue()...")

        // Timeout handler in case Firebase never responds
        viewModelScope.launch {
            var callbackReceived = false
            
            newMessageRef.setValue(messageData)
                .addOnSuccessListener {
                    callbackReceived = true
                    Log.d(TAG, "[CHAT] ✅ Message sent successfully to Firebase")
                    _messageText.value = ""
                    _isSending.value = false
                }
                .addOnFailureListener { error ->
                    callbackReceived = true
                    Log.e(TAG, "[CHAT] ❌ Failed to send message: ${error.message}", error)
                    Log.e(TAG, "[CHAT] ❌ Error details: ${error.javaClass.name}")
                    _error.value = "Không thể gửi tin nhắn: ${error.message}"
                    _isSending.value = false
                }
            
            Log.d(TAG, "[CHAT] setValue() called, waiting for callback...")
            
            // Wait for timeout
            delay(SEND_TIMEOUT_MS)
            
            if (!callbackReceived) {
                Log.e(TAG, "[CHAT] ⏱️ TIMEOUT: Firebase Realtime Database không phản hồi sau ${SEND_TIMEOUT_MS}ms")
                Log.e(TAG, "[CHAT] ⚠️ Kiểm tra:")
                Log.e(TAG, "[CHAT] 1. Realtime Database đã được tạo trong Firebase Console chưa?")
                Log.e(TAG, "[CHAT] 2. Rules cho phép authenticated user ghi chưa?")
                Log.e(TAG, "[CHAT] 3. Database URL trong google-services.json đúng chưa?")
                _error.value = "Kết nối Firebase timeout. Kiểm tra cấu hình Realtime Database."
                _isSending.value = false
            }
        }
    }

    /**
     * Update message text
     */
    fun onMessageChanged(text: String) {
        _messageText.value = text
    }

    /**
     * Add emoji to message
     */
    fun onEmojiClick(emoji: String) {
        _messageText.value = _messageText.value + emoji
    }

    /**
     * Send emoji as message via Realtime Database
     */
    fun sendEmoji(emoji: String) {
        if (_isSending.value) return

        val currentUserId = authRepository.currentUser?.uid
        val partnerId = _partner.value?.id

        if (currentUserId == null || partnerId == null) {
            Log.e(TAG, "[CHAT] ❌ Cannot send emoji: missing user or partner")
            return
        }

        _isSending.value = true
        Log.d(TAG, "[CHAT] 📤 Sending emoji from $currentUserId to $partnerId: $emoji")

        val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")
        Log.d(TAG, "[CHAT] Generated coupleId for emoji: $coupleId")
        
        val messagesRef = realtimeDatabase.getReference("chats/$coupleId/messages")
        Log.d(TAG, "[CHAT] Firebase path for emoji: chats/$coupleId/messages")
        
        // Create new emoji message
        val newMessageRef = messagesRef.push()
        val messageId = newMessageRef.key
        Log.d(TAG, "[CHAT] Generated emoji message ID: $messageId")
        
        val messageData = mapOf(
            "senderId" to currentUserId,
            "message" to emoji,
            "messageType" to "emoji",
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false
        )
        Log.d(TAG, "[CHAT] Emoji data prepared: $messageData")
        Log.d(TAG, "[CHAT] Calling setValue() for emoji...")

        viewModelScope.launch {
            var callbackReceived = false
            
            newMessageRef.setValue(messageData)
                .addOnSuccessListener {
                    callbackReceived = true
                    Log.d(TAG, "[CHAT] ✅ Emoji sent successfully to Firebase")
                    _isSending.value = false
                }
                .addOnFailureListener { error ->
                    callbackReceived = true
                    Log.e(TAG, "[CHAT] ❌ Failed to send emoji: ${error.message}", error)
                    Log.e(TAG, "[CHAT] ❌ Error details: ${error.javaClass.name}")
                    _error.value = "Không thể gửi emoji: ${error.message}"
                    _isSending.value = false
                }
            
            Log.d(TAG, "[CHAT] setValue() called for emoji, waiting for callback...")
            
            delay(SEND_TIMEOUT_MS)
            
            if (!callbackReceived) {
                Log.e(TAG, "[CHAT] ⏱️ TIMEOUT: Emoji sending timeout after ${SEND_TIMEOUT_MS}ms")
                _error.value = "Kết nối Firebase timeout. Kiểm tra cấu hình Realtime Database."
                _isSending.value = false
            }
        }
    }

    /**
     * Convert Firebase Timestamp to LocalDateTime
     */
    private fun Date.toLocalDateTime(): LocalDateTime {
        return Instant.ofEpochMilli(this.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
}
