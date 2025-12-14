# Firebase Setup Guide - CoupleApp

## Tổng quan
CoupleApp đã được tích hợp Firebase để làm backend với các tính năng:
- **Firebase Authentication**: Đăng nhập/Đăng ký
- **Cloud Firestore**: Database lưu trữ dữ liệu
- **Firebase Storage**: Lưu trữ ảnh và file
- **Firebase Analytics**: Phân tích người dùng
- **Firebase Cloud Messaging**: Push notifications

## Bước 1: Tạo Firebase Project

1. Truy cập [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add project"** hoặc **"Create a project"**
3. Nhập tên project: `CoupleApp` (hoặc tên bạn muốn)
4. Chọn có/không sử dụng Google Analytics (khuyến nghị: có)
5. Chọn hoặc tạo Google Analytics account
6. Click **"Create project"** và đợi Firebase khởi tạo

## Bước 2: Thêm Android App vào Firebase Project

1. Trong Firebase Console, click icon Android để thêm Android app
2. Điền thông tin:
   - **Android package name**: `com.example.coupleapp` (bắt buộc khớp với package trong app)
   - **App nickname**: `CoupleApp` (tùy chọn)
   - **Debug signing certificate SHA-1**: (tùy chọn, cần cho Google Sign-In)

### Lấy SHA-1 Certificate (nếu cần):
```bash
# Trên Linux/Mac
./gradlew signingReport

# Hoặc dùng keytool
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

3. Click **"Register app"**

## Bước 3: Download google-services.json

1. Firebase sẽ tự động tạo file `google-services.json`
2. Click **"Download google-services.json"**
3. **QUAN TRỌNG**: Copy file này vào thư mục:
   ```
   INT3120_1_Nhom9/app/google-services.json
   ```
   
   **CHÚ Ý**: File này đã có template trong project, bạn cần THAY THẾ bằng file thực từ Firebase Console.

4. Đảm bảo file `google-services.json` chứa đúng thông tin từ Firebase project của bạn.

## Bước 4: Cấu hình Firebase Services

### 4.1. Enable Authentication

1. Trong Firebase Console, vào **Authentication** > **Sign-in method**
2. Enable các phương thức đăng nhập:
   - **Email/Password**: Enable
   - **Phone** (tùy chọn): Enable nếu cần đăng nhập bằng SĐT

### 4.2. Setup Cloud Firestore

1. Vào **Firestore Database** > **Create database**
2. Chọn location: `asia-southeast1` (Singapore - gần VN nhất)
3. Chọn mode: **Start in test mode** (cho development)
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```
   **LƯU Ý**: Sau khi production, cần update rules cho bảo mật hơn.

### 4.3. Setup Firebase Storage

1. Vào **Storage** > **Get Started**
2. Chọn location: `asia-southeast1` 
3. Security rules (test mode):
   ```
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /{allPaths=**} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

### 4.4. Enable Firebase Analytics (nếu chọn)

Firebase Analytics tự động được enable khi tạo project.

### 4.5. Setup Cloud Messaging (cho push notifications)

1. Vào **Project Settings** > **Cloud Messaging**
2. Copy **Server key** để dùng sau này

## Bước 5: Cấu trúc Database (Firestore Collections)

App đã được thiết kế với các collections sau:

### Collections:
- **users**: Thông tin người dùng
  ```
  {
    id: string,
    email: string,
    phoneNumber: string,
    displayName: string,
    profileImageUrl: string,
    dateOfBirth: string,
    gender: string,
    coupleId: string?,
    partnerId: string?,
    linkCode: string,
    createdAt: timestamp,
    updatedAt: timestamp
  }
  ```

- **couples**: Thông tin cặp đôi
- **messages**: Tin nhắn chat
- **moments**: Hoạt động timeline
- **sleep_records**: Dữ liệu ngủ
- **locket_posts**: Bài đăng Locket
- **locations**: Vị trí người dùng
- **shared_places**: Địa điểm chung
- **qa_questions**: Câu hỏi Q&A
- **calendar_events**: Sự kiện lịch

## Bước 6: Build và Test

1. Sync Gradle:
   ```bash
   ./gradlew build
   ```

2. Chạy app:
   ```bash
   ./gradlew installDebug
   ```

3. Test đăng ký tài khoản mới để xem Firebase hoạt động

## Cấu trúc Code

### Repositories
- `FirebaseAuthRepository`: Xử lý authentication
- `FirebaseFirestoreRepository`: Xử lý database operations
- `FirebaseStorageRepository`: Xử lý file upload/download

### ViewModels
- `AuthViewModel`: Quản lý trạng thái authentication
- Các ViewModel khác sẽ tích hợp với Firebase repositories

### Models
- `FirebaseModels.kt`: Chứa các data class cho Firestore

## Troubleshooting

### Lỗi: "google-services.json is missing"
- Đảm bảo file `google-services.json` nằm trong thư mục `app/`
- File phải có đúng tên, không thêm số như `google-services(1).json`

### Lỗi: "Default FirebaseApp is not initialized"
- Kiểm tra `AndroidManifest.xml` có `android:name=".CoupleApplication"`
- Kiểm tra file `CoupleApplication.kt` đã khởi tạo Firebase

### Lỗi build: "Plugin with id 'com.google.gms.google-services' not found"
- Sync lại Gradle
- Clean project: `./gradlew clean`

### Lỗi Firestore: "PERMISSION_DENIED"
- Kiểm tra Firestore rules
- Đảm bảo user đã đăng nhập (request.auth != null)

## Security Best Practices

### Production Firestore Rules:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Couples data accessible by both partners
    match /couples/{coupleId} {
      allow read, write: if request.auth != null && 
        (get(/databases/$(database)/documents/users/$(request.auth.uid)).data.coupleId == coupleId);
    }
    
    // Messages readable by couple members
    match /messages/{messageId} {
      allow read, write: if request.auth != null && 
        (get(/databases/$(database)/documents/users/$(request.auth.uid)).data.coupleId == resource.data.coupleId);
    }
  }
}
```

### Production Storage Rules:
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /profile_images/{userId}/{allPaths=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    match /locket_images/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Tài nguyên tham khảo

- [Firebase Android Documentation](https://firebase.google.com/docs/android/setup)
- [Firestore Documentation](https://firebase.google.com/docs/firestore)
- [Firebase Authentication](https://firebase.google.com/docs/auth)
- [Firebase Storage](https://firebase.google.com/docs/storage)

## Hỗ trợ

Nếu gặp vấn đề, kiểm tra:
1. [Firebase Status Dashboard](https://status.firebase.google.com/)
2. Android Studio Logcat để xem lỗi chi tiết
3. Firebase Console > Usage để xem requests

---

**Lưu ý**: File `google-services.json` chứa thông tin nhạy cảm. KHÔNG commit file này lên Git public repository. Đã thêm vào `.gitignore`.
