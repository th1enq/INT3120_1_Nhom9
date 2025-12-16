package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel cho Partner Hub - màn hình chính của tab Partner
 * Sử dụng Firebase thay vì mock data
 */
class PartnerHubViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()

    companion object {
        private const val TAG = "PartnerHubViewModel"
    }

    private val _uiState = MutableStateFlow(PartnerHubFirebaseState())
    val uiState: StateFlow<PartnerHubFirebaseState> = _uiState.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<FirebaseLinkRequest>>(emptyList())
    val pendingRequests: StateFlow<List<FirebaseLinkRequest>> = _pendingRequests.asStateFlow()

    // Danh sách shortcuts đến các chức năng trong app
    val shortcuts = listOf(
        PartnerShortcut(
            id = "sleep",
            name = "Sleep",
            iconName = "bedtime",
            route = "sleep_tracker",
            backgroundColor = 0xFFE8F5FF,
            iconColor = 0xFF64B5F6
        ),
        PartnerShortcut(
            id = "locket",
            name = "Locket",
            iconName = "photo_camera",
            route = "locket",
            backgroundColor = 0xFFFFF0F5,
            iconColor = 0xFFFF9ECE
        ),
        PartnerShortcut(
            id = "missing",
            name = "Missing",
            iconName = "favorite",
            route = "missing",
            backgroundColor = 0xFFFFE8E8,
            iconColor = 0xFFFF6B6B
        ),
        PartnerShortcut(
            id = "location",
            name = "Distance",
            iconName = "location_on",
            route = "distance",
            backgroundColor = 0xFFE8FFE8,
            iconColor = 0xFF66BB6A
        ),
        PartnerShortcut(
            id = "calendar",
            name = "Calendar",
            iconName = "event",
            route = "calendar",
            backgroundColor = 0xFFFFF8E1,
            iconColor = 0xFFFFB74D
        ),
        PartnerShortcut(
            id = "garden",
            name = "Garden",
            iconName = "local_florist",
            route = "garden",
            backgroundColor = 0xFFE8F5E9,
            iconColor = 0xFF81C784
        ),
        PartnerShortcut(
            id = "quest",
            name = "Quest",
            iconName = "assignment",
            route = "quest",
            backgroundColor = 0xFFF3E5F5,
            iconColor = 0xFFBA68C8
        ),
        PartnerShortcut(
            id = "store",
            name = "Store",
            iconName = "store",
            route = "store",
            backgroundColor = 0xFFE0F7FA,
            iconColor = 0xFF4DD0E1
        )
    )

    // Q&A Questions flow (sẽ implement sau)
    private val _qaQuestions = MutableStateFlow<List<QAQuestion>>(emptyList())
    val qaQuestions: StateFlow<List<QAQuestion>> = _qaQuestions.asStateFlow()

    init {
        Log.d(TAG, "PartnerHubViewModelFirebase initialized")
        loadData()
        loadPendingRequests()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val firebaseUser = authRepository.currentUser
                if (firebaseUser == null) {
                    Log.e(TAG, "User not logged in")
                    _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
                    return@launch
                }

                val userId = firebaseUser.uid
                Log.d(TAG, "Loading data for user: $userId")

                // Load current user data
                val currentUserResult = firestoreRepository.getDocument(
                    "users", 
                    userId, 
                    com.example.coupleapp.data.model.FirebaseUser::class.java
                )
                
                if (currentUserResult.isFailure) {
                    Log.e(TAG, "Failed to load user document", currentUserResult.exceptionOrNull())
                    _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
                    return@launch
                }

                val currentUser = currentUserResult.getOrNull()
                if (currentUser == null) {
                    Log.e(TAG, "User document is null")
                    _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
                    return@launch
                }

                Log.d(TAG, "Current user loaded: ${currentUser.displayName}, partnerId: ${currentUser.partnerId}")

                // Check if user has partner
                val partnerId = currentUser.partnerId
                if (partnerId.isNullOrEmpty()) {
                    Log.d(TAG, "User has no partner")
                    _uiState.update {
                        it.copy(
                            currentUser = currentUser,
                            partner = null,
                            linkStatus = LinkStatus.NOT_LINKED,
                            isLoading = false,
                            myLinkCode = currentUser.linkCode ?: ""
                        )
                    }
                    return@launch
                }

                // Load partner data
                Log.d(TAG, "Loading partner: $partnerId")
                val partnerResult = firestoreRepository.getDocument(
                    "users", 
                    partnerId, 
                    com.example.coupleapp.data.model.FirebaseUser::class.java
                )
                val partner = partnerResult.getOrNull()

                if (partner != null) {
                    Log.d(TAG, "Partner loaded: ${partner.displayName}")
                    _uiState.update {
                        it.copy(
                            currentUser = currentUser,
                            partner = partner,
                            linkStatus = LinkStatus.LINKED,
                            isLoading = false,
                            myLinkCode = currentUser.linkCode ?: ""
                        )
                    }
                } else {
                    Log.e(TAG, "Partner document not found")
                    _uiState.update {
                        it.copy(
                            currentUser = currentUser,
                            partner = null,
                            linkStatus = LinkStatus.NOT_LINKED,
                            isLoading = false,
                            myLinkCode = currentUser.linkCode ?: ""
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
                _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
            }
        }
    }

    fun refreshData() {
        Log.d(TAG, "Refreshing data")
        loadData()
    }

    // Q&A Functions (sẽ implement sau khi có Firestore collection cho Q&A)
    fun createQuestion(question: String) {
        viewModelScope.launch {
            // TODO: Implement create question in Firestore
            Log.d(TAG, "Create question: $question")
        }
    }

    fun answerQuestion(questionId: String, answer: String) {
        viewModelScope.launch {
            // TODO: Implement answer question in Firestore
            Log.d(TAG, "Answer question $questionId: $answer")
        }
    }

    fun approveAnswer(questionId: String) {
        viewModelScope.launch {
            // TODO: Implement approve answer in Firestore
            Log.d(TAG, "Approve answer: $questionId")
        }
    }

    fun rejectAnswer(questionId: String, comment: String = "") {
        viewModelScope.launch {
            // TODO: Implement reject answer in Firestore
            Log.d(TAG, "Reject answer $questionId: $comment")
        }
    }

    fun unlinkPartner() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val firebaseUser = authRepository.currentUser
                val userId = firebaseUser?.uid
                val partnerId = _uiState.value.partner?.id

                if (userId != null && partnerId != null) {
                    Log.d(TAG, "Unlinking partner: $partnerId")

                    // Remove partnerId from both users
                    firestoreRepository.updateDocument(
                        "users",
                        userId,
                        mapOf("partnerId" to "")
                    )

                    firestoreRepository.updateDocument(
                        "users",
                        partnerId,
                        mapOf("partnerId" to "")
                    )

                    Log.d(TAG, "Partner unlinked successfully")
                    loadData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unlinking partner", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Load pending link requests for current user
     */
    private fun loadPendingRequests() {
        viewModelScope.launch {
            try {
                val firebaseUser = authRepository.currentUser
                val userId = firebaseUser?.uid ?: return@launch

                Log.d(TAG, "Loading pending requests for user: $userId")

                // Query link requests where toUserId = currentUserId and status = pending
                val result = firestoreRepository.queryDocuments(
                    "link_requests",
                    "toUserId",
                    userId,
                    FirebaseLinkRequest::class.java
                )

                result.onSuccess { allRequests ->
                    val pendingRequests = allRequests.filter { it.status == "pending" }
                    _pendingRequests.value = pendingRequests
                    Log.d(TAG, "Found ${pendingRequests.size} pending requests")

                    // Update UI state based on pending requests
                    if (pendingRequests.isNotEmpty() && _uiState.value.linkStatus == LinkStatus.NOT_LINKED) {
                        _uiState.update { it.copy(linkStatus = LinkStatus.PENDING_RECEIVED) }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to load pending requests", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pending requests", e)
            }
        }
    }

    /**
     * Accept link request
     */
    fun acceptLinkRequest(request: FirebaseLinkRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                Log.d(TAG, "Accepting link request from ${request.fromUserId}")

                // Update both users' partnerId
                firestoreRepository.updateDocument(
                    "users",
                    request.toUserId,
                    mapOf("partnerId" to request.fromUserId)
                )

                firestoreRepository.updateDocument(
                    "users",
                    request.fromUserId,
                    mapOf("partnerId" to request.toUserId)
                )

                // Update request status
                firestoreRepository.updateDocument(
                    "link_requests",
                    request.id,
                    mapOf("status" to "accepted")
                )

                Log.d(TAG, "Link request accepted successfully")
                
                // Reload data to show linked state
                loadData()
                loadPendingRequests()
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting link request", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Reject link request
     */
    fun rejectLinkRequest(request: FirebaseLinkRequest) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Rejecting link request from ${request.fromUserId}")

                // Update request status
                firestoreRepository.updateDocument(
                    "link_requests",
                    request.id,
                    mapOf("status" to "rejected")
                )

                Log.d(TAG, "Link request rejected")
                loadPendingRequests()
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting link request", e)
            }
        }
    }
}

/**
 * UI State cho Partner Hub với Firebase
 */
data class PartnerHubFirebaseState(
    val currentUser: FirebaseUser? = null,
    val partner: FirebaseUser? = null,
    val linkStatus: LinkStatus = LinkStatus.NOT_LINKED,
    val isLoading: Boolean = false,
    val myLinkCode: String = "",
    val unreadMessageCount: Int = 0,
    val pendingQACount: Int = 0,
    val partnerDistance: Double? = null,
    val partnerLocationName: String = ""
)
