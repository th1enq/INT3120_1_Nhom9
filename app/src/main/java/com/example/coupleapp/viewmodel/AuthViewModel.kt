package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.data.cache.CacheManager
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.repository.*
import com.example.coupleapp.utils.FCMHelper
import com.example.coupleapp.widget.WidgetManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for Authentication operations with Firebase
 */
class AuthViewModel : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    
    // Auth state listener for auto-detecting login/logout
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        Log.d("AuthViewModel", "Auth state changed: user = ${auth.currentUser?.uid}")
        if (auth.currentUser != null) {
            // User logged in - load data
            loadUserData(auth.currentUser!!.uid)
        } else {
            // User logged out
            _currentUser.value = null
            _authState.value = AuthState.Unauthenticated
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // Register auth state listener for persistent login detection
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        // Also check immediately
        checkAuthStatus()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Remove listener when ViewModel is destroyed
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }
    
    /**
     * Load user data from Firestore
     */
    private fun loadUserData(userId: String) {
        viewModelScope.launch {
            firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                userId,
                FirebaseUser::class.java
            ).onSuccess { user ->
                _currentUser.value = user
                _authState.value = AuthState.Authenticated
                Log.d("AuthViewModel", "User data loaded: ${user?.displayName}")
                
                // Register FCM token for push notifications
                registerFCMToken()
            }.onFailure { error ->
                Log.e("AuthViewModel", "Failed to load user data: ${error.message}")
                // Even if Firestore fails, user is still authenticated
                _authState.value = AuthState.Authenticated
                // Still try to register FCM token
                registerFCMToken()
            }
        }
    }
    
    /**
     * Register FCM token for push notifications
     */
    private fun registerFCMToken() {
        viewModelScope.launch {
            try {
                FCMHelper.registerFCMToken()
                Log.d("AuthViewModel", "FCM token registered")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to register FCM token", e)
            }
        }
    }

    /**
     * Check if user is already logged in
     */
    fun checkAuthStatus() {
        val firebaseUser = authRepository.currentUser
        Log.d("AuthViewModel", "checkAuthStatus: firebaseUser = ${firebaseUser?.uid}")
        if (firebaseUser != null) {
            // Load user data from Firestore
            loadUserData(firebaseUser.uid)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Sign in with email and password
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            authRepository.signInWithEmailAndPassword(email, password)
                .onSuccess { firebaseUser ->
                    // Load user data
                    firestoreRepository.getDocument(
                        FirebaseFirestoreRepository.USERS_COLLECTION,
                        firebaseUser.uid,
                        FirebaseUser::class.java
                    ).onSuccess { user ->
                        _currentUser.value = user
                        _authState.value = AuthState.Authenticated
                    }.onFailure { error ->
                        _authState.value = AuthState.Error(error.message ?: "Không thể tải thông tin người dùng")
                    }
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.message ?: "Đăng nhập thất bại")
                }
        }
    }

    /**
     * Register new user
     */
    fun registerUser(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        dateOfBirth: String,
        gender: String
    ) {
        viewModelScope.launch {
            Log.d("AuthViewModel", "Starting registration for: $email")
            _authState.value = AuthState.Loading

            authRepository.createUserWithEmailAndPassword(email, password)
                .onSuccess { firebaseUser ->
                    Log.d("AuthViewModel", "Auth user created with UID: ${firebaseUser.uid}")
                    Log.d("AuthViewModel", "User authenticated: ${authRepository.isUserLoggedIn}")
                    Log.d("AuthViewModel", "Current user: ${authRepository.currentUser?.uid}")
                    
                    // Generate unique link code
                    val linkCode = generateLinkCode()
                    
                    // Create user document in Firestore
                    val user = FirebaseUser(
                        id = firebaseUser.uid,
                        email = email,
                        phoneNumber = phoneNumber,
                        displayName = fullName,
                        dateOfBirth = dateOfBirth,
                        gender = gender,
                        bio = "",
                        linkCode = linkCode
                    )
                    
                    Log.d("AuthViewModel", "Creating Firestore document for user: ${user.id}")
                    Log.d("AuthViewModel", "Firestore auth state: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid}")
                    
                    // Small delay to ensure auth token propagates
                    kotlinx.coroutines.delay(500)
                    
                    firestoreRepository.setDocument(
                        FirebaseFirestoreRepository.USERS_COLLECTION,
                        firebaseUser.uid,
                        user
                    ).onSuccess {
                        Log.d("AuthViewModel", "User document created successfully")
                        _currentUser.value = user
                        _authState.value = AuthState.Authenticated
                    }.onFailure { error ->
                        Log.e("AuthViewModel", "Failed to create Firestore document: ${error.message}", error)
                        _authState.value = AuthState.Error(error.message ?: "Không thể tạo thông tin người dùng")
                    }
                }
                .onFailure { error ->
                    Log.e("AuthViewModel", "Registration failed: ${error.message}", error)
                    _authState.value = AuthState.Error(error.message ?: "Đăng ký thất bại")
                }
        }
    }

    /**
     * Sign out
     */
    fun signOut() {
        viewModelScope.launch {
            // Unregister FCM token before signing out
            try {
                FCMHelper.unregisterFCMToken()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to unregister FCM token", e)
            }
            
            // Clear all cached data on logout
            try {
                val context = CoupleApplication.instance
                
                // Clear central cache manager
                CacheManager.invalidateAllCache(context)
                Log.d("AuthViewModel", "🗑️ Central cache invalidated")
                
                // Clear all repository caches
                ProfileCacheRepository.getInstance().clearOnLogout()
                QuestCacheRepository.getInstance().clearOnLogout()
                GardenCacheRepository.getInstance().clearOnLogout()
                SleepCacheRepository.getInstance().clearOnLogout()
                StoreCacheRepository.getInstance().clearOnLogout()
                MomentsCacheRepository.getInstance().clearOnLogout()
                CalendarCacheRepository.getInstance().clearOnLogout()
                
                // Clear widget cache and stop observers
                WidgetManager.onUserLoggedOut(context)
                
                Log.d("AuthViewModel", "🗑️ All caches cleared on logout")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error clearing caches on logout", e)
            }
            
            authRepository.signOut()
            _currentUser.value = null
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Generate unique 6-digit link code
     */
    private fun generateLinkCode(): String {
        return (100000..999999).random().toString()
    }

    /**
     * Reset error state
     */
    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Reset to initial state (for Welcome screen)
     */
    fun resetAuthState() {
        _authState.value = AuthState.Initial
        _currentUser.value = null
    }
}

/**
 * Auth state sealed class
 */
sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
