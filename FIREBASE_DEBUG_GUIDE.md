# Hướng dẫn Debug và Kiểm tra Firebase

## Vấn đề: Không thấy collection trong Firebase sau khi đăng ký

### ✅ Đã sửa các vấn đề sau:

1. **Thêm INTERNET permission** vào `AndroidManifest.xml`
2. **Tạo AuthScreens.kt** - Wrapper screens tích hợp Firebase
3. **Cập nhật NavGraph.kt** - Sử dụng Firebase authentication
4. **Thêm logging** vào AuthViewModel để debug
5. **Cấu hình Firestore settings** trong CoupleApplication

## 🔍 Các bước kiểm tra

### Bước 1: Đảm bảo google-services.json đúng

```bash
# Kiểm tra file có tồn tại
ls -la app/google-services.json

# File này phải từ Firebase Console, không phải template
```

**CHÚ Ý**: File `google-services.json` hiện tại là TEMPLATE. Bạn phải:
1. Vào [Firebase Console](https://console.firebase.google.com/)
2. Chọn project của bạn
3. Project Settings > Your apps > Download google-services.json
4. Thay thế file trong `app/google-services.json`

### Bước 2: Kiểm tra Firebase Console

1. Vào [Firebase Console](https://console.firebase.google.com/)
2. Chọn project của bạn
3. Kiểm tra các mục sau:

#### Authentication:
- Vào **Authentication** > **Users**
- Sau khi đăng ký, bạn nên thấy user mới ở đây
- Nếu thấy user → Authentication hoạt động ✅
- Nếu không thấy → Có lỗi khi tạo user

#### Firestore:
- Vào **Firestore Database**
- Xem có collection `users` không
- Nếu có → Hoạt động tốt ✅
- Nếu không có → Có lỗi khi ghi vào Firestore

### Bước 3: Kiểm tra Firestore Rules

Vào **Firestore Database** > **Rules**, đảm bảo rules cho phép ghi:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

Hoặc cho development (tạm thời):
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true; // ⚠️ CHỈ CHO TEST
    }
  }
}
```

### Bước 4: Build lại project

```bash
# Clean project
./gradlew clean

# Build lại
./gradlew assembleDebug

# Hoặc trong Android Studio: Build > Clean Project > Rebuild Project
```

### Bước 5: Xem Logcat

Khi chạy app và đăng ký, xem Logcat trong Android Studio:

```
Filter: "AuthViewModel" hoặc "CoupleApplication"
```

Bạn sẽ thấy các log:
```
CoupleApplication: Firebase initialized successfully
AuthViewModel: Starting registration for: 0123456789@coupleapp.temp
AuthViewModel: Auth user created with UID: xxxxx
AuthViewModel: Creating Firestore document for user: xxxxx
AuthViewModel: User document created successfully
```

### Bước 6: Test đăng ký

1. Mở app
2. Click "Register"
3. Nhập thông tin:
   - Họ tên: Test User
   - Ngày sinh: 01/01/2000
   - SĐT: 0123456789
   - Mật khẩu: test123456
   - Xác nhận: test123456
4. Click "Create Account"
5. Đợi loading indicator
6. Kiểm tra Firebase Console

## ❗ Các lỗi thường gặp

### Lỗi 1: "Default FirebaseApp is not initialized"
**Nguyên nhân**: google-services.json không đúng hoặc thiếu
**Giải pháp**: Thay file google-services.json bằng file thực từ Firebase Console

### Lỗi 2: "PERMISSION_DENIED: Missing or insufficient permissions"
**Nguyên nhân**: Firestore rules không cho phép ghi
**Giải pháp**: Cập nhật Firestore rules như ở Bước 3

### Lỗi 3: "User created but no Firestore document"
**Nguyên nhân**: Authentication thành công nhưng Firestore bị lỗi
**Giải pháp**: 
- Kiểm tra Firestore rules
- Xem Logcat để biết lỗi cụ thể
- Đảm bảo Firestore database đã được tạo

### Lỗi 4: "No internet connection"
**Nguyên nhân**: Emulator/device không có mạng
**Giải pháp**: 
- Emulator: Kiểm tra internet settings
- Real device: Bật WiFi/Mobile data

### Lỗi 5: "Email already in use"
**Nguyên nhân**: Số điện thoại đã được đăng ký
**Giải pháp**: 
- Dùng số điện thoại khác
- Hoặc xóa user trong Firebase Console > Authentication

## 🧪 Test với Firebase Emulator (Optional)

Nếu muốn test local không cần internet:

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# Init emulators
firebase init emulators

# Start emulators
firebase emulators:start
```

Thêm vào `CoupleApplication.kt`:
```kotlin
import com.example.coupleapp.BuildConfig

override fun onCreate() {
    super.onCreate()
    FirebaseApp.initializeApp(this)
    
    // Use emulators for debug builds
    if (BuildConfig.DEBUG) {
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    }
    
    // ... rest of code
}
```

## 📊 Cấu trúc dữ liệu mong đợi

Sau khi đăng ký thành công, trong Firestore bạn sẽ thấy:

```
Collection: users
├── Document: [user_id]
    ├── id: "user_id"
    ├── email: "0123456789@coupleapp.temp"
    ├── phoneNumber: "0123456789"
    ├── displayName: "Test User"
    ├── dateOfBirth: "01/01/2000"
    ├── gender: ""
    ├── linkCode: "123456"
    ├── coupleId: null
    ├── partnerId: null
    ├── createdAt: [timestamp]
    └── updatedAt: [timestamp]
```

## 🎯 Checklist trước khi test

- [ ] File `google-services.json` từ Firebase Console (không phải template)
- [ ] Firebase project đã tạo
- [ ] Authentication đã enable Email/Password
- [ ] Firestore database đã tạo
- [ ] Firestore rules cho phép read/write với auth
- [ ] Internet permission trong AndroidManifest
- [ ] Project đã build lại (Clean + Rebuild)
- [ ] Emulator/device có kết nối internet

## 📞 Nếu vẫn không được

Gửi cho tôi:
1. Screenshot Logcat khi đăng ký
2. Screenshot Firebase Console > Authentication
3. Screenshot Firebase Console > Firestore Database
4. Screenshot Firestore Rules

Tôi sẽ giúp debug cụ thể hơn!
