package com.example.coupleapp.data.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Trạng thái liên kết giữa hai người dùng
 */
enum class LinkStatus {
    NOT_LINKED,      // Chưa liên kết với ai
    PENDING_SENT,    // Đã gửi yêu cầu liên kết, đang chờ đối phương chấp nhận
    PENDING_RECEIVED,// Đã nhận yêu cầu liên kết từ người khác
    LINKED           // Đã liên kết thành công
}

/**
 * Thông tin người dùng trong hệ thống
 */
data class PartnerUser(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val nickname: String = "",
    val avatarUrl: String = "",
    val phone: String = "",
    val linkCode: String = generateLinkCode(), // Mã liên kết duy nhất của người dùng
    val createdAt: LocalDateTime = LocalDateTime.now(),
    // Location info
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "", // Tên tỉnh/thành phố
    val lastLocationUpdate: LocalDateTime? = null
) {
    companion object {
        fun generateLinkCode(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..6).map { chars.random() }.joinToString("")
        }
    }
}

/**
 * Yêu cầu liên kết giữa hai người dùng
 */
data class LinkRequest(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val senderAvatar: String = "",
    val receiverId: String,
    val receiverName: String,
    val receiverAvatar: String = "",
    val status: LinkRequestStatus = LinkRequestStatus.PENDING,
    val message: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class LinkRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}

/**
 * Thông tin về cặp đôi đã liên kết
 */
data class PartnerLink(
    val id: String = UUID.randomUUID().toString(),
    val user1Id: String,
    val user2Id: String,
    val linkedAt: LocalDateTime = LocalDateTime.now(),
    val anniversaryDate: LocalDateTime? = null
)

/**
 * Tin nhắn trong cuộc trò chuyện
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val receiverId: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class MessageType {
    TEXT,
    IMAGE,
    EMOJI,
    SYSTEM
}

/**
 * Câu hỏi trong chức năng hỏi đáp
 */
data class QAQuestion(
    val id: String = UUID.randomUUID().toString(),
    val askerId: String,
    val askerName: String,
    val responderId: String,
    val responderName: String,
    val question: String,
    val answer: String? = null,
    val status: QAStatus = QAStatus.PENDING,
    val isApproved: Boolean? = null, // null = chưa đánh giá, true = chấp thuận, false = từ chối
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val answeredAt: LocalDateTime? = null,
    val approvedAt: LocalDateTime? = null
)

enum class QAStatus {
    PENDING,        // Đang chờ trả lời
    ANSWERED,       // Đã trả lời, đang chờ người hỏi đánh giá
    APPROVED,       // Người hỏi đã chấp thuận câu trả lời
    REJECTED,       // Người hỏi đã từ chối câu trả lời
    EXPIRED         // Hết hạn (quá thời gian)
}

/**
 * Shortcut item cho màn hình Partner Hub
 */
data class PartnerShortcut(
    val id: String,
    val name: String,
    val iconName: String,
    val route: String,
    val backgroundColor: Long,
    val iconColor: Long
)

/**
 * State tổng hợp cho Partner Hub
 */
data class PartnerHubState(
    val currentUser: PartnerUser? = null,
    val partner: PartnerUser? = null,
    val linkStatus: LinkStatus = LinkStatus.NOT_LINKED,
    val pendingLinkRequest: LinkRequest? = null,
    val unreadMessageCount: Int = 0,
    val pendingQACount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Distance & Location
    val partnerDistance: String = "", // e.g. "46,6 km"
    val partnerLocationName: String = "" // e.g. "Kim Dong"
)

/**
 * State cho màn hình Chat
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

/**
 * State cho màn hình Q&A
 */
data class QAState(
    val questions: List<QAQuestion> = emptyList(),
    val myQuestions: List<QAQuestion> = emptyList(),
    val questionsForMe: List<QAQuestion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
