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
        
        // Time constants
        private const val MIN_UPDATE_INTERVAL_MINUTES = 15L
        private const val MAX_UPDATE_INTERVAL_MINUTES = 30L
        private const val LOW_BATTERY_THRESHOLD = 20
        
        // Distance threshold for considering same location (meters)
        private const val SAME_LOCATION_THRESHOLD_METERS = 200.0
        
        // Minimum stay duration to record in history (minutes)
        private const val MIN_HISTORY_DURATION_MINUTES = 10
        
        /**
         * Schedule background location updates
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // We handle battery internally
                .build()
            
            val request = PeriodicWorkRequestBuilder<BackgroundLocationWorker>(
                repeatInterval = MIN_UPDATE_INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5,
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
            
            Log.d(TAG, "Background location worker scheduled")
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
     * Intelligently update location history by merging nearby locations
     * to avoid creating duplicate entries when user stays at same place
     */
    private suspend fun updateLocationHistory(
        userId: String,
        coupleId: String,
        coordinate: LocationCoordinate,
        address: String
    ) {
        try {
            // Get recent location history entries
            val recentHistory = db.collection("location_history")
                .whereEqualTo("userId", userId)
                .whereEqualTo("coupleId", coupleId)
                .limit(10)
                .get()
                .await()
            
            val now = Date()
            var shouldCreateNewEntry = true
            
            // Check each recent entry to see if we're still at the same location
            for (doc in recentHistory.documents) {
                val historyLat = doc.getDouble("latitude") ?: continue
                val historyLng = doc.getDouble("longitude") ?: continue
                val historyCoord = LocationCoordinate(historyLat, historyLng)
                
                val distance = calculateDistance(coordinate, historyCoord)
                
                if (distance <= SAME_LOCATION_THRESHOLD_METERS) {
                    // We're still at the same location - update departure time
                    val arrivalTime = doc.getDate("arrivalTime")
                    if (arrivalTime != null) {
                        val durationMinutes = ((now.time - arrivalTime.time) / 60_000).toInt()
                        
                        doc.reference.update(
                            mapOf(
                                "departureTime" to now,
                                "durationMinutes" to durationMinutes
                            )
                        ).await()
                        
                        Log.d(TAG, "Updated existing history entry: ${doc.id}, duration: ${durationMinutes}min")
                        shouldCreateNewEntry = false
                        break
                    }
                }
            }
            
            // Only create new entry if we've moved to a new location
            if (shouldCreateNewEntry) {
                // Check if we have an active entry without departure time
                val activeEntry = recentHistory.documents.find { 
                    it.getDate("departureTime") == null 
                }
                
                if (activeEntry != null) {
                    // Close the active entry first
                    val arrivalTime = activeEntry.getDate("arrivalTime")
                    if (arrivalTime != null) {
                        val durationMinutes = ((now.time - arrivalTime.time) / 60_000).toInt()
                        
                        if (durationMinutes >= MIN_HISTORY_DURATION_MINUTES) {
                            activeEntry.reference.update(
                                mapOf(
                                    "departureTime" to now,
                                    "durationMinutes" to durationMinutes
                                )
                            ).await()
                            Log.d(TAG, "Closed active entry: ${activeEntry.id}")
                        } else {
                            // Duration too short, delete the entry
                            activeEntry.reference.delete().await()
                            Log.d(TAG, "Deleted short entry: ${activeEntry.id}")
                        }
                    }
                }
                
                // Create new entry for new location
                val locationName = detectPlaceName(address)
                val newEntry = mapOf(
                    "userId" to userId,
                    "coupleId" to coupleId,
                    "locationName" to locationName,
                    "address" to address,
                    "latitude" to coordinate.latitude,
                    "longitude" to coordinate.longitude,
                    "arrivalTime" to now,
                    "departureTime" to null,
                    "durationMinutes" to 0,
                    "locationType" to detectLocationType(address).name
                )
                
                db.collection("location_history").add(newEntry)
                    .addOnSuccessListener {
                        Log.d(TAG, "Created new history entry: ${it.id}")
                    }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location history", e)
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
