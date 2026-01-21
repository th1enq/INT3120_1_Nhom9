package com.example.coupleapp.data.model

import android.graphics.Bitmap
import java.time.LocalDateTime

/**
 * Types of Locket posts
 */
enum class LocketType {
    PHOTO,      // Photo from camera or gallery
    EMOJI,      // Emoji emoticon
    DRAWING,    // Drawing
    TEXT        // Text message
}

/**
 * Represents a Locket post
 */
data class LocketPost(
    val id: String,
    val type: LocketType,
    val content: String,        // Photo URL, emoji, drawing path or text
    val caption: String? = null,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String? = null,
    val receiverId: String,
    val receiverName: String,
    val timestamp: LocalDateTime,
    val isRead: Boolean = false
)

/**
 * Drawing data for canvas
 */
data class DrawingPath(
    val points: List<DrawingPoint>,
    val color: Long,
    val strokeWidth: Float
)

data class DrawingPoint(
    val x: Float,
    val y: Float
)

/**
 * Emoji item for picker
 */
data class EmojiItem(
    val emoji: String,
    val name: String,
    val category: EmojiCategory
)

enum class EmojiCategory {
    RECENT,
    SMILEYS,
    ANIMALS,
    FOOD,
    ACTIVITIES,
    OBJECTS,
    SYMBOLS
}

/**
 * Tab items for Locket feature
 */
enum class LocketTab(val title: String, val vietnameseTitle: String, val iconName: String) {
    PHOTO("Photo", "Ảnh", "photo"),
    EMOJI("Emoji", "Biểu tượng", "emoji"),
    DRAWING("Draw", "Vẽ", "drawing"),
    TEXT("Text", "Chữ", "text");
    
    fun getLocalizedTitle(isVietnamese: Boolean): String {
        return if (isVietnamese && vietnameseTitle.isNotEmpty()) vietnameseTitle else title
    }
}

/**
 * Camera state
 */
data class CameraState(
    val isFlashOn: Boolean = false,
    val isFrontCamera: Boolean = true,
    val zoomLevel: Float = 1.0f
)

/**
 * Locket settings
 */
data class LocketSettings(
    val userId: String,
    val notificationsEnabled: Boolean = true,
    val autoSaveToGallery: Boolean = false
)

/**
 * UI State for Locket feature
 */
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
