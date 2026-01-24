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
 * - Updates location every 15-20 minutes when app is closed
 * - Simple logic: update existing entry or create new one
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
        
        // Update interval: 15-20 minutes (WorkManager minimum is 15 min)
        private const val MIN_UPDATE_INTERVAL_MINUTES = 15L
        private const val FLEX_INTERVAL_MINUTES = 5L // Worker runs between 15-20 minutes
        private const val LOW_BATTERY_THRESHOLD = 15 // Skip when battery < 15%
        
        // === UNIFIED THRESHOLD: 200m ===
        private const val SAME_LOCATION_THRESHOLD_METERS = 200.0
        
        // Maximum gap to consider same visit session (2 hours)
        private const val MAX_SESSION_GAP_MINUTES = 120
        
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
            // BATTERY OPTIMIZED: Dùng LOW_POWER thay vì BALANCED
            // Độ chính xác ~100m là đủ cho location history
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_LOW_POWER,
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
     * SIMPLIFIED location history update.
     * 
     * Logic đơn giản:
     * 1. Tìm entry ACTIVE (departureTime = null) trong vòng 200m
     *    → Nếu có: Update duration
     * 2. Nếu không có active entry, tìm entry đã đóng trong 2 giờ và 200m
     *    → Nếu có: Mở lại entry đó  
     * 3. Nếu không tìm thấy gì: Tạo entry mới
     * 
     * Không cần "pending" - luôn tạo/update entry ngay lập tức
     */
    private suspend fun updateLocationHistory(
        userId: String,
        coupleId: String,
        coordinate: LocationCoordinate,
        address: String
    ) {
        try {
            val now = System.currentTimeMillis()
            
            // Fetch recent history entries
            val recentHistory = db.collection("location_history")
                .whereEqualTo("userId", userId)
                .whereEqualTo("coupleId", coupleId)
                .limit(20)
                .get()
                .await()
            
            // Step 1: Find ACTIVE entry (no departureTime) within 200m
            var foundActiveEntry: com.google.firebase.firestore.DocumentSnapshot? = null
            for (doc in recentHistory.documents) {
                val departureTime = doc.getDate("departureTime")
                if (departureTime != null) continue // Skip closed entries
                
                val historyLat = doc.getDouble("latitude") ?: continue
                val historyLng = doc.getDouble("longitude") ?: continue
                val historyCoord = LocationCoordinate(historyLat, historyLng)
                
                if (calculateDistance(coordinate, historyCoord) <= SAME_LOCATION_THRESHOLD_METERS) {
                    foundActiveEntry = doc
                    break
                }
            }
            
            if (foundActiveEntry != null) {
                // Update active entry's duration
                val arrivalTime = foundActiveEntry.getDate("arrivalTime")
                val durationMinutes = if (arrivalTime != null && arrivalTime.time <= now) {
                    ((now - arrivalTime.time) / 60_000).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                foundActiveEntry.reference.update("durationMinutes", durationMinutes).await()
                Log.d(TAG, "📍 Updated active entry duration: ${durationMinutes}min at ${foundActiveEntry.getString("locationName")}")
                return
            }
            
            // Step 2: Find recently CLOSED entry within 200m and 2 hours - reopen it
            for (doc in recentHistory.documents) {
                val departureTime = doc.getDate("departureTime") ?: continue
                
                // Check if closed within MAX_SESSION_GAP_MINUTES
                val timeSinceDeparture = (now - departureTime.time) / 60_000
                if (timeSinceDeparture > MAX_SESSION_GAP_MINUTES) continue
                
                val historyLat = doc.getDouble("latitude") ?: continue
                val historyLng = doc.getDouble("longitude") ?: continue
                val historyCoord = LocationCoordinate(historyLat, historyLng)
                
                if (calculateDistance(coordinate, historyCoord) <= SAME_LOCATION_THRESHOLD_METERS) {
                    // Reopen this entry
                    val arrivalTime = doc.getDate("arrivalTime")
                    val durationMinutes = if (arrivalTime != null && arrivalTime.time <= now) {
                        ((now - arrivalTime.time) / 60_000).toInt().coerceAtLeast(0)
                    } else {
                        0
                    }
                    doc.reference.update(
                        mapOf(
                            "departureTime" to null,
                            "durationMinutes" to durationMinutes
                        )
                    ).await()
                    Log.d(TAG, "🔄 Reopened entry: ${doc.getString("locationName")}, duration: ${durationMinutes}min")
                    return
                }
            }
            
            // Step 3: No matching entry found - create new one
            val locationName = detectPlaceName(address)
            val historyData = mapOf(
                "userId" to userId,
                "coupleId" to coupleId,
                "locationName" to locationName,
                "address" to address,
                "latitude" to coordinate.latitude,
                "longitude" to coordinate.longitude,
                "arrivalTime" to Date(now),
                "departureTime" to null, // Active - still here
                "durationMinutes" to 0,
                "locationType" to detectLocationType(address).name,
                "source" to "background_worker"
            )
            
            db.collection("location_history").add(historyData).await()
            Log.d(TAG, "📝 Created new history entry: $locationName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location history", e)
        }
    }
    
    /**
     * Find an ACTIVE history entry (no departureTime) at the given location.
     */
    private suspend fun findActiveHistoryEntry(
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
            val departureTime = doc.getDate("departureTime")
            if (departureTime != null) continue
            
            val historyLat = doc.getDouble("latitude") ?: continue
            val historyLng = doc.getDouble("longitude") ?: continue
            val historyCoord = LocationCoordinate(historyLat, historyLng)
            
            if (calculateDistance(coordinate, historyCoord) <= SAME_LOCATION_THRESHOLD_METERS) {
                return doc
            }
        }
        return null
    }
    
    /**
     * Find a recently CLOSED entry at the same location.
     */
    private suspend fun findRecentClosedEntryAtLocation(
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
        
        val now = System.currentTimeMillis()
        
        for (doc in recentHistory.documents) {
            val departureTime = doc.getDate("departureTime") ?: continue
            
            // Check if closed within session gap
            val gapMinutes = (now - departureTime.time) / 60_000
            if (gapMinutes > MAX_SESSION_GAP_MINUTES) continue
            
            val historyLat = doc.getDouble("latitude") ?: continue
            val historyLng = doc.getDouble("longitude") ?: continue
            val historyCoord = LocationCoordinate(historyLat, historyLng)
            
            if (calculateDistance(coordinate, historyCoord) <= SAME_LOCATION_THRESHOLD_METERS) {
                return doc
            }
        }
        return null
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
