package com.example.coupleapp.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.LocketRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LocketViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LocketUiState())

    val uiState: StateFlow<LocketUiState> = _uiState
        .debounce(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocketUiState()
        )

    private var loadDataJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val currentUser = LocketRepository.getCurrentUser()
        val partnerUser = LocketRepository.getPartnerUser()

        _uiState.update { currentState ->
            currentState.copy(
                currentUser = currentUser,
                partnerUser = partnerUser
            )
        }

        loadData()
    }

    private fun loadData() {
        loadDataJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }

        loadDataJob = viewModelScope.launch {
            try {
                // Load data immediately (no fake delay)
                val history = LocketRepository.getLocketHistory()
                val emojis = LocketRepository.getEmojis()
                val settings = LocketRepository.getLocketSettings(_uiState.value.currentUser.id)

                _uiState.update { currentState ->
                    currentState.copy(
                        locketHistory = history,
                        emojis = emojis,
                        settings = settings,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Tab navigation
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

    fun setZoomLevel(zoom: Float) {
        _uiState.update { currentState ->
            currentState.copy(
                cameraState = currentState.cameraState.copy(
                    zoomLevel = zoom.coerceIn(1f, 5f)
                )
            )
        }
    }

    fun cycleZoom() {
        val currentZoom = _uiState.value.cameraState.zoomLevel
        val newZoom = when {
            currentZoom < 1.5f -> 2f
            currentZoom < 2.5f -> 3f
            else -> 1f
        }
        setZoomLevel(newZoom)
    }

    // Capture photo
    fun onPhotoCaptured(bitmap: Bitmap?) {
        if (bitmap != null) {
            _uiState.update { it.copy(
                capturedPhoto = bitmap,
                showPreview = true
            )}
        }
    }

    fun clearCapturedPhoto() {
        _uiState.update { it.copy(
            capturedPhoto = null,
            showPreview = false
        )}
    }

    // Emoji selection
    fun selectEmoji(emoji: String) {
        _uiState.update { it.copy(selectedEmoji = emoji) }
    }

    fun showEmojiPicker(show: Boolean) {
        _uiState.update { it.copy(showEmojiPicker = show) }
    }

    // Text input
    fun updateTextContent(text: String) {
        _uiState.update { it.copy(textContent = text) }
    }

    // Drawing
    fun showDrawingScreen(show: Boolean) {
        _uiState.update { it.copy(showDrawingScreen = show) }
    }

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
                delay(500) // Simulate network delay
                
                val state = _uiState.value
                val type = state.selectedTab
                
                val content = when (type) {
                    LocketTab.PHOTO -> "photo_captured"
                    LocketTab.EMOJI -> state.selectedEmoji ?: "❤️"
                    LocketTab.DRAWING -> "drawing_created"
                    LocketTab.TEXT -> state.textContent
                }
                
                val locketType = when (type) {
                    LocketTab.PHOTO -> LocketType.PHOTO
                    LocketTab.EMOJI -> LocketType.EMOJI
                    LocketTab.DRAWING -> LocketType.DRAWING
                    LocketTab.TEXT -> LocketType.TEXT
                }
                
                LocketRepository.sendLocket(locketType, content)
                
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
                
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isSending = false,
                    error = e.message
                )}
            }
        }
    }

    // Pin toggle
    fun togglePinMode(isPinMode: Boolean) {
        _uiState.update { it.copy(isPinMode = isPinMode) }
    }

    // View user history
    fun viewPartnerHistory() {
        _uiState.update { it.copy(showHistory = true) }
    }

    fun hideHistory() {
        _uiState.update { it.copy(showHistory = false) }
    }

    // Settings
    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    // Gallery picker
    fun showGalleryPicker(show: Boolean) {
        _uiState.update { it.copy(showGalleryPicker = show) }
    }

    fun onGalleryImageSelected(uri: String) {
        _uiState.update { it.copy(
            selectedGalleryImage = uri,
            showGalleryPicker = false,
            showPreview = true
        )}
    }

    // Clear error
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getActiveUser(): UserProfile = _uiState.value.currentUser

    fun getPartnerUser(): UserProfile = _uiState.value.partnerUser

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
    }
}

data class LocketUiState(
    val currentUser: UserProfile = UserProfile("", "", null),
    val partnerUser: UserProfile = UserProfile("", "", null),
    val selectedTab: LocketTab = LocketTab.PHOTO,
    val cameraState: CameraState = CameraState(),
    val capturedPhoto: Bitmap? = null,
    val selectedGalleryImage: String? = null,
    val showPreview: Boolean = false,
    val selectedEmoji: String? = null,
    val showEmojiPicker: Boolean = false,
    val textContent: String = "",
    val drawingPaths: List<DrawingPath> = emptyList(),
    val drawingBitmap: Bitmap? = null,
    val showDrawingScreen: Boolean = false,
    val locketHistory: List<LocketPost> = emptyList(),
    val emojis: List<EmojiItem> = emptyList(),
    val settings: LocketSettings = LocketSettings(""),
    val isPinMode: Boolean = true,
    val showHistory: Boolean = false,
    val showSettings: Boolean = false,
    val showGalleryPicker: Boolean = false,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val sendSuccess: Boolean = false,
    val error: String? = null
)
