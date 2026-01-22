package com.example.coupleapp.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.coupleapp.data.model.LocationCoordinate
import com.example.coupleapp.data.model.LocationType
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Background worker for periodic location updates when app is in background or closed.
 * This is battery-efficient compared to continuous foreground service.
 * 
 * Features:
 * - Updates location every 15-30 minutes when app is closed
 * - Intelligently merges location history to avoid duplicates
 * - Respects battery level - reduces updates when battery is low
 * - Only works when user is paired with a partner
 */
class BackgroundLocationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "BackgroundLocationWorker"
        const val WORK_NAME = "background_location_update"
        
        // PHƯƠNG ÁN B: Khi app bị kill, worker này là cơ chế DUY NHẤT đáng tin cậy
        // Chạy mỗi 15 phút để lấy location history
        private const val MIN_UPDATE_INTERVAL_MINUTES = 15L // 15 phút - cân bằng giữa history accuracy và battery
        private const val FLEX_INTERVAL_MINUTES = 5L // Cho phép ±5 phút flexibility
        private const val LOW_BATTERY_THRESHOLD = 20 // Skip khi pin < 20%
        
        // Distance threshold for considering same location (meters)
        private const val SAME_LOCATION_THRESHOLD_METERS = 300.0
        
        // Minimum stay duration to record in history (minutes)
        // User must stay at location for at least this long before creating history entry
        private const val MIN_HISTORY_DURATION_MINUTES = 10
        
        // SharedPreferences key for tracking pending location
        private const val PREF_PENDING_LOCATION = "pending_location_for_history"
        private const val PREF_PENDING_LOCATION_TIME = "pending_location_time"
        private const val PREF_PENDING_LOCATION_LAT = "pending_location_lat"
        private const val PREF_PENDING_LOCATION_LNG = "pending_location_lng"
        private const val PREF_PENDING_LOCATION_ADDRESS = "pending_location_address"
        
        /**
         * Schedule background location updates.
         * Đây là cơ chế chính khi app bị kill để tracking location history.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // We handle battery internally
                .build()
            
            val request = PeriodicWorkRequestBuilder<BackgroundLocationWorker>(
                repeatInterval = MIN_UPDATE_INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = FLEX_INTERVAL_MINUTES,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            
            Log.d(TAG, "Background location worker scheduled every $MIN_UPDATE_INTERVAL_MINUTES minutes")
        }
        
        /**
         * Cancel background location updates
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            
            Log.d(TAG, "Background location worker cancelled")
        }
        
        /**
         * Check if worker is scheduled
         */
        suspend fun isScheduled(context: Context): Boolean {
            return try {
                val workInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .await()
                workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Background location update started")
        
        try {
            // Check if user is logged in
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "User not logged in, skipping update")
                return@withContext Result.success()
            }
            
            // Check location permission
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission not granted")
                return@withContext Result.success()
            }
            
            // Get user data
            val userId = currentUser.uid
            val userDoc = db.collection("users").document(userId).get().await()
            val coupleId = userDoc.getString("coupleId") ?: ""
            
            if (coupleId.isEmpty()) {
                Log.d(TAG, "User not paired, skipping update")
                return@withContext Result.success()
            }
            
            val userName = userDoc.getString("displayName") ?: "User"
            val avatarUrl = userDoc.getString("profileImageUrl") ?: ""
            
            // Check battery level - reduce frequency if low
            val batteryLevel = getBatteryLevel()
            if (batteryLevel < LOW_BATTERY_THRESHOLD) {
                Log.d(TAG, "Battery low ($batteryLevel%), skipping this update")
                return@withContext Result.success()
            }
            
            // Get current location
            val location = getCurrentLocation()
            if (location == null) {
                Log.w(TAG, "Could not get location")
                return@withContext Result.retry()
            }
            
            val coordinate = LocationCoordinate(location.latitude, location.longitude)
            val address = getAddressFromCoordinate(coordinate)
            
            Log.d(TAG, "Got location: ${coordinate.latitude}, ${coordinate.longitude}")
            
            // Upload location to Firebase
            uploadLocation(userId, userName, avatarUrl, coupleId, coordinate, address, batteryLevel)
            
            // Check and update location history intelligently
            updateLocationHistory(userId, coupleId, coordinate, address)
            
            Log.d(TAG, "Background location update completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in background location update", e)
            Result.retry()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private suspend fun getCurrentLocation(): android.location.Location? {
        return try {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
    
    private fun uploadLocation(
        userId: String,
        userName: String,
        avatarUrl: String,
        coupleId: String,
        coordinate: LocationCoordinate,
        address: String,
        batteryLevel: Int
    ) {
        val documentId = "${coupleId}_${userId}"
        
        val locationData = mapOf(
            "userId" to userId,
            "coupleId" to coupleId,
            "userName" to userName,
            "avatarUrl" to avatarUrl,
            "latitude" to coordinate.latitude,
            "longitude" to coordinate.longitude,
            "address" to address,
            "batteryLevel" to batteryLevel,
            "isOnline" to true,
            "timestamp" to Date(),
            "source" to "background_worker"
        )
        
        db.collection("locations")
            .document(documentId)
            .set(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "Location uploaded: $documentId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload location", e)
            }
    }
    
    /**
     * Intelligently update location history using a "pending location" approach.
     * 
     * LOGIC:
     * 1. Khi worker chạy, check xem có "pending location" (vị trí đang chờ xác nhận) không
     * 2. Nếu có pending location và user VẪN Ở ĐÓ (trong 200m) → Confirm & tạo history entry
     * 3. Nếu có pending location nhưng user ĐÃ RỜI ĐI → Xóa pending (user chỉ đi qua, không ở lại)
     * 4. Nếu không có pending và đang ở vị trí mới → Lưu làm pending, đợi lần sau xác nhận
     * 
     * Cách này đảm bảo:
     * - User phải ở một chỗ ít nhất 2 lần worker chạy (~30 phút) mới được ghi vào history
     * - Không tạo entry khi user đang di chuyển trên đường
     */
    private suspend fun updateLocationHistory(
        userId: String,
        coupleId: String,
        coordinate: LocationCoordinate,
        address: String
    ) {
        try {
            val prefs = context.getSharedPreferences("background_location_worker", Context.MODE_PRIVATE)
            
            // Get pending location info
            val pendingLat = prefs.getFloat(PREF_PENDING_LOCATION_LAT, 0f).toDouble()
            val pendingLng = prefs.getFloat(PREF_PENDING_LOCATION_LNG, 0f).toDouble()
            val pendingTime = prefs.getLong(PREF_PENDING_LOCATION_TIME, 0L)
            val pendingAddress = prefs.getString(PREF_PENDING_LOCATION_ADDRESS, "") ?: ""
            val hasPendingLocation = pendingLat != 0.0 && pendingLng != 0.0 && pendingTime > 0
            
            if (hasPendingLocation) {
                val pendingCoordinate = LocationCoordinate(pendingLat, pendingLng)
                val distanceFromPending = calculateDistance(coordinate, pendingCoordinate)
                
                if (distanceFromPending <= SAME_LOCATION_THRESHOLD_METERS) {
                    // User is STILL at the pending location → Confirm and create/update history entry
                    val stayDurationMinutes = ((System.currentTimeMillis() - pendingTime) / 60_000).toInt()
                    
                    if (stayDurationMinutes >= MIN_HISTORY_DURATION_MINUTES) {
                        // Long enough stay - create or update history entry
                        createOrUpdateHistoryEntry(userId, coupleId, pendingCoordinate, pendingAddress, pendingTime)
                        Log.d(TAG, "✅ Confirmed location stay: $pendingAddress, duration: ${stayDurationMinutes}min")
                    } else {
                        Log.d(TAG, "⏳ Still at pending location, waiting for min duration: ${stayDurationMinutes}/${MIN_HISTORY_DURATION_MINUTES}min")
                    }
                } else {
                    // User has MOVED AWAY from pending location
                    // Check if they stayed long enough before leaving
                    val stayDurationMinutes = ((System.currentTimeMillis() - pendingTime) / 60_000).toInt()
                    
                    if (stayDurationMinutes >= MIN_HISTORY_DURATION_MINUTES) {
                        // They stayed long enough - save the history entry with departure time
                        createOrUpdateHistoryEntry(userId, coupleId, pendingCoordinate, pendingAddress, pendingTime, departed = true)
                        Log.d(TAG, "✅ User left location after ${stayDurationMinutes}min: $pendingAddress")
                    } else {
                        // Too short - user was just passing through, discard
                        Log.d(TAG, "🚶 User moved away after only ${stayDurationMinutes}min - discarding (was just passing through)")
                    }
                    
                    // Clear pending and set new pending location
                    savePendingLocation(prefs, coordinate, address)
                }
            } else {
                // No pending location - check if we're at an existing history location or new place
                val existingEntry = findExistingHistoryEntry(userId, coupleId, coordinate)
                
                if (existingEntry != null) {
                    // Already have history for this location - just update departure time
                    updateExistingHistoryEntry(existingEntry, coordinate)
                    Log.d(TAG, "📍 At known location: ${existingEntry.getString("locationName")}")
                } else {
                    // New location - save as pending, wait for next worker run to confirm
                    savePendingLocation(prefs, coordinate, address)
                    Log.d(TAG, "📝 New location detected, saved as pending: $address")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location history", e)
        }
    }
    
    private fun savePendingLocation(prefs: android.content.SharedPreferences, coordinate: LocationCoordinate, address: String) {
        prefs.edit()
            .putFloat(PREF_PENDING_LOCATION_LAT, coordinate.latitude.toFloat())
            .putFloat(PREF_PENDING_LOCATION_LNG, coordinate.longitude.toFloat())
            .putLong(PREF_PENDING_LOCATION_TIME, System.currentTimeMillis())
            .putString(PREF_PENDING_LOCATION_ADDRESS, address)
            .apply()
    }
    
    private fun clearPendingLocation(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .remove(PREF_PENDING_LOCATION_LAT)
            .remove(PREF_PENDING_LOCATION_LNG)
            .remove(PREF_PENDING_LOCATION_TIME)
            .remove(PREF_PENDING_LOCATION_ADDRESS)
            .apply()
    }
    
    private suspend fun findExistingHistoryEntry(
        userId: String,
        coupleId: String,
        coordinate: LocationCoordinate
    ): com.google.firebase.firestore.DocumentSnapshot? {
        val recentHistory = db.collection("location_history")
            .whereEqualTo("userId", userId)
            .whereEqualTo("coupleId", coupleId)
            .limit(20)
            .get()
            .await()
        
        for (doc in recentHistory.documents) {
            val historyLat = doc.getDouble("latitude") ?: continue
            val historyLng = doc.getDouble("longitude") ?: continue
            val historyCoord = LocationCoordinate(historyLat, historyLng)
            
            if (calculateDistance(coordinate, historyCoord) <= SAME_LOCATION_THRESHOLD_METERS) {
                return doc
            }
        }
        return null
    }
    
    private suspend fun updateExistingHistoryEntry(
        existingEntry: com.google.firebase.firestore.DocumentSnapshot,
        coordinate: LocationCoordinate
    ) {
        val arrivalTime = existingEntry.getDate("arrivalTime") ?: return
        val now = Date()
        val durationMinutes = ((now.time - arrivalTime.time) / 60_000).toInt()
        
        existingEntry.reference.update(
            mapOf(
                "departureTime" to now,
                "durationMinutes" to durationMinutes
            )
        ).await()
    }
    
    private suspend fun createOrUpdateHistoryEntry(
        userId: String,
        coupleId: String,
        coordinate: LocationCoordinate,
        address: String,
        arrivalTimeMs: Long,
        departed: Boolean = false
    ) {
        val now = Date()
        val arrivalTime = Date(arrivalTimeMs)
        val durationMinutes = ((now.time - arrivalTimeMs) / 60_000).toInt()
        
        // Check if we already have an entry for this location
        val existingEntry = findExistingHistoryEntry(userId, coupleId, coordinate)
        
        if (existingEntry != null) {
            // Update existing entry
            val updateData = mutableMapOf<String, Any?>(
                "durationMinutes" to durationMinutes
            )
            if (departed) {
                updateData["departureTime"] = now
            }
            existingEntry.reference.update(updateData).await()
            Log.d(TAG, "Updated existing history entry: ${existingEntry.id}")
        } else {
            // Create new entry
            val locationName = detectPlaceName(address)
            val newEntry = mapOf(
                "userId" to userId,
                "coupleId" to coupleId,
                "locationName" to locationName,
                "address" to address,
                "latitude" to coordinate.latitude,
                "longitude" to coordinate.longitude,
                "arrivalTime" to arrivalTime,
                "departureTime" to if (departed) now else null,
                "durationMinutes" to durationMinutes,
                "locationType" to detectLocationType(address).name,
                "source" to "background_worker"
            )
            
            db.collection("location_history").add(newEntry)
                .addOnSuccessListener {
                    Log.d(TAG, "Created new history entry: ${it.id}")
                }
        }
    }
    
    private fun getAddressFromCoordinate(coordinate: LocationCoordinate): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                buildString {
                    address.featureName?.let { feature ->
                        if (!feature.matches(Regex("^\\d+$"))) {
                            append(feature)
                        }
                    }
                    address.thoroughfare?.let { street ->
                        if (isEmpty() || !contains(street)) {
                            if (isNotEmpty()) append(", ")
                            append(street)
                        }
                    }
                    address.subLocality?.let { subLocality ->
                        if (isEmpty()) {
                            append(subLocality)
                        } else if (!contains(subLocality)) {
                            append(", ")
                            append(subLocality)
                        }
                    }
                    if (isEmpty()) {
                        address.locality?.let { city -> append(city) }
                    }
                }
            }?.takeIf { it.isNotBlank() } ?: "Unknown location"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting address", e)
            "Unknown location"
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            100
        }
    }
    
    private fun calculateDistance(coord1: LocationCoordinate, coord2: LocationCoordinate): Double {
        val earthRadius = 6371000.0
        val lat1Rad = Math.toRadians(coord1.latitude)
        val lat2Rad = Math.toRadians(coord2.latitude)
        val deltaLat = Math.toRadians(coord2.latitude - coord1.latitude)
        val deltaLon = Math.toRadians(coord2.longitude - coord1.longitude)
        
        val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    private fun detectPlaceName(address: String): String {
        return address.split(",").firstOrNull()?.trim() ?: address
    }
    
    private fun detectLocationType(address: String): LocationType {
        val lowerAddress = address.lowercase()
        return when {
            lowerAddress.contains("cafe") || lowerAddress.contains("coffee") -> LocationType.CAFE
            lowerAddress.contains("restaurant") || lowerAddress.contains("nhà hàng") -> LocationType.RESTAURANT
            lowerAddress.contains("mall") || lowerAddress.contains("vincom") ||
                    lowerAddress.contains("center") -> LocationType.SHOPPING
            lowerAddress.contains("park") || lowerAddress.contains("công viên") -> LocationType.PARK
            lowerAddress.contains("cinema") || lowerAddress.contains("cgv") -> LocationType.ENTERTAINMENT
            lowerAddress.contains("hospital") || lowerAddress.contains("bệnh viện") -> LocationType.HOSPITAL
            lowerAddress.contains("gym") || lowerAddress.contains("fitness") -> LocationType.GYM
            lowerAddress.contains("school") || lowerAddress.contains("university") ||
                    lowerAddress.contains("đại học") -> LocationType.SCHOOL
            else -> LocationType.OTHER
        }
    }
}
