package com.example.coupleapp.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.coupleapp.data.model.LocationType
import com.example.coupleapp.util.SyncTriggerHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Smart Geofencing Manager for important places (like Widgetable).
 * 
 * HOW IT WORKS:
 * 1. User saves important places (Home, Work, Partner's home, etc.)
 * 2. We create geofences around those places
 * 3. When user enters/exits, we get notified with ZERO battery usage
 * 
 * BATTERY IMPACT: ~0% (Google Play Services handles this passively)
 * 
 * COMPARISON:
 * - Continuous GPS: 15-20% per hour
 * - Significant Changes: 1-2% per hour  
 * - Geofencing: ~0% (uses cell towers/wifi passively)
 * 
 * USE CASES:
 * - "You arrived at Partner's home!" notification
 * - Automatic location history when at saved places
 * - "Partner left work" notification to prepare dinner 😄
 */
class SmartGeofenceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SmartGeofence"
        private const val REQUEST_CODE = 3001
        
        // Geofence radius in meters
        private const val GEOFENCE_RADIUS_METERS = 150f
        
        // How long to stay for dwell trigger (milliseconds)
        private const val DWELL_DELAY_MS = 5 * 60 * 1000 // 5 minutes
        
        // Maximum geofences allowed (Android limit is 100, we use less)
        private const val MAX_GEOFENCES = 20
        
        // Singleton
        @Volatile
        private var INSTANCE: SmartGeofenceManager? = null
        
        fun getInstance(context: Context): SmartGeofenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartGeofenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Add a geofence for an important place.
     * 
     * @param placeId Unique ID for the place
     * @param name Display name (e.g., "Home", "Work")
     * @param latitude Latitude
     * @param longitude Longitude
     * @param type Type of place (HOME, WORK, PARTNER_HOME, etc.)
     */
    suspend fun addGeofence(
        placeId: String,
        name: String,
        latitude: Double,
        longitude: Double,
        type: ImportantPlaceType
    ): Result<Unit> {
        return try {
            if (!hasLocationPermission()) {
                return Result.failure(SecurityException("Location permission not granted"))
            }
            
            Log.d(TAG, "Adding geofence: $name at ($latitude, $longitude)")
            
            // Save to Firestore first
            saveImportantPlace(placeId, name, latitude, longitude, type)
            
            // Create geofence
            val geofence = Geofence.Builder()
                .setRequestId(placeId)
                .setCircularRegion(latitude, longitude, GEOFENCE_RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL
                )
                .setLoiteringDelay(DWELL_DELAY_MS)
                .build()
            
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()
            
            geofencingClient.addGeofences(request, createPendingIntent()).await()
            
            Log.d(TAG, "✅ Geofence added successfully: $name")
            Result.success(Unit)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception adding geofence", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding geofence", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove a geofence by place ID
     */
    suspend fun removeGeofence(placeId: String): Result<Unit> {
        return try {
            geofencingClient.removeGeofences(listOf(placeId)).await()
            
            // Also remove from Firestore
            val userId = auth.currentUser?.uid ?: return Result.success(Unit)
            firestore.collection("users")
                .document(userId)
                .collection("important_places")
                .document(placeId)
                .delete()
                .await()
            
            Log.d(TAG, "✅ Geofence removed: $placeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing geofence", e)
            Result.failure(e)
        }
    }
    
    /**
     * Re-register all saved geofences.
     * Call this after boot or when app restarts.
     */
    suspend fun reRegisterAllGeofences(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.success(Unit)
            
            val places = firestore.collection("users")
                .document(userId)
                .collection("important_places")
                .get()
                .await()
            
            val geofences = places.documents.mapNotNull { doc ->
                val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                
                Geofence.Builder()
                    .setRequestId(doc.id)
                    .setCircularRegion(lat, lng, GEOFENCE_RADIUS_METERS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT or
                        Geofence.GEOFENCE_TRANSITION_DWELL
                    )
                    .setLoiteringDelay(DWELL_DELAY_MS)
                    .build()
            }
            
            if (geofences.isEmpty()) {
                Log.d(TAG, "No geofences to register")
                return Result.success(Unit)
            }
            
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            
            geofencingClient.addGeofences(request, createPendingIntent()).await()
            
            Log.d(TAG, "✅ Re-registered ${geofences.size} geofences")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error re-registering geofences", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all saved important places
     */
    suspend fun getImportantPlaces(): List<ImportantPlace> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("important_places")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                ImportantPlace(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    type = try {
                        ImportantPlaceType.valueOf(doc.getString("type") ?: "OTHER")
                    } catch (e: Exception) {
                        ImportantPlaceType.OTHER
                    },
                    isGeofenceActive = doc.getBoolean("isGeofenceActive") ?: false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting important places", e)
            emptyList()
        }
    }
    
    private suspend fun saveImportantPlace(
        placeId: String,
        name: String,
        latitude: Double,
        longitude: Double,
        type: ImportantPlaceType
    ) {
        val userId = auth.currentUser?.uid ?: return
        
        val placeData = mapOf(
            "name" to name,
            "latitude" to latitude,
            "longitude" to longitude,
            "type" to type.name,
            "isGeofenceActive" to true,
            "createdAt" to Date()
        )
        
        firestore.collection("users")
            .document(userId)
            .collection("important_places")
            .document(placeId)
            .set(placeData)
            .await()
    }
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
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
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    data class ImportantPlace(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val type: ImportantPlaceType,
        val isGeofenceActive: Boolean
    )
    
    enum class ImportantPlaceType {
        HOME,           // User's home
        WORK,           // User's workplace
        PARTNER_HOME,   // Partner's home
        PARTNER_WORK,   // Partner's workplace
        FAVORITE,       // Favorite place (cafe, restaurant, etc.)
        OTHER           // Other
    }
}

/**
 * BroadcastReceiver for Geofence transitions.
 * Called when user enters, exits, or dwells at an important place.
 */
class GeofenceReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceReceiver"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }
        
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }
        
        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        
        val transitionType = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTERED"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXITED"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELLING"
            else -> "UNKNOWN"
        }
        
        Log.d(TAG, "📍 Geofence transition: $transitionType")
        
        for (geofence in triggeringGeofences) {
            Log.d(TAG, "  Place: ${geofence.requestId}")
            
            scope.launch {
                processGeofenceTransition(
                    context = context,
                    placeId = geofence.requestId,
                    transitionType = transitionType,
                    location = geofencingEvent.triggeringLocation
                )
            }
        }
    }
    
    private suspend fun processGeofenceTransition(
        context: Context,
        placeId: String,
        transitionType: String,
        location: android.location.Location?
    ) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: return
            
            // Get place info
            val placeDoc = firestore.collection("users")
                .document(userId)
                .collection("important_places")
                .document(placeId)
                .get()
                .await()
            
            val placeName = placeDoc.getString("name") ?: "Unknown"
            val placeType = placeDoc.getString("type") ?: "OTHER"
            
            Log.d(TAG, "User $transitionType at $placeName ($placeType)")
            
            // Record in location history (especially for DWELL - stayed at place)
            if (transitionType == "DWELLING" || transitionType == "ENTERED") {
                val userDoc = firestore.collection("users").document(userId).get().await()
                val coupleId = userDoc.getString("coupleId") ?: return
                
                val latitude = location?.latitude ?: placeDoc.getDouble("latitude") ?: return
                val longitude = location?.longitude ?: placeDoc.getDouble("longitude") ?: return
                
                // Add to location history
                val historyEntry = mapOf(
                    "userId" to userId,
                    "coupleId" to coupleId,
                    "locationName" to placeName,
                    "address" to placeName,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "arrivalTime" to Date(),
                    "departureTime" to null,
                    "durationMinutes" to 0,
                    "locationType" to placeType,
                    "source" to "geofence_$transitionType"
                )
                
                firestore.collection("location_history").add(historyEntry).await()
                
                // Update current location
                val locationData = mapOf(
                    "userId" to userId,
                    "coupleId" to coupleId,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "address" to placeName,
                    "isOnline" to true,
                    "timestamp" to Date(),
                    "source" to "geofence",
                    "atPlace" to placeName,
                    "placeType" to placeType
                )
                
                val documentId = "${coupleId}_${userId}"
                firestore.collection("locations")
                    .document(documentId)
                    .set(locationData)
                    .await()
                
                // Notify partner
                SyncTriggerHelper.notifyLocationUpdated(context)
                
                Log.d(TAG, "✅ Location history updated for geofence event")
            }
            
            // Close previous history entry when exiting
            if (transitionType == "EXITED") {
                closeOpenHistoryEntry(firestore, userId)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing geofence transition", e)
        }
    }
    
    private suspend fun closeOpenHistoryEntry(firestore: FirebaseFirestore, userId: String) {
        try {
            val openEntries = firestore.collection("location_history")
                .whereEqualTo("userId", userId)
                .whereEqualTo("departureTime", null)
                .get()
                .await()
            
            for (entry in openEntries.documents) {
                val arrivalTime = entry.getDate("arrivalTime") ?: continue
                val now = Date()
                val durationMinutes = ((now.time - arrivalTime.time) / 60_000).toInt()
                
                if (durationMinutes >= 5) {
                    entry.reference.update(
                        mapOf(
                            "departureTime" to now,
                            "durationMinutes" to durationMinutes
                        )
                    ).await()
                    Log.d(TAG, "Closed history entry: ${durationMinutes}min")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing history entry", e)
        }
    }
}
