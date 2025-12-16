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
 * ViewModel cho Q&A Screen - Firebase version
 */
class QAViewModelFirebase : ViewModel() {
    private val authRepository = FirebaseAuthRepository()
    private val firestoreRepository = FirebaseFirestoreRepository()
    
    companion object {
        private const val TAG = "QAViewModelFirebase"
    }
    
    private val _questions = MutableStateFlow<List<QAQuestion>>(emptyList())
    val questions: StateFlow<List<QAQuestion>> = _questions.asStateFlow()
    
    val currentUserId: String get() = authRepository.currentUser?.uid ?: ""
    
    private val _partner = MutableStateFlow<FirebaseUser?>(null)
    val partner: StateFlow<FirebaseUser?> = _partner.asStateFlow()
    
    private val _newQuestion = MutableStateFlow("")
    val newQuestion: StateFlow<String> = _newQuestion.asStateFlow()
    
    private val _answerText = MutableStateFlow("")
    val answerText: StateFlow<String> = _answerText.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0) // 0 = Tất cả, 1 = Câu hỏi của tôi, 2 = Trả lời cho tôi
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    
    // Lọc câu hỏi theo tab
    val filteredQuestions: StateFlow<List<QAQuestion>> = combine(
        questions,
        _selectedTab
    ) { allQuestions, tab ->
        when (tab) {
            1 -> allQuestions.filter { it.askerId == currentUserId }
            2 -> allQuestions.filter { it.responderId == currentUserId }
            else -> allQuestions
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    init {
        Log.d(TAG, "QAViewModelFirebase initialized")
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            try {
                val firebaseUser = authRepository.currentUser
                if (firebaseUser == null) {
                    Log.e(TAG, "User not logged in")
                    return@launch
                }
                
                val userId = firebaseUser.uid
                Log.d(TAG, "Loading data for user: $userId")
                
                // Load current user
                val currentUserResult = firestoreRepository.getDocument(
                    "users",
                    userId,
                    FirebaseUser::class.java
                )
                
                val currentUser = currentUserResult.getOrNull()
                if (currentUser == null) {
                    Log.e(TAG, "Current user not found")
                    return@launch
                }
                
                // Load partner
                val partnerId = currentUser.partnerId
                if (partnerId.isNullOrEmpty()) {
                    Log.d(TAG, "No partner linked")
                    return@launch
                }
                
                val partnerResult = firestoreRepository.getDocument(
                    "users",
                    partnerId,
                    FirebaseUser::class.java
                )
                
                _partner.value = partnerResult.getOrNull()
                
                // Load Q&A questions
                val coupleId = currentUser.coupleId
                if (coupleId.isNullOrEmpty()) {
                    Log.d(TAG, "No coupleId")
                    return@launch
                }
                
                Log.d(TAG, "[QA] Loading questions for coupleId: $coupleId")
                
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
                        _questions.value = firebaseQuestions
                            .sortedByDescending { it.createdAt }
                            .map { it.toQAQuestion() }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
            }
        }
    }
    
    fun onTabSelected(tab: Int) {
        _selectedTab.value = tab
    }
    
    fun onNewQuestionChanged(text: String) {
        _newQuestion.value = text
    }
    
    fun onAnswerTextChanged(text: String) {
        _answerText.value = text
    }
    
    /**
     * Create a new question
     */
    fun createQuestion() {
        val question = _newQuestion.value.trim()
        if (question.isEmpty()) return
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val firebaseUser = authRepository.currentUser
                val currentUserResult = firestoreRepository.getDocument(
                    "users",
                    firebaseUser?.uid ?: "",
                    FirebaseUser::class.java
                )
                
                val currentUser = currentUserResult.getOrNull()
                val partner = _partner.value
                
                if (currentUser == null || partner == null) {
                    Log.e(TAG, "[QA] Cannot create question: missing user or partner")
                    _isLoading.value = false
                    return@launch
                }
                
                val coupleId = currentUser.coupleId
                if (coupleId.isNullOrEmpty()) {
                    Log.e(TAG, "[QA] Cannot create question: no coupleId")
                    _isLoading.value = false
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
                    _newQuestion.value = ""
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to create question", result.exceptionOrNull())
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error creating question", e)
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Answer a question
     */
    fun answerQuestion(questionId: String) {
        val answer = _answerText.value.trim()
        if (answer.isEmpty()) return
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                Log.d(TAG, "[QA] Answering question $questionId: $answer")
                
                val updates = mapOf(
                    "answer" to answer,
                    "status" to "answered",
                    "answeredAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                val result = firestoreRepository.updateDocument("qa_questions", questionId, updates)
                
                if (result.isSuccess) {
                    Log.d(TAG, "[QA] ✅ Question answered successfully")
                    _answerText.value = ""
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to answer question", result.exceptionOrNull())
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error answering question", e)
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Approve an answer
     */
    fun approveAnswer(questionId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
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
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error approving answer", e)
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Reject an answer
     */
    fun rejectAnswer(questionId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                Log.d(TAG, "[QA] Rejecting answer: $questionId")
                
                val updates = mutableMapOf<String, Any>(
                    "status" to "pending",
                    "isApproved" to false
                )
                
                // Use FieldValue.delete() for nullable fields
                updates["answer"] = com.google.firebase.firestore.FieldValue.delete()
                updates["answeredAt"] = com.google.firebase.firestore.FieldValue.delete()
                updates["approvedAt"] = com.google.firebase.firestore.FieldValue.delete()
                
                val result = firestoreRepository.updateDocument("qa_questions", questionId, updates)
                
                if (result.isSuccess) {
                    Log.d(TAG, "[QA] ✅ Answer rejected, question reset to pending")
                } else {
                    Log.e(TAG, "[QA] ❌ Failed to reject answer", result.exceptionOrNull())
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "[QA] Error rejecting answer", e)
                _isLoading.value = false
            }
        }
    }
}
