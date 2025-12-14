package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.ChatMessage
import com.example.coupleapp.data.model.FirebaseChatMessage
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.model.MessageType
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * ViewModel for Chat Screen with Firebase integration
 */
class ChatViewModel : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    companion object {
        private const val TAG = "ChatViewModel"
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
     * Load messages between current user and partner
     */
    private fun listenToMessages(userId: String, partnerId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading messages between $userId and $partnerId")

            // Generate couple ID
            val coupleId = listOf(userId, partnerId).sorted().joinToString("_")
            Log.d(TAG, "Couple ID: $coupleId")

            // Query messages by coupleId
            firestoreRepository.queryDocuments(
                collection = FirebaseFirestoreRepository.MESSAGES_COLLECTION,
                field = "coupleId",
                value = coupleId,
                clazz = FirebaseChatMessage::class.java
            ).onSuccess { firebaseMessages ->
                Log.d(TAG, "Loaded ${firebaseMessages.size} messages")
                
                // Sort by timestamp
                val sortedMessages = firebaseMessages.sortedBy { it.timestamp }
                
                // Convert Firebase messages to ChatMessage
                val chatMessages = sortedMessages.map { firebaseMsg ->
                    val receiverId = if (firebaseMsg.senderId == userId) partnerId else userId
                    ChatMessage(
                        id = firebaseMsg.id,
                        senderId = firebaseMsg.senderId,
                        receiverId = receiverId,
                        content = firebaseMsg.message,
                        type = MessageType.valueOf(firebaseMsg.messageType.uppercase()),
                        createdAt = firebaseMsg.timestamp?.toLocalDateTime() ?: LocalDateTime.now(),
                        isRead = firebaseMsg.isRead
                    )
                }

                _messages.value = chatMessages
                Log.d(TAG, "Converted to ${chatMessages.size} chat messages")

                // Mark unread messages as read
                markMessagesAsRead(userId, sortedMessages)
            }.onFailure { error ->
                Log.e(TAG, "Failed to load messages: ${error.message}", error)
                _error.value = "Không thể tải tin nhắn"
            }
        }
    }

    /**
     * Mark messages as read
     */
    private fun markMessagesAsRead(currentUserId: String, messages: List<FirebaseChatMessage>) {
        viewModelScope.launch {
            messages
                .filter { it.senderId != currentUserId && !it.isRead }
                .forEach { message ->
                    firestoreRepository.updateDocument(
                        FirebaseFirestoreRepository.MESSAGES_COLLECTION,
                        message.id,
                        mapOf("isRead" to true)
                    )
                }
        }
    }

    /**
     * Send a new message
     */
    fun sendMessage() {
        val text = _messageText.value.trim()
        if (text.isEmpty() || _isSending.value) {
            return
        }

        val currentUserId = authRepository.currentUser?.uid
        val partnerId = _partner.value?.id

        if (currentUserId == null || partnerId == null) {
            Log.e(TAG, "Cannot send message: missing user or partner")
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            Log.d(TAG, "Sending message from $currentUserId to $partnerId: $text")

            // Get or create coupleId (for now, use a combined ID)
            val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")

            val message = FirebaseChatMessage(
                coupleId = coupleId,
                senderId = currentUserId,
                message = text,
                messageType = "text",
                isRead = false
            )

            firestoreRepository.addDocument(
                FirebaseFirestoreRepository.MESSAGES_COLLECTION,
                message
            ).onSuccess {
                Log.d(TAG, "Message sent successfully")
                _messageText.value = ""
                _isSending.value = false
            }.onFailure { error ->
                Log.e(TAG, "Failed to send message: ${error.message}", error)
                _error.value = "Không thể gửi tin nhắn"
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
     * Send emoji as message
     */
    fun sendEmoji(emoji: String) {
        if (_isSending.value) return

        val currentUserId = authRepository.currentUser?.uid
        val partnerId = _partner.value?.id

        if (currentUserId == null || partnerId == null) {
            Log.e(TAG, "Cannot send emoji: missing user or partner")
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            Log.d(TAG, "Sending emoji from $currentUserId to $partnerId: $emoji")

            val coupleId = listOf(currentUserId, partnerId).sorted().joinToString("_")

            val message = FirebaseChatMessage(
                coupleId = coupleId,
                senderId = currentUserId,
                message = emoji,
                messageType = "emoji",
                isRead = false
            )

            firestoreRepository.addDocument(
                FirebaseFirestoreRepository.MESSAGES_COLLECTION,
                message
            ).onSuccess {
                Log.d(TAG, "Emoji sent successfully")
                _isSending.value = false
                // Reload messages
                listenToMessages(currentUserId, partnerId)
            }.onFailure { error ->
                Log.e(TAG, "Failed to send emoji: ${error.message}", error)
                _error.value = "Không thể gửi emoji"
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
