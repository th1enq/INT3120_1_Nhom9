package com.example.coupleapp.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.coupleapp.data.model.LocationCoordinate
import com.example.coupleapp.data.model.LocationType
import com.example.coupleapp.util.SyncTriggerHelper
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Battery-efficient Location Tracking using Significant Location Changes.
 * 
 * This follows the Widgetable approach:
 * - NO continuous foreground service
 * - Uses Geofencing for significant location changes (200m+ movement)
 * - Activity Recognition to detect movement vs stationary
 * - Only updates when user actually moves to a new place
 * 
 * Battery Impact:
 * - Traditional: ~15-20% per hour (continuous GPS)
 * - This approach: ~1-2% per hour (passive, triggers only on movement)
 * 
 * How it works:
 * 1. Register for significant location changes (200m displacement)
 * 2. When triggered, get accurate location once
 * 3. Save to Firebase + update widget
 * 4. Go back to sleep until next significant change
 */
class SignificantLocationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SignificantLocation"
        private const val REQUEST_CODE = 2001
        private const val PREFS_NAME = "significant_location_prefs"
        private const val KEY_TRACKING_ACTIVE = "tracking_active"
        
        // ================================================================
        // LAYER 1: PASSIVE LOCATION (Primary - TRULY battery efficient)
        // ================================================================
        // Sử dụng PRIORITY_PASSIVE để thực sự piggyback location từ apps khác
        // KHÔNG bật GPS riêng, chỉ nhận location khi có app khác request
        // 
        // Kết hợp với Layer 2 (WorkManager 20 phút) và Layer 3 (AlarmManager 25 phút):
        // - Normal case: Passive nhận location từ Google Maps, Grab, v.v.
        // - Fallback: WorkManager/AlarmManager request location mỗi 15-25 phút
        // ================================================================
        private const val DISPLACEMENT_METERS = 300f // Trigger khi di chuyển 300m
        
        // Interval cho passive mode - chỉ là hint, thực tế phụ thuộc vào apps khác
        // Tăng interval vì đây là PASSIVE - không tự bật GPS
        private const val MIN_UPDATE_INTERVAL_MS = 30 * 60 * 1000L // 30 phút minimum
        private const val MAX_UPDATE_INTERVAL_MS = 60 * 60 * 1000L // 1 giờ maximum
        
        // Singleton instance
        @Volatile
        private var INSTANCE: SignificantLocationManager? = null
        
        fun getInstance(context: Context): SignificantLocationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SignificantLocationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Internal state to track if we've registered
    @Volatile
    private var isRegistered = false
    
    /**
     * Start tracking significant location changes.
     * 
     * IMPORTANT: Uses PRIORITY_PASSIVE which:
     * - Does NOT turn on GPS by itself
     * - Only receives location when OTHER apps request GPS
     * - Zero battery impact when no other app uses location
     * - Perfect for background tracking when user uses Maps, Grab, etc.
     * 
     * Call this when user logs in or enables location sharing.
     */
    fun startTracking(): Boolean {
        // Avoid duplicate registration
        if (isRegistered && isTrackingViaPendingIntent()) {
            Log.d(TAG, "Already tracking, skipping re-registration")
            return true
        }
        
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return false
        }
        
        try {
            // ============================================================
            // TRULY PASSIVE: PRIORITY_PASSIVE
            // ============================================================
            // - Không tự bật GPS - chỉ piggyback từ apps khác
            // - Khi user mở Google Maps, Grab, Zalo → ta nhận được location
            // - Battery impact: ~0% khi không có app khác dùng GPS
            // - Fallback: WorkManager/AlarmManager sẽ request location riêng
            // ============================================================
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_PASSIVE, // PASSIVE = piggyback only, không tự bật GPS
                MAX_UPDATE_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(DISPLACEMENT_METERS)
                .setWaitForAccurateLocation(false)
                // Batch để giảm wakeup frequency
                .setMaxUpdateDelayMillis(MAX_UPDATE_INTERVAL_MS)
                .build()
            
            val pendingIntent = createPendingIntent()
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                pendingIntent
            ).addOnSuccessListener {
                isRegistered = true
                prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, true).apply()
                Log.d(TAG, "✅ Passive location tracking started (piggyback mode)")
            }.addOnFailureListener { e ->
                isRegistered = false
                prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, false).apply()
                Log.e(TAG, "❌ Failed to start location tracking", e)
            }
            
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting tracking", e)
            return false
        }
    }
    
    /**
     * Stop tracking and release GPS resources.
     * 
     * CRITICAL: Call this when user logs out or disables location sharing.
     * This releases the PendingIntent registration with FusedLocationClient,
     * which stops the GPS icon from showing.
     */
    fun stopTracking() {
        try {
            val pendingIntent = createPendingIntent()
            fusedLocationClient.removeLocationUpdates(pendingIntent)
            
            // Also cancel the PendingIntent itself to ensure cleanup
            pendingIntent.cancel()
            
            isRegistered = false
            prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, false).apply()
            
            Log.d(TAG, "✅ Significant location tracking stopped, GPS resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }
    
    /**
     * Check if currently tracking.
     * Uses both internal state and PendingIntent check for accuracy.
     */
    fun isTracking(): Boolean {
        // First check internal state (faster)
        if (!isRegistered && !prefs.getBoolean(KEY_TRACKING_ACTIVE, false)) {
            return false
        }
        
        // Then verify via PendingIntent (authoritative)
        return isTrackingViaPendingIntent()
    }
    
    /**
     * Check if PendingIntent is registered with system.
     * This is the authoritative check - if PendingIntent exists, GPS may be active.
     */
    private fun isTrackingViaPendingIntent(): Boolean {
        val intent = Intent(context, SignificantLocationReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags) != null
    }
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, SignificantLocationReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
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
}

/**
 * BroadcastReceiver for significant location changes.
 * This only wakes up when user moves 500m+ from last known position.
 * 
 * IMPORTANT: Uses goAsync() to properly handle async work in BroadcastReceiver
 * and avoid holding GPS/CPU resources too long.
 */
class SignificantLocationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SignificantLocationRx"
        
        // Cache last location to avoid duplicate updates
        private var lastUpdateTime = 0L
        private var lastLatitude = 0.0
        private var lastLongitude = 0.0
        private const val MIN_UPDATE_GAP_MS = 60_000L // 1 minute minimum gap
        private const val MIN_DISTANCE_METERS = 50.0 // 50m minimum to consider different
        
        // Timeout for async work - prevents holding resources too long
        private const val ASYNC_TIMEOUT_MS = 15_000L // 15 seconds max
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (!LocationResult.hasResult(intent)) {
            return
        }
        
        val result = LocationResult.extractResult(intent) ?: return
        val location = result.lastLocation ?: return
        
        Log.d(TAG, "📍 Significant location change: ${location.latitude}, ${location.longitude}")
        
        // Check if this is a duplicate
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < MIN_UPDATE_GAP_MS) {
            val distance = calculateDistance(
                location.latitude, location.longitude,
                lastLatitude, lastLongitude
            )
            if (distance < MIN_DISTANCE_METERS) {
                Log.d(TAG, "Skipping duplicate update (${distance}m, ${(now - lastUpdateTime)/1000}s)")
                return
            }
        }
        
        // Update cache
        lastUpdateTime = now
        lastLatitude = location.latitude
        lastLongitude = location.longitude
        
        // Use goAsync() to properly handle async work in BroadcastReceiver
        // This prevents ANR and properly releases resources when done
        val pendingResult = goAsync()
        
        // Create a scoped coroutine that will finish the pendingResult when done
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Add timeout to prevent holding resources too long
                kotlinx.coroutines.withTimeout(ASYNC_TIMEOUT_MS) {
                    processLocationUpdate(context, location.latitude, location.longitude)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "Location update timed out after ${ASYNC_TIMEOUT_MS}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing location update", e)
            } finally {
                // CRITICAL: Always finish the pending result to release resources
                // This allows Android to release GPS/CPU resources faster
                pendingResult.finish()
                Log.d(TAG, "✅ Receiver finished, resources released")
            }
        }
    }
    
    private suspend fun processLocationUpdate(context: Context, latitude: Double, longitude: Double) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            
            val currentUser = auth.currentUser ?: return
            val userId = currentUser.uid
            
            // Get user info
            val userDoc = firestore.collection("users").document(userId).get().await()
            val coupleId = userDoc.getString("coupleId") ?: return
            val userName = userDoc.getString("displayName") ?: "User"
            val avatarUrl = userDoc.getString("profileImageUrl") ?: ""
            
            // Get address
            val address = getAddressFromCoordinate(context, latitude, longitude)
            
            // Upload to Firebase
            val documentId = "${coupleId}_${userId}"
            val locationData = mapOf(
                "userId" to userId,
                "coupleId" to coupleId,
                "userName" to userName,
                "avatarUrl" to avatarUrl,
                "latitude" to latitude,
                "longitude" to longitude,
                "address" to address,
                "isOnline" to true,
                "timestamp" to Date(),
                "source" to "significant_change", // Mark as battery-efficient source
                "accuracy" to "balanced"
            )
            
            firestore.collection("locations")
                .document(documentId)
                .set(locationData)
                .await()
            
            Log.d(TAG, "✅ Location uploaded to Firebase")
            
            // Update location history
            updateLocationHistory(firestore, userId, coupleId, latitude, longitude, address)
            
            // Notify partner that location updated
            SyncTriggerHelper.notifyLocationUpdated(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location update", e)
        }
    }
    
    private suspend fun updateLocationHistory(
        firestore: FirebaseFirestore,
        userId: String,
        coupleId: String,
        latitude: Double,
        longitude: Double,
        address: String
    ) {
        try {
            val now = Date()
            val currentCoord = LocationCoordinate(latitude, longitude)
            
            // Check recent history for nearby locations
            val recentHistory = firestore.collection("location_history")
                .whereEqualTo("userId", userId)
                .whereEqualTo("coupleId", coupleId)
                .limit(10)
                .get()
                .await()
            
            // Find ACTIVE entry (no departure time) that's nearby
            val activeEntry = recentHistory.documents.find { doc ->
                val depTime = doc.getDate("departureTime")
                if (depTime != null) return@find false
                
                val histLat = doc.getDouble("latitude") ?: return@find false
                val histLng = doc.getDouble("longitude") ?: return@find false
                
                val distance = calculateDistance(latitude, longitude, histLat, histLng)
                distance < 300 // Within 300m (same location threshold)
            }
            
            if (activeEntry != null) {
                // Update existing active entry - user is still at same location
                val arrivalTime = activeEntry.getDate("arrivalTime")
                val durationMinutes = if (arrivalTime != null) {
                    ((now.time - arrivalTime.time) / 60_000).toInt().coerceAtLeast(0)
                } else 0
                
                activeEntry.reference.update("durationMinutes", durationMinutes).await()
                Log.d(TAG, "📍 Updated active entry: ${durationMinutes}min")
                return
            }
            
            // No active entry at current location - close any old active entry, then create new
            val oldActiveEntry = recentHistory.documents.find { it.getDate("departureTime") == null }
            if (oldActiveEntry != null) {
                val arrivalTime = oldActiveEntry.getDate("arrivalTime")
                val durationMinutes = if (arrivalTime != null) {
                    ((now.time - arrivalTime.time) / 60_000).toInt()
                } else 0
                
                if (durationMinutes >= 3) {
                    oldActiveEntry.reference.update(
                        mapOf(
                            "departureTime" to now,
                            "durationMinutes" to durationMinutes
                        )
                    ).await()
                    Log.d(TAG, "🔒 Closed previous entry: ${durationMinutes}min")
                } else {
                    oldActiveEntry.reference.delete().await()
                    Log.d(TAG, "🗑️ Deleted short entry: ${durationMinutes}min")
                }
            }
            
            // Create new entry
            val locationName = address.split(",").firstOrNull()?.trim() ?: address
            val locationType = detectLocationType(address)
            
            val newEntry = mapOf(
                "userId" to userId,
                "coupleId" to coupleId,
                "locationName" to locationName,
                "address" to address,
                "latitude" to latitude,
                "longitude" to longitude,
                "arrivalTime" to now,
                "departureTime" to null,
                "durationMinutes" to 0,
                "locationType" to locationType.name,
                "source" to "significant_change"
            )
            
            firestore.collection("location_history").add(newEntry).await()
            Log.d(TAG, "📝 Created new entry: $locationName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location history", e)
        }
    }
    
    private fun getAddressFromCoordinate(context: Context, latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                buildString {
                    // Priority order for location name:
                    // 1. Feature name (if not just a number)
                    // 2. Thoroughfare (street name)
                    // 3. SubLocality (neighborhood/district)
                    // 4. Locality (city)
                    // 5. SubAdminArea/AdminArea (fallback)
                    
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
                        if (isEmpty()) append(subLocality)
                        else if (!contains(subLocality)) {
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
            }?.takeIf { it.isNotBlank() } ?: generateFallbackLocationName(latitude, longitude)
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder failed", e)
            generateFallbackLocationName(latitude, longitude)
        }
    }
    
    /**
     * Generate a fallback location name when Geocoder fails.
     */
    private fun generateFallbackLocationName(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.US, "%.3f", latitude)
        val lng = String.format(Locale.US, "%.3f", longitude)
        return "Vị trí ($lat, $lng)"
    }
    
    private fun detectLocationType(address: String): LocationType {
        val lower = address.lowercase()
        return when {
            lower.contains("cafe") || lower.contains("coffee") -> LocationType.CAFE
            lower.contains("restaurant") || lower.contains("nhà hàng") -> LocationType.RESTAURANT
            lower.contains("mall") || lower.contains("vincom") -> LocationType.SHOPPING
            lower.contains("park") || lower.contains("công viên") -> LocationType.PARK
            lower.contains("hospital") || lower.contains("bệnh viện") -> LocationType.HOSPITAL
            else -> LocationType.OTHER
        }
    }
    
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lng2 - lng1)
        
        val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
}
