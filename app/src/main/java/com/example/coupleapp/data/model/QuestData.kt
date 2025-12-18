package com.example.coupleapp.data.model

import androidx.annotation.DrawableRes
import com.example.coupleapp.R

/**
 * Quest types available in the app
 */
enum class QuestType(val displayName: String, val route: String) {
    WATCH_AD("Xem quảng cáo", ""),
    SEND_LOCKET("Gửi Locket", "locket"),
    SEND_MISSING("Gửi nhớ nhung", "missing"),
    CARE_PLANT("Chăm sóc cây", "store"),
    DAILY_LOGIN("Đăng nhập", ""),
    ADD_CALENDAR_EVENT("Thêm sự kiện", "calendar"),
    SLEEP_TRACKING("Theo dõi giấc ngủ", "sleep_tracker"),
    SHARE_LOCATION("Chia sẻ vị trí", "distance"),
    LINK_PARTNER("Liên kết người yêu", "link_partner")
}

/**
 * Quest status
 */
enum class QuestStatus {
    NOT_STARTED,    // Chưa bắt đầu
    IN_PROGRESS,    // Đang thực hiện
    COMPLETED,      // Đã hoàn thành (chưa nhận xu)
    CLAIMED         // Đã nhận xu
}

/**
 * Quest reward data
 */
data class QuestReward(
    val coins: Int,
    val experiencePoints: Int = 0,
    val bonusItem: String? = null
)

/**
 * Single quest data
 */
data class Quest(
    val id: String,
    val type: QuestType,
    val title: String,
    val vietnameseTitle: String = "",
    val description: String,
    val vietnameseDescription: String = "",
    @DrawableRes val iconRes: Int,
    val reward: QuestReward,
    val currentProgress: Int = 0,
    val targetProgress: Int = 1,
    val status: QuestStatus = QuestStatus.NOT_STARTED,
    val isSpecial: Boolean = false,
    val navigationRoute: String = ""
) {
    val progressPercent: Float
        get() = (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)
    
    val isCompleted: Boolean
        get() = status == QuestStatus.COMPLETED || status == QuestStatus.CLAIMED
    
    val canClaim: Boolean
        get() = status == QuestStatus.COMPLETED
    
    val canNavigate: Boolean
        get() = navigationRoute.isNotEmpty() && status != QuestStatus.CLAIMED
    
    /**
     * Get localized title based on language
     */
    fun getLocalizedTitle(isVietnamese: Boolean): String {
        return if (isVietnamese && vietnameseTitle.isNotBlank()) vietnameseTitle else title
    }
    
    /**
     * Get localized description based on language
     */
    fun getLocalizedDescription(isVietnamese: Boolean): String {
        return if (isVietnamese && vietnameseDescription.isNotBlank()) vietnameseDescription else description
    }
}

/**
 * Daily quest summary
 */
data class DailyQuestSummary(
    val totalQuests: Int,
    val completedQuests: Int,
    val claimedQuests: Int,
    val totalCoinsEarned: Int,
    val totalCoinsAvailable: Int,
    val bonusRewardUnlocked: Boolean = false
) {
    val completionPercent: Float
        get() = if (totalQuests > 0) completedQuests.toFloat() / totalQuests.toFloat() else 0f
    
    val allCompleted: Boolean
        get() = completedQuests == totalQuests
}

/**
 * Quest UI State
 */
data class QuestUiState(
    val isLoading: Boolean = true,
    val quests: List<Quest> = emptyList(),
    val specialQuest: Quest? = null,
    val dailySummary: DailyQuestSummary = DailyQuestSummary(0, 0, 0, 0, 0),
    val userCoins: Int = 0,
    val currentStreak: Int = 0,
    val showRewardDialog: Boolean = false,
    val claimedReward: QuestReward? = null,
    val errorMessage: String? = null,
    val todayDate: String = "",
    val isLinkedWithPartner: Boolean = false
)

/**
 * Quest completion result
 */
sealed class QuestCompletionResult {
    data class Success(val quest: Quest, val coinsEarned: Int) : QuestCompletionResult()
    object AlreadyClaimed : QuestCompletionResult()
    object NotCompleted : QuestCompletionResult()
    data class Error(val message: String) : QuestCompletionResult()
}

/**
 * Quest Pool - All available quests that can be selected daily
 */
object QuestPool {
    
