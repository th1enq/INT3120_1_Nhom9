# Q&A Feature - Firebase Implementation

## Tổng quan
Tính năng Q&A (Questions & Answers) cho phép 2 người trong couple hỏi đáp nhau để hiểu nhau hơn.

## Cấu trúc Firebase

### Collection: `qa_questions`
```json
{
  "id": "auto-generated",
  "coupleId": "user1_user2",
  "askerId": "user1",
  "askerName": "Emma",
  "responderId": "user2",
  "responderName": "Alex",
  "question": "Điều gì khiến em yêu anh?",
  "answer": "Em yêu sự chân thành của anh...",
  "status": "pending|answered|approved|rejected",
  "isApproved": null|true|false,
  "createdAt": Timestamp,
  "answeredAt": Timestamp,
  "approvedAt": Timestamp
}
```

### Trạng thái (Status)
1. **pending**: Câu hỏi mới tạo, chờ người được hỏi trả lời
2. **answered**: Đã có câu trả lời, chờ người hỏi đánh giá
3. **approved**: Người hỏi chấp nhận câu trả lời
4. **rejected**: Người hỏi từ chối, câu hỏi quay về trạng thái pending

## Luồng hoạt động

### 1. Tạo câu hỏi
- User A tạo câu hỏi
- Hệ thống tự động set:
  - `askerId` = User A
  - `responderId` = User B (partner)
  - `status` = "pending"
  - `coupleId` = sorted user IDs

### 2. Trả lời câu hỏi
- User B (responder) thấy câu hỏi trong tab "Trả lời cho tôi"
- User B nhập câu trả lời
- Hệ thống update:
  - `answer` = câu trả lời
  - `status` = "answered"
  - `answeredAt` = timestamp

### 3. Đánh giá câu trả lời
User A (asker) có 2 lựa chọn:

#### Approve (Chấp nhận)
- `status` = "approved"
- `isApproved` = true
- `approvedAt` = timestamp

#### Reject (Từ chối)
- `status` = "pending"
- `isApproved` = false
- `answer` = deleted
- `answeredAt` = deleted
- `approvedAt` = deleted
- User B phải trả lời lại

## Components

### ViewModels
1. **PartnerHubViewModelFirebase.kt**
   - Load Q&A questions realtime
   - Create/answer/approve/reject questions
   - Sync với Partner Hub screen

2. **QAViewModelFirebase.kt**
   - Dedicated ViewModel cho QA Screen
   - Filter questions theo tab (All/My Questions/For Me)
   - Handle Q&A interactions

### UI
1. **PartnerHubScreen.kt**
   - Hiển thị recent Q&A trong timeline
   - Button tạo câu hỏi mới
   - Navigate to QAScreen

2. **QAScreen.kt**
   - Tab view: All / My Questions / Answer For Me
   - Create question dialog
   - Answer question dialog
   - Question cards với actions

3. **QATimelineSection.kt**
   - Component hiển thị Q&A timeline
   - Compact view cho PartnerHubScreen

4. **QADialogs.kt**
   - CreateQuestionDialog: Tạo câu hỏi mới
   - AnswerQuestionDialog: Trả lời câu hỏi
   - Suggested questions

## Models

### FirebaseQAQuestion
```kotlin
data class FirebaseQAQuestion(
    val id: String = "",
    val coupleId: String = "",
    val askerId: String = "",
    val askerName: String = "",
    val responderId: String = "",
    val responderName: String = "",
    val question: String = "",
    val answer: String? = null,
    val status: String = "pending",
    val isApproved: Boolean? = null,
    val createdAt: Date? = null,
    val answeredAt: Date? = null,
    val approvedAt: Date? = null
)
```

### QAQuestion (UI Model)
```kotlin
data class QAQuestion(
    val id: String,
    val askerId: String,
    val askerName: String,
    val responderId: String,
    val responderName: String,
    val question: String,
    val answer: String? = null,
    val status: QAStatus,
    val isApproved: Boolean? = null,
    val createdAt: LocalDateTime,
    val answeredAt: LocalDateTime? = null,
    val approvedAt: LocalDateTime? = null
)
```

## Realtime Sync
- Dùng `listenToQuery()` để listen realtime changes
- Auto-update UI khi có câu hỏi mới/câu trả lời mới
- Sort theo `createdAt` descending (mới nhất trước)

## Security Rules (Firestore)
```javascript
match /qa_questions/{questionId} {
  allow read: if request.auth != null && 
    get(/databases/$(database)/documents/qa_questions/$(questionId)).data.coupleId == 
    getCoupleId(request.auth.uid);
    
  allow create: if request.auth != null && 
    request.resource.data.coupleId == getCoupleId(request.auth.uid);
    
  allow update: if request.auth != null && 
    get(/databases/$(database)/documents/qa_questions/$(questionId)).data.coupleId == 
    getCoupleId(request.auth.uid);
    
  allow delete: if request.auth != null && 
    get(/databases/$(database)/documents/qa_questions/$(questionId)).data.askerId == 
    request.auth.uid;
}
```

## Testing
1. Link 2 users thành couple
2. User A tạo câu hỏi → Check Firestore collection `qa_questions`
3. User B thấy câu hỏi trong tab "Trả lời cho tôi"
4. User B trả lời → User A thấy trong tab "Câu hỏi của tôi" với status "answered"
5. User A approve/reject → Status update realtime

## Log Tags
- `[QA]` - Q&A operations
- Debug logs show:
  - Question creation
  - Answer submission
  - Approve/reject actions
  - Realtime updates count

## Future Enhancements
- Question categories
- Question suggestions based on couple profile
- Statistics (total questions, answer rate)
- Question templates
- Photo/image in answers
- Reactions to answers
