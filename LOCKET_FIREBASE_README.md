# Firebase Integration cho Locket Feature

## Tổng quan
Tích hợp Firebase cho tính năng Locket, cho phép gửi và nhận:
- 📷 Ảnh (chụp từ camera hoặc chọn từ thư viện)
- 😊 Emoji
- 🎨 Vẽ tay
- 💬 Text message

## Các file đã tạo/cập nhật

### 1. Firebase Models (`FirebaseModels.kt`)
- **FirebaseLocketPost**: Model đã được cập nhật để hỗ trợ 4 loại nội dung
  - `type`: "photo", "emoji", "drawing", "text"
  - `photoUrl`: URL ảnh trên Firebase Storage
  - `emoji`: Chuỗi emoji
  - `drawingUrl`: URL ảnh vẽ trên Firebase Storage  
  - `textContent`: Nội dung text

### 2. Repository (`LocketFirebaseRepository.kt`)
Repository mới với các functions:
- `sendPhotoLocket()`: Upload ảnh lên Storage và lưu metadata vào Firestore
- `sendEmojiLocket()`: Lưu emoji vào Firestore
- `sendDrawingLocket()`: Upload vẽ lên Storage và lưu metadata
- `sendTextLocket()`: Lưu text vào Firestore
- `getReceivedLocketsFlow()`: Stream realtime các locket nhận được
- `getSentLocketsFlow()`: Stream realtime các locket đã gửi
- `getAllLocketsForCoupleFlow()`: Stream tất cả locket của cặp đôi
- `markAsRead()`: Đánh dấu đã đọc
- `getUnreadCount()`: Đếm số locket chưa đọc
- `deleteLocket()`: Xóa locket (bao gồm cả file trên Storage)

### 3. ViewModel (`LocketViewModelFirebase.kt`)
ViewModel mới sử dụng Firebase:
- Kế thừa tất cả chức năng của LocketViewModel gốc
- Thay thế mock data bằng Firebase realtime data
- Tự động observe các locket mới từ partner
- Upload ảnh/vẽ lên Firebase Storage khi send

### 4. UI Components

#### `LocketFirebaseComponents.kt`
- `LocketImageFromUrl`: Component load ảnh từ Firebase Storage URL (sử dụng Coil)
- `LocketGridItemWithFirebase`: Grid item với hỗ trợ ảnh từ Firebase
- `LocketDetailWithFirebase`: Chi tiết locket với ảnh từ Firebase

#### `LocketHistoryScreen.kt`
Đã cập nhật để:
- Hiển thị ảnh từ Firebase Storage URL
- Sử dụng Coil AsyncImage để load ảnh
- Hỗ trợ tất cả 4 loại locket

### 5. Dependencies (`build.gradle.kts`)
Đã thêm:
```kotlin
implementation("io.coil-kt:coil-compose:2.5.0") // Image loading từ URL
```

## Cách sử dụng

### ✅ Firebase ViewModel đã được tích hợp sẵn

`LocketScreen` và `LocketHistoryScreen` đã được cập nhật để sử dụng `LocketViewModelFirebase` làm default ViewModel:

```kotlin
// LocketScreen.kt
@Composable
fun LocketScreen(
    // ... params
    viewModel: LocketViewModelFirebase = viewModel() // ✅ Firebase by default
)

// LocketHistoryScreen.kt  
@Composable
fun LocketHistoryScreen(
    // ... params
    viewModel: LocketViewModelFirebase = viewModel() // ✅ Firebase by default
)
```

**Không cần thay đổi gì trong NavGraph!** Các screen tự động sử dụng Firebase.

### Chuyển về Mock Data (nếu cần test)

Nếu muốn test với mock data, có thể tạo instance của `LocketViewModel` gốc:

```kotlin
import com.example.coupleapp.viewmodel.LocketViewModel

LocketScreen(
    // ... params
    viewModel = LocketViewModel() // Mock data
)
```

## Cấu trúc Firestore

### Collection: `locket_posts`
```
{
  id: "auto-generated",
  coupleId: "couple_123",
  senderId: "user_1",
  senderName: "Emma",
  senderAvatarUrl: "https://...",
  receiverId: "user_2", 
  receiverName: "Alex",
  type: "photo", // "photo" | "emoji" | "drawing" | "text"
  
  // For photo
  photoUrl: "https://firebasestorage...",
  
  // For emoji
  emoji: "😊",
  
  // For drawing
  drawingUrl: "https://firebasestorage...",
  
  // For text
  textContent: "Missing you!",
  
  caption: "",
  isRead: false,
  timestamp: Timestamp
}
```

### Storage Structure
```
locket_images/
  ├── locket_photo_<uuid>.jpg
  └── locket_drawing_<uuid>.png
```

## Realtime Updates
- Tất cả locket sử dụng Firestore realtime listeners
- Khi partner gửi locket mới, màn hình tự động cập nhật
- Không cần refresh thủ công

## Security Rules (Cần thêm vào Firebase Console)

### Firestore Rules
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /locket_posts/{locketId} {
      // Allow read if user is sender or receiver
      allow read: if request.auth != null && 
        (resource.data.senderId == request.auth.uid || 
         resource.data.receiverId == request.auth.uid);
      
      // Allow create if user is sender
      allow create: if request.auth != null && 
        request.resource.data.senderId == request.auth.uid;
      
      // Allow update/delete if user is sender
      allow update, delete: if request.auth != null && 
        resource.data.senderId == request.auth.uid;
    }
  }
}
```

### Storage Rules
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /locket_images/{imageId} {
      // Allow authenticated users to read
      allow read: if request.auth != null;
      
      // Allow authenticated users to upload
      allow write: if request.auth != null &&
        request.resource.size < 5 * 1024 * 1024 && // Max 5MB
        request.resource.contentType.matches('image/.*');
    }
  }
}
```

## Testing Checklist
- [ ] Chụp ảnh và gửi -> Kiểm tra ảnh lên Storage và metadata vào Firestore
- [ ] Chọn ảnh từ gallery và gửi -> Kiểm tra upload thành công
- [ ] Gửi emoji -> Kiểm tra lưu vào Firestore
- [ ] Vẽ và gửi -> Kiểm tra ảnh vẽ lên Storage
- [ ] Gửi text -> Kiểm tra text lưu vào Firestore
- [ ] Xem lịch sử -> Kiểm tra hiển thị đầy đủ và realtime
- [ ] Partner gửi locket -> Kiểm tra nhận realtime
- [ ] Đánh dấu đã đọc -> Kiểm tra update status
- [ ] Xóa locket -> Kiểm tra xóa file trên Storage

## Lưu ý quan trọng
1. **Authentication**: User phải đăng nhập (FirebaseAuth) mới sử dụng được
2. **Couple Link**: User phải có partnerId và coupleId trong profile
3. **Image Compression**: Ảnh được compress xuống 85% quality để tiết kiệm storage
4. **Drawing Format**: Vẽ lưu dạng PNG (100% quality) để giữ chất lượng
5. **Error Handling**: Tất cả operations đều trả về Result<T> để xử lý errors

## Next Steps
1. Cập nhật NavGraph để sử dụng LocketViewModelFirebase
2. Test với Firebase Emulator trước khi deploy
3. Thêm loading indicators khi upload
4. Thêm retry logic cho failed uploads
5. Implement notifications khi nhận locket mới
6. Thêm feature xem ảnh fullscreen
7. Thêm feature save ảnh về máy
