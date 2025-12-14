# ✅ Đã sửa xong - Firebase Integration cho Đăng ký/Đăng nhập

## 🔧 Những gì đã làm:

### 1. Thêm Internet Permissions
- ✅ `INTERNET` và `ACCESS_NETWORK_STATE` trong AndroidManifest.xml

### 2. Tạo Firebase Auth Screens
- ✅ `AuthScreens.kt` với 2 wrapper screens:
  - `RegisterWithFirebaseScreen` - Tích hợp đăng ký với Firebase
  - `LoginWithFirebaseScreen` - Tích hợp đăng nhập với Firebase

### 3. Update Navigation
- ✅ `NavGraph.kt` giờ sử dụng Firebase screens thay vì mock screens
- ✅ Đăng ký/đăng nhập thực sự tạo user trong Firebase

### 4. Thêm Logging
- ✅ `AuthViewModel` giờ có logs để debug
- ✅ `CoupleApplication` có log khi khởi tạo Firebase

### 5. Cấu hình Firestore
- ✅ Enable offline persistence trong `CoupleApplication`

## 🚀 Cách test:

### Bước 1: Đảm bảo Firebase Project đã setup đúng

**QUAN TRỌNG**: Bạn PHẢI có file `google-services.json` THỰC từ Firebase Console!

1. Vào https://console.firebase.google.com/
2. Tạo project (hoặc dùng project có sẵn)
3. Thêm Android app với package: `com.example.coupleapp`
4. Download `google-services.json`
5. Copy vào: `app/google-services.json` (thay thế file template)

### Bước 2: Enable Firebase Services

Trong Firebase Console:

1. **Authentication**:
   - Vào Authentication > Sign-in method
   - Enable "Email/Password"

2. **Firestore**:
   - Vào Firestore Database
   - Create database
   - Location: asia-southeast1 (Singapore)
   - Start in **test mode** (cho development)

3. **Firestore Rules** (quan trọng!):
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

### Bước 3: Install và test app

```bash
# Build và install
./gradlew installDebug

# Hoặc trong Android Studio: Run > Run 'app'
```

### Bước 4: Đăng ký tài khoản mới

1. Mở app
2. Click "Get Started"
3. Click "Register"
4. Nhập thông tin:
   - Họ tên: Test User
   - Ngày sinh: 01/01/2000  
   - SĐT: 0123456789
   - Mật khẩu: test123456
   - Xác nhận: test123456
5. Click "Create Account"
6. **Đợi loading indicator**

### Bước 5: Kiểm tra Firebase Console

Sau khi đăng ký:

1. **Authentication** > Users
   - Bạn sẽ thấy user mới với email: `0123456789@coupleapp.temp`

2. **Firestore Database**
   - Sẽ có collection `users`
   - Document với ID là user UID
   - Chứa thông tin:
     - displayName
     - phoneNumber
     - dateOfBirth
     - linkCode (mã 6 số)
     - createdAt, updatedAt timestamps

## 📊 Xem Logs để Debug

Trong Android Studio Logcat, filter bởi:
- `AuthViewModel` - Xem quá trình đăng ký
- `CoupleApplication` - Xem Firebase initialization

Logs bạn sẽ thấy:
```
CoupleApplication: Firebase initialized successfully
AuthViewModel: Starting registration for: 0123456789@coupleapp.temp
AuthViewModel: Auth user created with UID: xxxxx
AuthViewModel: Creating Firestore document for user: xxxxx
AuthViewModel: User document created successfully
```

## ❗ Nếu vẫn không thấy collection:

### Kiểm tra 1: File google-services.json
```bash
# File phải có thông tin thực, không phải template
cat app/google-services.json | grep project_id
# Phải show project_id thực của bạn, không phải "your-project-id"
```

### Kiểm tra 2: Firestore Rules
Vào Firebase Console > Firestore Database > Rules
Đảm bảo rules cho phép write khi có auth:
```javascript
allow read, write: if request.auth != null;
```

### Kiểm tra 3: Xem Logcat
Tìm lỗi trong Logcat:
- `PERMISSION_DENIED` → Sửa Firestore rules
- `Default FirebaseApp is not initialized` → Sửa google-services.json
- `No internet` → Bật WiFi/Mobile data

## 🧪 Test Login

Sau khi đã đăng ký:
1. Quay lại màn hình Welcome
2. Click "Login"  
3. Nhập SĐT và mật khẩu đã đăng ký
4. Click "Sign In"
5. Sẽ vào Home screen

## 📱 Cấu trúc dữ liệu

Sau khi đăng ký thành công, trong Firestore:

```
Collection: users
└── Document: [UID của user]
    ├── id: "user_uid"
    ├── email: "0123456789@coupleapp.temp"
    ├── phoneNumber: "0123456789"
    ├── displayName: "Test User"
    ├── dateOfBirth: "01/01/2000"
    ├── gender: ""
    ├── linkCode: "123456" (random 6 digits)
    ├── coupleId: null
    ├── partnerId: null
    ├── profileImageUrl: ""
    ├── createdAt: [timestamp]
    └── updatedAt: [timestamp]
```

## 📖 Tài liệu khác

- `FIREBASE_SETUP.md` - Hướng dẫn setup Firebase chi tiết
- `FIREBASE_USAGE_EXAMPLES.md` - Code examples cho các features khác
- `FIREBASE_DEBUG_GUIDE.md` - Troubleshooting guide
- `FIREBASE_INTEGRATION_SUMMARY.md` - Tổng quan về integration

## 🎯 Next Steps

Giờ bạn có thể:
1. ✅ Đăng ký user mới
2. ✅ Đăng nhập
3. ✅ Data được lưu vào Firestore
4. 🔄 Tiếp tục tích hợp các features khác (Chat, Locket, Sleep Tracker, etc.)

---

**Lưu ý cuối**: Đừng quên thay file `app/google-services.json` bằng file thực từ Firebase Console của bạn!
