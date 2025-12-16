package com.example.coupleapp.data.repository

import com.example.coupleapp.data.model.*
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Mock repository for Locket feature data
 * This provides fake data for UI development
 * Replace with actual repository implementation later
 */
object LocketRepository {

    // Mock users (same as SleepRepository for consistency)
    private val currentUser = UserProfile(
        id = "user1",
        name = "Emma",
        avatarUrl = null
    )

    private val partnerUser = UserProfile(
        id = "user2",
        name = "Alex",
        avatarUrl = null
    )

    // Mock emoji list
    private val mockEmojis = listOf(
        // Smileys
        EmojiItem("😊", "Smiling", EmojiCategory.SMILEYS),
        EmojiItem("😂", "Laughing", EmojiCategory.SMILEYS),
        EmojiItem("🥰", "Love", EmojiCategory.SMILEYS),
        EmojiItem("😘", "Kiss", EmojiCategory.SMILEYS),
        EmojiItem("😍", "Heart Eyes", EmojiCategory.SMILEYS),
        EmojiItem("🤗", "Hugging", EmojiCategory.SMILEYS),
        EmojiItem("😴", "Sleeping", EmojiCategory.SMILEYS),
        EmojiItem("🥺", "Pleading", EmojiCategory.SMILEYS),
        EmojiItem("😭", "Crying", EmojiCategory.SMILEYS),
        EmojiItem("😤", "Angry", EmojiCategory.SMILEYS),
        EmojiItem("🤔", "Thinking", EmojiCategory.SMILEYS),
        EmojiItem("😎", "Cool", EmojiCategory.SMILEYS),
        EmojiItem("🥳", "Party", EmojiCategory.SMILEYS),
        EmojiItem("😜", "Winking", EmojiCategory.SMILEYS),
        EmojiItem("🤪", "Crazy", EmojiCategory.SMILEYS),
        EmojiItem("😇", "Angel", EmojiCategory.SMILEYS),
        EmojiItem("🥶", "Cold", EmojiCategory.SMILEYS),
        EmojiItem("🤒", "Sick", EmojiCategory.SMILEYS),
        EmojiItem("😱", "Shocked", EmojiCategory.SMILEYS),
        EmojiItem("🫠", "Melting", EmojiCategory.SMILEYS),
        
        // Animals
        EmojiItem("🐱", "Cat", EmojiCategory.ANIMALS),
        EmojiItem("🐶", "Dog", EmojiCategory.ANIMALS),
        EmojiItem("🐰", "Bunny", EmojiCategory.ANIMALS),
        EmojiItem("🐻", "Bear", EmojiCategory.ANIMALS),
        EmojiItem("🦊", "Fox", EmojiCategory.ANIMALS),
        EmojiItem("🐼", "Panda", EmojiCategory.ANIMALS),
        EmojiItem("🦋", "Butterfly", EmojiCategory.ANIMALS),
        EmojiItem("🐢", "Turtle", EmojiCategory.ANIMALS),
        
        // Food
        EmojiItem("❤️", "Red Heart", EmojiCategory.SYMBOLS),
        EmojiItem("💕", "Two Hearts", EmojiCategory.SYMBOLS),
        EmojiItem("💖", "Sparkling Heart", EmojiCategory.SYMBOLS),
        EmojiItem("💗", "Growing Heart", EmojiCategory.SYMBOLS),
        EmojiItem("💘", "Heart Arrow", EmojiCategory.SYMBOLS),
        EmojiItem("💝", "Heart Gift", EmojiCategory.SYMBOLS),
        EmojiItem("🌸", "Cherry Blossom", EmojiCategory.SYMBOLS),
        EmojiItem("✨", "Sparkles", EmojiCategory.SYMBOLS)
    )

    // Generate mock history posts
    private fun generateMockHistory(): List<LocketPost> {
        val posts = mutableListOf<LocketPost>()
        val types = LocketType.entries

        // Generate 20 mock posts
        repeat(20) { index ->
            val isFromCurrentUser = Random.nextBoolean()
            val sender = if (isFromCurrentUser) currentUser else partnerUser
            val receiver = if (isFromCurrentUser) partnerUser else currentUser
            val type = types[index % types.size]
            
            val content = when (type) {
                LocketType.PHOTO -> "photo_${index}.jpg"
                LocketType.EMOJI -> mockEmojis.random().emoji
                LocketType.DRAWING -> "drawing_${index}.png"
                LocketType.TEXT -> listOf(
                    "Missing you so much! 💕",
                    "Good night! Sleep well! 😴",
                    "How was your day? 🤗",
                    "Love you so much! ❤️",
                    "Thinking about you... 🥰",
                    "Can't wait to see you! 💖",
                    "Have you eaten yet? 🍚",
                    "Remember to drink water! 💧"
                ).random()
            }
            
            posts.add(
                LocketPost(
                    id = "locket_$index",
                    type = type,
                    content = content,
                    caption = if (Random.nextBoolean() && type == LocketType.PHOTO) 
                        "Caption for photo $index" else null,
                    senderId = sender.id,
                    senderName = sender.name,
                    senderAvatar = sender.avatarUrl,
                    receiverId = receiver.id,
                    receiverName = receiver.name,
                    timestamp = LocalDateTime.now().minusHours(index.toLong() * 2 + Random.nextLong(0, 5)),
                    isRead = index > 3
                )
            )
        }
        
        return posts.sortedByDescending { it.timestamp }
    }

    private val mockHistory = generateMockHistory()

    /**
     * Get current user profile
     */
    fun getCurrentUser(): UserProfile = currentUser

    /**
     * Get partner user profile
     */
    fun getPartnerUser(): UserProfile = partnerUser

    /**
     * Get all emoji items
     */
    fun getEmojis(): List<EmojiItem> = mockEmojis

    /**
     * Get emojis by category
     */
    fun getEmojisByCategory(category: EmojiCategory): List<EmojiItem> {
        return mockEmojis.filter { it.category == category }
    }

    /**
     * Get locket history between current user and partner
     */
    fun getLocketHistory(): List<LocketPost> = mockHistory

    /**
     * Get locket history for specific user
     */
    fun getLocketHistoryForUser(userId: String): List<LocketPost> {
        return mockHistory.filter { 
            it.senderId == userId || it.receiverId == userId 
        }
    }

    /**
     * Get recent locket posts (last 10)
     */
    fun getRecentPosts(limit: Int = 10): List<LocketPost> {
        return mockHistory.take(limit)
    }

    /**
     * Send a new locket post
     */
    fun sendLocket(
        type: LocketType,
        content: String,
        caption: String? = null
    ): LocketPost {
        val newPost = LocketPost(
            id = "locket_${System.currentTimeMillis()}",
            type = type,
            content = content,
            caption = caption,
            senderId = currentUser.id,
            senderName = currentUser.name,
            senderAvatar = currentUser.avatarUrl,
            receiverId = partnerUser.id,
            receiverName = partnerUser.name,
            timestamp = LocalDateTime.now(),
            isRead = false
        )
        // In real implementation, this would save to database
        return newPost
    }

    /**
     * Get locket settings
     */
    fun getLocketSettings(userId: String): LocketSettings {
        return LocketSettings(
            userId = userId,
            notificationsEnabled = true,
            autoSaveToGallery = false
        )
    }

    /**
     * Update locket settings
     */
    fun updateLocketSettings(settings: LocketSettings) {
        // TODO: Implement actual update logic
    }
}
