package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.FirebaseCouple
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.data.repository.ProfileCacheRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * ViewModel for Profile Screen
 * 
 * Uses LAZY LOADING strategy with ProfileCacheRepository:
 * 1. Show cached data immediately (no loading spinner if cache exists)
 * 2. Background refresh if cache is stale (> 30 minutes)
 * 3. Force refresh on pull-to-refresh
 */
class ProfileViewModel : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    private val profileCache = ProfileCacheRepository.getInstance()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    init {
        Log.d(TAG, "ProfileViewModel initialized")
        loadUserProfile()
    }

    /**
     * Load user profile with cache-first strategy
     * @param forceRefresh If true, skip cache and load from network
     */
    fun loadUserProfile(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            Log.d(TAG, "loadUserProfile() started, forceRefresh=$forceRefresh")
            
            val currentUserId = authRepository.currentUser?.uid
            Log.d(TAG, "Current user ID: $currentUserId")
            
            if (currentUserId == null) {
                Log.e(TAG, "No user logged in!")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "User not logged in"
                )
                return@launch
            }

            // ========== LAZY LOADING: Try cache first ==========
            if (!forceRefresh) {
                val cachedUser = profileCache.getCachedCurrentUser()
                val cachedPartner = profileCache.getCachedPartner()
                val cachedCouple = profileCache.getCachedCouple()
                
                if (cachedUser != null && cachedUser.id == currentUserId) {
                    Log.d(TAG, "✅ Cache hit! Showing cached profile immediately")
                    
                    // Update UI with cached data immediately (no loading spinner!)
                    _uiState.value = _uiState.value.copy(
                        currentUser = cachedUser,
                        partner = cachedPartner,
                        couple = cachedCouple,
                        isLoading = false
                    )
                    
                    // Calculate days together from cached couple (only if anniversaryDate is not empty)
                    if (cachedCouple?.anniversaryDate?.isNotEmpty() == true) {
                        calculateDaysTogether(cachedCouple.anniversaryDate)
                    } else if (cachedCouple == null && cachedUser.coupleId?.isNotEmpty() == true) {
                        // Couple cache is missing but user has coupleId - load couple data
                        Log.d(TAG, "⚠️ Couple cache missing but user has coupleId: ${cachedUser.coupleId}, loading couple...")
                        loadCoupleInfo(cachedUser.coupleId, currentUserId)
                    }
                    
                    // Check if cache needs refresh
                    if (profileCache.needsRefresh(currentUserId)) {
                        Log.d(TAG, "📦 Cache stale, background refresh...")
                        refreshInBackground(currentUserId, cachedUser.coupleId)
                    }
                    return@launch
                }
            }
            
            // ========== No cache or force refresh: Load from network ==========
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadFromNetwork(currentUserId)
        }
    }
    
    /**
     * Load profile data from network and cache it
     */
    private suspend fun loadFromNetwork(currentUserId: String) {
        Log.d(TAG, "Fetching user document: users/$currentUserId")
        firestoreRepository.getDocument(
            FirebaseFirestoreRepository.USERS_COLLECTION,
            currentUserId,
            FirebaseUser::class.java
        ).onSuccess { user ->
            Log.d(TAG, "User document loaded successfully")
            Log.d(TAG, "  displayName: ${user?.displayName}")
            Log.d(TAG, "  email: ${user?.email}")
            Log.d(TAG, "  phoneNumber: ${user?.phoneNumber}")
            Log.d(TAG, "  linkCode: ${user?.linkCode}")
            Log.d(TAG, "  coupleId: ${user?.coupleId}")
            Log.d(TAG, "  partnerId: ${user?.partnerId}")
            
            // Cache user data
            user?.let { profileCache.cacheCurrentUser(it) }
            
            _uiState.value = _uiState.value.copy(
                currentUser = user,
                isLoading = false
            )

            // Load couple info if coupled (this also loads partner info)
            val coupleId = user?.coupleId?.takeIf { it.isNotEmpty() }
            if (coupleId != null) {
                Log.d(TAG, "User has coupleId: $coupleId, loading couple info")
                loadCoupleInfo(coupleId, currentUserId)
            } else {
                // Fallback: load partner info if partnerId exists but no coupleId
                val partnerId = user?.partnerId?.takeIf { it.isNotEmpty() }
                if (partnerId != null) {
                    Log.d(TAG, "No coupleId but has partnerId: $partnerId, loading partner info")
                    loadPartnerInfo(partnerId)
                } else {
                    Log.d(TAG, "No coupleId or partnerId, user is not linked")
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to load user document: ${error.message}", error)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = error.message
            )
        }
    }
    
    /**
     * Refresh profile in background without showing loading spinner
     */
    private fun refreshInBackground(currentUserId: String, coupleIdFromCache: String? = null) {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Background refresh started")
            
            val user = profileCache.loadCurrentUserFromNetwork(currentUserId)
            if (user != null) {
                _uiState.value = _uiState.value.copy(currentUser = user)
                
                // Also refresh partner
                user.partnerId?.let { partnerId ->
                    if (partnerId.isNotEmpty()) {
                        val partner = profileCache.loadPartnerFromNetwork(partnerId)
                        partner?.let { _uiState.value = _uiState.value.copy(partner = it) }
                    }
                }
                
                // Also refresh couple data and recalculate daysTogether
                val coupleId = user.coupleId?.takeIf { it.isNotEmpty() } ?: coupleIdFromCache
                coupleId?.let { cId ->
                    if (cId.isNotEmpty()) {
                        Log.d(TAG, "🔄 Background refreshing couple data: $cId")
                        firestoreRepository.getDocument(
                            FirebaseFirestoreRepository.COUPLES_COLLECTION,
                            cId,
                            FirebaseCouple::class.java
                        ).onSuccess { couple ->
                            couple?.let {
                                profileCache.cacheCouple(it)
                                _uiState.value = _uiState.value.copy(couple = it)
                                // Recalculate days together
                                if (it.anniversaryDate.isNotEmpty()) {
                                    calculateDaysTogether(it.anniversaryDate)
                                    Log.d(TAG, "🔄 Days together recalculated from background refresh")
                                }
                            }
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to refresh couple data: ${error.message}")
                        }
                    }
                }
                
                Log.d(TAG, "✅ Background refresh completed")
            }
        }
    }

    private fun loadCoupleInfo(coupleId: String, currentUserId: String) {
        viewModelScope.launch {
            Log.d(TAG, "loadCoupleInfo() started for coupleId: $coupleId")
            // Load couple data
            firestoreRepository.getDocument(
                FirebaseFirestoreRepository.COUPLES_COLLECTION,
                coupleId,
                FirebaseCouple::class.java
            ).onSuccess { couple ->
                Log.d(TAG, "Couple document loaded successfully")
                Log.d(TAG, "  anniversaryDate: ${couple?.anniversaryDate}")
                Log.d(TAG, "  user1Id: ${couple?.user1Id}")
                Log.d(TAG, "  user2Id: ${couple?.user2Id}")
                
                // Cache couple data
                couple?.let { profileCache.cacheCouple(it) }
                
                _uiState.value = _uiState.value.copy(couple = couple)

                // Calculate days together (only if anniversaryDate is not empty)
                couple?.anniversaryDate?.takeIf { it.isNotEmpty() }?.let { calculateDaysTogether(it) }

                // Load partner info
                val partnerId = if (couple?.user1Id == currentUserId) {
                    couple?.user2Id
                } else {
                    couple?.user1Id
                }

                Log.d(TAG, "Partner ID determined: $partnerId")
                partnerId?.let { loadPartnerInfo(it) }
            }.onFailure { error ->
                Log.e(TAG, "Failed to load couple: ${error.message}", error)
            }
        }
    }
    
    /**
     * Calculate days together from anniversary date string
     * Note: On the first day together (anniversaryDate == today), daysTogether = 1 (not 0)
     */
    private fun calculateDaysTogether(dateString: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val anniversaryDate = LocalDate.parse(dateString, formatter)
            val today = LocalDate.now()
            // Add 1 because the first day counts as "Day 1", not "Day 0"
            val daysTogether = ChronoUnit.DAYS.between(anniversaryDate, today).toInt() + 1
            Log.d(TAG, "Days together calculated: $daysTogether days (from $dateString)")
            _uiState.value = _uiState.value.copy(daysTogether = daysTogether)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse anniversary date: ${e.message}", e)
        }
    }

    private fun loadPartnerInfo(partnerId: String) {
        viewModelScope.launch {
            Log.d(TAG, "loadPartnerInfo() started for partnerId: $partnerId")
            
            // Load and cache partner
            val partner = profileCache.loadPartner(partnerId)
            if (partner != null) {
                Log.d(TAG, "Partner loaded: ${partner.displayName}")
                _uiState.value = _uiState.value.copy(partner = partner)
            } else {
                Log.e(TAG, "Failed to load partner")
            }
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut() called")
        viewModelScope.launch {
            // Clear cache on logout
            profileCache.clearOnLogout()
        }
        authRepository.signOut()
        _uiState.value = ProfileUiState()
        Log.d(TAG, "User signed out, state reset")
    }

    /**
     * Unlink from partner - removes partner connection for both users
     * Also deletes ALL shared data to reduce database load:
     * - Couple document
     * - Locket posts
     * - Chat messages  
     * - Sleep records
     * - Location history
     * - Shared places & photos
     * - Moments
     * - Calendar events
     * - Q&A questions
     * - Link requests
     * - Colocation sessions
     * 
     * @param onSuccess Callback when unlink succeeds
     * @param onError Callback when unlink fails with error message
     */
    fun unlinkPartner(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.currentUser?.uid
                val partnerId = _uiState.value.currentUser?.partnerId
                val coupleId = _uiState.value.currentUser?.coupleId
                
                Log.d(TAG, "unlinkPartner() - currentUserId: $currentUserId, partnerId: $partnerId, coupleId: $coupleId")
                
                if (currentUserId == null) {
                    onError("User not logged in")
                    return@launch
                }
                
                if (partnerId.isNullOrEmpty()) {
                    onError("No partner to unlink")
                    return@launch
                }
                
                // ========== DELETE ALL SHARED DATA ==========
                if (!coupleId.isNullOrEmpty()) {
                    Log.d(TAG, "Deleting all shared data for coupleId: $coupleId")
                    
                    // Delete all shared data in parallel for better performance
                    coroutineScope {
                        val deletionJobs = listOf(
                            // 1. Delete locket posts
                            async {
                                deleteCollectionByCoupleId("locket_posts", coupleId)
                            },
                            // 2. Delete chat messages
                            async {
                                deleteCollectionByCoupleId("messages", coupleId)
                            },
                            // 3. Delete sleep records
                            async {
                                deleteCollectionByCoupleId("sleep_records", coupleId)
                            },
                            // 4. Delete location history
                            async {
                                deleteCollectionByCoupleId("location_history", coupleId)
                            },
                            // 5. Delete current locations
                            async {
                                deleteCollectionByCoupleId("locations", coupleId)
                            },
                            // 6. Delete shared places and their photos
                            async {
                                deleteSharedPlacesAndPhotos(coupleId)
                            },
                            // 7. Delete moments
                            async {
                                deleteCollectionByCoupleId("moments", coupleId)
                            },
                            // 8. Delete calendar events
                            async {
                                deleteCollectionByCoupleId("calendar_events", coupleId)
                            },
                            // 9. Delete Q&A questions
                            async {
                                deleteCollectionByCoupleId("qa_questions", coupleId)
                            },
                            // 10. Delete colocation sessions
                            async {
                                deleteColocationSessions(coupleId)
                            },
                            // 11. Delete couple document
                            async {
                                firestoreRepository.deleteDocument(
                                    FirebaseFirestoreRepository.COUPLES_COLLECTION,
                                    coupleId
                                )
                            }
                        )
                        
                        // Wait for all deletions to complete
                        deletionJobs.awaitAll()
                    }
                    Log.d(TAG, "All shared data deleted successfully")
                }
                
                // Delete link requests between these two users
                deleteLinkRequests(currentUserId, partnerId)
                
                // ========== UPDATE USER DOCUMENTS ==========
                // Remove partnerId and coupleId from current user
                firestoreRepository.updateDocument(
                    FirebaseFirestoreRepository.USERS_COLLECTION,
                    currentUserId,
                    mapOf(
                        "partnerId" to "",
                        "coupleId" to ""
                    )
                ).onFailure { error ->
                    Log.e(TAG, "Failed to update current user", error)
                    onError("Failed to unlink: ${error.message}")
                    return@launch
                }
                
                // Remove partnerId and coupleId from partner
                firestoreRepository.updateDocument(
                    FirebaseFirestoreRepository.USERS_COLLECTION,
                    partnerId,
                    mapOf(
                        "partnerId" to "",
                        "coupleId" to ""
                    )
                ).onFailure { error ->
                    Log.e(TAG, "Failed to update partner", error)
                    // Continue anyway since current user is already updated
                }
                
                // Clear cache
                profileCache.invalidateCache()
                profileCache.clearOnLogout() // Clear all cached data
                
                // Reset UI state
                _uiState.value = _uiState.value.copy(
                    partner = null,
                    couple = null,
                    daysTogether = 0
                )
                
                // Reload profile to get fresh data
                loadUserProfile(forceRefresh = true)
                
                Log.d(TAG, "Partner unlinked successfully - all data deleted")
                onSuccess()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error unlinking partner", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Delete all documents in a collection that match coupleId
     */
    private suspend fun deleteCollectionByCoupleId(collectionName: String, coupleId: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val snapshot = db.collection(collectionName)
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            
            Log.d(TAG, "Deleting ${snapshot.documents.size} documents from $collectionName")
            
            // Delete in batches of 500 (Firestore limit)
            val batch = db.batch()
            var count = 0
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
                count++
                if (count >= 500) {
                    batch.commit().await()
                    count = 0
                }
            }
            if (count > 0) {
                batch.commit().await()
            }
            
            Log.d(TAG, "Deleted all documents from $collectionName for coupleId: $coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting $collectionName: ${e.message}", e)
        }
    }
    
    /**
     * Delete shared places and their associated photos
     */
    private suspend fun deleteSharedPlacesAndPhotos(coupleId: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // First get all shared places
            val placesSnapshot = db.collection("shared_places")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            
            Log.d(TAG, "Found ${placesSnapshot.documents.size} shared places to delete")
            
            // Delete photos for each place
            placesSnapshot.documents.forEach { placeDoc ->
                val placeId = placeDoc.id
                val photosSnapshot = db.collection("shared_place_photos")
                    .whereEqualTo("placeId", placeId)
                    .get()
                    .await()
                
                val batch = db.batch()
                photosSnapshot.documents.forEach { photoDoc ->
                    batch.delete(photoDoc.reference)
                }
                // Also delete the place itself
                batch.delete(placeDoc.reference)
                batch.commit().await()
            }
            
            Log.d(TAG, "Deleted all shared places and photos for coupleId: $coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting shared places: ${e.message}", e)
        }
    }
    
    /**
     * Delete colocation sessions
     */
    private suspend fun deleteColocationSessions(coupleId: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // Delete active session document
            db.collection("colocation_sessions")
                .document("${coupleId}_active")
                .delete()
                .await()
            
            // Delete any other sessions with this coupleId
            val sessionsSnapshot = db.collection("colocation_sessions")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            
            val batch = db.batch()
            sessionsSnapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            
            Log.d(TAG, "Deleted all colocation sessions for coupleId: $coupleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting colocation sessions: ${e.message}", e)
        }
    }
    
    /**
     * Delete link requests between two users
     */
    private suspend fun deleteLinkRequests(userId1: String, userId2: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // Delete requests from user1 to user2
            val requests1 = db.collection("link_requests")
                .whereEqualTo("fromUserId", userId1)
                .whereEqualTo("toUserId", userId2)
                .get()
                .await()
            
            // Delete requests from user2 to user1
            val requests2 = db.collection("link_requests")
                .whereEqualTo("fromUserId", userId2)
                .whereEqualTo("toUserId", userId1)
                .get()
                .await()
            
            val batch = db.batch()
            (requests1.documents + requests2.documents).forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            
            Log.d(TAG, "Deleted all link requests between users")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting link requests: ${e.message}", e)
        }
    }

    /**
     * Force refresh profile (for pull-to-refresh)
     */
    fun refreshProfile() {
        Log.d(TAG, "refreshProfile() called - forcing network refresh")
        viewModelScope.launch {
            profileCache.invalidateCache()
        }
        loadUserProfile(forceRefresh = true)
    }
    
    /**
     * Update user profile
     */
    fun updateProfile(
        displayName: String,
        email: String,
        phoneNumber: String,
        dateOfBirth: String,
        gender: String,
        bio: String,
        profileImageUrl: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.currentUser?.uid
                if (currentUserId == null) {
                    onError("User not logged in")
                    return@launch
                }
                
                val updates = mutableMapOf<String, Any>(
                    "displayName" to displayName,
                    "email" to email,
                    "phoneNumber" to phoneNumber,
                    "dateOfBirth" to dateOfBirth,
                    "gender" to gender,
                    "bio" to bio
                )
                
                profileImageUrl?.let {
                    updates["profileImageUrl"] = it
                }
                
                firestoreRepository.updateDocument(
                    FirebaseFirestoreRepository.USERS_COLLECTION,
                    currentUserId,
                    updates
                ).onSuccess {
                    Log.d(TAG, "Profile updated successfully")
                    // Invalidate cache after profile update
                    profileCache.invalidateCache()
                    loadUserProfile(forceRefresh = true) // Force reload from network
                    onSuccess()
                }.onFailure { error ->
                    Log.e(TAG, "Failed to update profile", error)
                    onError(error.message ?: "Failed to update profile")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Clean up resources when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ProfileViewModel cleared")
    }
}

/**
 * UI State for Profile Screen
 */
data class ProfileUiState(
    val currentUser: FirebaseUser? = null,
    val partner: FirebaseUser? = null,
    val couple: FirebaseCouple? = null,
    val daysTogether: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)
