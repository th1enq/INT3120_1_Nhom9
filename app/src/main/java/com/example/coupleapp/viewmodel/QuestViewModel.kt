package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Quest Screen
 * Manages daily quests, progress tracking, and reward claiming
 */
class QuestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QuestUiState())
    val uiState: StateFlow<QuestUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    init {
        loadQuestData()
    }

    /**
     * Load quest data including daily quests and user progress
     */
    private fun loadQuestData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                delay(600) // Simulate loading

                val today = Date()
                val todayString = dateFormat.format(today)
                val todayDisplay = dateFormatDisplay.format(today)
                
                // Use today's date as seed for consistent daily quests
                val seed = todayString.replace("-", "").toLong()
                
                // Select 5 random quests from pool
                val dailyQuests = selectDailyQuestsWithProgress(seed)
                
                val userCoins = loadUserCoins()
                val streak = loadStreak()
                val isLinked = checkPartnerLinkStatus()
                
                // Get special quest for unlinked users
                val specialQuest = if (!isLinked) {
                    QuestPool.getSpecialLinkPartnerQuest()
                } else null
                
                val summary = calculateDailySummary(dailyQuests)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        quests = dailyQuests,
                        specialQuest = specialQuest,
                        dailySummary = summary,
                        userCoins = userCoins,
                        currentStreak = streak,
                        todayDate = todayDisplay,
                        isLinkedWithPartner = isLinked
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không thể tải dữ liệu nhiệm vụ"
                    )
                }
            }
        }
    }

    /**
     * Select daily quests from pool with user progress
     */
    private fun selectDailyQuestsWithProgress(seed: Long): List<Quest> {
        val selectedQuests = QuestPool.selectDailyQuests(count = 5, seed = seed)
        
        // Apply user progress (from storage)
        // For now, auto-complete login quest
        return selectedQuests.map { quest ->
            if (quest.type == QuestType.DAILY_LOGIN) {
                quest.copy(currentProgress = 1, status = QuestStatus.COMPLETED)
            } else {
                quest
            }
        }
    }

    /**
     * Load user coins from storage
     */
    private suspend fun loadUserCoins(): Int {
        // TODO: Load from Firebase/local storage
        return 500
    }

    /**
     * Load current streak
     */
    private suspend fun loadStreak(): Int {
        // TODO: Load from Firebase/local storage
        return 3
    }
    
    /**
     * Check if user has linked with partner
     */
    private suspend fun checkPartnerLinkStatus(): Boolean {
        // TODO: Load from Firebase/local storage
        return false  // Default to false to show special quest
    }

    /**
     * Calculate daily summary from quests
     */
    private fun calculateDailySummary(quests: List<Quest>): DailyQuestSummary {
        val total = quests.size
        val completed = quests.count { it.status == QuestStatus.COMPLETED || it.status == QuestStatus.CLAIMED }
        val claimed = quests.count { it.status == QuestStatus.CLAIMED }
        val coinsEarned = quests.filter { it.status == QuestStatus.CLAIMED }.sumOf { it.reward.coins }
        val totalAvailable = quests.sumOf { it.reward.coins }
        val bonusUnlocked = completed == total && total > 0

        return DailyQuestSummary(
            totalQuests = total,
            completedQuests = completed,
            claimedQuests = claimed,
            totalCoinsEarned = coinsEarned,
            totalCoinsAvailable = totalAvailable,
            bonusRewardUnlocked = bonusUnlocked
        )
    }

    /**
     * Update quest progress (called from other features)
     */
    fun updateQuestProgress(questType: QuestType, progressAmount: Int = 1) {
        viewModelScope.launch {
            val updatedQuests = _uiState.value.quests.map { quest ->
                if (quest.type == questType && quest.status != QuestStatus.CLAIMED) {
                    val newProgress = (quest.currentProgress + progressAmount).coerceAtMost(quest.targetProgress)
                    val newStatus = when {
                        newProgress >= quest.targetProgress -> QuestStatus.COMPLETED
                        newProgress > 0 -> QuestStatus.IN_PROGRESS
                        else -> QuestStatus.NOT_STARTED
                    }
                    quest.copy(currentProgress = newProgress, status = newStatus)
                } else {
                    quest
                }
            }

            val newSummary = calculateDailySummary(updatedQuests)

            _uiState.update {
                it.copy(
                    quests = updatedQuests,
                    dailySummary = newSummary
                )
            }
        }
    }

    /**
     * Claim reward for completed quest
     */
    fun claimReward(quest: Quest) {
        if (quest.status != QuestStatus.COMPLETED) return

        viewModelScope.launch {
            // Check if it's the special quest
            if (quest.isSpecial) {
                val newCoins = _uiState.value.userCoins + quest.reward.coins
                _uiState.update {
                    it.copy(
                        specialQuest = quest.copy(status = QuestStatus.CLAIMED),
                        userCoins = newCoins,
                        showRewardDialog = true,
                        claimedReward = quest.reward
                    )
                }
                return@launch
            }
            
            val updatedQuests = _uiState.value.quests.map {
                if (it.id == quest.id) {
                    it.copy(status = QuestStatus.CLAIMED)
                } else {
                    it
                }
            }

            val newCoins = _uiState.value.userCoins + quest.reward.coins
            val newSummary = calculateDailySummary(updatedQuests)

            _uiState.update {
                it.copy(
                    quests = updatedQuests,
                    userCoins = newCoins,
                    dailySummary = newSummary,
                    showRewardDialog = true,
                    claimedReward = quest.reward
                )
            }
        }
    }

    /**
     * Claim all completed rewards at once
     */
    fun claimAllRewards() {
        viewModelScope.launch {
            val completedQuests = _uiState.value.quests.filter { it.status == QuestStatus.COMPLETED }
            if (completedQuests.isEmpty()) return@launch

            val totalCoins = completedQuests.sumOf { it.reward.coins }

            val updatedQuests = _uiState.value.quests.map {
                if (it.status == QuestStatus.COMPLETED) {
                    it.copy(status = QuestStatus.CLAIMED)
                } else {
                    it
                }
            }

            val newCoins = _uiState.value.userCoins + totalCoins
            val newSummary = calculateDailySummary(updatedQuests)

            _uiState.update {
                it.copy(
                    quests = updatedQuests,
                    userCoins = newCoins,
                    dailySummary = newSummary,
                    showRewardDialog = true,
                    claimedReward = QuestReward(coins = totalCoins)
                )
            }
        }
    }

    /**
     * Watch ad to complete ad quest
     */
    fun watchAd() {
        viewModelScope.launch {
            // TODO: Implement AdMob logic
            delay(100) // Simulate ad completion
            updateQuestProgress(QuestType.WATCH_AD, 1)
        }
    }

    /**
     * Dismiss reward dialog
     */
    fun dismissRewardDialog() {
        _uiState.update {
            it.copy(
                showRewardDialog = false,
                claimedReward = null
            )
        }
    }

    /**
     * Show bonus reward for completing all quests
     */
    fun showBonusReward() {
        if (!_uiState.value.dailySummary.bonusRewardUnlocked) return
        
        viewModelScope.launch {
            // Calculate bonus based on streak
            val streakBonus = _uiState.value.currentStreak * 10
            val bonusCoins = 50 + streakBonus
            
            val newCoins = _uiState.value.userCoins + bonusCoins
            
            _uiState.update {
                it.copy(
                    userCoins = newCoins,
                    showRewardDialog = true,
                    claimedReward = QuestReward(
                        coins = bonusCoins,
                        bonusItem = "Thưởng hoàn thành tất cả!"
                    ),
                    dailySummary = it.dailySummary.copy(bonusRewardUnlocked = false)
                )
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Refresh quest data
     */
    fun refreshQuests() {
        loadQuestData()
    }
}
