package com.example.coupleapp.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.LocketFirebaseRepository
import com.example.coupleapp.data.repository.LocketRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(FlowPreview::class)
class LocketViewModelFirebase : ViewModel() {

    private val locketRepository = LocketFirebaseRepository()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _uiState = MutableStateFlow(LocketUiState())

    val uiState: StateFlow<LocketUiState> = _uiState
        .debounce(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocketUiState()
        )

    private var loadDataJob: Job? = null
    private var receivedLocketsJob: Job? = null
    private var sentLocketsJob: Job? = null

    init {
        loadInitialData()
        observeReceivedLockets()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "User not logged in"
                    )}
                    return@launch
                }
                
                // Load user info
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                val userName = userDoc.getString("displayName") ?: "You"
                val userAvatarUrl = userDoc.getString("profileImageUrl")
                val partnerId = userDoc.getString("partnerId")
                val coupleId = userDoc.getString("coupleId")
                
                // Load partner info
                var partnerName = "Partner"
                var partnerAvatarUrl: String? = null
                
                if (partnerId != null) {
                    val partnerDoc = firestore.collection("users")
                        .document(partnerId)
                        .get()
                        .await()
                    
                    partnerName = partnerDoc.getString("displayName") ?: "Partner"
                    partnerAvatarUrl = partnerDoc.getString("profileImageUrl")
                }
                
                // Load emojis (use mock data for now)
                val emojis = LocketRepository.getEmojis()
                
                _uiState.update { currentState ->
                    currentState.copy(
                        currentUser = UserProfile(currentUser.uid, userName, userAvatarUrl),
                        partnerUser = UserProfile(partnerId ?: "", partnerName, partnerAvatarUrl),
                        emojis = emojis,
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load data"
                )}
            }
        }
    }
    
    /**
     * Observe lockets received from partner (realtime)
     */
    private fun observeReceivedLockets() {
        receivedLocketsJob?.cancel()
        receivedLocketsJob = viewModelScope.launch {
            locketRepository.getReceivedLocketsFlow()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { firebaseLockets ->
                    // Convert Firebase lockets to LocketPost
                    val locketPosts = firebaseLockets.map { firebaseLocket ->
                        convertFirebaseLocketToLocketPost(firebaseLocket)
                    }
                    
                    _uiState.update { it.copy(
                        locketHistory = locketPosts
                    )}
                }
        }
    }
    
    /**
     * Load sent lockets (for history view with both sent and received)
     */
    fun loadAllLockets() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                val coupleId = userDoc.getString("coupleId")
                
                if (coupleId != null) {
                    locketRepository.getAllLocketsForCoupleFlow(coupleId)
                        .catch { e ->
                            _uiState.update { it.copy(
                                isLoading = false,
                                error = e.message
                            )}
                        }
                        .collect { firebaseLockets ->
                            val locketPosts = firebaseLockets.map { firebaseLocket ->
                                convertFirebaseLocketToLocketPost(firebaseLocket)
                            }
                            
                            _uiState.update { it.copy(
                                locketHistory = locketPosts,
                                isLoading = false
                            )}
                        }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message
                )}
            }
        }
    }
    
    /**
     * Convert Firebase locket to app LocketPost model
     */
    private fun convertFirebaseLocketToLocketPost(firebaseLocket: com.example.coupleapp.data.model.FirebaseLocketPost): LocketPost {
        val type = when (firebaseLocket.type) {
            "photo" -> LocketType.PHOTO
            "emoji" -> LocketType.EMOJI
            "drawing" -> LocketType.DRAWING
            "text" -> LocketType.TEXT
            else -> LocketType.PHOTO
        }
        
        val content = when (firebaseLocket.type) {
            "photo" -> firebaseLocket.photoUrl
            "emoji" -> firebaseLocket.emoji
            "drawing" -> firebaseLocket.drawingUrl
            "text" -> firebaseLocket.textContent
            else -> ""
        }
        
        val timestamp = firebaseLocket.timestamp?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()
            ?: LocalDateTime.now()
        
        return LocketPost(
            id = firebaseLocket.id,
            type = type,
            content = content,
            caption = firebaseLocket.caption,
            senderId = firebaseLocket.senderId,
            senderName = firebaseLocket.senderName,
            senderAvatar = firebaseLocket.senderAvatarUrl,
            receiverId = firebaseLocket.receiverId,
            receiverName = firebaseLocket.receiverName,
            timestamp = timestamp,
            isRead = firebaseLocket.isRead
        )
    }

    // Tab selection
    fun selectTab(tab: LocketTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // Camera controls
    fun toggleFlash() {
        _uiState.update { currentState ->
            currentState.copy(
                cameraState = currentState.cameraState.copy(
                    isFlashOn = !currentState.cameraState.isFlashOn
                )
            )
        }
    }

    fun toggleCamera() {
        _uiState.update { currentState ->
            currentState.copy(
                cameraState = currentState.cameraState.copy(
                    isFrontCamera = !currentState.cameraState.isFrontCamera
                )
            )
        }
    }

    fun cycleZoom() {
        _uiState.update { currentState ->
            val currentZoom = currentState.cameraState.zoomLevel
            val newZoom = when (currentZoom) {
                1.0f -> 2.0f
                2.0f -> 3.0f
                else -> 1.0f
            }
            currentState.copy(
                cameraState = currentState.cameraState.copy(zoomLevel = newZoom)
            )
        }
    }

    // Photo handling
    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.update { it.copy(
            capturedPhoto = bitmap,
            showPreview = true
        ) }
    }
    
    fun onGalleryImageSelected(bitmap: Bitmap) {
        _uiState.update { it.copy(
            capturedPhoto = bitmap,
            showPreview = true
        ) }
    }

    fun clearCapturedPhoto() {
        _uiState.update { it.copy(
            capturedPhoto = null,
            showPreview = false
        ) }
    }

    // Emoji handling
    fun selectEmoji(emoji: String) {
        _uiState.update { it.copy(selectedEmoji = emoji) }
    }

    fun showEmojiPicker(show: Boolean) {
        _uiState.update { it.copy(showEmojiPicker = show) }
    }

    // Text handling
    fun updateTextContent(text: String) {
        _uiState.update { it.copy(textContent = text) }
    }

    // Drawing handling
    fun updateDrawingPaths(paths: List<DrawingPath>) {
        _uiState.update { it.copy(drawingPaths = paths) }
    }

    fun setDrawingBitmap(bitmap: Bitmap?) {
        _uiState.update { it.copy(drawingBitmap = bitmap) }
    }

    // Send locket
    fun sendLocket() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            
            try {
                val state = _uiState.value
                val partnerId = state.partnerUser.id
                val partnerName = state.partnerUser.name
                
                if (partnerId.isEmpty()) {
                    _uiState.update { it.copy(
                        isSending = false,
                        error = "No partner connected"
                    )}
                    return@launch
                }
                
                val result = when (state.selectedTab) {
                    LocketTab.PHOTO -> {
                        val bitmap = state.capturedPhoto
                        if (bitmap != null) {
                            locketRepository.sendPhotoLocket(
                                bitmap = bitmap,
                                receiverId = partnerId,
                                receiverName = partnerName
                            )
                        } else {
                            Result.failure(Exception("No photo captured"))
                        }
                    }
                    
                    LocketTab.EMOJI -> {
                        val emoji = state.selectedEmoji
                        if (emoji != null) {
                            locketRepository.sendEmojiLocket(
                                emoji = emoji,
                                receiverId = partnerId,
                                receiverName = partnerName
                            )
                        } else {
                            Result.failure(Exception("No emoji selected"))
                        }
                    }
                    
                    LocketTab.DRAWING -> {
                        val bitmap = state.drawingBitmap
                        if (bitmap != null) {
                            locketRepository.sendDrawingLocket(
                                drawingBitmap = bitmap,
                                receiverId = partnerId,
                                receiverName = partnerName
                            )
                        } else {
                            Result.failure(Exception("No drawing created"))
                        }
                    }
                    
                    LocketTab.TEXT -> {
                        val text = state.textContent.trim()
                        if (text.isNotEmpty()) {
                            locketRepository.sendTextLocket(
                                text = text,
                                receiverId = partnerId,
                                receiverName = partnerName
                            )
                        } else {
                            Result.failure(Exception("Text is empty"))
                        }
                    }
                }
                
                if (result.isSuccess) {
                    // Reset state after sending
                    _uiState.update { currentState ->
                        currentState.copy(
                            isSending = false,
                            capturedPhoto = null,
                            showPreview = false,
                            selectedEmoji = null,
                            textContent = "",
                            drawingPaths = emptyList(),
                            drawingBitmap = null,
                            sendSuccess = true
                        )
                    }
                    
                    // Reset success flag after showing
                    delay(2000)
                    _uiState.update { it.copy(sendSuccess = false) }
                } else {
                    _uiState.update { it.copy(
                        isSending = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to send locket"
                    )}
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isSending = false,
                    error = e.message
                )}
            }
        }
    }
    
    /**
     * Mark a locket as read
     */
    fun markLocketAsRead(locketId: String) {
        viewModelScope.launch {
            locketRepository.markAsRead(locketId)
        }
    }

    // Pin toggle
    fun togglePinMode(isPinMode: Boolean) {
        _uiState.update { it.copy(isPinMode = isPinMode) }
        
        // Load appropriate data based on mode
        if (!isPinMode) {
            loadAllLockets()
        }
    }

    // Settings
    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun showHistory(show: Boolean) {
        _uiState.update { it.copy(showHistory = show) }
    }
    
    override fun onCleared() {
        super.onCleared()
        receivedLocketsJob?.cancel()
        sentLocketsJob?.cancel()
        loadDataJob?.cancel()
    }
}
