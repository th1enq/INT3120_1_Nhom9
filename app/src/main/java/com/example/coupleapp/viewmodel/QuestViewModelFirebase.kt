package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase-integrated ViewModel for Quest Screen
 * Syncs with user_wallets collection for coin balance
 * Saves quest progress to Firebase
 */
class QuestViewModelFirebase : ViewModel() {

    // Initialize repositories inside class to avoid factory issues
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    private val _uiState = MutableStateFlow(QuestUiState())
    val uiState: StateFlow<QuestUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateFormatDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val TAG = "QuestViewModelFirebase"

    init {
        Log.d(TAG, "Initializing QuestViewModelFirebase")
        loadQuestData()
    }

    /**
     * Load quest data including daily quests and user progress from Firebase
     */
    private fun loadQuestData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser?.uid
                if (userId == null) {
                    Log.e(TAG, "No authenticated user found")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Vui lòng đăng nhập"
                        )
                    }
                    return@launch
                }

                Log.d(TAG, "Loading quest data for userId: $userId")

                // Load user wallet from Firebase
                val userCoins = loadUserCoinsFromFirebase(userId)
                Log.d(TAG, "Loaded user coins: $userCoins")

                // Load quest progress from Firebase
                val questProgress = loadQuestProgressFromFirebase(userId)
                Log.d(TAG, "Loaded quest progress: ${questProgress.size} quests")

                // Generate today's quests
                val today = Date()
                val todayString = dateFormat.format(today)
                val todayDisplay = dateFormatDisplay.format(today)

                // Use today's date as seed for consistent daily quests
                val seed = todayString.replace("-", "").toLong()

                // Select 5 random quests from pool with saved progress
                val dailyQuests = selectDailyQuestsWithProgress(seed, questProgress)
                Log.d(TAG, "Generated ${dailyQuests.size} daily quests")

                // Check partner link status
                val isLinked = checkPartnerLinkStatus(userId)
                Log.d(TAG, "Partner link status: $isLinked")

                // Get special quest for unlinked users
                val specialQuest = if (!isLinked) {
                    val specialProgress = questProgress["special_link_partner"]
                    QuestPool.getSpecialLinkPartnerQuest().copy(
                        currentProgress = specialProgress?.currentProgress ?: 0,
                        status = specialProgress?.status ?: QuestStatus.NOT_STARTED
                    )
                } else null

                // Calculate summary
                val summary = calculateDailySummary(dailyQuests)
                Log.d(TAG, "Daily summary: ${summary.completedQuests}/${summary.totalQuests} completed")

                // Load streak
                val streak = loadStreakFromFirebase(userId)
                Log.d(TAG, "Current streak: $streak")

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
                Log.e(TAG, "Failed to load quest data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không thể tải dữ liệu nhiệm vụ: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Load user coins from Firebase user_wallets collection
     */
    private suspend fun loadUserCoinsFromFirebase(userId: String): Int {
        return try {
            val result = firestoreRepository.getDocument(
                collection = "user_wallets",
                documentId = userId,
                clazz = FirebaseUserWallet::class.java
            )

            result.fold(
                onSuccess = { wallet ->
                    if (wallet == null) {
                        Log.d(TAG, "[QUEST] No wallet found at document: user_wallets/$userId")
                        Log.d(TAG, "[QUEST] Creating default wallet with 1000 coins")
                        // Create default wallet with 1000 coins
                        createDefaultWallet(userId)
                        1000
                    } else {
                        Log.d(TAG, "[QUEST] Wallet loaded from user_wallets/$userId")
                        Log.d(TAG, "[QUEST] Wallet has ${wallet.coins} coins")
                        wallet.coins
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error loading wallet: ${e.message}")
                    // Try to create default wallet
                    createDefaultWallet(userId)
                    1000
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading user coins", e)
            1000 // Default value
        }
    }

    /**
     * Create default wallet with 1000 coins
     */
    private suspend fun createDefaultWallet(userId: String): Boolean {
        return try {
            val wallet = FirebaseUserWallet(
                id = userId,
                userId = userId,
                coins = 1000,
                freeCoins = 0,
                lastFreeGiftDate = null,
                updatedAt = Date()
            )
            
            val result = firestoreRepository.setDocument(
                collection = "user_wallets",
                documentId = userId,
                data = wallet,
                merge = false
            )

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Created default wallet for user: $userId")
                    true
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to create default wallet: ${e.message}")
                    false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating default wallet", e)
            false
        }
    }

    /**
     * Load quest progress from Firebase
     */
    private suspend fun loadQuestProgressFromFirebase(userId: String): Map<String, SavedQuestProgress> {
        return try {
            val todayString = dateFormat.format(Date())
            
            val result = firestoreRepository.queryDocuments(
                collection = "quest_progress",
                field = "userId",
                value = userId,
                clazz = FirebaseQuestProgress::class.java
            )

            result.fold(
                onSuccess = { allProgress ->
                    // Filter only today's progress
                    val todayProgress = allProgress.filter { it.date == todayString }
                    
                    Log.d(TAG, "Loaded ${todayProgress.size} quest progress for today")
                    
                    todayProgress.associate { 
                        it.questId to SavedQuestProgress(
                            currentProgress = it.currentProgress,
                            status = QuestStatus.valueOf(it.status)
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error loading quest progress: ${e.message}")
                    emptyMap()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading quest progress", e)
            emptyMap()
        }
    }

    /**
     * Save quest progress to Firebase
     */
    private suspend fun saveQuestProgressToFirebase(quest: Quest) {
        val userId = authRepository.currentUser?.uid ?: return
        val todayString = dateFormat.format(Date())
        
        try {
            val progressData = FirebaseQuestProgress(
                userId = userId,
                questId = quest.id,
                questType = quest.type.name,
                date = todayString,
                currentProgress = quest.currentProgress,
                targetProgress = quest.targetProgress,
                status = quest.status.name
            )

            val documentId = "${userId}_${quest.id}_$todayString"
            
            val result = firestoreRepository.setDocument(
                collection = "quest_progress",
                documentId = documentId,
                data = progressData,
                merge = true
            )

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Saved quest progress for ${quest.id}")
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to save quest progress: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving quest progress", e)
        }
    }

    /**
     * Load current streak from Firebase
     */
    private suspend fun loadStreakFromFirebase(userId: String): Int {
        // TODO: Implement streak tracking in Firebase
        // For now, return default value
        return 3
    }

    /**
     * Check if user has linked with partner
     */
    private suspend fun checkPartnerLinkStatus(userId: String): Boolean {
        return try {
            val result = firestoreRepository.getDocument(
                collection = "users",
                documentId = userId,
                clazz = FirebaseUser::class.java
            )

            result.fold(
                onSuccess = { user ->
                    val hasPartner = !user?.coupleId.isNullOrEmpty()
                    Log.d(TAG, "Partner link status: $hasPartner (coupleId: ${user?.coupleId})")
                    hasPartner
                },
                onFailure = { e ->
                    Log.e(TAG, "Error checking partner link: ${e.message}")
                    false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking partner link", e)
            false
        }
    }

    /**
     * Select daily quests from pool with user progress
     */
    private fun selectDailyQuestsWithProgress(seed: Long, savedProgress: Map<String, SavedQuestProgress>): List<Quest> {
        val selectedQuests = QuestPool.selectDailyQuests(count = 5, seed = seed)

        return selectedQuests.map { quest ->
            val saved = savedProgress[quest.id]
            if (saved != null) {
                quest.copy(
                    currentProgress = saved.currentProgress,
                    status = saved.status
                )
            } else {
                // Auto-complete login quest
                if (quest.type == QuestType.DAILY_LOGIN) {
                    quest.copy(currentProgress = 1, status = QuestStatus.COMPLETED)
                } else {
                    quest
                }
            }
        }
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
            Log.d(TAG, "Updating quest progress: $questType by $progressAmount")
            
            val updatedQuests = _uiState.value.quests.map { quest ->
                if (quest.type == questType && quest.status != QuestStatus.CLAIMED) {
                    val newProgress = (quest.currentProgress + progressAmount).coerceAtMost(quest.targetProgress)
                    val newStatus = when {
                        newProgress >= quest.targetProgress -> QuestStatus.COMPLETED
                        newProgress > 0 -> QuestStatus.IN_PROGRESS
                        else -> QuestStatus.NOT_STARTED
                    }
                    val updatedQuest = quest.copy(currentProgress = newProgress, status = newStatus)
                    
                    // Save to Firebase
                    saveQuestProgressToFirebase(updatedQuest)
                    
                    updatedQuest
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
            
            Log.d(TAG, "Quest progress updated, summary: ${newSummary.completedQuests}/${newSummary.totalQuests}")
        }
    }

    /**
     * Claim reward for completed quest and update Firebase wallet
     */
    fun claimReward(quest: Quest) {
        if (quest.status != QuestStatus.COMPLETED) return

        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Cannot claim reward: no authenticated user")
                return@launch
            }

            Log.d(TAG, "Claiming reward for quest: ${quest.id}, reward: ${quest.reward.coins} coins")

            // Check if it's the special quest
            if (quest.isSpecial) {
                val success = addCoinsToWallet(userId, quest.reward.coins)
                if (success) {
                    val newCoins = _uiState.value.userCoins + quest.reward.coins
                    _uiState.update {
                        it.copy(
                            specialQuest = quest.copy(status = QuestStatus.CLAIMED),
                            userCoins = newCoins,
                            showRewardDialog = true,
                            claimedReward = quest.reward
                        )
                    }
                    // Save special quest progress
                    saveQuestProgressToFirebase(quest.copy(status = QuestStatus.CLAIMED))
                    Log.d(TAG, "Special quest reward claimed successfully, new balance: $newCoins")
                }
                return@launch
            }

            // Regular quest reward
            val updatedQuests = _uiState.value.quests.map {
                if (it.id == quest.id) {
                    it.copy(status = QuestStatus.CLAIMED)
                } else {
                    it
                }
            }

            val success = addCoinsToWallet(userId, quest.reward.coins)
            if (success) {
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

                // Save quest progress to Firebase
                val claimedQuest = updatedQuests.find { it.id == quest.id }
                if (claimedQuest != null) {
                    saveQuestProgressToFirebase(claimedQuest)
                }

                Log.d(TAG, "Quest reward claimed successfully, new balance: $newCoins")
            }
        }
    }

    /**
     * Add coins to user wallet in Firebase
     */
    private suspend fun addCoinsToWallet(userId: String, amount: Int): Boolean {
        return try {
            Log.d(TAG, "Adding $amount coins to wallet for user: $userId")
            
            // Load current wallet
            val result = firestoreRepository.getDocument(
                collection = "user_wallets",
                documentId = userId,
                clazz = FirebaseUserWallet::class.java
            )

            val currentWallet = result.fold(
                onSuccess = { wallet -> wallet },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load wallet: ${e.message}")
                    null
                }
            )

            if (currentWallet == null) {
                Log.d(TAG, "No wallet found, creating new wallet")
                createDefaultWallet(userId)
                return false
            }

            // Update wallet
            val updates = mapOf(
                "coins" to (currentWallet.coins + amount),
                "updatedAt" to com.google.firebase.Timestamp.now()
            )

            val updateResult = firestoreRepository.updateDocument(
                collection = "user_wallets",
                documentId = userId,
                updates = updates
            )

            updateResult.fold(
                onSuccess = {
                    Log.d(TAG, "Wallet updated successfully, added $amount coins")
                    true
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to update wallet: ${e.message}")
                    false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception adding coins to wallet", e)
            false
        }
    }

    /**
     * Claim all completed rewards at once
     */
    fun claimAllRewards() {
        viewModelScope.launch {
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Cannot claim rewards: no authenticated user")
                return@launch
            }

            val completedQuests = _uiState.value.quests.filter { it.status == QuestStatus.COMPLETED }
            if (completedQuests.isEmpty()) {
                Log.d(TAG, "No completed quests to claim")
                return@launch
            }

            val totalCoins = completedQuests.sumOf { it.reward.coins }
            Log.d(TAG, "Claiming all rewards: ${completedQuests.size} quests, $totalCoins coins")

            val success = addCoinsToWallet(userId, totalCoins)
            if (success) {
                val updatedQuests = _uiState.value.quests.map {
                    if (it.status == QuestStatus.COMPLETED) {
                        val claimed = it.copy(status = QuestStatus.CLAIMED)
                        // Save each quest progress
                        saveQuestProgressToFirebase(claimed)
                        claimed
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

                Log.d(TAG, "All rewards claimed successfully, new balance: $newCoins")
            }
        }
    }

    /**
     * Watch ad to complete ad quest
     */
    fun watchAd() {
        viewModelScope.launch {
            // TODO: Implement AdMob logic
            Log.d(TAG, "Watching ad...")
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
            val userId = authRepository.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Cannot show bonus: no authenticated user")
                return@launch
            }

            // Calculate bonus based on streak
            val streakBonus = _uiState.value.currentStreak * 10
            val bonusCoins = 50 + streakBonus
            Log.d(TAG, "Showing bonus reward: $bonusCoins coins")

            val success = addCoinsToWallet(userId, bonusCoins)
            if (success) {
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

                Log.d(TAG, "Bonus reward claimed successfully, new balance: $newCoins")
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
        Log.d(TAG, "Refreshing quest data")
        loadQuestData()
    }

    /**
     * Insert mock quest progress data for testing
     */
    fun insertMockQuestData() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.currentUser
                if (currentUser == null) {
                    Log.e(TAG, "No authenticated user found")
                    _uiState.update {
                        it.copy(errorMessage = "Bạn cần đăng nhập để thêm dữ liệu mẫu")
                    }
                    return@launch
                }

                val userId = currentUser.uid
                val todayString = dateFormat.format(Date())
                
                Log.d(TAG, "Inserting mock quest data for user: $userId, date: $todayString")

                // Get today's selected quests
                val seed = todayString.replace("-", "").toLong()
                val selectedQuests = QuestPool.selectDailyQuests(count = 5, seed = seed)
                
                // Mark first 3 quests as completed (but not claimed)
                selectedQuests.take(3).forEach { quest ->
                    val docId = "${userId}_${todayString}_${quest.id}"
                    val progressData = FirebaseQuestProgress(
                        id = docId,
                        userId = userId,
                        questId = quest.id,
                        questType = quest.type.name,
                        date = todayString,
                        currentProgress = quest.targetProgress,
                        targetProgress = quest.targetProgress,
                        status = "COMPLETED",
                        updatedAt = Date()
                    )
                    
                    firestoreRepository.setDocument(
                        collection = "quest_progress",
                        documentId = docId,
                        data = progressData
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "Mock quest ${quest.id} saved successfully")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to save mock quest ${quest.id}", e)
                        }
                    )
                }
                
                // Mark quest 4 as in progress
                if (selectedQuests.size >= 4) {
                    val quest = selectedQuests[3]
                    val halfProgress = (quest.targetProgress / 2).coerceAtLeast(1)
                    val docId = "${userId}_${todayString}_${quest.id}"
                    val progressData = FirebaseQuestProgress(
                        id = docId,
                        userId = userId,
                        questId = quest.id,
                        questType = quest.type.name,
                        date = todayString,
                        currentProgress = halfProgress,
                        targetProgress = quest.targetProgress,
                        status = "IN_PROGRESS",
                        updatedAt = Date()
                    )
                    
                    firestoreRepository.setDocument(
                        collection = "quest_progress",
                        documentId = docId,
                        data = progressData
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "Mock quest ${quest.id} (in progress) saved successfully")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to save mock quest ${quest.id}", e)
                        }
                    )
                }
                
                Log.d(TAG, "Mock quest data inserted successfully")
                
                // Reload quest data to show the changes
                kotlinx.coroutines.delay(500)
                loadQuestData()
                
                _uiState.update {
                    it.copy(errorMessage = "Đã thêm dữ liệu mẫu nhiệm vụ thành công!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert mock quest data", e)
                _uiState.update {
                    it.copy(errorMessage = "Lỗi khi thêm dữ liệu mẫu: ${e.message}")
                }
            }
        }
    }
}

/**
 * Saved quest progress for restoration
 */
data class SavedQuestProgress(
    val currentProgress: Int,
    val status: QuestStatus
)