    fun getAllQuests(): List<Quest> = listOf(
        Quest(
            id = "daily_login",
            type = QuestType.DAILY_LOGIN,
            title = "Good Morning!",
            vietnameseTitle = "Chào buổi sáng!",
            description = "Log in to the app daily",
            vietnameseDescription = "Đăng nhập ứng dụng hàng ngày",
            iconRes = R.drawable.sun,
            reward = QuestReward(coins = 50),  // 10 * 5 = 50
            targetProgress = 1,
            navigationRoute = ""
        ),
        Quest(
            id = "watch_ad_1",
            type = QuestType.WATCH_AD,
            title = "Watch Ad",
            vietnameseTitle = "Xem quảng cáo",
            description = "Watch 1 ad to get coins",
            vietnameseDescription = "Xem 1 quảng cáo để nhận xu",
            iconRes = R.drawable.sun,
            reward = QuestReward(coins = 100),  // 20 * 5 = 100
            targetProgress = 1,
            navigationRoute = ""
        ),
        Quest(
            id = "watch_ad_3",
            type = QuestType.WATCH_AD,
            title = "Ad Enthusiast",
            vietnameseTitle = "Fan quảng cáo",
            description = "Watch 3 ads to get coins",
            vietnameseDescription = "Xem 3 quảng cáo để nhận xu",
            iconRes = R.drawable.sun,
            reward = QuestReward(coins = 250),  // 50 * 5 = 250
            targetProgress = 3,
            navigationRoute = ""
        ),
        Quest(
            id = "send_locket",
            type = QuestType.SEND_LOCKET,
            title = "Send Locket",
            vietnameseTitle = "Gửi Locket",
            description = "Send 1 image/emoji/text via Locket",
            vietnameseDescription = "Gửi 1 ảnh/emoji/tin nhắn qua Locket",
            iconRes = R.drawable.locket,
            reward = QuestReward(coins = 75),  // 15 * 5 = 75
            targetProgress = 1,
            navigationRoute = "locket"
        ),
        Quest(
            id = "send_locket_3",
            type = QuestType.SEND_LOCKET,
            title = "Locket Master",
            vietnameseTitle = "Bậc thầy Locket",
            description = "Send 3 contents via Locket",
            vietnameseDescription = "Gửi 3 nội dung qua Locket",
            iconRes = R.drawable.locket,
            reward = QuestReward(coins = 200),  // 40 * 5 = 200
            targetProgress = 3,
            navigationRoute = "locket"
        ),
        Quest(
            id = "send_missing_5",
            type = QuestType.SEND_MISSING,
            title = "Miss Your Love",
            vietnameseTitle = "Nhớ người yêu",
            description = "Tap missing 5 times",
            vietnameseDescription = "Nhấn nút nhớ 5 lần",
            iconRes = R.drawable.background_missing,
            reward = QuestReward(coins = 100),  // 20 * 5 = 100
            targetProgress = 5,
            navigationRoute = "missing"
        ),
        Quest(
            id = "send_missing_10",
            type = QuestType.SEND_MISSING,
            title = "Missing So Much",
            vietnameseTitle = "Nhớ nhiều lắm",
            description = "Tap missing 10 times",
            vietnameseDescription = "Nhấn nút nhớ 10 lần",
            iconRes = R.drawable.background_missing,
            reward = QuestReward(coins = 175),  // 35 * 5 = 175
            targetProgress = 10,
            navigationRoute = "missing"
        ),
        Quest(
            id = "care_plant",
            type = QuestType.CARE_PLANT,
            title = "Care for Plants",
            vietnameseTitle = "Chăm sóc cây",
            description = "Water or fertilize your plant",
            vietnameseDescription = "Tưới nước hoặc bón phân cho cây",
            iconRes = R.drawable.garden,
            reward = QuestReward(coins = 75),  // 15 * 5 = 75
            targetProgress = 1,
            navigationRoute = "store"
        ),
        Quest(
            id = "care_plant_3",
            type = QuestType.CARE_PLANT,
            title = "Green Thumb",
            vietnameseTitle = "Tay làm vườn",
            description = "Care for plants 3 times",
            vietnameseDescription = "Chăm sóc cây 3 lần",
            iconRes = R.drawable.garden,
            reward = QuestReward(coins = 200),  // 40 * 5 = 200
            targetProgress = 3,
            navigationRoute = "store"
        ),
        Quest(
            id = "add_event",
            type = QuestType.ADD_CALENDAR_EVENT,
            title = "Make Plans",
            vietnameseTitle = "Lập kế hoạch",
            description = "Add 1 event to calendar",
            vietnameseDescription = "Thêm 1 sự kiện vào lịch",
            iconRes = R.drawable.calendar,
            reward = QuestReward(coins = 125),  // 25 * 5 = 125
            targetProgress = 1,
            navigationRoute = "calendar"
        ),
        Quest(
            id = "sleep_tracking",
            type = QuestType.SLEEP_TRACKING,
            title = "Track Sleep",
            vietnameseTitle = "Theo dõi giấc ngủ",
            description = "Record your sleep",
            vietnameseDescription = "Ghi lại giấc ngủ của bạn",
            iconRes = R.drawable.sleep_widget,
            reward = QuestReward(coins = 150),  // 30 * 5 = 150
            targetProgress = 1,
            navigationRoute = "sleep_tracker"
        ),
        Quest(
            id = "share_location",
            type = QuestType.SHARE_LOCATION,
            title = "Share Location",
            vietnameseTitle = "Chia sẻ vị trí",
            description = "Share location with your love",
            vietnameseDescription = "Chia sẻ vị trí với người yêu",
            iconRes = R.drawable.distance,
            reward = QuestReward(coins = 100),  // 20 * 5 = 100
            targetProgress = 1,
            navigationRoute = "distance"
        )
    )
    
    fun getSpecialLinkPartnerQuest(): Quest = Quest(
        id = "link_partner",
        type = QuestType.LINK_PARTNER,
        title = "Connect Hearts",
        vietnameseTitle = "Kết nối trái tim",
        description = "Link your account with your partner to unlock all features!",
        vietnameseDescription = "Liên kết tài khoản với người ấy để mở khóa tất cả tính năng!",
        iconRes = R.drawable.background_missing,
        reward = QuestReward(coins = 2500, bonusItem = "Premium Badge"),  // 500 * 5 = 2500
        targetProgress = 1,
        isSpecial = true,
        navigationRoute = "link_partner"
    )
    
    fun selectDailyQuests(count: Int = 5, seed: Long): List<Quest> {
        val allQuests = getAllQuests()
        val random = java.util.Random(seed)
        
        val loginQuest = allQuests.find { it.id == "daily_login" }
        val otherQuests = allQuests.filter { it.id != "daily_login" }.shuffled(random)
        
        val selectedQuests = mutableListOf<Quest>()
        if (loginQuest != null) {
            selectedQuests.add(loginQuest)
        }
        
        selectedQuests.addAll(otherQuests.take(count - selectedQuests.size))
        
        return selectedQuests
    }
}
