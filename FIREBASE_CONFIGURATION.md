# CẤU HÌNH FIREBASE CHO LOCKET FEATURE

## Lỗi Permission Denied

Nếu gặp lỗi khi đăng nhập hoặc sử dụng tính năng Locket, cần cấu hình Firebase Rules.

---

## 1. FIRESTORE RULES

### Bước 1: Vào Firebase Console
1. Mở https://console.firebase.google.com
2. Chọn project của bạn
3. Vào **Firestore Database** → **Rules**

### Bước 2: Copy rules từ file FIRESTORE_RULES.txt

Hoặc copy trực tiếp từ đây:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Users collection
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Locket posts collection
    match /locket_posts/{postId} {
      allow create: if request.auth != null;
      allow read: if request.auth != null && (
        resource.data.senderId == request.auth.uid ||
        resource.data.receiverId == request.auth.uid
      );
      allow update, delete: if request.auth != null && 
        resource.data.senderId == request.auth.uid;
    }
    
    // Other collections
    match /couples/{coupleId} {
      allow read, write: if request.auth != null;
    }
    
    match /moments/{momentId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### Bước 3: Publish
Click nút **Publish** để áp dụng rules

---

## 2. STORAGE RULES

### Bước 1: Vào Storage Rules
1. Vào **Storage** → **Rules** trong Firebase Console

### Bước 2: Copy rules này:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Locket images
    match /locket_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Profile images
    match /profile_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Moments images
    match /moments_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Place images
    match /place_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Chat images
    match /chat_images/{imageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

### Bước 3: Publish
Click nút **Publish**

---

## 3. RULES ĐƠN GIẢN CHO DEVELOPMENT (không khuyến khích cho production)

Nếu chỉ muốn test nhanh, dùng rules đơn giản này:

### Firestore Rules (allow all):
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

### Storage Rules (allow all):
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

**⚠️ CHÚ Ý:** Rules này CHỈ dùng cho development. Trước khi deploy production, phải dùng rules chi tiết ở trên.

---

## 4. KIỂM TRA SAU KHI CẤU HÌNH

1. **Logout và login lại** trong app
2. **Thử các chức năng:**
   - Gửi emoji ✅
   - Gửi text ✅
   - Chụp ảnh và gửi 📸
   - Vẽ và gửi 🎨
   - Xem lịch sử

3. **Nếu vẫn lỗi**, kiểm tra:
   - User đã đăng nhập chưa? (Firebase Auth)
   - User có `uid` chưa?
   - Firestore collection tên có đúng không? (`users`, `locket_posts`)

---

## 5. DEBUG

Nếu vẫn gặp lỗi, xem logs:

```bash
# Xem logs liên quan đến Firebase
adb logcat | grep -E "Firebase|Firestore|Storage|Locket"

# Hoặc chỉ xem logs của LocketFirebaseRepo
adb logcat -s LocketFirebaseRepo:D
```

Logs sẽ hiển thị:
- ✅ Upload successful
- ✅ Photo URL: https://...
- ✅ Success! Document ID: ...
- ❌ Error: permission denied
- ❌ Error: object does not exist

---

## 6. CHECKLIST

- [ ] Cấu hình Firestore Rules
- [ ] Cấu hình Storage Rules
- [ ] Publish cả 2 rules
- [ ] Logout và login lại trong app
- [ ] Test gửi emoji (đơn giản nhất)
- [ ] Test gửi text
- [ ] Test chụp/chọn ảnh
- [ ] Test vẽ
- [ ] Kiểm tra Firebase Console xem có data không

---

## Tóm tắt

**Firestore Rules:** Cho phép user đọc/ghi collection `users` và `locket_posts`
**Storage Rules:** Cho phép user upload/download ảnh trong các folder `locket_images`, `profile_images`, etc.

Sau khi cấu hình xong, **PHẢI PUBLISH** rules thì mới có hiệu lực!
