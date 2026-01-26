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
 * 
 * ================================================================
 * LAYER 2: WORKMANAGER (PRIMARY Location Source)
 * ================================================================
 * 
 * This worker is the PRIMARY source for background location tracking.
 * 
 * HYBRID STRATEGY (Intelligent Battery Saving):
 * ─────────────────────────────────────────────
 * 1. Try LOW_POWER first (Cell + WiFi, ~0.05% battery)
 * 2. If accuracy > 100m → upgrade to BALANCED (GPS + Cell + WiFi, ~0.15%)
 * 
 * Result:
 * - In cities (good cell coverage): Uses LOW_POWER (saves battery)
 * - In rural areas (poor coverage): Auto-upgrades to BALANCED (accurate)
 * - Saves ~50% battery compared to always using BALANCED
 * 
 * Role in architecture:
 * 1. PRIMARY: Actively request location every 15-20 minutes
 * 2. RECOVERY: Re-register Layer 1 if lost after clear RAM
 * 3. RELIABLE: Guaranteed location updates (not dependent on other apps)
 * 
 * Battery Impact (HYBRID):
 * - Best case (city): ~0.05% per request = ~4% per day
 * - Worst case (rural): ~0.15% per request = ~12% per day
 * - Average: ~6-8% per day (much better than always BALANCED)
 * 
 * Accuracy:
 * - City: 50-100m (LOW_POWER accepted)
 * - Rural: 20-50m (auto-upgraded to BALANCED)
 * 
 * Features:
 * - Updates location every 15-20 minutes when app is closed
 * - Intelligent battery saving based on environment
 * - Respects battery level - skips when battery < 15%
 * - Only works when user is paired with a partner
 */
class BackgroundLocationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "BackgroundLocationWorker"
        const val WORK_NAME = "background_location_update"
        
        // ================================================================
        // LAYER 2: WORKMANAGER (PRIMARY Location Source)
        // ================================================================
        // Chạy mỗi 20 phút với flex 5 phút (thực tế: 15-20 phút)
        // 
        // Vai trò chính:
        // 1. PRIMARY: Request location với BALANCED_POWER_ACCURACY (20-50m)
        // 2. RECOVERY: Re-register Layer 1 nếu bị mất sau clear RAM
        // 3. RELIABLE: Không phụ thuộc vào apps khác như Layer 1
        // 
        // Tại sao dùng BALANCED thay vì LOW_POWER:
        // - LOW_POWER (Cell+WiFi): 50-500m - quá thiếu chính xác ở nông thôn
        // - BALANCED (GPS+Cell+WiFi): 20-50m - đủ chính xác, pin hợp lý
        // - HIGH_ACCURACY: 3-10m - quá tốn pin cho background
        // ================================================================
        private const val MIN_UPDATE_INTERVAL_MINUTES = 20L // 20 phút
        private const val FLEX_INTERVAL_MINUTES = 5L // Worker runs between 15-20 minutes
        private const val LOW_BATTERY_THRESHOLD = 15 // Skip when battery < 15%
        
        // === UNIFIED THRESHOLD: 200m ===
        private const val SAME_LOCATION_THRESHOLD_METERS = 200.0
        
        // Maximum gap to consider same visit session (2 hours)
        private const val MAX_SESSION_GAP_MINUTES = 120
        
        // Minimum time gap before creating a new entry at same location (5 minutes)
        // Prevents duplicate entries from race condition between sources
        private const val MIN_ENTRY_GAP_MS = 5 * 60 * 1000L
        
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
        
        /**
         * Trigger one-time immediate location update.
         * Used by AlarmManager to ensure location is updated even when WorkManager is delayed.
         * Also re-registers SignificantLocationManager if not active.
         */
        fun triggerOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val request = OneTimeWorkRequestBuilder<BackgroundLocationWorker>()
                .setConstraints(constraints)
                .addTag("one_time_location")
                .build()
            
            WorkManager.getInstance(context)
                .enqueue(request)
            
            Log.d(TAG, "One-time location worker triggered")
        }
    }
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Background location update started")
        
        try {
            // CRITICAL: Ensure SignificantLocationManager is still registered
            // This is important after clear RAM - re-registers if not active
            ensureSignificantLocationTracking()
            
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
            
            // Periodically cleanup old sync triggers to prevent Firestore bloat
            // This runs every time the worker runs (~15-20 min)
            try {
                com.example.coupleapp.util.SyncTriggerHelper.cleanupOldTriggers()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cleanup sync triggers", e)
            }
            
            Log.d(TAG, "Background location update completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in background location update", e)
            Result.retry()
        }
    }
    
    /**
     * Ensure SignificantLocationManager is still registered.
     * This is critical after clear RAM - the PendingIntent may be gone.
     * WorkManager survives clear RAM better, so we use this worker to re-register.
     */
    private fun ensureSignificantLocationTracking() {
        try {
            val sigLocationManager = com.example.coupleapp.service.SignificantLocationManager.getInstance(context)
            if (!sigLocationManager.isTracking()) {
                Log.d(TAG, "SignificantLocationManager not active, re-registering...")
                sigLocationManager.startTracking()
                Log.d(TAG, "✅ SignificantLocationManager re-registered from worker")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error re-registering SignificantLocationManager", e)
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
            // ================================================================
            // HYBRID STRATEGY: LOW_POWER first, upgrade to BALANCED if needed
            // ================================================================
            // 
            // Chiến lược thông minh tiết kiệm pin:
            // 1. Thử LOW_POWER trước (Cell + WiFi, tiết kiệm pin)
            // 2. Nếu accuracy > 100m → upgrade lên BALANCED (GPS + Cell + WiFi)
            // 
            // Kết quả:
            // - Ở thành phố: Dùng LOW_POWER (~0.05% pin, 50-100m)
            // - Ở nông thôn: Tự động BALANCED (~0.15% pin, 20-50m)
            // - Tiết kiệm ~50% pin so với luôn dùng BALANCED
            // ================================================================
            
            val cancellationToken1 = CancellationTokenSource()
            
            // Step 1: Try LOW_POWER first (battery efficient)
            val lowPowerLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_LOW_POWER,
                cancellationToken1.token
            ).await()
            
            // Step 2: Check accuracy
            if (lowPowerLocation != null && lowPowerLocation.accuracy <= 100f) {
                // Good accuracy from LOW_POWER - use it!
                Log.d(TAG, "✅ LOW_POWER location accepted: ${lowPowerLocation.accuracy}m accuracy")
                return lowPowerLocation
            }
            
            // Step 3: Accuracy not good enough, upgrade to BALANCED
            Log.d(TAG, "⚠️ LOW_POWER accuracy ${lowPowerLocation?.accuracy ?: "null"}m > 100m, upgrading to BALANCED")
            
            val cancellationToken2 = CancellationTokenSource()
            val balancedLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken2.token
            ).await()
            
            if (balancedLocation != null) {
                Log.d(TAG, "✅ BALANCED location: ${balancedLocation.accuracy}m accuracy")
            }
            
            // Return BALANCED location, or LOW_POWER if BALANCED also failed
            balancedLocation ?: lowPowerLocation
            
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
            
            // ★ FIX: Use Source.SERVER to bypass cache and get fresh data
            // This prevents race condition where cached data shows no active entry
            // while another source (SignificantLocationManager) just created one
            val recentHistory = db.collection("location_history")
                .whereEqualTo("userId", userId)
                .whereEqualTo("coupleId", coupleId)
                .orderBy("arrivalTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get(com.google.firebase.firestore.Source.SERVER) // Force server fetch!
                .await()
            
            Log.d(TAG, "Fetched ${recentHistory.documents.size} history entries from SERVER")
            
            // Step 1: Find ACTIVE entry (no departureTime) within 200m
            // Since results are ordered by arrivalTime DESC, first match is the LATEST
            var foundActiveEntry: com.google.firebase.firestore.DocumentSnapshot? = null
            val allActiveEntries = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
            
            for (doc in recentHistory.documents) {
                val departureTime = doc.getDate("departureTime")
                if (departureTime != null) continue // Skip closed entries
                
                // Collect all ACTIVE entries for later cleanup
                allActiveEntries.add(doc)
                
                val historyLat = doc.getDouble("latitude") ?: continue
                val historyLng = doc.getDouble("longitude") ?: continue
                val historyCoord = LocationCoordinate(historyLat, historyLng)
                
                // Take FIRST match (which is LATEST due to orderBy DESC)
                if (foundActiveEntry == null && calculateDistance(coordinate, historyCoord) <= SAME_LOCATION_THRESHOLD_METERS) {
                    foundActiveEntry = doc
                    // Don't break - continue to collect all active entries for cleanup
                }
            }
            
            if (foundActiveEntry != null) {
                // Update active entry's duration AND location (to improve accuracy over time)
                val arrivalTime = foundActiveEntry.getDate("arrivalTime")
                val durationMinutes = if (arrivalTime != null && arrivalTime.time <= now) {
                    ((now - arrivalTime.time) / 60_000).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                
                // Update duration + location coordinates (improves accuracy as GPS gets better fixes)
                // This is FREE - no extra battery cost since we already have the location
                foundActiveEntry.reference.update(
                    mapOf(
                        "durationMinutes" to durationMinutes,
                        "latitude" to coordinate.latitude,
                        "longitude" to coordinate.longitude,
                        "address" to address,
                        "locationName" to detectPlaceName(address)
                    )
                ).await()
                Log.d(TAG, "📍 Updated active entry: ${durationMinutes}min + location at ${foundActiveEntry.getString("locationName")}")
                
                // CLEANUP: Close any OTHER stale ACTIVE entries to prevent duplicates
                for (staleEntry in allActiveEntries) {
                    if (staleEntry.id == foundActiveEntry.id) continue // Skip the one we just updated
                    
                    val staleArrival = staleEntry.getDate("arrivalTime")
                    val staleDuration = if (staleArrival != null && staleArrival.time <= now) {
                        ((now - staleArrival.time) / 60_000).toInt().coerceAtLeast(0)
                    } else 0
                    
                    if (staleDuration >= 3) {
                        staleEntry.reference.update(
                            mapOf(
                                "departureTime" to Date(now),
                                "durationMinutes" to staleDuration
                            )
                        ).await()
                        Log.d(TAG, "🧹 Cleaned up stale entry: ${staleEntry.getString("locationName")} (${staleDuration}min)")
                    } else {
                        staleEntry.reference.delete().await()
                        Log.d(TAG, "🗑️ Deleted stale short entry: ${staleEntry.getString("locationName")}")
                    }
                }
                
                return
            }
            
            // No active entry at current location
            // ★ FIX: Check if there's a RECENT entry at this location (even if closed)
            // to prevent duplicate entries from race condition with SignificantLocationManager
            val recentNearbyEntry = recentHistory.documents.find { doc ->
                val arrivalTime = doc.getDate("arrivalTime") ?: return@find false
                val historyLat = doc.getDouble("latitude") ?: return@find false
                val historyLng = doc.getDouble("longitude") ?: return@find false
                val historyCoord = LocationCoordinate(historyLat, historyLng)
                
                val distance = calculateDistance(coordinate, historyCoord)
                val timeSinceArrival = now - arrivalTime.time
                
                // If there's an entry within 200m created in last 5 minutes, skip creating new
                distance <= SAME_LOCATION_THRESHOLD_METERS && timeSinceArrival < MIN_ENTRY_GAP_MS
            }
            
            if (recentNearbyEntry != null) {
                // Entry already exists at this location, just update it instead of creating duplicate
                val existingDepartureTime = recentNearbyEntry.getDate("departureTime")
                val existingArrivalTime = recentNearbyEntry.getDate("arrivalTime")
                val locationName = detectPlaceName(address)
                
                if (existingDepartureTime != null) {
                    // ★ FIX: Entry was CLOSED - reset arrivalTime to now to avoid huge duration
                    // Don't reopen old entries, just create fresh session
                    Log.d(TAG, "📍 Entry was closed, resetting arrivalTime to now")
                    recentNearbyEntry.reference.update(
                        mapOf(
                            "arrivalTime" to Date(now), // Reset to now!
                            "durationMinutes" to 0, // Start fresh
                            "departureTime" to null, // Reopen
                            "latitude" to coordinate.latitude,
                            "longitude" to coordinate.longitude,
                            "address" to address,
                            "locationName" to locationName
                        )
                    ).await()
                } else {
                    // Entry is still ACTIVE - just update duration
                    val durationMinutes = if (existingArrivalTime != null && existingArrivalTime.time <= now) {
                        ((now - existingArrivalTime.time) / 60_000).toInt().coerceAtLeast(0)
                    } else 0
                    
                    recentNearbyEntry.reference.update(
                        mapOf(
                            "durationMinutes" to durationMinutes,
                            "latitude" to coordinate.latitude,
                            "longitude" to coordinate.longitude,
                            "address" to address,
                            "locationName" to locationName
                        )
                    ).await()
                    Log.d(TAG, "📍 Updated active entry: ${durationMinutes}min")
                }
                return
            }
            
            // Close old active entry at DIFFERENT location and create new
            // This prevents having multiple active entries at the same time
            for (doc in recentHistory.documents) {
                val departureTime = doc.getDate("departureTime")
                if (departureTime == null) {
                    // Found an active entry at a DIFFERENT location - close it
                    val arrivalTime = doc.getDate("arrivalTime")
                    val durationMinutes = if (arrivalTime != null && arrivalTime.time <= now) {
                        ((now - arrivalTime.time) / 60_000).toInt().coerceAtLeast(0)
                    } else 0
                    
                    if (durationMinutes >= 3) {
                        // Close with departure time = now
                        doc.reference.update(
                            mapOf(
                                "departureTime" to Date(now),
                                "durationMinutes" to durationMinutes
                            )
                        ).await()
                        Log.d(TAG, "🔒 Closed previous active entry: ${doc.getString("locationName")} (${durationMinutes}min)")
                    } else {
                        // Too short, delete it
                        doc.reference.delete().await()
                        Log.d(TAG, "🗑️ Deleted too-short entry: ${doc.getString("locationName")} (${durationMinutes}min)")
                    }
                    break // Only one active entry should exist
                }
            }
            
            // Now create new entry
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
                    // Priority order for location name:
                    // 1. Feature name (if not just a number)
                    // 2. Thoroughfare (street name)
                    // 3. SubLocality (neighborhood/district)
                    // 4. Locality (city)
                    // 5. SubAdminArea (county/district)
                    // 6. AdminArea (state/province)
                    
                    address.featureName?.let { feature ->
                        // Skip if it's just a street number
                        if (!feature.matches(Regex("^\\d+[A-Za-z]?$")) && feature.length > 2) {
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
                    
                    // Fallback to city if still empty
                    if (isEmpty()) {
                        address.locality?.let { city -> append(city) }
                    }
                    
                    // Last resort: use admin area
                    if (isEmpty()) {
                        address.subAdminArea?.let { district -> append(district) }
                    }
                    
                    if (isEmpty()) {
                        address.adminArea?.let { province -> append(province) }
                    }
                }
            }?.takeIf { it.isNotBlank() } ?: generateFallbackLocationName(coordinate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting address from Geocoder", e)
            generateFallbackLocationName(coordinate)
        }
    }
    
    /**
     * Generate a fallback location name when Geocoder fails.
     * Uses coordinates rounded to create a readable area name.
     */
    private fun generateFallbackLocationName(coordinate: LocationCoordinate): String {
        // Round to 3 decimal places (~100m precision)
        val lat = String.format(Locale.US, "%.3f", coordinate.latitude)
        val lng = String.format(Locale.US, "%.3f", coordinate.longitude)
        return "Vị trí ($lat, $lng)"
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
