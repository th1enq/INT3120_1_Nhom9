# Profile Screen Firebase Integration

## Tóm tắt thay đổi

Đã tích hợp Firebase vào màn hình Profile để hiển thị dữ liệu người dùng thực từ Firestore thay vì dữ liệu cứng (hardcoded).

## Các tệp đã thay đổi

### 1. ProfileViewModel.kt (MỚI TẠO)
**Đường dẫn**: `app/src/main/java/com/example/coupleapp/viewmodel/ProfileViewModel.kt`

**Chức năng**:
- Quản lý state của màn hình Profile
- Tải thông tin người dùng từ Firestore
- Tải thông tin couple và partner nếu có
- Tính số ngày yêu nhau từ `anniversaryDate`
- Xử lý đăng xuất

**Các hàm chính**:
```kotlin
- loadUserProfile() // Tải thông tin user hiện tại
- loadCoupleInfo() // Tải thông tin couple nếu có coupleId
- loadPartnerInfo() // Tải thông tin partner nếu có partnerId
- signOut() // Đăng xuất khỏi Firebase Auth
- refreshProfile() // Refresh toàn bộ dữ liệu
```

**State quản lý**:
```kotlin
data class ProfileUiState(
    val currentUser: FirebaseUser? = null,
    val partner: FirebaseUser? = null,
    val couple: FirebaseCouple? = null,
    val daysTogether: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
```

### 2. ProfileScreen.kt (CẬP NHẬT)
**Đường dẫn**: `app/src/main/java/com/example/coupleapp/ui/screens/profile/ProfileScreen.kt`

**Thay đổi**:

#### a. Thêm import cho ViewModel
```kotlin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.viewmodel.ProfileViewModel
```

#### b. Thêm ProfileViewModel parameter
```kotlin
@Composable
fun ProfileScreen(
    // ... các tham số khác ...
    viewModel: ProfileViewModel = viewModel()  // ← THÊM MỚI
)
```

#### c. Thay thế dữ liệu hardcoded bằng Firebase data
**TRƯỚC**:
```kotlin
val userName = remember { "You" }
val userAvatar = remember { "😊" }
val partnerName = remember { "Partner" }
val partnerAvatar = remember { "💕" }
val daysTogethers = remember { 365 }
val linkCode = remember { "ABC123" }
```

**SAU**:
```kotlin
val uiState by viewModel.uiState.collectAsState()

val userName = uiState.currentUser?.displayName ?: "User"
val userAvatar = "😊" // TODO: Hỗ trợ avatar tùy chỉnh sau
val partnerName = uiState.partner?.displayName ?: "No Partner"
val partnerAvatar = if (uiState.partner != null) "💕" else "❓"
val daysTogethers = uiState.daysTogether
val linkCode = uiState.currentUser?.linkCode ?: "------"
```

#### d. Cập nhật logout handler
**TRƯỚC**:
```kotlin
onClick = {
    showLogoutDialog = false
    onLogout()
}
```

**SAU**:
```kotlin
onClick = {
    showLogoutDialog = false
    viewModel.signOut()  // ← Gọi Firebase signOut
    onLogout()
}
```

#### e. Thêm loading indicator và error handling
```kotlin
// Hiển thị loading khi đang tải dữ liệu
if (uiState.isLoading) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color(0xFFFF6B9D))
    }
}

// Hiển thị lỗi nếu có
uiState.error?.let { error ->
    LaunchedEffect(error) {
        android.util.Log.e("ProfileScreen", "Error: $error")
    }
}
```

#### f. Chuyển text sang tiếng Việt
- "Logout" → "Đăng xuất"
- "Are you sure..." → "Bạn có chắc muốn đăng xuất..."
- "Cancel" → "Hủy"

## Dữ liệu hiển thị từ Firebase

### Thông tin User
- `displayName` - Tên hiển thị
- `phoneNumber` - Số điện thoại
- `email` - Email
- `linkCode` - Mã liên kết với partner
- `coupleId` - ID của couple (nếu đã liên kết)
- `partnerId` - ID của partner (nếu đã liên kết)

### Thông tin Couple
- `anniversaryDate` - Ngày kỷ niệm
- Tự động tính `daysTogether` từ anniversaryDate

### Thông tin Partner
- `displayName` - Tên partner
- Các thông tin khác của partner

## Cách hoạt động

1. **Khi màn hình được tạo**:
   - ProfileViewModel khởi tạo và gọi `loadUserProfile()`
   - Lấy UID của user đang đăng nhập từ Firebase Auth
   - Truy vấn Firestore collection `users` để lấy thông tin user

2. **Nếu user đã liên kết với partner**:
   - Gọi `loadCoupleInfo()` để lấy thông tin couple
   - Tính số ngày yêu nhau từ `anniversaryDate`
   - Gọi `loadPartnerInfo()` để lấy thông tin partner

3. **Hiển thị dữ liệu**:
   - ProfileScreen đọc `uiState` từ ViewModel
   - Hiển thị loading indicator khi `isLoading = true`
   - Hiển thị dữ liệu khi load xong
   - Hiển thị thông báo lỗi nếu có

4. **Khi đăng xuất**:
   - Gọi `viewModel.signOut()`
   - Firebase Auth đăng xuất user
   - Navigate về màn hình Welcome

## Test

### Để kiểm tra integration:

1. **Build app**:
   ```bash
   ./gradlew assembleDebug
   ```

2. **Đăng nhập với tài khoản đã tạo**:
   - Màn hình Profile sẽ hiển thị tên thật từ Firebase
   - Link code sẽ hiển thị mã thật (6 ký tự)
   - Nếu chưa có partner: "No Partner" và biểu tượng "❓"

3. **Kiểm tra trên Firebase Console**:
   - Vào Firestore → Collection `users`
   - Tìm document của user đang test
   - So sánh `displayName`, `linkCode` với màn hình

### Dữ liệu mẫu trong Firestore:

```json
{
  "displayName": "Nguyễn Văn A",
  "phoneNumber": "+84123456789",
  "email": "84123456789@coupleapp.temp",
  "linkCode": "ABC123",
  "partnerId": null,
  "coupleId": null,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

## Tính năng chưa hoàn thành

- [ ] Upload và hiển thị ảnh đại diện tùy chỉnh
- [ ] Chỉnh sửa profile (EditProfileScreen với Firebase)
- [ ] Hiển thị Snackbar khi có lỗi (hiện tại chỉ log)
- [ ] Pull-to-refresh để cập nhật dữ liệu
- [ ] Cache dữ liệu để giảm số lần truy vấn Firestore

## Lưu ý

1. **Firestore Rules**: Đảm bảo rules cho phép user đọc dữ liệu của mình:
   ```javascript
   allow read, write: if request.auth != null;
   ```

2. **Link Code**: Mỗi user có một link code duy nhất (6 ký tự) để kết nối với partner

3. **Partner Linking**: Hiện tại chỉ hiển thị thông tin partner, chức năng liên kết partner sẽ được thêm sau

4. **Performance**: ViewModel tự động cache state, không load lại khi rotate screen

## Kết quả

✅ Profile Screen đã tích hợp thành công với Firebase
✅ Hiển thị dữ liệu người dùng thực từ Firestore
✅ Xử lý loading state và error state
✅ Logout được xử lý qua Firebase Auth
✅ Build thành công không có lỗi
