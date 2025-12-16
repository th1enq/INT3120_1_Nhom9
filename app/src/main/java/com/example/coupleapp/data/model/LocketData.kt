package com.example.coupleapp.data.model

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
enum class LocketTab(val title: String, val iconName: String) {
    PHOTO("Photo", "photo"),
    EMOJI("Emoji", "emoji"),
    DRAWING("Draw", "drawing"),
    TEXT("Text", "text")
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
