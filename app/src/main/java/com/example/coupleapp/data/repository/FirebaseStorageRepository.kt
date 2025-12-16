package com.example.coupleapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Repository for image storage operations
 * Uses Firestore to store compressed base64 images instead of Firebase Storage
 * This allows the app to work without upgrading to Blaze plan
 */
class FirebaseStorageRepository {
    private val db = FirebaseFirestore.getInstance()
    
    // Collection for storing images
    companion object {
        const val IMAGES_COLLECTION = "images"
        const val PROFILE_IMAGES_PATH = "profile_images"
        const val LOCKET_IMAGES_PATH = "locket_images"
        const val MOMENTS_IMAGES_PATH = "moments_images"
        const val PLACE_IMAGES_PATH = "place_images"
        const val CHAT_IMAGES_PATH = "chat_images"
        
        // Image compression settings
        const val MAX_IMAGE_WIDTH = 800  // Max width in pixels
        const val MAX_IMAGE_HEIGHT = 800 // Max height in pixels
        const val COMPRESSION_QUALITY = 60 // JPEG quality (0-100)
        const val MAX_BASE64_SIZE = 900_000 // ~900KB to stay under 1MB Firestore limit
        
        // Prefix for base64 data URLs
        const val BASE64_PREFIX = "data:image/jpeg;base64,"
    }

    /**
     * Upload image from URI - compresses and stores in Firestore as base64
     */
    suspend fun uploadImage(uri: Uri, path: String, filename: String): Result<String> {
        return Result.failure(Exception("Use uploadImageWithContext instead"))
    }
    
    /**
     * Upload image from URI with context - compresses and stores in Firestore as base64
     */
    suspend fun uploadImageWithContext(context: Context, uri: Uri, path: String, filename: String): Result<String> {
        return try {
            android.util.Log.d("FirebaseStorageRepository", "Starting image upload: $path/$filename")
            android.util.Log.d("FirebaseStorageRepository", "URI: $uri")
            
            // Read and compress the image
            val base64Image = compressAndEncodeImage(context, uri)
            
            if (base64Image == null) {
                android.util.Log.e("FirebaseStorageRepository", "Failed to compress image")
                return Result.failure(Exception("Failed to compress image"))
            }
            
            android.util.Log.d("FirebaseStorageRepository", "Compressed image size: ${base64Image.length} chars")
            
            // Generate unique ID for the image
            val imageId = UUID.randomUUID().toString()
            
            // Store in Firestore
            val imageData = mapOf(
                "id" to imageId,
                "path" to path,
                "filename" to filename,
                "data" to base64Image,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            db.collection(IMAGES_COLLECTION)
                .document(imageId)
                .set(imageData)
                .await()
            
            // Return a special URL format that we can recognize later
            val imageUrl = "$BASE64_PREFIX$imageId"
            android.util.Log.d("FirebaseStorageRepository", "Image saved successfully: $imageId")
            
            Result.success(imageUrl)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseStorageRepository", "Upload failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Compress image and encode to base64
     */
    private fun compressAndEncodeImage(context: Context, uri: Uri): String? {
        return try {
            // Read the image
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (originalBitmap == null) {
                android.util.Log.e("FirebaseStorageRepository", "Failed to decode bitmap")
                return null
            }
            
            android.util.Log.d("FirebaseStorageRepository", "Original size: ${originalBitmap.width}x${originalBitmap.height}")
            
            // Calculate new dimensions while maintaining aspect ratio
            val scaledBitmap = scaleBitmap(originalBitmap, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            android.util.Log.d("FirebaseStorageRepository", "Scaled size: ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // Compress to JPEG with progressive quality reduction if needed
            var quality = COMPRESSION_QUALITY
            var base64: String
            
            do {
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val bytes = outputStream.toByteArray()
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                android.util.Log.d("FirebaseStorageRepository", "Quality: $quality, Size: ${base64.length} chars")
                
                if (base64.length <= MAX_BASE64_SIZE) {
                    break
                }
                
                quality -= 10
            } while (quality > 10)
            
            // Clean up
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()
            
            base64
        } catch (e: Exception) {
            android.util.Log.e("FirebaseStorageRepository", "Error compressing image", e)
            null
        }
    }
    
    /**
     * Scale bitmap to fit within max dimensions while maintaining aspect ratio
     */
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val ratioWidth = maxWidth.toFloat() / width
        val ratioHeight = maxHeight.toFloat() / height
        val ratio = minOf(ratioWidth, ratioHeight)
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Get base64 image data from Firestore by image ID
     */
    suspend fun getImageData(imageId: String): Result<String> {
        return try {
            val doc = db.collection(IMAGES_COLLECTION).document(imageId).get().await()
            
            if (!doc.exists()) {
                return Result.failure(Exception("Image not found"))
            }
            
            val data = doc.getString("data")
            if (data == null) {
                return Result.failure(Exception("Image data is empty"))
            }
            
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if a URL is a base64 image reference
     */
    fun isBase64ImageUrl(url: String): Boolean {
        return url.startsWith(BASE64_PREFIX)
    }
    
    /**
     * Extract image ID from base64 URL
     */
    fun extractImageId(url: String): String? {
        return if (isBase64ImageUrl(url)) {
            url.removePrefix(BASE64_PREFIX)
        } else {
            null
        }
    }

    /**
     * Upload image from Bitmap - compresses and stores in Firestore
     */
    suspend fun uploadBitmap(bitmap: Bitmap, path: String, filename: String, quality: Int = COMPRESSION_QUALITY): Result<String> {
        return try {
            // Scale if needed
            val scaledBitmap = scaleBitmap(bitmap, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            // Clean up
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            
            // Generate unique ID
            val imageId = UUID.randomUUID().toString()
            
            // Store in Firestore
            val imageData = mapOf(
                "id" to imageId,
                "path" to path,
                "filename" to filename,
                "data" to base64,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            db.collection(IMAGES_COLLECTION).document(imageId).set(imageData).await()
            
            Result.success("$BASE64_PREFIX$imageId")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload raw bytes
     */
    suspend fun uploadBytes(bytes: ByteArray, path: String, filename: String): Result<String> {
        return try {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val imageId = UUID.randomUUID().toString()
            
            val imageData = mapOf(
                "id" to imageId,
                "path" to path,
                "filename" to filename,
                "data" to base64,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            db.collection(IMAGES_COLLECTION).document(imageId).set(imageData).await()
            
            Result.success("$BASE64_PREFIX$imageId")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an image
     */
    suspend fun deleteFile(fileUrl: String): Result<Unit> {
        return try {
            val imageId = extractImageId(fileUrl)
            if (imageId != null) {
                db.collection(IMAGES_COLLECTION).document(imageId).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a file by path (not supported in Firestore approach)
     */
    suspend fun deleteFileByPath(path: String): Result<Unit> {
        return Result.success(Unit) // No-op for Firestore
    }

    /**
     * Get download URL for a file path (not applicable for base64)
     */
    suspend fun getDownloadUrl(path: String): Result<String> {
        return Result.failure(Exception("Not supported for base64 images"))
    }

    /**
     * List all files in a directory (returns empty for Firestore approach)
     */
    suspend fun listFiles(path: String): Result<List<String>> {
        return try {
            val snapshot = db.collection(IMAGES_COLLECTION)
                .whereEqualTo("path", path)
                .get()
                .await()
            
            val ids = snapshot.documents.mapNotNull { it.getString("id") }
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
