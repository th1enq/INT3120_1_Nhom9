package com.example.coupleapp.data.repository

import com.example.coupleapp.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

/**
 * Repository để quản lý dữ liệu Partner (liên kết, chat, Q&A)
 * TODO: Thay thế bằng Firebase Firestore trong tương lai
 */
class PartnerRepository {
    
    // Mock data cho người dùng hiện tại
    private val _currentUser = MutableStateFlow(
        PartnerUser(
            id = "user1",
            name = "Emma",
            nickname = "Em",
            avatarUrl = "",
            phone = "0123456789",
            linkCode = "ABC123",
            latitude = 21.0285,
            longitude = 105.8542,
            locationName = "Hà Nội"
        )
    )
    val currentUser = _currentUser.asStateFlow()
    
    // Mock data cho partner đã liên kết
    private val _partner = MutableStateFlow<PartnerUser?>(
        PartnerUser(
            id = "user2",
            name = "Broccoli",
            nickname = "Al",
            avatarUrl = "",
            phone = "0987654321",
            linkCode = "XYZ789",
            latitude = 20.8449,
            longitude = 106.3468,
            locationName = "Kim Động"
        )
    )
    val partner = _partner.asStateFlow()
    
    // Khoảng cách và địa điểm partner (mock)
    private val _partnerDistance = MutableStateFlow("46,6 km")
    val partnerDistance = _partnerDistance.asStateFlow()
    
    // Trạng thái liên kết
    private val _linkStatus = MutableStateFlow(LinkStatus.LINKED)
    val linkStatus = _linkStatus.asStateFlow()
    
    // Yêu cầu liên kết đang chờ
    private val _pendingLinkRequest = MutableStateFlow<LinkRequest?>(null)
    val pendingLinkRequest = _pendingLinkRequest.asStateFlow()
    
    // Danh sách tin nhắn
    private val _messages = MutableStateFlow<List<ChatMessage>>(generateMockMessages())
    val messages = _messages.asStateFlow()
    
    // Danh sách câu hỏi Q&A
    private val _qaQuestions = MutableStateFlow<List<QAQuestion>>(generateMockQuestions())
    val qaQuestions = _qaQuestions.asStateFlow()
    
    /**
     * Tìm người dùng bằng link code
     */
    suspend fun findUserByLinkCode(linkCode: String): Result<PartnerUser?> {
        delay(1000) // Simulate network delay
        
        // Mock: Tìm theo link code
        return if (linkCode.uppercase() == "XYZ789") {
            Result.success(
                PartnerUser(
                    id = "user2",
                    name = "Alex",
                    nickname = "Al",
                    avatarUrl = "",
                    linkCode = "XYZ789"
                )
            )
        } else if (linkCode.uppercase() == "DEF456") {
            Result.success(
                PartnerUser(
                    id = "user3",
                    name = "Sarah",
                    nickname = "Sa",
                    avatarUrl = "",
                    linkCode = "DEF456"
                )
            )
        } else {
            Result.success(null)
        }
    }
    
    /**
     * Gửi yêu cầu liên kết
     */
    suspend fun sendLinkRequest(targetUserId: String, message: String = ""): Result<LinkRequest> {
        delay(800)
        
        val currentUser = _currentUser.value
        val request = LinkRequest(
            senderId = currentUser.id,
            senderName = currentUser.name,
            senderAvatar = currentUser.avatarUrl,
            receiverId = targetUserId,
            receiverName = "Alex", // Mock
            receiverAvatar = "",
            message = message
        )
        
        _pendingLinkRequest.value = request
        _linkStatus.value = LinkStatus.PENDING_SENT
        
        return Result.success(request)
    }
    
    /**
     * Chấp nhận yêu cầu liên kết
     */
    suspend fun acceptLinkRequest(requestId: String): Result<Boolean> {
        delay(500)
        
        val request = _pendingLinkRequest.value
        if (request != null && request.id == requestId) {
            _partner.value = PartnerUser(
                id = request.senderId,
                name = request.senderName,
                avatarUrl = request.senderAvatar
            )
            _linkStatus.value = LinkStatus.LINKED
            _pendingLinkRequest.value = null
        }
        
        return Result.success(true)
    }
    
