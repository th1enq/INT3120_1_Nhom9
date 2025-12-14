# CoupleApp Backend - Firebase Integration Examples

## Ví dụ sử dụng Firebase trong ViewModels

### 1. Đăng ký và Đăng nhập

```kotlin
// In LoginScreen hoặc RegisterScreen
val authViewModel: AuthViewModel = viewModel()
val authState by authViewModel.authState.collectAsState()

// Đăng nhập
authViewModel.signInWithEmail(email, password)

// Đăng ký
authViewModel.registerUser(
    email = email,
    password = password,
    fullName = fullName,
    phoneNumber = phoneNumber,
    dateOfBirth = dateOfBirth,
    gender = gender
)

// Xử lý trạng thái
when (authState) {
    is AuthState.Loading -> ShowLoadingIndicator()
    is AuthState.Authenticated -> NavigateToHome()
    is AuthState.Error -> ShowError((authState as AuthState.Error).message)
    else -> {}
}
```

### 2. Lưu và đọc dữ liệu Firestore

```kotlin
class ChatViewModel : ViewModel() {
    private val firestoreRepo = FirebaseFirestoreRepository()
    
    // Gửi tin nhắn
    fun sendMessage(coupleId: String, senderId: String, message: String) {
        viewModelScope.launch {
            val chatMessage = FirebaseChatMessage(
                coupleId = coupleId,
                senderId = senderId,
                message = message,
                messageType = "text"
            )
            
            firestoreRepo.addDocument(
                FirebaseFirestoreRepository.MESSAGES_COLLECTION,
                chatMessage
            ).onSuccess { messageId ->
                println("Message sent: $messageId")
            }.onFailure { error ->
                println("Error: ${error.message}")
            }
        }
    }
    
    // Lắng nghe tin nhắn real-time
    fun listenToMessages(coupleId: String) {
        viewModelScope.launch {
            firestoreRepo.listenToQuery(
                collection = FirebaseFirestoreRepository.MESSAGES_COLLECTION,
                field = "coupleId",
                value = coupleId,
                clazz = FirebaseChatMessage::class.java,
                orderBy = "timestamp",
                descending = false
            ).collect { messages ->
                // Update UI with new messages
                _messages.value = messages
            }
        }
    }
}
```

### 3. Upload ảnh lên Storage

```kotlin
class LocketViewModel : ViewModel() {
    private val storageRepo = FirebaseStorageRepository()
    private val firestoreRepo = FirebaseFirestoreRepository()
    
    fun uploadLocketPhoto(bitmap: Bitmap, senderId: String, receiverId: String, coupleId: String) {
        viewModelScope.launch {
            _isUploading.value = true
            
            // Upload ảnh
            val filename = "${UUID.randomUUID()}.jpg"
            storageRepo.uploadBitmap(
                bitmap = bitmap,
                path = FirebaseStorageRepository.LOCKET_IMAGES_PATH,
                filename = filename
            ).onSuccess { imageUrl ->
                // Lưu thông tin vào Firestore
                val locketPost = FirebaseLocketPost(
                    coupleId = coupleId,
                    senderId = senderId,
                    receiverId = receiverId,
                    type = "photo",
                    imageUrl = imageUrl
                )
                
                firestoreRepo.addDocument(
                    FirebaseFirestoreRepository.LOCKET_POSTS_COLLECTION,
                    locketPost
                ).onSuccess {
                    _isUploading.value = false
                    _uploadSuccess.value = true
                }
            }.onFailure { error ->
                _isUploading.value = false
                _error.value = error.message
            }
        }
    }
}
```

### 4. Lưu dữ liệu Sleep Tracker

```kotlin
class SleepTrackerViewModel : ViewModel() {
    private val firestoreRepo = FirebaseFirestoreRepository()
    
    fun saveSleepRecord(
        userId: String,
        coupleId: String,
        date: String,
        sleepTime: String,
        wakeTime: String,
        quality: String,
        notes: String
    ) {
        viewModelScope.launch {
            val sleepRecord = FirebaseSleepRecord(
                userId = userId,
                coupleId = coupleId,
                date = date,
                sleepTime = sleepTime,
                wakeTime = wakeTime,
                quality = quality,
                notes = notes
            )
            
            firestoreRepo.addDocument(
                FirebaseFirestoreRepository.SLEEP_RECORDS_COLLECTION,
                sleepRecord
            ).onSuccess {
                println("Sleep record saved")
            }
        }
    }
    
    fun loadSleepHistory(userId: String) {
        viewModelScope.launch {
            firestoreRepo.queryDocuments(
                collection = FirebaseFirestoreRepository.SLEEP_RECORDS_COLLECTION,
                field = "userId",
                value = userId,
                clazz = FirebaseSleepRecord::class.java
            ).onSuccess { records ->
                _sleepHistory.value = records
            }
        }
    }
}
```

### 5. Kết nối với Partner (Link Partner)

