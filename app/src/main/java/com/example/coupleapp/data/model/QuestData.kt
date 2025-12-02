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
    val description: String,
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
            description = "Log in to the app daily",
            iconRes = R.drawable.sun,
            reward = QuestReward(coins = 10),
            targetProgress = 1,
            navigationRoute = ""
        ),
        Quest(
            id = "watch_ad_1",
            type = QuestType.WATCH_AD,
            title = "Watch Ad",
            description = "Watch 1 ad to get coins",
            iconRes = R.drawable.sun,
            reward = QuestReward(coins = 20),
            targetProgress = 1,
            navigationRoute = ""
        ),
        Quest(
            id = "watch_ad_3",
            type = QuestType.WATCH_AD,
            title = "Ad Enthusiast",
            description = "Watch 3 ads to get coins",
            iconRes = R.drawable.sun,
            reward = QuestReward(coins = 50),
            targetProgress = 3,
            navigationRoute = ""
        ),
        Quest(
            id = "send_locket",
            type = QuestType.SEND_LOCKET,
            title = "Send Locket",
            description = "Send 1 image/emoji/text via Locket",
            iconRes = R.drawable.locket,
            reward = QuestReward(coins = 15),
            targetProgress = 1,
            navigationRoute = "locket"
        ),
        Quest(
            id = "send_locket_3",
            type = QuestType.SEND_LOCKET,
            title = "Locket Master",
            description = "Send 3 contents via Locket",
            iconRes = R.drawable.locket,
            reward = QuestReward(coins = 40),
            targetProgress = 3,
            navigationRoute = "locket"
        ),
        Quest(
            id = "send_missing_5",
            type = QuestType.SEND_MISSING,
            title = "Miss Your Love",
            description = "Tap missing 5 times",
            iconRes = R.drawable.background_missing,
            reward = QuestReward(coins = 20),
            targetProgress = 5,
            navigationRoute = "missing"
        ),
        Quest(
            id = "send_missing_10",
            type = QuestType.SEND_MISSING,
            title = "Missing So Much",
            description = "Tap missing 10 times",
            iconRes = R.drawable.background_missing,
            reward = QuestReward(coins = 35),
            targetProgress = 10,
            navigationRoute = "missing"
        ),
        Quest(
            id = "care_plant",
            type = QuestType.CARE_PLANT,
            title = "Care for Plants",
            description = "Water or fertilize your plant",
            iconRes = R.drawable.garden,
            reward = QuestReward(coins = 15),
            targetProgress = 1,
            navigationRoute = "store"
        ),
        Quest(
            id = "care_plant_3",
            type = QuestType.CARE_PLANT,
            title = "Green Thumb",
            description = "Care for plants 3 times",
            iconRes = R.drawable.garden,
            reward = QuestReward(coins = 40),
            targetProgress = 3,
            navigationRoute = "store"
        ),
        Quest(
            id = "add_event",
            type = QuestType.ADD_CALENDAR_EVENT,
            title = "Make Plans",
            description = "Add 1 event to calendar",
            iconRes = R.drawable.calendar,
            reward = QuestReward(coins = 25),
            targetProgress = 1,
            navigationRoute = "calendar"
        ),
        Quest(
            id = "sleep_tracking",
            type = QuestType.SLEEP_TRACKING,
            title = "Track Sleep",
            description = "Record your sleep",
            iconRes = R.drawable.sleep_widget,
            reward = QuestReward(coins = 30),
            targetProgress = 1,
            navigationRoute = "sleep_tracker"
        ),
        Quest(
            id = "share_location",
            type = QuestType.SHARE_LOCATION,
            title = "Share Location",
            description = "Share location with your love",
            iconRes = R.drawable.distance,
            reward = QuestReward(coins = 20),
            targetProgress = 1,
            navigationRoute = "distance"
        )
    )
    
    fun getSpecialLinkPartnerQuest(): Quest = Quest(
        id = "link_partner",
        type = QuestType.LINK_PARTNER,
        title = "Connect Hearts",
        description = "Link your account with your partner to unlock all features!",
        iconRes = R.drawable.background_missing,
        reward = QuestReward(coins = 500, bonusItem = "Premium Badge"),
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
