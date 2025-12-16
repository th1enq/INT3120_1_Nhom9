# Firebase Storage Rules Configuration

## Lỗi "object does not exist at location"

Lỗi này xảy ra khi:
1. File chưa được upload lên Storage
2. Storage Rules không cho phép đọc/ghi
3. URL không đúng hoặc file bị xóa

## Giải pháp

### 1. Cấu hình Firebase Storage Rules

Vào Firebase Console → Storage → Rules và thay đổi rules:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Allow authenticated users to read/write their locket images
    match /locket_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Allow authenticated users to read/write profile images
    match /profile_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Allow authenticated users to read/write moments images
    match /moments_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Allow authenticated users to read/write place images
    match /place_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Allow authenticated users to read/write chat images
    match /chat_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

### 2. Cấu hình tạm thời để test (KHÔNG KHUYẾN KHÍCH cho production)

Nếu muốn test nhanh, dùng rules này (CHỈ CHO DEVELOPMENT):

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### 3. Kiểm tra trong code

Code đã được update với:
- ✅ Delay 500ms sau khi upload để đảm bảo file available
- ✅ PNG format cho drawings (preserve transparency)
- ✅ Verify upload task succeeded trước khi lấy URL
- ✅ Logging chi tiết để debug

### 4. Cách debug

Để xem logs chi tiết:

```bash
# Xem logs của LocketFirebaseRepo
adb logcat -s LocketFirebaseRepo:D

# Hoặc xem tất cả logs liên quan đến Firebase
adb logcat -s FirebaseStorage:D LocketFirebaseRepo:D
```

### 5. Test flow

1. Chọn/chụp ảnh
2. Kiểm tra log: "sendPhotoLocket: Starting upload"
3. Kiểm tra log: "sendPhotoLocket: Upload successful"
4. Kiểm tra log: "sendPhotoLocket: Photo URL: ..."
5. Kiểm tra log: "sendPhotoLocket: Success! Document ID: ..."

Nếu thấy error ở bước nào thì có thể biết vấn đề ở đâu.

### 6. Kiểm tra Firebase Console

1. Vào Firebase Console → Storage
2. Mở folder `locket_images`
3. Kiểm tra xem có file mới được upload không
4. Click vào file và xem có lấy được download URL không

## Thay đổi trong code

### FirebaseStorageRepository.kt
- Dùng PNG cho drawings
- Verify upload task succeeded
- Better error handling

### LocketFirebaseRepository.kt  
- Thêm delay 500ms sau upload
- Logging chi tiết hơn
- Better error messages