    /**
     * Từ chối yêu cầu liên kết
     */
    suspend fun rejectLinkRequest(requestId: String): Result<Boolean> {
        delay(500)
        
        _pendingLinkRequest.value = null
        _linkStatus.value = LinkStatus.NOT_LINKED
        
        return Result.success(true)
    }
    
    /**
     * Hủy liên kết với partner
     */
    suspend fun unlinkPartner(): Result<Boolean> {
        delay(500)
        
        _partner.value = null
        _linkStatus.value = LinkStatus.NOT_LINKED
        
        return Result.success(true)
    }
    
    /**
     * Gửi tin nhắn
     */
    suspend fun sendMessage(content: String, type: MessageType = MessageType.TEXT, imageUrl: String? = null): Result<ChatMessage> {
        delay(300)
        
        val currentUser = _currentUser.value
        val partner = _partner.value ?: return Result.failure(Exception("Chưa liên kết với ai"))
        
        val message = ChatMessage(
            senderId = currentUser.id,
            receiverId = partner.id,
            content = content,
            type = type,
            imageUrl = imageUrl
        )
        
        _messages.value = _messages.value + message
        
        return Result.success(message)
    }
    
    /**
     * Đánh dấu tin nhắn đã đọc
     */
    suspend fun markMessagesAsRead() {
        delay(100)
        _messages.value = _messages.value.map { 
            if (it.receiverId == _currentUser.value.id && !it.isRead) {
                it.copy(isRead = true)
            } else {
                it
            }
        }
    }
    
    /**
     * Lấy số tin nhắn chưa đọc
     */
    fun getUnreadMessageCount(): Int {
        return _messages.value.count { 
            it.receiverId == _currentUser.value.id && !it.isRead 
        }
    }
    
    /**
     * Tạo câu hỏi mới
     */
    suspend fun createQuestion(question: String): Result<QAQuestion> {
        delay(300)
        
        val currentUser = _currentUser.value
        val partner = _partner.value ?: return Result.failure(Exception("Chưa liên kết với ai"))
        
        val qa = QAQuestion(
            askerId = currentUser.id,
            askerName = currentUser.name,
            responderId = partner.id,
            responderName = partner.name,
            question = question
        )
        
        _qaQuestions.value = listOf(qa) + _qaQuestions.value
        
        return Result.success(qa)
    }
    
    /**
     * Trả lời câu hỏi
     */
    suspend fun answerQuestion(questionId: String, answer: String): Result<QAQuestion> {
        delay(300)
        
        val updatedQuestions = _qaQuestions.value.map { q ->
            if (q.id == questionId) {
                q.copy(
                    answer = answer,
                    status = QAStatus.ANSWERED,
                    answeredAt = LocalDateTime.now()
                )
            } else q
        }
        
        _qaQuestions.value = updatedQuestions
        
        return Result.success(updatedQuestions.find { it.id == questionId }!!)
    }
    
    /**
     * Đánh giá câu trả lời (chấp thuận/từ chối)
     */
    suspend fun evaluateAnswer(questionId: String, isApproved: Boolean): Result<QAQuestion> {
        delay(300)
        
        val updatedQuestions = _qaQuestions.value.map { q ->
            if (q.id == questionId) {
                q.copy(
                    isApproved = isApproved,
                    status = if (isApproved) QAStatus.APPROVED else QAStatus.REJECTED,
                    approvedAt = LocalDateTime.now()
                )
            } else q
        }
        
        _qaQuestions.value = updatedQuestions
        
        return Result.success(updatedQuestions.find { it.id == questionId }!!)
    }
    
    /**
     * Lấy câu hỏi đang chờ trả lời
     */
    fun getPendingQACount(): Int {
        val currentUserId = _currentUser.value.id
        return _qaQuestions.value.count { 
            (it.responderId == currentUserId && it.status == QAStatus.PENDING) ||
            (it.askerId == currentUserId && it.status == QAStatus.ANSWERED)
        }
    }
    
