package com.example.coupleapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.FirebaseAuthRepository
import com.example.coupleapp.data.repository.FirebaseFirestoreRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
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
        Log.d(TAG, "[PARTNER] PartnerHubViewModelFirebase initialized")
        try {
            loadData()
            loadPendingRequests()
        } catch (e: Exception) {
            Log.e(TAG, "[PARTNER] ❌ Error in init", e)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            Log.d(TAG, "[PARTNER] loadData() started")
            _uiState.update { it.copy(isLoading = true) }

            try {
                val firebaseUser = authRepository.currentUser
                if (firebaseUser == null) {
                    Log.e(TAG, "[PARTNER] ❌ User not logged in")
                    _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
                    return@launch
                }

                val userId = firebaseUser.uid
                Log.d(TAG, "[PARTNER] Loading data for user: $userId")

                // Load current user data
                Log.d(TAG, "[PARTNER] Fetching user document: $userId")
                val currentUserResult = firestoreRepository.getDocument(
                    "users", 
                    userId, 
                    com.example.coupleapp.data.model.FirebaseUser::class.java
                )
                
                if (currentUserResult.isFailure) {
                    Log.e(TAG, "[PARTNER] ❌ Failed to load user document", currentUserResult.exceptionOrNull())
                    _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
                    return@launch
                }

                val currentUser = currentUserResult.getOrNull()
                if (currentUser == null) {
                    Log.e(TAG, "[PARTNER] ❌ User document is null")
                    _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
                    return@launch
                }

                Log.d(TAG, "[PARTNER] ✅ Current user loaded: ${currentUser.displayName}, partnerId: ${currentUser.partnerId}")

                // Check if user has partner
                val partnerId = currentUser.partnerId
                if (partnerId.isNullOrEmpty()) {
                    Log.d(TAG, "[PARTNER] User has no partner")
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
                Log.d(TAG, "[PARTNER] Loading partner: $partnerId")
                val partnerResult = firestoreRepository.getDocument(
                    "users", 
                    partnerId, 
                    com.example.coupleapp.data.model.FirebaseUser::class.java
                )
                val partner = partnerResult.getOrNull()

                if (partner != null) {
                    Log.d(TAG, "[PARTNER] ✅ Partner loaded: ${partner.displayName}")
                    _uiState.update {
                        it.copy(
                            currentUser = currentUser,
                            partner = partner,
                            linkStatus = LinkStatus.LINKED,
                            isLoading = false,
                            myLinkCode = currentUser.linkCode ?: ""
                        )
                    }
                    Log.d(TAG, "[PARTNER] UI state updated with partner")
                    // Load partner location
                    loadPartnerLocation(partner.id)
                    // Load Q&A questions after partner is loaded
                    Log.d(TAG, "[PARTNER] Loading Q&A questions...")
                    try {
                        loadQAQuestions()
                    } catch (e: Exception) {
                        Log.e(TAG, "[PARTNER] ❌ Error loading Q&A questions", e)
                    }
                } else {
                    Log.e(TAG, "[PARTNER] ❌ Partner document not found")
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
                Log.e(TAG, "[PARTNER] ❌ Error loading data", e)
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, linkStatus = LinkStatus.NOT_LINKED) }
            }
        }
    }

    fun refreshData() {
        Log.d(TAG, "Refreshing data")
        loadData()
        loadQAQuestions()
    }

    /**
     * Load Q&A questions from Firebase
     */
    private fun loadQAQuestions() {
        viewModelScope.launch {
            try {
                val firebaseUser = authRepository.currentUser
                val currentUser = _uiState.value.currentUser
                
                if (firebaseUser == null || currentUser == null) {
                    Log.e(TAG, "Cannot load Q&A: user not logged in")
                    return@launch
                }
                
                val coupleId = currentUser.coupleId
                if (coupleId.isNullOrEmpty()) {
                    Log.d(TAG, "No coupleId, cannot load Q&A questions")
                    _qaQuestions.value = emptyList()
                    return@launch
                }
                
                Log.d(TAG, "[QA] Loading questions for coupleId: $coupleId")
                
                // Listen to Q&A questions realtime
                // Temporarily without orderBy to avoid index requirement
                viewModelScope.launch {
                    firestoreRepository.listenToQuery(
                        collection = "qa_questions",
                        field = "coupleId",
                        value = coupleId,
                        clazz = FirebaseQAQuestion::class.java,
                        orderBy = null,
                        descending = false
                    ).collect { firebaseQuestions ->
                        Log.d(TAG, "[QA] Loaded ${firebaseQuestions.size} questions")
                        // Sort in code instead of Firestore query
                        _qaQuestions.value = firebaseQuestions
                            .sortedByDescending { it.createdAt }
                            .map { it.toQAQuestion() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error loading questions", e)
            }
        }
    }

    /**
     * Create a new Q&A question
     */
    fun createQuestion(question: String) {
        viewModelScope.launch {
            try {
                val firebaseUser = authRepository.currentUser
                val currentUser = _uiState.value.currentUser
                val partner = _uiState.value.partner
                
                if (firebaseUser == null || currentUser == null || partner == null) {
                    Log.e(TAG, "[QA] Cannot create question: missing user or partner")
                    return@launch
                }
                
                val coupleId = currentUser.coupleId
                if (coupleId.isNullOrEmpty()) {
                    Log.e(TAG, "[QA] Cannot create question: no coupleId")
                    return@launch
                }
                
                Log.d(TAG, "[QA] Creating question: $question")
                
                val qaQuestion = FirebaseQAQuestion(
                    coupleId = coupleId,
                    askerId = currentUser.id,
                    askerName = currentUser.displayName,
                    responderId = partner.id,
                    responderName = partner.displayName,
                    question = question,
                    status = "pending"
                )
                
                val result = firestoreRepository.addDocument("qa_questions", qaQuestion)
                
                if (result.isSuccess) {
                    Log.d(TAG, "[QA] ✅ Question created successfully")
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to create question", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error creating question", e)
            }
        }
    }

    /**
     * Answer a Q&A question
     */
    fun answerQuestion(questionId: String, answer: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "[QA] Answering question $questionId: $answer")
                
                val updates = mapOf(
                    "answer" to answer,
                    "status" to "answered",
                    "answeredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                val result = firestoreRepository.updateDocument("qa_questions", questionId, updates)
                
                if (result.isSuccess) {
                    Log.d(TAG, "[QA] ✅ Question answered successfully")
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to answer question", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error answering question", e)
            }
        }
    }

    /**
     * Approve answer
     */
    fun approveAnswer(questionId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "[QA] Approving answer: $questionId")
                
                val updates = mapOf(
                    "status" to "approved",
                    "isApproved" to true,
                    "approvedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                val result = firestoreRepository.updateDocument("qa_questions", questionId, updates)
                
                if (result.isSuccess) {
                    Log.d(TAG, "[QA] ✅ Answer approved successfully")
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to approve answer", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error approving answer", e)
            }
        }
    }

    /**
     * Reject answer
     */
    fun rejectAnswer(questionId: String, comment: String = "") {
        viewModelScope.launch {
            try {
                Log.d(TAG, "[QA] Rejecting answer: $questionId")
                
                val updates = mapOf(
                    "status" to "rejected",
                    "isApproved" to false,
                    "approvedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                val result = firestoreRepository.updateDocument("qa_questions", questionId, updates)
                
                if (result.isSuccess) {
                    Log.d(TAG, "[QA] ✅ Answer rejected successfully")
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to reject answer", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error rejecting answer", e)
            }
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

                    // Remove partnerId AND coupleId from both users
                    firestoreRepository.updateDocument(
                        "users",
                        userId,
                        mapOf(
                            "partnerId" to "",
                            "coupleId" to ""
                        )
                    )

                    firestoreRepository.updateDocument(
                        "users",
                        partnerId,
                        mapOf(
                            "partnerId" to "",
                            "coupleId" to ""
                        )
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

                // Create coupleId (sorted userIds to ensure consistency)
                val sortedIds = listOf(request.fromUserId, request.toUserId).sorted()
                val coupleId = sortedIds.joinToString("_")
                Log.d(TAG, "Creating couple with coupleId: $coupleId")

                // Create couple document
                val couple = FirebaseCouple(
                    id = coupleId,
                    user1Id = sortedIds[0],
                    user2Id = sortedIds[1],
                    anniversaryDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                    relationshipStatus = "dating",
                    sharedGardenLevel = 1,
                    sharedPoints = 0,
                    createdAt = java.util.Date()
                )

                firestoreRepository.setDocument(
                    "couples",
                    coupleId,
                    couple
                )

                // Update both users' partnerId AND coupleId
                firestoreRepository.updateDocument(
                    "users",
                    request.toUserId,
                    mapOf(
                        "partnerId" to request.fromUserId,
                        "coupleId" to coupleId
                    )
                )

                firestoreRepository.updateDocument(
                    "users",
                    request.fromUserId,
                    mapOf(
                        "partnerId" to request.toUserId,
                        "coupleId" to coupleId
                    )
                )

                // Update request status
                firestoreRepository.updateDocument(
                    "link_requests",
                    request.id,
                    mapOf("status" to "accepted")
                )

                Log.d(TAG, "Link request accepted successfully - coupleId: $coupleId")
                
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

    /**
     * Load partner's latest location and update UI state
     */
    private fun loadPartnerLocation(partnerId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.currentUser ?: return@launch
                val firestore = Firebase.firestore
                
                // Load partner location
                val partnerLocationSnapshot = firestore.collection("locations")
                    .whereEqualTo("userId", partnerId)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                val partnerLocationDoc = partnerLocationSnapshot.documents.firstOrNull()
                val partnerLocationName = partnerLocationDoc?.getString("address") ?: ""
                
                // Load current user location
                val myLocationSnapshot = firestore.collection("locations")
                    .whereEqualTo("userId", currentUser.uid)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                val myLocationDoc = myLocationSnapshot.documents.firstOrNull()
                
                // Calculate distance if both locations available
                var distance: Double? = null
                if (partnerLocationDoc != null && myLocationDoc != null) {
                    val partnerLat = partnerLocationDoc.getDouble("latitude")
                    val partnerLng = partnerLocationDoc.getDouble("longitude")
                    val myLat = myLocationDoc.getDouble("latitude")
                    val myLng = myLocationDoc.getDouble("longitude")
                    
                    if (partnerLat != null && partnerLng != null && myLat != null && myLng != null) {
                        distance = calculateDistance(myLat, myLng, partnerLat, partnerLng)
                    }
                }
                
                _uiState.update { state ->
                    state.copy(
                        partnerLocationName = partnerLocationName,
                        partnerDistance = distance
                    )
                }
            } catch (e: Exception) {
                // Nếu lỗi thì không cập nhật gì, giữ nguyên
                Log.e(TAG, "Error loading partner location", e)
            }
        }
    }
    
    /**
     * Calculate distance between two coordinates in kilometers
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // km
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
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
