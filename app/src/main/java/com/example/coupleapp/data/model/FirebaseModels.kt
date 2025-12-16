package com.example.coupleapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Extension function to convert Date to LocalDateTime
 */
fun Date.toLocalDateTime(): LocalDateTime {
    return Instant.ofEpochMilli(this.time)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
}

/**
 * Firebase User Model
 */
data class FirebaseUser(
    @DocumentId
    val id: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val profileImageUrl: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val bio: String = "",
    val coupleId: String? = null,
    val partnerId: String? = null,
    val linkCode: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Firebase Couple Model
 */
data class FirebaseCouple(
    @DocumentId
    val id: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val user1Nickname: String = "",
    val user2Nickname: String = "",
    val anniversaryDate: String = "",
    val relationshipStatus: String = "dating",
    val sharedGardenLevel: Int = 1,
    val sharedPoints: Int = 0,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Firebase Chat Message Model
 */
data class FirebaseChatMessage(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val senderId: String = "",
    val message: String = "",
    val messageType: String = "text", // text, image, sticker
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null
)

/**
 * Firebase Moment Model
 */
data class FirebaseMoment(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val userId: String = "",
    val activityType: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val points: Int = 0,
    @ServerTimestamp
    val timestamp: Date? = null
)

/**
 * Firebase Locket Post Model
 * Supports: photo (from camera/gallery), emoji, drawing, text
 */
data class FirebaseLocketPost(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val type: String = "photo", // photo, emoji, drawing, text
    
    // For photo type
    val photoUrl: String = "",
    
    // For emoji type
    val emoji: String = "",
    
    // For drawing type
    val drawingUrl: String = "",
    
    // For text type
    val textContent: String = "",
    
    // Common fields
    val caption: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null
)

/**
 * Firebase Location Model
 */
data class FirebaseLocation(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val coupleId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)

/**
 * Firebase Shared Place Model
 */
data class FirebaseSharedPlace(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val imageUrls: List<String> = emptyList(),
    val visitCount: Int = 0,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val lastVisited: Date? = null
)

/**
 * Firebase QA Question Model
 */
data class FirebaseQAQuestion(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val askerId: String = "",
    val askerName: String = "",
    val responderId: String = "",
    val responderName: String = "",
    val question: String = "",
    val answer: String? = null,
    val status: String = "pending", // pending, answered, approved, rejected
    val isApproved: Boolean? = null, // null = chưa đánh giá, true = chấp thuận, false = từ chối
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val answeredAt: Date? = null,
    @ServerTimestamp
    val approvedAt: Date? = null
) {
    fun toQAQuestion(): QAQuestion {
        return QAQuestion(
            id = id,
            askerId = askerId,
            askerName = askerName,
            responderId = responderId,
            responderName = responderName,
            question = question,
            answer = answer,
            status = when (status) {
                "pending" -> QAStatus.PENDING
                "answered" -> QAStatus.ANSWERED
                "approved" -> QAStatus.APPROVED
                "rejected" -> QAStatus.REJECTED
                else -> QAStatus.PENDING
            },
            isApproved = isApproved,
            createdAt = createdAt?.toLocalDateTime() ?: java.time.LocalDateTime.now(),
            answeredAt = answeredAt?.toLocalDateTime(),
            approvedAt = approvedAt?.toLocalDateTime()
        )
    }
}

/**
 * Firebase Calendar Event Model
 */
data class FirebaseCalendarEvent(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val title: String = "",
    val description: String = "",
    val date: String = "", // YYYY-MM-DD format
    val time: String = "", // HH:mm format
    val eventType: String = "date", // date, anniversary, birthday, other
    val isRecurring: Boolean = false,
    val reminderMinutes: Int = 60,
    @ServerTimestamp
    val createdAt: Date? = null
)

/**
 * Firebase Link Request Model
 */
data class FirebaseLinkRequest(
    @DocumentId
    val id: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserImageUrl: String = "",
    val toUserId: String = "",
    val toUserName: String = "",
    val status: String = "pending", // pending, accepted, rejected
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val respondedAt: Date? = null
)

/**
 * Firebase Missing Record Model
 * Tracks daily missing/longing counts between partners
 */
data class FirebaseMissingRecord(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val userId: String = "",
    val date: String = "", // YYYY-MM-DD format
    val count: Int = 0,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Firebase User Wallet Model
 * Tracks user coins and points
 */
data class FirebaseUserWallet(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val coins: Int = 0,
    val freeCoins: Int = 0,
    val lastFreeGiftDate: String? = null, // YYYY-MM-DD format
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Firebase Purchase History Model
 */
data class FirebasePurchaseHistory(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemType: String = "",
    val price: Int = 0,
    val purchaseType: String = "", // coin, free, ad
    val quantity: Int = 1,
    @ServerTimestamp
    val purchaseDate: Date? = null
)

/**
 * Firebase Garden Plant Model
 */
data class FirebaseGardenPlant(
    @DocumentId
    val id: String = "",
    val coupleId: String = "",
    val userId: String = "",
    val plantName: String = "",
    val stage: String = "seed", // seed, sprout, growing, blooming, mature
    val rarity: String = "common", // common, rare, super_rare
    val flowerColor: String = "pink", // pink, red, yellow, blue, purple, white
    val sunlight: Float = 100f,
    val water: Float = 100f,
    val health: Float = 100f,
    val isInGreenhouse: Boolean = false,
    val lastWateredAt: Long = 0,
    val lastSunlightAt: Long = 0,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Firebase Garden Inventory Model
 */
data class FirebaseGardenInventory(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val seeds: Int = 0,
    val fertilizer4h: Int = 0,
    val fertilizer8h: Int = 0,
    val fertilizer12h: Int = 0,
    val wateringCan: Int = 0,
    val sunlightBottle: Int = 0,
    val pesticide: Int = 0,
    val scissors: Int = 0,
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Firebase Gallery Item Model
 */
data class FirebaseGalleryItem(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val coupleId: String = "",
    val flowerColor: String = "",
    val rarity: String = "",
    val isUnlocked: Boolean = false,
    @ServerTimestamp
    val unlockedAt: Timestamp? = null
)

/**
 * Firebase Quest Progress Model
 */
data class FirebaseQuestProgress(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val questId: String = "",
    val questType: String = "",
    val date: String = "",
    val currentProgress: Int = 0,
    val targetProgress: Int = 1,
    val status: String = "NOT_STARTED",
    @ServerTimestamp
    val updatedAt: Date? = null
)