```kotlin
class LinkPartnerViewModel : ViewModel() {
    private val firestoreRepo = FirebaseFirestoreRepository()
    private val authRepo = FirebaseAuthRepository()
    
    fun searchPartnerByLinkCode(linkCode: String) {
        viewModelScope.launch {
            _isSearching.value = true
            
            // Tìm user có link code
            firestoreRepo.queryDocuments(
                collection = FirebaseFirestoreRepository.USERS_COLLECTION,
                field = "linkCode",
                value = linkCode,
                clazz = FirebaseUser::class.java
            ).onSuccess { users ->
                if (users.isNotEmpty()) {
                    _foundUser.value = users.first()
                } else {
                    _error.value = "No user found with this code"
                }
                _isSearching.value = false
            }
        }
    }
    
    fun linkWithPartner(partnerId: String) {
        viewModelScope.launch {
            val currentUserId = authRepo.currentUser?.uid ?: return@launch
            
            // Tạo couple document
            val coupleId = UUID.randomUUID().toString()
            val couple = FirebaseCouple(
                id = coupleId,
                user1Id = currentUserId,
                user2Id = partnerId
            )
            
            firestoreRepo.setDocument(
                FirebaseFirestoreRepository.COUPLES_COLLECTION,
                coupleId,
                couple
            ).onSuccess {
                // Update cả 2 user
                firestoreRepo.updateDocument(
                    FirebaseFirestoreRepository.USERS_COLLECTION,
                    currentUserId,
                    mapOf("coupleId" to coupleId, "partnerId" to partnerId)
                )
                
                firestoreRepo.updateDocument(
                    FirebaseFirestoreRepository.USERS_COLLECTION,
                    partnerId,
                    mapOf("coupleId" to coupleId, "partnerId" to currentUserId)
                )
                
                _linkSuccess.value = true
            }
        }
    }
}
```

### 6. Lưu và đọc Location

```kotlin
class DistanceViewModel : ViewModel() {
    private val firestoreRepo = FirebaseFirestoreRepository()
    
    fun updateUserLocation(userId: String, coupleId: String, latitude: Double, longitude: Double, address: String) {
        viewModelScope.launch {
            val location = FirebaseLocation(
                userId = userId,
                coupleId = coupleId,
                latitude = latitude,
                longitude = longitude,
                address = address
            )
            
            // Update hoặc create location document
            firestoreRepo.setDocument(
                collection = FirebaseFirestoreRepository.LOCATIONS_COLLECTION,
                documentId = userId, // Use userId as document ID
                data = location,
                merge = true
            )
        }
    }
    
    fun listenToPartnerLocation(partnerId: String) {
        viewModelScope.launch {
            firestoreRepo.listenToDocument(
                collection = FirebaseFirestoreRepository.LOCATIONS_COLLECTION,
                documentId = partnerId,
                clazz = FirebaseLocation::class.java
            ).collect { location ->
                _partnerLocation.value = location
            }
        }
    }
}
```

### 7. Calendar Events

```kotlin
class CalendarViewModel : ViewModel() {
    private val firestoreRepo = FirebaseFirestoreRepository()
    
    fun addCalendarEvent(
        coupleId: String,
        title: String,
        description: String,
        date: String,
        time: String,
        eventType: String
    ) {
        viewModelScope.launch {
            val event = FirebaseCalendarEvent(
                coupleId = coupleId,
                title = title,
                description = description,
                date = date,
                time = time,
                eventType = eventType
            )
            
            firestoreRepo.addDocument(
                FirebaseFirestoreRepository.CALENDAR_EVENTS_COLLECTION,
                event
            ).onSuccess {
                println("Event added")
            }
        }
    }
    
    fun loadEventsForMonth(coupleId: String, yearMonth: String) {
        viewModelScope.launch {
            // Query events for specific couple and month
            firestoreRepo.queryDocuments(
                collection = FirebaseFirestoreRepository.CALENDAR_EVENTS_COLLECTION,
                field = "coupleId",
                value = coupleId,
                clazz = FirebaseCalendarEvent::class.java
            ).onSuccess { events ->
                // Filter by month
                val filteredEvents = events.filter { it.date.startsWith(yearMonth) }
                _events.value = filteredEvents
            }
        }
    }
}
```

## Best Practices

### 1. Error Handling
```kotlin
firestoreRepo.addDocument(collection, data)
    .onSuccess { documentId ->
        // Success
    }
    .onFailure { error ->
        when (error) {
            is FirebaseNetworkException -> showError("No internet connection")
            is FirebaseAuthException -> showError("Authentication error")
            else -> showError(error.message ?: "Unknown error")
        }
    }
```

### 2. Loading States
```kotlin
private val _isLoading = MutableStateFlow(false)
val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

fun loadData() {
    viewModelScope.launch {
        _isLoading.value = true
        try {
            // Load data
        } finally {
            _isLoading.value = false
        }
    }
}
```

### 3. Real-time Listeners
```kotlin
private var listenerJob: Job? = null

fun startListening() {
    listenerJob = viewModelScope.launch {
        firestoreRepo.listenToCollection(...)
            .collect { data ->
                _data.value = data
            }
    }
}

fun stopListening() {
    listenerJob?.cancel()
}

override fun onCleared() {
    super.onCleared()
    stopListening()
}
```

### 4. Offline Support
Firestore tự động cache data offline. Để enable:
```kotlin
// In CoupleApplication.kt
val settings = FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(true)
    .build()
FirebaseFirestore.getInstance().firestoreSettings = settings
```

### 5. Batch Operations
```kotlin
fun updateMultipleDocuments() {
    viewModelScope.launch {
        firestoreRepo.batchWrite { batch ->
            val doc1 = db.collection("users").document("user1")
            batch.update(doc1, "status", "online")
            
            val doc2 = db.collection("users").document("user2")
            batch.update(doc2, "status", "online")
        }
    }
}
```

## Testing với Firebase Emulator

### Setup Emulator
```bash
firebase init emulators
firebase emulators:start
```

### Connect App to Emulator
```kotlin
// In CoupleApplication.kt for development
if (BuildConfig.DEBUG) {
    FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
    FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199)
}
```