    /**
     * Reset về trạng thái chưa liên kết (để test)
     */
    fun resetToUnlinkedState() {
        _partner.value = null
        _linkStatus.value = LinkStatus.NOT_LINKED
        _pendingLinkRequest.value = null
        _messages.value = emptyList()
        _qaQuestions.value = emptyList()
    }
    
    /**
     * Set về trạng thái đã liên kết (để test)
     */
    fun setLinkedState() {
        _partner.value = PartnerUser(
            id = "user2",
            name = "Alex",
            nickname = "Al",
            avatarUrl = "",
            phone = "0987654321",
            linkCode = "XYZ789"
        )
        _linkStatus.value = LinkStatus.LINKED
        _pendingLinkRequest.value = null
        _messages.value = generateMockMessages()
        _qaQuestions.value = generateMockQuestions()
    }
    
    private fun generateMockMessages(): List<ChatMessage> {
        val user1 = "user1"
        val user2 = "user2"
        val now = LocalDateTime.now()
        
        return listOf(
            ChatMessage(
                senderId = user2,
                receiverId = user1,
                content = "Good morning! 🌸",
                createdAt = now.minusHours(3),
                isRead = true
            ),
            ChatMessage(
                senderId = user1,
                receiverId = user2,
                content = "Morning babe! Did you sleep well?",
                createdAt = now.minusHours(2).minusMinutes(55)
            ),
            ChatMessage(
                senderId = user2,
                receiverId = user1,
                content = "Yes! I dreamed about our trip 💕",
                createdAt = now.minusHours(2).minusMinutes(50),
                isRead = true
            ),
            ChatMessage(
                senderId = user1,
                receiverId = user2,
                content = "Aww that's sweet! Can't wait for it",
                createdAt = now.minusHours(2).minusMinutes(45)
            ),
            ChatMessage(
                senderId = user2,
                receiverId = user1,
                content = "What are you doing now?",
                createdAt = now.minusMinutes(30),
                isRead = false
            ),
            ChatMessage(
                senderId = user2,
                receiverId = user1,
                content = "I miss you 🥺",
                createdAt = now.minusMinutes(5),
                isRead = false
            )
        )
    }
    
    private fun generateMockQuestions(): List<QAQuestion> {
        val user1 = "user1"
        val user2 = "user2"
        val now = LocalDateTime.now()
        
        return listOf(
            QAQuestion(
                askerId = user2,
                askerName = "Alex",
                responderId = user1,
                responderName = "Emma",
                question = "Điều gì khiến em yêu anh ngay từ lần đầu gặp mặt?",
                status = QAStatus.PENDING,
                createdAt = now.minusHours(1)
            ),
            QAQuestion(
                askerId = user1,
                askerName = "Emma",
                responderId = user2,
                responderName = "Alex",
                question = "Kỷ niệm nào của chúng ta khiến anh nhớ nhất?",
                answer = "Lần đầu tiên anh nấu ăn cho em, dù món đó hơi cháy nhưng em vẫn ăn hết 😂",
                status = QAStatus.ANSWERED,
                createdAt = now.minusDays(1),
                answeredAt = now.minusHours(12)
            ),
            QAQuestion(
                askerId = user2,
                askerName = "Alex",
                responderId = user1,
                responderName = "Emma",
                question = "Em muốn đi du lịch ở đâu nhất?",
                answer = "Em muốn đi Nhật Bản vào mùa hoa anh đào với anh! 🌸",
                status = QAStatus.APPROVED,
                isApproved = true,
                createdAt = now.minusDays(3),
                answeredAt = now.minusDays(2),
                approvedAt = now.minusDays(2)
            )
        )
    }
    
    companion object {
        @Volatile
        private var instance: PartnerRepository? = null
        
        fun getInstance(): PartnerRepository {
            return instance ?: synchronized(this) {
                instance ?: PartnerRepository().also { instance = it }
            }
        }
    }
}
