package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * ViewModel for Moments screen - Load real data from Firebase
 */
class MomentsViewModel : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    
    private val _uiState = MutableStateFlow(MomentsUiState())
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()
    
    companion object {
        private const val TAG = "MomentsViewModel"
    }
    
    init {
        loadMoments()
    }
    
    /**
     * Load all moments from Firebase data sources
     */
    fun loadMoments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val firebaseUser = authRepository.currentUser
                if (firebaseUser == null) {
                    Log.e(TAG, "User not logged in")
                    _uiState.update { it.copy(isLoading = false, error = "User not logged in") }
                    return@launch
                }
                
                val userId = firebaseUser.uid
                Log.d(TAG, "Loading moments for user: $userId")
                
                // Load current user
                val userResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )
                val currentUser = userResult.getOrNull()
                val coupleId = currentUser?.coupleId
                
                if (coupleId.isNullOrEmpty()) {
                    Log.w(TAG, "User not linked to partner, showing empty moments")
                    _uiState.update { it.copy(isLoading = false, momentsGroups = emptyList()) }
                    return@launch
                }
                
                // Load partner info
                val partnerId = currentUser.partnerId
                val partnerResult = if (partnerId != null) {
                    firestoreRepository.getDocument("users", partnerId, FirebaseUser::class.java)
                } else {
                    Result.failure(Exception("No partner"))
                }
                val partner = partnerResult.getOrNull()
                
                // Collect all moments
                val allMoments = mutableListOf<MomentItem>()
                
                // 1. Load Sleep moments (last 7 days)
                loadSleepMoments(currentUser, partner)?.let { allMoments.addAll(it) }
                
                // 2. Load Missing moments (last 30 days)
                loadMissingMoments(coupleId, currentUser, partner)?.let { allMoments.addAll(it) }
                
                // 3. Load Locket moments (last 30 days)
                loadLocketMoments(coupleId, currentUser, partner)?.let { allMoments.addAll(it) }
                
                // 4. Load Anniversary moments
                loadAnniversaryMoments(currentUser, partner)?.let { allMoments.add(it) }
                
                // 5. Load Upcoming Events
                loadUpcomingEvents(coupleId)?.let { allMoments.addAll(it) }
                
                // Group by date
                val groupedMoments = groupMomentsByDate(allMoments)
                
                Log.d(TAG, "Loaded ${allMoments.size} moments")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        momentsGroups = groupedMoments,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading moments", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    /**
     * Refresh moments data
     */
    fun refreshMoments() {
        loadMoments()
    }
    
    /**
     * Load sleep moments from Firebase
     */
    private suspend fun loadSleepMoments(currentUser: FirebaseUser, partner: FirebaseUser?): List<SleepMoment>? {
        return try {
            val sevenDaysAgo = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)
            
            // Query sleep records from Firestore
            val db = Firebase.firestore
            val sleepSnapshot = db.collection("sleep_records")
                .whereIn("userId", listOfNotNull(currentUser.id, partner?.id))
                .whereGreaterThan("date", sevenDaysAgo)
                .get()
                .await()
            
            sleepSnapshot.documents.mapNotNull { doc ->
                try {
                    val userId = doc.getString("userId") ?: return@mapNotNull null
                    val userName = if (userId == currentUser.id) currentUser.displayName else partner?.displayName ?: "Partner"
                    val dateTimestamp = doc.getTimestamp("date") ?: return@mapNotNull null
                    val bedTimeHour = doc.getLong("bedTimeHour")?.toInt() ?: return@mapNotNull null
                    val bedTimeMinute = doc.getLong("bedTimeMinute")?.toInt() ?: return@mapNotNull null
                    val wakeUpTimeHour = doc.getLong("wakeUpTimeHour")?.toInt() ?: return@mapNotNull null
                    val wakeUpTimeMinute = doc.getLong("wakeUpTimeMinute")?.toInt() ?: return@mapNotNull null
                    val durationMinutes = doc.getLong("sleepDurationMinutes")?.toInt() ?: return@mapNotNull null
                    val qualityStr = doc.getString("quality") ?: "GOOD"
                    val achievementPercentage = doc.getDouble("achievementPercentage")?.toFloat() ?: 0f
                    
                    val date = LocalDateTime.ofInstant(
                        dateTimestamp.toDate().toInstant(),
                        java.time.ZoneId.systemDefault()
                    )
                    
                    val bedTime = LocalTime.of(bedTimeHour, bedTimeMinute)
                    val wakeUpTime = LocalTime.of(wakeUpTimeHour, wakeUpTimeMinute)
                    
                    SleepMoment(
                        id = doc.id,
                        timestamp = date,
                        userName = userName,
                        userAvatar = if (userId == currentUser.id) "😊" else "💕",
                        bedTime = bedTime,
                        wakeUpTime = wakeUpTime,
                        sleepDuration = durationMinutes,
                        quality = SleepQuality.valueOf(qualityStr),
                        achievementPercentage = achievementPercentage
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing sleep record", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sleep moments", e)
            null
        }
    }
    
    /**
     * Load missing moments from Firebase
     */
    private suspend fun loadMissingMoments(coupleId: String, currentUser: FirebaseUser, partner: FirebaseUser?): List<MissingMoment>? {
        return try {
            val db = Firebase.firestore
            
            // Get records from last 30 days
            val thirtyDaysAgo = LocalDate.now().minusDays(30)
            
            val missingSnapshot = db.collection("missing_records")
                .whereEqualTo("coupleId", coupleId)
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()
            
            missingSnapshot.documents.mapNotNull { doc ->
                try {
                    val userId = doc.getString("userId") ?: return@mapNotNull null
                    val dateStr = doc.getString("date") ?: return@mapNotNull null
                    val count = doc.getLong("count")?.toInt() ?: return@mapNotNull null
                    
                    // Only show records with count > 0
                    if (count == 0) return@mapNotNull null
                    
                    // Parse date
                    val date = LocalDate.parse(dateStr)
                    if (date.isBefore(thirtyDaysAgo)) return@mapNotNull null
                    
                    val senderName = if (userId == currentUser.id) currentUser.displayName else partner?.displayName ?: "Partner"
                    val receiverName = if (userId == currentUser.id) partner?.displayName ?: "You" else currentUser.displayName
                    
                    val timestamp = date.atTime(12, 0) // Use noon as timestamp
                    
                    MissingMoment(
                        id = doc.id,
                        timestamp = timestamp,
                        senderName = senderName,
                        senderAvatar = if (userId == currentUser.id) "😊" else "💕",
                        receiverName = receiverName,
                        receiverAvatar = if (userId == currentUser.id) "💕" else "😊",
                        missCount = count
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing missing record", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading missing moments", e)
            null
        }
    }
    
    /**
     * Load locket moments from Firebase
     */
    private suspend fun loadLocketMoments(coupleId: String, currentUser: FirebaseUser, partner: FirebaseUser?): List<LocketMoment>? {
        return try {
            val db = Firebase.firestore
            // Use locket_posts collection (same as LocketFirebaseRepository)
            val locketSnapshot = db.collection("locket_posts")
                .whereEqualTo("coupleId", coupleId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            
            locketSnapshot.documents.mapNotNull { doc ->
                try {
                    val senderId = doc.getString("senderId") ?: return@mapNotNull null
                    val typeStr = doc.getString("type") ?: "text"
                    val photoUrl = doc.getString("photoUrl")
                    val emoji = doc.getString("emoji")
                    val drawingUrl = doc.getString("drawingUrl")
                    val textContent = doc.getString("textContent")
                    val caption = doc.getString("caption")
                    val createdAt = doc.getTimestamp("timestamp") ?: return@mapNotNull null
                    
                    val senderName = if (senderId == currentUser.id) currentUser.displayName else partner?.displayName ?: "Partner"
                    
                    val timestamp = LocalDateTime.ofInstant(
                        createdAt.toDate().toInstant(),
                        java.time.ZoneId.systemDefault()
                    )
                    
                    val locketType = when (typeStr.lowercase()) {
                        "photo" -> LocketType.PHOTO
                        "emoji" -> LocketType.EMOJI
                        "drawing" -> LocketType.DRAWING
                        "text" -> LocketType.TEXT
                        else -> LocketType.TEXT
                    }
                    
                    // Get content based on type
                    val content = when (locketType) {
                        LocketType.PHOTO -> photoUrl ?: ""
                        LocketType.EMOJI -> emoji ?: ""
                        LocketType.DRAWING -> drawingUrl ?: ""
                        LocketType.TEXT -> textContent ?: ""
                    }
                    
                    LocketMoment(
                        id = doc.id,
                        timestamp = timestamp,
                        senderName = senderName,
                        senderAvatar = "💕",
                        locketType = locketType,
                        content = content,
                        caption = caption
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing locket record", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading locket moments", e)
            null
        }
    }
    
    /**
     * Load anniversary moment
     */
    private suspend fun loadAnniversaryMoments(currentUser: FirebaseUser, partner: FirebaseUser?): AnniversaryMoment? {
        return try {
            val coupleId = currentUser.coupleId ?: return null
            val coupleResult = firestoreRepository.getDocument("couples", coupleId, FirebaseCouple::class.java)
            val couple = coupleResult.getOrNull() ?: return null
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anniversaryDate = dateFormat.parse(couple.anniversaryDate) ?: return null
            val today = Date()
            
            val daysTogether = ((today.time - anniversaryDate.time) / (1000 * 60 * 60 * 24))
            val monthsTogether = (daysTogether / 30)
            val yearsTogether = (daysTogether / 365)
            
            if (daysTogether % 100 == 0L || daysTogether % 365 == 0L) {
                AnniversaryMoment(
                    id = "anniversary_$daysTogether",
                    timestamp = LocalDateTime.now(),
                    daysTogether = daysTogether,
                    monthsTogether = monthsTogether,
                    yearsTogether = yearsTogether,
                    user1Name = currentUser.displayName,
                    user1Avatar = "😊",
                    user2Name = partner?.displayName ?: "Partner",
                    user2Avatar = "💕"
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading anniversary", e)
            null
        }
    }
    
    /**
     * Load upcoming events
     */
    private suspend fun loadUpcomingEvents(coupleId: String): List<EventMoment>? {
        return try {
            val db = Firebase.firestore
            val eventsSnapshot = db.collection("calendar_events")
                .whereEqualTo("coupleId", coupleId)
                .whereGreaterThan("eventDate", Date())
                .orderBy("eventDate", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .limit(5)
                .get()
                .await()
            
            eventsSnapshot.documents.mapNotNull { doc ->
                try {
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val description = doc.getString("description")
                    val eventDateMs = doc.getLong("eventDate") ?: return@mapNotNull null
                    val typeStr = doc.getString("type") ?: "OTHER"
                    
                    val eventDate = LocalDate.ofInstant(
                        java.time.Instant.ofEpochMilli(eventDateMs),
                        java.time.ZoneId.systemDefault()
                    )
                    val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), eventDate)
                    
                    EventMoment(
                        id = doc.id,
                        timestamp = LocalDateTime.now().minusDays(daysUntil),
                        title = title,
                        description = description,
                        eventDate = eventDate,
                        eventType = MomentEventType.valueOf(typeStr),
                        daysUntil = daysUntil
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing event", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading events", e)
            null
        }
    }
    
    /**
     * Group moments by date for timeline display
     */
    private fun groupMomentsByDate(moments: List<MomentItem>): List<MomentsGroup> {
        val grouped = moments
            .sortedByDescending { it.timestamp }
            .groupBy { it.timestamp.toLocalDate() }
        
        return grouped.map { (date, items) ->
            MomentsGroup(
                section = TimelineSection.from(date),
                moments = items
            )
        }
    }
    
    /**
     * Generate sample moments data - Fallback for testing
     */
    private fun generateSampleMoments(): List<MomentItem> {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        
        return listOf(
            // Today's sleep
            SleepMoment(
                id = "sleep1",
                timestamp = now.minusHours(6),
                userName = "Banana",
                userAvatar = "🍌",
                bedTime = LocalTime.of(3, 44),
                wakeUpTime = LocalTime.of(10, 31),
                sleepDuration = 406, // 6h 46min
                quality = SleepQuality.EXCELLENT,
                achievementPercentage = 84f
            ),
            
            // Missing sent
            MissingMoment(
                id = "missing1",
                timestamp = now.minusHours(8),
                senderName = "Banana",
                senderAvatar = "🍌",
                receiverName = "Broccoli",
                receiverAvatar = "🥦",
                missCount = 3
            ),
            
            // Locket posted
            LocketMoment(
                id = "locket1",
                timestamp = now.minusHours(10),
                senderName = "Broccoli",
                senderAvatar = "🥦",
                locketType = LocketType.EMOJI,
                content = "❤️",
                caption = "Thinking of you!"
            ),
            
            // Yesterday's sleep
            SleepMoment(
                id = "sleep2",
                timestamp = now.minusDays(1).minusHours(12),
                userName = "Broccoli",
                userAvatar = "🥦",
                bedTime = LocalTime.of(1, 9),
                wakeUpTime = LocalTime.of(7, 31),
                sleepDuration = 381, // 6h 21min
                quality = SleepQuality.GOOD,
                achievementPercentage = 79f
            ),
            
            // Upcoming event
            EventMoment(
                id = "event1",
                timestamp = now.minusDays(1).minusHours(15),
                title = "Valentine's Day",
                description = "Valentine's Day celebration",
                eventDate = today.plusDays(68),
                eventType = MomentEventType.SPECIAL_DAY,
                daysUntil = 68
            ),
            
            // Anniversary
            AnniversaryMoment(
                id = "anniversary1",
                timestamp = now.minusDays(2),
                daysTogether = 365,
                monthsTogether = 12,
                yearsTogether = 1,
                user1Name = "Banana",
                user1Avatar = "🍌",
                user2Name = "Broccoli",
                user2Avatar = "🥦"
            ),
            
            // More locket
            LocketMoment(
                id = "locket2",
                timestamp = now.minusDays(3).minusHours(5),
                senderName = "Banana",
                senderAvatar = "🍌",
                locketType = LocketType.TEXT,
                content = "Good morning! ☀️",
                caption = null
            ),
            
            // More missing
            MissingMoment(
                id = "missing2",
                timestamp = now.minusDays(4).minusHours(2),
                senderName = "Broccoli",
                senderAvatar = "🥦",
                receiverName = "Banana",
                receiverAvatar = "🍌",
                missCount = 5
            ),
            
            // Event
            EventMoment(
                id = "event2",
                timestamp = now.minusDays(5),
                title = "Banana's Birthday",
                description = null,
                eventDate = today.plusDays(30),
                eventType = MomentEventType.BIRTHDAY,
                daysUntil = 30
            ),
            
            // Old sleep
            SleepMoment(
                id = "sleep3",
                timestamp = now.minusDays(6).minusHours(8),
                userName = "Banana",
                userAvatar = "🍌",
                bedTime = LocalTime.of(2, 15),
                wakeUpTime = LocalTime.of(9, 0),
                sleepDuration = 405,
                quality = SleepQuality.GOOD,
                achievementPercentage = 82f
            )
        )
    }
}

/**
 * UI state for Moments screen
 */
data class MomentsUiState(
    val isLoading: Boolean = true,
    val momentsGroups: List<MomentsGroup> = emptyList(),
    val error: String? = null
)
