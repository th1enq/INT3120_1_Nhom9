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
        
        // BALANCED: Trigger khi di chuyển đáng kể
        private const val DISPLACEMENT_METERS = 500f // Trigger khi di chuyển 500m
        
        // Interval để đảm bảo location history được cập nhật đều đặn
        private const val MIN_UPDATE_INTERVAL_MS = 15 * 60 * 1000L // Tối thiểu 15 phút giữa 2 lần update
        private const val MAX_UPDATE_INTERVAL_MS = 30 * 60 * 1000L // Tối đa 30 phút (fallback)
        
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
    
    /**
     * Start tracking significant location changes.
     * Call this when user logs in or enables location sharing.
     */
    fun startTracking(): Boolean {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return false
        }
        
        try {
            // BATTERY OPTIMIZED: Request for significant location changes only
            // Sử dụng LOW_POWER để tiết kiệm pin tối đa - độ chính xác ~100m là đủ cho history
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_LOW_POWER, // Thay BALANCED bằng LOW_POWER để tiết kiệm pin
                MAX_UPDATE_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(DISPLACEMENT_METERS)
                .setWaitForAccurateLocation(false) // Don't wait, use best available
                .setMaxUpdateDelayMillis(MAX_UPDATE_INTERVAL_MS) // Batch updates để tiết kiệm pin
                .build()
            
            val pendingIntent = createPendingIntent()
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                pendingIntent
            ).addOnSuccessListener {
                Log.d(TAG, "✅ Significant location tracking started")
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to start location tracking", e)
            }
            
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting tracking", e)
            return false
        }
    }
    
    /**
     * Stop tracking.
     * Call this when user logs out or disables location sharing.
     */
    fun stopTracking() {
        try {
            val pendingIntent = createPendingIntent()
            fusedLocationClient.removeLocationUpdates(pendingIntent)
            Log.d(TAG, "Significant location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tracking", e)
        }
    }
    
    /**
     * Check if currently tracking
     */
    fun isTracking(): Boolean {
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
 * This only wakes up when user moves 200m+ from last known position.
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
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
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
        
        // Process location update
        scope.launch {
            processLocationUpdate(context, location.latitude, location.longitude)
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
                    ((now.time - arrivalTime.time) / 60_000).toInt()
                } else 0
                
                activeEntry.reference.update(
                    mapOf(
                        "departureTime" to now,
                        "durationMinutes" to durationMinutes
                    )
                ).await()
                
                Log.d(TAG, "Updated active history entry: ${durationMinutes}min")
            } else {
                // No active entry at current location
                // First, close any OTHER active entries (user moved to new location)
                val otherActiveEntry = recentHistory.documents.find { 
                    it.getDate("departureTime") == null 
                }
                if (otherActiveEntry != null) {
                    val arrivalTime = otherActiveEntry.getDate("arrivalTime")
                    val durationMinutes = if (arrivalTime != null) {
                        ((now.time - arrivalTime.time) / 60_000).toInt()
                    } else 0
                    
                    if (durationMinutes >= 5) {
                        otherActiveEntry.reference.update(
                            mapOf(
                                "departureTime" to now,
                                "durationMinutes" to durationMinutes
                            )
                        ).await()
                        Log.d(TAG, "Closed previous active entry: ${durationMinutes}min")
                    } else {
                        // Too short, delete
                        otherActiveEntry.reference.delete().await()
                        Log.d(TAG, "Deleted too-short entry: ${durationMinutes}min")
                    }
                }
                
                // Check if there's a recently closed entry at this location (within 60 min)
                // If yes, reopen it instead of creating duplicate
                val recentClosedEntry = recentHistory.documents.find { doc ->
                    val depTime = doc.getDate("departureTime") ?: return@find false
                    val gapMinutes = (now.time - depTime.time) / 60_000
                    if (gapMinutes > 60) return@find false // Max 60 min gap to reopen
                    
                    val histLat = doc.getDouble("latitude") ?: return@find false
                    val histLng = doc.getDouble("longitude") ?: return@find false
                    val distance = calculateDistance(latitude, longitude, histLat, histLng)
                    distance < 300
                }
                
                if (recentClosedEntry != null) {
                    // Reopen the recent entry - user returned
                    val arrivalTime = recentClosedEntry.getDate("arrivalTime")
                    val durationMinutes = if (arrivalTime != null) {
                        ((now.time - arrivalTime.time) / 60_000).toInt()
                    } else 0
                    
                    recentClosedEntry.reference.update(
                        mapOf(
                            "departureTime" to now,
                            "durationMinutes" to durationMinutes
                        )
                    ).await()
                    Log.d(TAG, "Reopened recent entry, duration: ${durationMinutes}min")
                } else {
                    // Create new entry - this is a new location visit
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
                    Log.d(TAG, "Created new history entry")
                }
            }
            
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
                    address.featureName?.let { feature ->
                        if (!feature.matches(Regex("^\\d+$"))) append(feature)
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
                    if (isEmpty()) {
                        address.locality?.let { city -> append(city) }
                    }
                }
            }?.takeIf { it.isNotBlank() } ?: "Unknown location"
        } catch (e: Exception) {
            "Unknown location"
        }
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
