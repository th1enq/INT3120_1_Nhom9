package com.example.coupleapp.worker

import android.content.ContentUris
import android.content.Context
import android.location.Location
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Worker để quét ảnh mới từ Gallery và tự động thêm vào album địa điểm
 * 
 * Cách hoạt động:
 * 1. Quét ảnh mới được chụp (trong 24h hoặc từ lần quét cuối)
 * 2. Đọc GPS location từ EXIF data của ảnh
 * 3. So sánh với danh sách shared places của couple
 * 4. Nếu ảnh nằm trong bán kính 500m của một place -> thêm vào album
 */
class PhotoSyncWorker(
    context: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PhotoSyncWorker"
        private const val PREFS_NAME = "photo_sync_prefs"
        private const val KEY_LAST_SCAN_TIMESTAMP = "last_scan_timestamp"
        private const val PLACE_MATCH_RADIUS_METERS = 500f // Bán kính so khớp (500m)
        private const val MAX_PHOTO_DIMENSION = 1200 // Max dimension khi resize
        private const val JPEG_QUALITY = 75
    }
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting photo sync work...")
                
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    Log.w(TAG, "User not logged in, skipping photo sync")
                    return@withContext Result.success()
                }
                
                // Get coupleId
                val userDoc = db.collection("users").document(userId).get().await()
                val coupleId = userDoc.getString("coupleId")
                
                if (coupleId.isNullOrEmpty()) {
                    Log.w(TAG, "User not paired, skipping photo sync")
                    return@withContext Result.success()
                }
                
                // Load shared places
                val sharedPlaces = loadSharedPlaces(coupleId)
                if (sharedPlaces.isEmpty()) {
                    Log.d(TAG, "No shared places found, skipping photo sync")
                    return@withContext Result.success()
                }
                
                Log.d(TAG, "Found ${sharedPlaces.size} shared places to check against")
                
                // Scan new photos
                val newPhotosCount = scanAndProcessPhotos(userId, coupleId, sharedPlaces)
                
                Log.d(TAG, "Photo sync completed. Processed $newPhotosCount new photos")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error during photo sync", e)
                Result.retry()
            }
        }
    }
    
    /**
     * Data class cho shared place
     */
    data class SharedPlace(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double
    )
    
    /**
     * Load danh sách shared places từ Firestore
     */
    private suspend fun loadSharedPlaces(coupleId: String): List<SharedPlace> {
        return try {
            val snapshot = db.collection("shared_places")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                val name = doc.getString("placeName") ?: "Unknown"
                
                if (lat != null && lng != null) {
                    SharedPlace(
                        id = doc.id,
                        name = name,
                        latitude = lat,
                        longitude = lng
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading shared places", e)
            emptyList()
        }
    }
    
    /**
     * Quét và xử lý ảnh mới
     */
    private suspend fun scanAndProcessPhotos(
        userId: String,
        coupleId: String,
        sharedPlaces: List<SharedPlace>
    ): Int {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastScanTimestamp = prefs.getLong(KEY_LAST_SCAN_TIMESTAMP, 0L)
        
        // Nếu chưa từng quét, chỉ lấy ảnh trong 24h qua
        val timeThreshold = if (lastScanTimestamp > 0) {
            lastScanTimestamp
        } else {
            System.currentTimeMillis() / 1000 - (24 * 60 * 60)
        }
        
        Log.d(TAG, "Scanning photos added after timestamp: $timeThreshold")
        
        val contentResolver = applicationContext.contentResolver
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(timeThreshold.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        var processedCount = 0
        var latestTimestamp = lastScanTimestamp
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            
            Log.d(TAG, "Found ${cursor.count} new photos to process")
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val name = cursor.getString(nameColumn)
                
                // Update latest timestamp
                if (dateAdded > latestTimestamp) {
                    latestTimestamp = dateAdded
                }
                
                // Create content URI for this photo
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                // Get photo GPS location from EXIF
                val photoLocation = getPhotoLocation(contentUri)
                
                if (photoLocation != null) {
                    Log.d(TAG, "Photo '$name' has GPS: ${photoLocation.first}, ${photoLocation.second}")
                    
                    // Find matching place
                    val matchedPlace = findMatchingPlace(photoLocation, sharedPlaces)
                    
                    if (matchedPlace != null) {
                        Log.d(TAG, "Photo '$name' matches place: ${matchedPlace.name}")
                        
                        // Add photo to this place's album
                        val success = addPhotoToPlace(
                            contentUri = contentUri,
                            placeId = matchedPlace.id,
                            userId = userId
                        )
                        
                        if (success) {
                            processedCount++
                            Log.d(TAG, "Successfully added photo to place ${matchedPlace.name}")
                        }
                    }
                } else {
                    Log.d(TAG, "Photo '$name' has no GPS data")
                }
            }
        }
        
        // Save latest scan timestamp
        prefs.edit().putLong(KEY_LAST_SCAN_TIMESTAMP, latestTimestamp).apply()
        Log.d(TAG, "Updated last scan timestamp to: $latestTimestamp")
        
        return processedCount
    }
    
    /**
     * Đọc GPS location từ EXIF data của ảnh
     * Yêu cầu quyền ACCESS_MEDIA_LOCATION
     */
    private fun getPhotoLocation(uri: Uri): Pair<Double, Double>? {
        return try {
            applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                
                if (exif.getLatLong(latLong)) {
                    Pair(latLong[0].toDouble(), latLong[1].toDouble())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EXIF from $uri", e)
            null
        }
    }
    
    /**
     * Tìm shared place phù hợp với vị trí ảnh
     */
    private fun findMatchingPlace(
        photoLocation: Pair<Double, Double>,
        sharedPlaces: List<SharedPlace>
    ): SharedPlace? {
        val results = FloatArray(1)
        var nearestPlace: SharedPlace? = null
        var nearestDistance = Float.MAX_VALUE
        
        for (place in sharedPlaces) {
            Location.distanceBetween(
                photoLocation.first, photoLocation.second, // Photo GPS
                place.latitude, place.longitude,           // Place GPS
                results
            )
            
            val distanceInMeters = results[0]
            
            // Tìm place gần nhất trong bán kính
            if (distanceInMeters <= PLACE_MATCH_RADIUS_METERS && distanceInMeters < nearestDistance) {
                nearestDistance = distanceInMeters
                nearestPlace = place
            }
        }
        
        if (nearestPlace != null) {
            Log.d(TAG, "Found matching place '${nearestPlace.name}' at ${nearestDistance}m")
        }
        
        return nearestPlace
    }
    
    /**
     * Thêm ảnh vào album của place
     * Chuyển đổi sang base64 và lưu vào Firestore
     */
    private suspend fun addPhotoToPlace(
        contentUri: Uri,
        placeId: String,
        userId: String
    ): Boolean {
        return try {
            // Check if photo already exists in this place's album
            val existingCheck = db.collection("shared_place_photos")
                .whereEqualTo("placeId", placeId)
                .whereEqualTo("originalUri", contentUri.toString())
                .get()
                .await()
            
            if (!existingCheck.isEmpty) {
                Log.d(TAG, "Photo already exists in album, skipping")
                return false
            }
            
            // Convert photo to base64
            val base64Photo = convertPhotoToBase64(contentUri)
            if (base64Photo == null) {
                Log.e(TAG, "Failed to convert photo to base64")
                return false
            }
            
            // Add to shared_place_photos collection
            val photoData = mapOf(
                "placeId" to placeId,
                "photoUrl" to base64Photo,
                "originalUri" to contentUri.toString(),
                "takenAt" to java.util.Date(),
                "takenByUserId" to userId,
                "addedBy" to "auto_sync",
                "caption" to null
            )
            
            db.collection("shared_place_photos").add(photoData).await()
            
            // Update photos count in shared_places
            db.runTransaction { transaction ->
                val placeRef = db.collection("shared_places").document(placeId)
                val placeSnapshot = transaction.get(placeRef)
                val currentCount = placeSnapshot.getLong("photosCount") ?: 0
                transaction.update(placeRef, "photosCount", currentCount + 1)
            }.await()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding photo to place", e)
            false
        }
    }
    
    /**
     * Chuyển đổi ảnh từ URI sang base64
     * Resize và nén để giảm dung lượng
     */
    private fun convertPhotoToBase64(uri: Uri): String? {
        return try {
            applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Đọc và decode bitmap
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 2 // Scale down 50%
                }
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                    ?: return null
                
                // Resize nếu cần
                val scaledBitmap = if (bitmap.width > MAX_PHOTO_DIMENSION || bitmap.height > MAX_PHOTO_DIMENSION) {
                    val scale = MAX_PHOTO_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    bitmap
                }
                
                // Convert to base64
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                val imageBytes = outputStream.toByteArray()
                
                // Cleanup
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
                
                "data:image/jpeg;base64,${Base64.encodeToString(imageBytes, Base64.NO_WRAP)}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting photo to base64", e)
            null
        }
    }
}
