package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.FirebaseLinkRequest
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Link Partner Screen with Firebase integration
 */
class LinkPartnerViewModel : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    companion object {
        private const val TAG = "LinkPartnerViewModel"
    }

    private val _linkCode = MutableStateFlow("")
    val linkCode: StateFlow<String> = _linkCode.asStateFlow()

    private val _myLinkCode = MutableStateFlow("")
    val myLinkCode: StateFlow<String> = _myLinkCode.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _foundUser = MutableStateFlow<FirebaseUser?>(null)
    val foundUser: StateFlow<FirebaseUser?> = _foundUser.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isSendingRequest = MutableStateFlow(false)
    val isSendingRequest: StateFlow<Boolean> = _isSendingRequest.asStateFlow()

    private val _requestSent = MutableStateFlow(false)
    val requestSent: StateFlow<Boolean> = _requestSent.asStateFlow()

    init {
        Log.d(TAG, "LinkPartnerViewModel initialized")
        loadMyLinkCode()
    }

    /**
     * Load current user's link code
     */
    private fun loadMyLinkCode() {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUser?.uid
            if (currentUserId == null) {
                Log.e(TAG, "No user logged in")
                return@launch
            }

            Log.d(TAG, "Loading link code for user: $currentUserId")

            firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                currentUserId,
                FirebaseUser::class.java
            ).onSuccess { user ->
                Log.d(TAG, "User loaded, link code: ${user?.linkCode}")
                _myLinkCode.value = user?.linkCode ?: ""
            }.onFailure { error ->
                Log.e(TAG, "Failed to load user: ${error.message}", error)
            }
        }
    }

    /**
     * Update link code input
     */
    fun onLinkCodeChanged(code: String) {
        _linkCode.value = code.uppercase().take(6)
        _error.value = null
        _foundUser.value = null
    }

    /**
     * Search for user by link code
     */
    fun searchByLinkCode() {
        val code = _linkCode.value.trim()
        if (code.length != 6) {
            _error.value = "Mã liên kết phải có 6 ký tự"
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            _foundUser.value = null

            Log.d(TAG, "Searching for user with link code: $code")

            // Query Firestore for user with this link code
            firestoreRepository.queryDocuments(
                collection = FirebaseFirestoreRepository.USERS_COLLECTION,
                field = "linkCode",
                value = code,
                clazz = FirebaseUser::class.java
            ).onSuccess { users ->
                _isSearching.value = false

                if (users.isEmpty()) {
                    Log.w(TAG, "No user found with link code: $code")
                    _error.value = "Không tìm thấy người dùng với mã này"
                } else {
                    val foundUser = users.first()
                    Log.d(TAG, "Found user: ${foundUser.displayName}")

                    // Check if it's not the current user
                    val currentUserId = authRepository.currentUser?.uid
                    if (foundUser.id == currentUserId) {
                        _error.value = "Bạn không thể liên kết với chính mình"
                        return@onSuccess
                    }

                    // Check if user already has a partner
                    if (foundUser.partnerId != null) {
                        _error.value = "Người dùng này đã có partner"
                        return@onSuccess
                    }

                    _foundUser.value = foundUser
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to search: ${error.message}", error)
                _isSearching.value = false
                _error.value = "Không thể tìm kiếm. Vui lòng thử lại"
            }
        }
    }

    /**
     * Send link request to found user
     */
    fun sendLinkRequest() {
        val partner = _foundUser.value
        if (partner == null) {
            _error.value = "Không tìm thấy người dùng"
            return
        }

        viewModelScope.launch {
            _isSendingRequest.value = true
            _error.value = null

            val currentUserId = authRepository.currentUser?.uid
            if (currentUserId == null) {
                _error.value = "Chưa đăng nhập"
                _isSendingRequest.value = false
                return@launch
            }

            Log.d(TAG, "Sending link request from $currentUserId to ${partner.id}")

            // Get current user info
            firestoreRepository.getDocument(
                FirebaseFirestoreRepository.USERS_COLLECTION,
                currentUserId,
                FirebaseUser::class.java
            ).onSuccess { currentUser ->
                if (currentUser == null) {
                    _error.value = "Không tìm thấy thông tin người dùng"
                    _isSendingRequest.value = false
                    return@onSuccess
                }

                // Create link request instead of directly linking
                val linkRequest = FirebaseLinkRequest(
                    fromUserId = currentUserId,
                    fromUserName = currentUser.displayName,
                    fromUserImageUrl = currentUser.profileImageUrl,
                    toUserId = partner.id,
                    toUserName = partner.displayName ?: "",
                    status = "pending"
                )

                firestoreRepository.addDocument(
                    "link_requests",
                    linkRequest
                ).onSuccess { requestId ->
                    Log.d(TAG, "Link request sent successfully: $requestId")
                    _requestSent.value = true
                    _isSendingRequest.value = false
                }.onFailure { error ->
                    Log.e(TAG, "Failed to send link request: ${error.message}", error)
                    _error.value = "Không thể gửi lời mời. Vui lòng thử lại"
                    _isSendingRequest.value = false
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to get current user: ${error.message}", error)
                _error.value = "Không thể tải thông tin người dùng"
                _isSendingRequest.value = false
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}
