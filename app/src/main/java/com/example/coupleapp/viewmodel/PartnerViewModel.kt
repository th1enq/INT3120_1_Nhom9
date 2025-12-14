package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import com.example.coupleapp.data.repository.PartnerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel cho Partner Hub - màn hình chính của tab Friends
 */
class PartnerHubViewModel(
    private val repository: PartnerRepository = PartnerRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PartnerHubState())
    val uiState: StateFlow<PartnerHubState> = _uiState.asStateFlow()
    
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
    
    // Q&A Questions flow
    val qaQuestions = repository.qaQuestions
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Combine flows
            combine(
                repository.currentUser,
                repository.partner,
                repository.linkStatus,
                repository.pendingLinkRequest,
                repository.partnerDistance
            ) { currentUser, partner, linkStatus, pendingRequest, distance ->
                PartnerHubState(
                    currentUser = currentUser,
                    partner = partner,
                    linkStatus = linkStatus,
                    pendingLinkRequest = pendingRequest,
                    unreadMessageCount = repository.getUnreadMessageCount(),
                    pendingQACount = repository.getPendingQACount(),
                    isLoading = false,
                    partnerDistance = distance,
                    partnerLocationName = partner?.locationName ?: ""
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    unreadMessageCount = repository.getUnreadMessageCount(),
                    pendingQACount = repository.getPendingQACount()
                )
            }
        }
    }
    
    // Q&A Functions
    fun createQuestion(question: String) {
        viewModelScope.launch {
            repository.createQuestion(question)
        }
    }
    
    fun answerQuestion(questionId: String, answer: String) {
        viewModelScope.launch {
            repository.answerQuestion(questionId, answer)
        }
    }
    
    fun approveAnswer(questionId: String) {
        viewModelScope.launch {
            repository.evaluateAnswer(questionId, true)
        }
    }
    
    fun rejectAnswer(questionId: String, comment: String = "") {
        viewModelScope.launch {
            repository.evaluateAnswer(questionId, false)
            // TODO: Send comment to partner
        }
    }
    
    fun acceptLinkRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.acceptLinkRequest(requestId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    fun rejectLinkRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.rejectLinkRequest(requestId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    fun unlinkPartner() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.unlinkPartner()
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // Để test: reset về trạng thái chưa liên kết
    fun resetToUnlinked() {
        repository.resetToUnlinkedState()
    }
    
    // Để test: set về trạng thái đã liên kết
    fun setLinked() {
        repository.setLinkedState()
    }
}

/**
 * ViewModel cho màn hình Q&A
 */
class QAViewModel(
    private val repository: PartnerRepository = PartnerRepository.getInstance()
) : ViewModel() {
    
    val questions: StateFlow<List<QAQuestion>> = repository.qaQuestions
    
    val currentUserId: String get() = repository.currentUser.value.id
    
    val partner: StateFlow<PartnerUser?> = repository.partner
    
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
    
    fun onTabSelected(tab: Int) {
        _selectedTab.value = tab
    }
    
    fun onNewQuestionChanged(text: String) {
        _newQuestion.value = text
    }
    
    fun onAnswerTextChanged(text: String) {
        _answerText.value = text
    }
    
    fun createQuestion() {
        val question = _newQuestion.value.trim()
        if (question.isEmpty()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            repository.createQuestion(question)
            _newQuestion.value = ""
            _isLoading.value = false
        }
    }
    
    fun answerQuestion(questionId: String) {
        val answer = _answerText.value.trim()
        if (answer.isEmpty()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            repository.answerQuestion(questionId, answer)
            _answerText.value = ""
            _isLoading.value = false
        }
    }
    
    fun approveAnswer(questionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.evaluateAnswer(questionId, true)
            _isLoading.value = false
        }
    }
    
    fun rejectAnswer(questionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.evaluateAnswer(questionId, false)
            _isLoading.value = false
        }
    }
}
