package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.FirebaseCouple
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * ViewModel for Profile Screen
 */
class ProfileViewModel : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    init {
        Log.d(TAG, "ProfileViewModel initialized")
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            Log.d(TAG, "loadUserProfile() started")
            _uiState.value = _uiState.value.copy(isLoading = true)
            
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

            // Load current user data
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
                
                _uiState.value = _uiState.value.copy(couple = couple)

                // Calculate days together
                couple?.anniversaryDate?.let { dateString ->
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

    private fun loadPartnerInfo(partnerId: String) {
        viewModelScope.launch {
            Log.d(TAG, "loadPartnerInfo() started for partnerId: $partnerId")
            firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                partnerId,
                FirebaseUser::class.java
            ).onSuccess { partner ->
                Log.d(TAG, "Partner document loaded successfully")
                Log.d(TAG, "  displayName: ${partner?.displayName}")
                Log.d(TAG, "  email: ${partner?.email}")
                
                _uiState.value = _uiState.value.copy(partner = partner)
            }.onFailure { error ->
                Log.e(TAG, "Failed to load partner: ${error.message}", error)
            }
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut() called")
        authRepository.signOut()
        _uiState.value = ProfileUiState()
        Log.d(TAG, "User signed out, state reset")
    }

    fun refreshProfile() {
        Log.d(TAG, "refreshProfile() called")
        loadUserProfile()
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
                    loadUserProfile() // Reload profile
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
