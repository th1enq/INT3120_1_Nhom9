package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.FirebaseCouple
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import com.example.coupleapp.data.repository.ProfileCacheRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
                    
                    // Calculate days together from cached couple
                    cachedCouple?.anniversaryDate?.let { calculateDaysTogether(it) }
                    
                    // Check if cache needs refresh
                    if (profileCache.needsRefresh(currentUserId)) {
                        Log.d(TAG, "📦 Cache stale, background refresh...")
                        refreshInBackground(currentUserId)
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

            // Load partner info from partnerId (new Firebase link system)
            user?.partnerId?.let { partnerId ->
                if (partnerId.isNotEmpty()) {
                    Log.d(TAG, "User has partnerId: $partnerId, loading partner info")
                    loadPartnerInfo(partnerId)
                }
            }

            // Load couple info if coupled (old system for existing couples)
            user?.coupleId?.let { coupleId ->
                Log.d(TAG, "User has coupleId: $coupleId, loading couple info")
                loadCoupleInfo(coupleId, currentUserId)
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
    private fun refreshInBackground(currentUserId: String) {
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

                // Calculate days together
                couple?.anniversaryDate?.let { calculateDaysTogether(it) }

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
     */
    private fun calculateDaysTogether(dateString: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val anniversaryDate = LocalDate.parse(dateString, formatter)
            val today = LocalDate.now()
            val daysTogether = ChronoUnit.DAYS.between(anniversaryDate, today).toInt()
            Log.d(TAG, "Days together calculated: $daysTogether days")
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
