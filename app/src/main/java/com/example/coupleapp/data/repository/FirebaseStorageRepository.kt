package com.example.coupleapp.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Repository for Firebase Storage operations
 */
class FirebaseStorageRepository {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val storageRef: StorageReference = storage.reference

    // Storage paths
    companion object {
        const val PROFILE_IMAGES_PATH = "profile_images"
        const val LOCKET_IMAGES_PATH = "locket_images"
        const val MOMENTS_IMAGES_PATH = "moments_images"
        const val PLACE_IMAGES_PATH = "place_images"
        const val CHAT_IMAGES_PATH = "chat_images"
    }

    /**
     * Upload image from URI
     */
    suspend fun uploadImage(uri: Uri, path: String, filename: String): Result<String> {
        return try {
            val imageRef = storageRef.child("$path/$filename")
            imageRef.putFile(uri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload image from Bitmap
     */
    suspend fun uploadBitmap(bitmap: Bitmap, path: String, filename: String, quality: Int = 80): Result<String> {
        return try {
            val baos = ByteArrayOutputStream()
            // Use PNG for drawings (preserve transparency), JPEG for photos
            val format = if (filename.contains("drawing")) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(format, quality, baos)
            val data = baos.toByteArray()

            val imageRef = storageRef.child("$path/$filename")
            val uploadTask = imageRef.putBytes(data).await()
            
            // Verify upload succeeded
            if (uploadTask.task.isSuccessful) {
                val downloadUrl = imageRef.downloadUrl.await()
                Result.success(downloadUrl.toString())
            } else {
                Result.failure(uploadTask.task.exception ?: Exception("Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload raw bytes
     */
    suspend fun uploadBytes(bytes: ByteArray, path: String, filename: String): Result<String> {
        return try {
            val fileRef = storageRef.child("$path/$filename")
            fileRef.putBytes(bytes).await()
            val downloadUrl = fileRef.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a file
     */
    suspend fun deleteFile(fileUrl: String): Result<Unit> {
        return try {
            val fileRef = storage.getReferenceFromUrl(fileUrl)
            fileRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a file by path
     */
    suspend fun deleteFileByPath(path: String): Result<Unit> {
        return try {
            storageRef.child(path).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get download URL for a file path
     */
    suspend fun getDownloadUrl(path: String): Result<String> {
        return try {
            val url = storageRef.child(path).downloadUrl.await()
            Result.success(url.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all files in a directory
     */
    suspend fun listFiles(path: String): Result<List<StorageReference>> {
        return try {
            val listResult = storageRef.child(path).listAll().await()
            Result.success(listResult.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
