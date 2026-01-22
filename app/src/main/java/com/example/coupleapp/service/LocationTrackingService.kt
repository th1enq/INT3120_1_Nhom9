package com.example.coupleapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.example.coupleapp.data.model.LocationCoordinate
import com.example.coupleapp.data.model.LocationHistory
import com.example.coupleapp.data.model.LocationType
import com.example.coupleapp.data.repository.LocationRepository
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

/**
 * Foreground service for continuous location tracking and photo monitoring
 * This service runs in the background to:
 * 1. Track user location and upload to Firebase (Pub/Sub pattern)
 * 2. Monitor for new photos taken and associate them with colocation sessions
 * 3. Build location history based on time spent at locations
 * 4. Detect colocation (both users within 50m for 10 minutes) and create shared places
 */
class LocationTrackingService : Service() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TRACKING = "com.example.coupleapp.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.coupleapp.STOP_TRACKING"
        
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_AVATAR_URL = "avatar_url"
        const val EXTRA_COUPLE_ID = "couple_id"
        const val EXTRA_PARTNER_ID = "partner_id"
        const val EXTRA_TRACKING_MODE = "tracking_mode"
        
        // Tracking mode constants
        const val TRACKING_MODE_ACTIVE = "active"      // App is actively being used (location screen)
        const val TRACKING_MODE_FOREGROUND = "foreground" // App is in foreground
        const val TRACKING_MODE_BACKGROUND = "background"  // App is in background
        
        // Location tracking constants - adaptive based on mode
        // PHƯƠNG ÁN B: Trong app = 30s-1min, Kill app = dựa vào WorkManager 15-30 phút
        // Foreground Service CHỈ hoạt động khi app đang mở
        const val LOCATION_UPDATE_INTERVAL_ACTIVE_MS = 10_000L   // 10 seconds when actively viewing location screen
        const val LOCATION_UPDATE_INTERVAL_FOREGROUND_MS = 30_000L // 30 seconds when app in foreground
        const val LOCATION_UPDATE_INTERVAL_BACKGROUND_MS = 60_000L // 1 minute - nhưng service sẽ dừng khi kill app
        
        const val LOCATION_FASTEST_INTERVAL_ACTIVE_MS = 5_000L    // 5 seconds fastest for active mode  
        const val LOCATION_FASTEST_INTERVAL_FOREGROUND_MS = 15_000L // 15 seconds fastest for foreground
        const val LOCATION_FASTEST_INTERVAL_BACKGROUND_MS = 30_000L // 30 seconds fastest for background
        const val LOCATION_HISTORY_MIN_DURATION_MS = 300_000L // 5 minutes to record in history (changed from 10)
        const val LOCATION_HISTORY_MIN_DISTANCE_METERS = 500.0 // 500m minimum distance from last history entry
        const val LOCATION_SIGNIFICANT_CHANGE_METERS = 100.0 // 100 meters to consider a location change
        const val COLOCATION_DISTANCE_METERS = 300.0 // 300 meters to be considered same location
        const val COLOCATION_TIME_MINUTES = 5L // 5 minutes to create shared place (reduced for easier testing)
        const val DUPLICATE_PLACE_DISTANCE_METERS = 500.0 // Don't create new place if one exists within 500m
        
        // State for external observation (Pub/Sub)
        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking
        
        private val _lastKnownLocation = MutableStateFlow<LocationCoordinate?>(null)
        val lastKnownLocation: StateFlow<LocationCoordinate?> = _lastKnownLocation
        
        // Colocation state
        private val _isColocationActive = MutableStateFlow(false)
        val isColocationActive: StateFlow<Boolean> = _isColocationActive
        
        private val _colocationStartTime = MutableStateFlow<Long?>(null)
        val colocationStartTime: StateFlow<Long?> = _colocationStartTime
        
        // Photo deduplication - shared across all observer instances
        // These need to be in companion object to persist across service restarts
        private val processedPhotoPaths = mutableSetOf<String>()
        private val recentlyProcessedUris = mutableMapOf<String, Long>()
        private val addingPhotos = mutableSetOf<String>()
        private val photoProcessingLock = Any()
        private const val DEBOUNCE_MS = 3000L // 3 seconds debounce window
        private const val MAX_CACHE_SIZE = 200 // Maximum entries before cleanup
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour expiry for URI cache
        
        /**
         * Periodic cleanup of stale entries in caches
         * Should be called periodically to prevent memory buildup
         */
        fun cleanupCaches() {
            synchronized(photoProcessingLock) {
                val now = System.currentTimeMillis()
                
                // Clean recentlyProcessedUris - remove entries older than CACHE_EXPIRY_MS
                val expiredUris = recentlyProcessedUris.filter { (_, timestamp) ->
                    now - timestamp > CACHE_EXPIRY_MS
                }.keys
                expiredUris.forEach { recentlyProcessedUris.remove(it) }
                
                // Trim processedPhotoPaths if too large
                if (processedPhotoPaths.size > MAX_CACHE_SIZE) {
                    val toRemove = processedPhotoPaths.size - MAX_CACHE_SIZE / 2
                    val iterator = processedPhotoPaths.iterator()
                    repeat(toRemove) {
                        if (iterator.hasNext()) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                }
                
                // Clear addingPhotos that might be stuck
                addingPhotos.clear()
                
                android.util.Log.d("LocationTrackingService", 
                    "Cache cleanup: processedPhotoPaths=${processedPhotoPaths.size}, recentlyProcessedUris=${recentlyProcessedUris.size}")
            }
        }
        
        /**
         * Full reset of all caches - call on logout or app termination
         */
        fun resetAllCaches() {
            synchronized(photoProcessingLock) {
                processedPhotoPaths.clear()
                recentlyProcessedUris.clear()
                addingPhotos.clear()
                _lastKnownLocation.value = null
                _isColocationActive.value = false
                _colocationStartTime.value = null
                android.util.Log.d("LocationTrackingService", "All caches reset")
            }
        }
        
        fun startService(
            context: Context,
            userId: String,
            userName: String,
            avatarUrl: String,
            coupleId: String,
            partnerId: String
        ): Boolean {
            // Validate required parameters before starting service
            if (userId.isEmpty()) {
                android.util.Log.w("LocationTrackingService", "Cannot start service: userId is empty")
                return false
            }
            if (coupleId.isEmpty()) {
                android.util.Log.w("LocationTrackingService", "Cannot start service: coupleId is empty (user may not be paired yet)")
                return false
            }
            
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
                putExtra(EXTRA_AVATAR_URL, avatarUrl)
                putExtra(EXTRA_COUPLE_ID, coupleId)
                putExtra(EXTRA_PARTNER_ID, partnerId)
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                return true
            } catch (e: Exception) {
                android.util.Log.e("LocationTrackingService", "Failed to start service", e)
                return false
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
        
        /**
         * Update tracking mode to adjust location update frequency
         * Call this when app state changes (foreground/background, entering/leaving location screen)
         */
        fun updateTrackingMode(context: Context, mode: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = "UPDATE_TRACKING_MODE"
                putExtra(EXTRA_TRACKING_MODE, mode)
            }
            try {
                context.startService(intent)
                android.util.Log.d("LocationTrackingService", "Updating tracking mode to: $mode")
            } catch (e: Exception) {
                android.util.Log.e("LocationTrackingService", "Failed to update tracking mode", e)
            }
        }
    }
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRepository: LocationRepository
    private lateinit var db: FirebaseFirestore
    
    private var userId: String = ""
    private var userName: String = ""
    private var avatarUrl: String = ""
    private var coupleId: String = ""
    private var partnerId: String = ""
    private var currentTrackingMode: String = TRACKING_MODE_FOREGROUND
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // SharedPreferences for persistent photo deduplication
    private val processedPhotosPrefs by lazy {
        getSharedPreferences("processed_photos", Context.MODE_PRIVATE)
    }
    
    // Location history tracking
    private var currentLocationEntry: LocationHistoryEntry? = null
    private var lastSignificantLocation: LocationCoordinate? = null
    private var lastSavedHistoryLocation: LocationCoordinate? = null // Track last saved history location for 500m check
    
    // Photo observer
    private var photoObserver: PhotoContentObserver? = null
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val coordinate = LocationCoordinate(location.latitude, location.longitude)
                _lastKnownLocation.value = coordinate
                
                android.util.Log.d("LocationTrackingService", "Location update received: ${location.latitude}, ${location.longitude}")
                
                serviceScope.launch {
                    processLocationUpdate(coordinate)
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRepository = LocationRepository(this)
        db = FirebaseFirestore.getInstance()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                // CRITICAL: Always call startForeground() immediately to prevent ANR/crash
                // Android requires this within 5 seconds of startForegroundService()
                startForeground(NOTIFICATION_ID, createNotification())
                
                val newUserId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
                val newCoupleId = intent.getStringExtra(EXTRA_COUPLE_ID) ?: ""
                val newPartnerId = intent.getStringExtra(EXTRA_PARTNER_ID) ?: ""
                
                // Check if service is already running with same user - avoid restart
                val isAlreadyRunning = _isTracking.value && 
                    userId == newUserId && 
                    coupleId == newCoupleId
                
                if (isAlreadyRunning) {
                    android.util.Log.d("LocationTrackingService", "Service already running for same user, skipping restart")
                    return START_STICKY
                }
                
                userId = newUserId
                userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
                avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL) ?: ""
                coupleId = newCoupleId
                partnerId = newPartnerId
                
                android.util.Log.d("LocationTrackingService", "Service started with: userId=$userId, coupleId=$coupleId, partnerId=$partnerId")
                
                // Validate required fields after startForeground
                if (userId.isEmpty() || coupleId.isEmpty()) {
                    android.util.Log.w("LocationTrackingService", "Missing userId or coupleId, stopping service")
                    _isTracking.value = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                if (partnerId.isEmpty()) {
                    android.util.Log.w("LocationTrackingService", "partnerId is empty - colocation tracking will not work")
                }
                
                // Clean up old processed photos from SharedPreferences (older than 24 hours)
                cleanupOldProcessedPhotos()
                
                startLocationTracking()
                startPhotoMonitoring()
                _isTracking.value = true
                android.util.Log.d("LocationTrackingService", "Service started successfully, tracking enabled")
            }
            ACTION_STOP_TRACKING -> {
                stopLocationTracking()
                stopPhotoMonitoring()
                _isTracking.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "UPDATE_TRACKING_MODE" -> {
                val newMode = intent?.getStringExtra(EXTRA_TRACKING_MODE) ?: TRACKING_MODE_FOREGROUND
                if (newMode != currentTrackingMode) {
                    currentTrackingMode = newMode
                    android.util.Log.d("LocationTrackingService", "Switching tracking mode to: $newMode")
                    
                    // Restart location tracking with new interval
                    if (_isTracking.value) {
                        stopLocationTracking()
                        startLocationTracking()
                    }
                }
            }
            else -> {
                // Handle case where service is restarted by system without intent
                // Must call startForeground to prevent crash
                startForeground(NOTIFICATION_ID, createNotification())
                if (userId.isEmpty() || coupleId.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        stopPhotoMonitoring()
        serviceScope.cancel()
        _isTracking.value = false
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when location tracking is active"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.sharing_location))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        // Select intervals based on current tracking mode
        val (updateInterval, fastestInterval) = when (currentTrackingMode) {
            TRACKING_MODE_ACTIVE -> Pair(
                LOCATION_UPDATE_INTERVAL_ACTIVE_MS, 
                LOCATION_FASTEST_INTERVAL_ACTIVE_MS
            )
            TRACKING_MODE_BACKGROUND -> Pair(
                LOCATION_UPDATE_INTERVAL_BACKGROUND_MS, 
                LOCATION_FASTEST_INTERVAL_BACKGROUND_MS
            )
            else -> Pair(
                LOCATION_UPDATE_INTERVAL_FOREGROUND_MS, 
                LOCATION_FASTEST_INTERVAL_FOREGROUND_MS
            )
        }
        
        // Use BALANCED_POWER_ACCURACY for background mode to save battery
        // HIGH_ACCURACY uses GPS continuously which drains battery fast
        val priority = when (currentTrackingMode) {
            TRACKING_MODE_BACKGROUND -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_HIGH_ACCURACY
        }
        
        android.util.Log.d("LocationTrackingService", 
            "Starting location tracking with mode: $currentTrackingMode, priority: $priority, interval: ${updateInterval}ms")
        
        val locationRequest = LocationRequest.Builder(
            priority,
            updateInterval
        ).apply {
            setMinUpdateIntervalMillis(fastestInterval)
            setWaitForAccurateLocation(false)
            // For background mode, also set displacement to avoid updates when stationary
            if (currentTrackingMode == TRACKING_MODE_BACKGROUND) {
                setMinUpdateDistanceMeters(50f) // Only update if moved 50m (battery optimization)
            }
        }.build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    private fun stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        // Save current location history entry if exists
        currentLocationEntry?.let { entry ->
            val duration = System.currentTimeMillis() - entry.arrivalTimeMs
            if (duration >= LOCATION_HISTORY_MIN_DURATION_MS) {
                saveLocationHistoryEntry(entry, System.currentTimeMillis())
            }
        }
    }
    
    private suspend fun processLocationUpdate(coordinate: LocationCoordinate) {
        // Upload current location to Firebase
        uploadCurrentLocation(coordinate)
        
        // Check if this is a significant location change
        val lastLocation = lastSignificantLocation
        if (lastLocation == null || calculateDistance(lastLocation, coordinate) > LOCATION_SIGNIFICANT_CHANGE_METERS) {
            // Significant change - save previous location to history if applicable
            currentLocationEntry?.let { entry ->
                val duration = System.currentTimeMillis() - entry.arrivalTimeMs
                if (duration >= LOCATION_HISTORY_MIN_DURATION_MS) {
                    saveLocationHistoryEntry(entry, System.currentTimeMillis())
                }
            }
            
            // Start tracking new location
            currentLocationEntry = LocationHistoryEntry(
                coordinate = coordinate,
                address = getAddressFromCoordinate(coordinate),
                arrivalTimeMs = System.currentTimeMillis()
            )
            lastSignificantLocation = coordinate
        }
        
        // Check colocation with partner
        checkColocationWithPartner(coordinate)
    }
    
    private fun uploadCurrentLocation(coordinate: LocationCoordinate) {
        // Validate we have required data before uploading
        if (userId.isEmpty() || coupleId.isEmpty()) {
            android.util.Log.w("LocationTrackingService", "Cannot upload location: missing userId or coupleId")
            return
        }
        
        val locationData = mapOf(
            "userId" to userId,
            "coupleId" to coupleId,
            "userName" to userName,
            "avatarUrl" to avatarUrl,
            "latitude" to coordinate.latitude,
            "longitude" to coordinate.longitude,
            "address" to getAddressFromCoordinate(coordinate),
            "batteryLevel" to getBatteryLevel(),
            "isOnline" to true,
            "timestamp" to Date()
        )
        
        val documentId = "${coupleId}_${userId}"
        db.collection("locations")
            .document(documentId)
            .set(locationData)
            .addOnSuccessListener {
                android.util.Log.d("LocationTrackingService", "Location uploaded successfully: $documentId")
                
                // Update location widget with new data
                com.example.coupleapp.widget.LocationWidgetProvider.onLocationChanged(this)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LocationTrackingService", "Failed to upload location", e)
            }
    }
    
    private fun saveLocationHistoryEntry(entry: LocationHistoryEntry, departureTimeMs: Long) {
        val durationMinutes = ((departureTimeMs - entry.arrivalTimeMs) / 60_000).toInt()
        
        // Skip if duration is too short (less than 5 minutes)
        if (durationMinutes < 5) {
            android.util.Log.d("LocationTrackingService", 
                "Skipping location history - duration too short: ${durationMinutes}min")
            return
        }
        
        // Smart history merging: Check if there's a recent entry at the same location
        // This prevents duplicate entries when user opens/closes app at the same place
        serviceScope.launch {
            try {
                val recentHistoryQuery = db.collection("location_history")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("coupleId", coupleId)
                    .limit(10)
                    .get()
                    .await()
                
                var mergedWithExisting = false
                val now = Date(departureTimeMs)
                
                for (doc in recentHistoryQuery.documents) {
                    val historyLat = doc.getDouble("latitude") ?: continue
                    val historyLng = doc.getDouble("longitude") ?: continue
                    val historyCoord = LocationCoordinate(historyLat, historyLng)
                    val historyArrival = doc.getDate("arrivalTime") ?: continue
                    val historyName = doc.getString("locationName") ?: ""
                    
                    val distance = calculateDistance(entry.coordinate, historyCoord)
                    
                    // Check if same location (within 200m) or same name
                    val isSameLocation = distance <= COLOCATION_DISTANCE_METERS
                    val isSameName = historyName.isNotBlank() && 
                        detectPlaceName(entry.address).equals(historyName, ignoreCase = true)
                    
                    // Check if within last 2 hours (to merge nearby sessions)
                    val timeDifferenceMs = entry.arrivalTimeMs - historyArrival.time
                    val isRecentEntry = timeDifferenceMs >= 0 && timeDifferenceMs < 2 * 60 * 60 * 1000 // 2 hours
                    
                    if ((isSameLocation || isSameName) && isRecentEntry) {
                        // Merge: Update the existing entry's departure time and duration
                        val existingDuration = doc.getLong("durationMinutes")?.toInt() ?: 0
                        val newDuration = existingDuration + durationMinutes
                        
                        doc.reference.update(
                            mapOf(
                                "departureTime" to now,
                                "durationMinutes" to newDuration
                            )
                        ).await()
                        
                        android.util.Log.d("LocationTrackingService", 
                            "Merged with existing history: ${doc.id}, new duration: ${newDuration}min")
                        mergedWithExisting = true
                        lastSavedHistoryLocation = entry.coordinate
                        break
                    }
                }
                
                // Only create new entry if not merged
                if (!mergedWithExisting) {
                    createNewHistoryEntry(entry, departureTimeMs, durationMinutes)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("LocationTrackingService", "Error checking for merge", e)
                // Fallback: create new entry
                createNewHistoryEntry(entry, departureTimeMs, durationMinutes)
            }
        }
    }
    
    private fun createNewHistoryEntry(entry: LocationHistoryEntry, departureTimeMs: Long, durationMinutes: Int) {
        // Check if this location is at least 500m from the last saved history entry
        val lastSaved = lastSavedHistoryLocation
        if (lastSaved != null) {
            val distanceFromLast = calculateDistance(lastSaved, entry.coordinate)
            if (distanceFromLast < LOCATION_HISTORY_MIN_DISTANCE_METERS) {
                android.util.Log.d("LocationTrackingService", 
                    "Skipping location history - only ${distanceFromLast}m from last saved (need ${LOCATION_HISTORY_MIN_DISTANCE_METERS}m)")
                return
            }
        }
        
        // Generate meaningful location name
        val locationName = if (entry.address.isNotBlank() && 
            !entry.address.startsWith("Location (") && 
            entry.address != "Unknown location") {
            detectPlaceName(entry.address)
        } else {
            // Create a descriptive name based on time of day
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = entry.arrivalTimeMs
            }.get(java.util.Calendar.HOUR_OF_DAY)
            
            val timeOfDay = when {
                hour in 6..11 -> "Morning"
                hour in 12..17 -> "Afternoon"
                hour in 18..20 -> "Evening"
                else -> "Night"
            }
            "$timeOfDay Location"
        }
        
        android.util.Log.d("LocationTrackingService", 
            "Saving location history: $locationName (${entry.address}), duration: ${durationMinutes}min")
        
        val historyData = mapOf(
            "userId" to userId,
            "coupleId" to coupleId,
            "locationName" to locationName,
            "address" to entry.address,
            "latitude" to entry.coordinate.latitude,
            "longitude" to entry.coordinate.longitude,
            "arrivalTime" to Date(entry.arrivalTimeMs),
            "departureTime" to Date(departureTimeMs),
            "durationMinutes" to durationMinutes,
            "locationType" to detectLocationType(entry.address).name
        )
        
        db.collection("location_history").add(historyData)
            .addOnSuccessListener {
                // Update the last saved history location
                lastSavedHistoryLocation = entry.coordinate
                android.util.Log.d("LocationTrackingService", "Location history saved successfully: $locationName")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LocationTrackingService", "Failed to save location history", e)
            }
    }
    
    private suspend fun checkColocationWithPartner(myCoordinate: LocationCoordinate) {
        try {
            if (partnerId.isEmpty()) {
                android.util.Log.w("LocationTrackingService", "Cannot check colocation: partnerId is empty")
                return
            }
            
            android.util.Log.d("LocationTrackingService", "Checking colocation - myCoord: ${myCoordinate.latitude}, ${myCoordinate.longitude}")
            android.util.Log.d("LocationTrackingService", "Looking for partner document: ${coupleId}_${partnerId}")
            
            val partnerDoc = db.collection("locations")
                .document("${coupleId}_${partnerId}")
                .get()
                .await()
            
            if (!partnerDoc.exists()) {
                android.util.Log.d("LocationTrackingService", "Partner location document not found in Firebase")
                _isColocationActive.value = false
                return
            }
            
            android.util.Log.d("LocationTrackingService", "Partner document found! Data: ${partnerDoc.data}")
            
            val partnerLat = partnerDoc.getDouble("latitude") ?: run {
                android.util.Log.e("LocationTrackingService", "Partner latitude is null")
                return
            }
            val partnerLng = partnerDoc.getDouble("longitude") ?: run {
                android.util.Log.e("LocationTrackingService", "Partner longitude is null")
                return
            }
            val partnerCoordinate = LocationCoordinate(partnerLat, partnerLng)
            
            val distance = calculateDistance(myCoordinate, partnerCoordinate)
            android.util.Log.d("LocationTrackingService", "Distance to partner: $distance meters (threshold: $COLOCATION_DISTANCE_METERS m)")
            
            if (distance <= COLOCATION_DISTANCE_METERS) {
                android.util.Log.d("LocationTrackingService", ">>> Users are TOGETHER! Distance: $distance m")
                _isColocationActive.value = true
                
                // Update or create colocation session
                updateColocationSession(myCoordinate, partnerCoordinate)
            } else {
                android.util.Log.d("LocationTrackingService", "Users are APART. Distance: $distance m")
                _isColocationActive.value = false
                _colocationStartTime.value = null
                
                // End colocation session if exists
                endColocationSessionIfExists()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackingService", "Error checking colocation", e)
        }
    }
    
    private fun updateColocationSession(myCoordinate: LocationCoordinate, partnerCoordinate: LocationCoordinate) {
        val centerCoordinate = LocationCoordinate(
            (myCoordinate.latitude + partnerCoordinate.latitude) / 2,
            (myCoordinate.longitude + partnerCoordinate.longitude) / 2
        )
        
        val sessionRef = db.collection("colocation_sessions").document("${coupleId}_active")
        
        // First check session status without transaction
        sessionRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // Create new colocation session - users just came together
                android.util.Log.d("LocationTrackingService", "Creating new colocation session at ${centerCoordinate}")
                _colocationStartTime.value = Date().time
                
                val sessionData = mapOf(
                    "coupleId" to coupleId,
                    "latitude" to centerCoordinate.latitude,
                    "longitude" to centerCoordinate.longitude,
                    "address" to getAddressFromCoordinate(centerCoordinate),
                    "startTime" to Date(),
                    "isActive" to true,
                    "convertedToSharedPlace" to false,
                    "sharedPlaceId" to null,
                    "photosCollected" to emptyList<String>()
                )
                sessionRef.set(sessionData)
                    .addOnSuccessListener {
                        android.util.Log.d("LocationTrackingService", "Colocation session created successfully")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("LocationTrackingService", "Failed to create colocation session", e)
                    }
            } else {
                // Session exists - check duration and update
                val startTime = snapshot.getDate("startTime")
                val convertedToSharedPlace = snapshot.getBoolean("convertedToSharedPlace") ?: false
                val existingSharedPlaceId = snapshot.getString("sharedPlaceId")
                
                android.util.Log.d("LocationTrackingService", "Session exists - convertedToSharedPlace: $convertedToSharedPlace, sharedPlaceId: $existingSharedPlaceId")
                
                if (startTime != null) {
                    _colocationStartTime.value = startTime.time
                    val durationMinutes = (Date().time - startTime.time) / 60_000
                    android.util.Log.d("LocationTrackingService", "Colocation duration: $durationMinutes minutes (need ${COLOCATION_TIME_MINUTES} min)")
                    
                    // Check for failed conversion (marked as converted but no sharedPlaceId)
                    if (convertedToSharedPlace && existingSharedPlaceId.isNullOrEmpty()) {
                        android.util.Log.w("LocationTrackingService", ">>> Previous conversion failed! Resetting to try again...")
                        sessionRef.update("convertedToSharedPlace", false)
                            .addOnSuccessListener {
                                android.util.Log.d("LocationTrackingService", "Reset convertedToSharedPlace to false")
                            }
                        return@addOnSuccessListener
                    }
                    
                    if (durationMinutes >= COLOCATION_TIME_MINUTES && !convertedToSharedPlace) {
                        // Time threshold reached! Create shared place
                        android.util.Log.d("LocationTrackingService", ">>> Creating shared place after $durationMinutes minutes together!")
                        createSharedPlaceFromSession(snapshot) { sharedPlaceId ->
                            // Mark session as converted with the shared place ID
                            if (sharedPlaceId.isNotEmpty()) {
                                sessionRef.update(mapOf(
                                    "convertedToSharedPlace" to true,
                                    "sharedPlaceId" to sharedPlaceId
                                )).addOnSuccessListener {
                                    android.util.Log.d("LocationTrackingService", "Session marked as converted")
                                }
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("LocationTrackingService", "Error checking colocation session", e)
        }
    }
    
    private fun createSharedPlaceFromSession(
        sessionSnapshot: com.google.firebase.firestore.DocumentSnapshot,
        onComplete: (String) -> Unit
    ) {
        val latitude = sessionSnapshot.getDouble("latitude") ?: run {
            android.util.Log.e("LocationTrackingService", "Missing latitude in session")
            onComplete("")
            return
        }
        val longitude = sessionSnapshot.getDouble("longitude") ?: run {
            android.util.Log.e("LocationTrackingService", "Missing longitude in session")
            onComplete("")
            return
        }
        val address = sessionSnapshot.getString("address") ?: "Unknown location"
        val startTime = sessionSnapshot.getDate("startTime") ?: Date()
        @Suppress("UNCHECKED_CAST")
        val photos = sessionSnapshot.get("photosCollected") as? List<String> ?: emptyList()
        
        val placeName = if (address.isNotEmpty() && address != "Unknown location") {
            detectPlaceName(address)
        } else {
            "Shared Place ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(startTime)}"
        }
        
        // Check for existing place within 500m to avoid duplicates
        checkForExistingPlaceNearby(latitude, longitude, DUPLICATE_PLACE_DISTANCE_METERS) { existingPlaceId ->
            if (existingPlaceId != null) {
                // Place already exists nearby - update duration and add photos to it
                android.util.Log.d("LocationTrackingService", "Found existing place within ${DUPLICATE_PLACE_DISTANCE_METERS}m: $existingPlaceId")
                updateExistingPlace(existingPlaceId, photos, startTime, onComplete)
            } else {
                // No existing place nearby - create new one
                createNewSharedPlace(placeName, address, latitude, longitude, startTime, photos, onComplete)
            }
        }
    }
    
    /**
     * Check if there's an existing shared place within specified distance
     */
    private fun checkForExistingPlaceNearby(
        latitude: Double,
        longitude: Double,
        maxDistanceMeters: Double,
        callback: (String?) -> Unit
    ) {
        db.collection("shared_places")
            .whereEqualTo("coupleId", coupleId)
            .get()
            .addOnSuccessListener { snapshot ->
                var nearestPlaceId: String? = null
                var nearestDistance = Double.MAX_VALUE
                
                for (doc in snapshot.documents) {
                    val placeLat = doc.getDouble("latitude") ?: continue
                    val placeLng = doc.getDouble("longitude") ?: continue
                    
                    val distance = calculateDistance(
                        LocationCoordinate(latitude, longitude),
                        LocationCoordinate(placeLat, placeLng)
                    )
                    
                    if (distance <= maxDistanceMeters && distance < nearestDistance) {
                        nearestDistance = distance
                        nearestPlaceId = doc.id
                    }
                }
                
                android.util.Log.d("LocationTrackingService", 
                    "Checked ${snapshot.documents.size} places. Nearest: $nearestPlaceId at ${nearestDistance}m")
                callback(nearestPlaceId)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LocationTrackingService", "Error checking nearby places", e)
                callback(null) // Proceed to create new place on error
            }
    }
    
    /**
     * Update existing place with new visit data and photos
     */
    private fun updateExistingPlace(
        placeId: String,
        photos: List<String>,
        startTime: Date,
        onComplete: (String) -> Unit
    ) {
        val placeRef = db.collection("shared_places").document(placeId)
        
        placeRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                onComplete("")
                return@addOnSuccessListener
            }
            
            val currentDuration = doc.getLong("durationMinutes")?.toInt() ?: 0
            val newDuration = currentDuration + ((Date().time - startTime.time) / 60_000).toInt()
            
            // Update duration first
            placeRef.update("durationMinutes", newDuration, "visitDate", Date())
                .addOnSuccessListener {
                    android.util.Log.d("LocationTrackingService", "Updated existing place duration: $placeId")
                    
                    // Convert and add photos in background with deduplication
                    serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val currentRepPhoto = doc.getString("representativePhotoUrl") ?: ""
                        var firstPhotoBase64: String? = null
                        
                        // Fetch ALL existing photos for this place ONCE for efficient dedup checking
                        val allExistingPhotos = try {
                            db.collection("shared_place_photos")
                                .whereEqualTo("placeId", placeId)
                                .get()
                                .await()
                        } catch (e: Exception) {
                            android.util.Log.e("LocationTrackingService", "Error fetching existing photos", e)
                            null
                        }
                        
                        // Build a set of existing filenames for fast lookup
                        val existingFilenames = mutableSetOf<String>()
                        allExistingPhotos?.documents?.forEach { doc ->
                            val existingPath = doc.getString("originalPath") ?: ""
                            val existingStableFilename = doc.getString("stableFilename") ?: ""
                            if (existingPath.isNotEmpty()) {
                                existingFilenames.add(existingPath.substringAfterLast("/"))
                            }
                            if (existingStableFilename.isNotEmpty()) {
                                existingFilenames.add(existingStableFilename)
                            }
                        }
                        android.util.Log.d("LocationTrackingService", "Existing filenames in place: ${existingFilenames.size}")
                        
                        photos.forEachIndexed { index, photoPath ->
                            // Extract stable filename for reliable deduplication
                            val stableFilename = photoPath.substringAfterLast("/")
                            
                            // Skip .pending- files (camera hasn't finished writing)
                            if (photoPath.contains(".pending-")) {
                                android.util.Log.d("LocationTrackingService", "SKIP_PENDING: Skipping pending file in updateExistingPlace: $photoPath")
                                return@forEachIndexed
                            }
                            
                            // Check for duplicate by comparing filename with existing set
                            if (existingFilenames.contains(stableFilename)) {
                                android.util.Log.d("LocationTrackingService", "DEDUP: Photo already exists in place (by filename), skipping: $stableFilename")
                                return@forEachIndexed
                            }
                            
                            // Add to set to prevent duplicates within same batch
                            existingFilenames.add(stableFilename)
                            
                            val base64Photo = convertPhotoToBase64(photoPath)
                            if (base64Photo != null) {
                                if (index == 0 || firstPhotoBase64 == null) {
                                    firstPhotoBase64 = base64Photo
                                }
                                
                                val photoData = mapOf(
                                    "placeId" to placeId,
                                    "photoUrl" to base64Photo,
                                    "takenAt" to Date(),
                                    "takenByUserId" to userId,
                                    "caption" to null,
                                    "originalPath" to photoPath, // For dedup checking
                                    "stableFilename" to stableFilename // For reliable dedup
                                )
                                db.collection("shared_place_photos").add(photoData)
                                    .addOnSuccessListener {
                                        android.util.Log.d("LocationTrackingService", "Added photo to existing place")
                                        
                                        // Increment photosCount for each successful photo add
                                        db.runTransaction { transaction ->
                                            val placeSnapshot = transaction.get(placeRef)
                                            val currentCount = placeSnapshot.getLong("photosCount") ?: 0
                                            transaction.update(placeRef, "photosCount", currentCount + 1)
                                        }
                                    }
                            }
                        }
                        
                        // Set representative photo if empty
                        if (currentRepPhoto.isEmpty() && firstPhotoBase64 != null) {
                            placeRef.update("representativePhotoUrl", firstPhotoBase64)
                        }
                    }
                    
                    onComplete(placeId)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("LocationTrackingService", "Failed to update place", e)
                    onComplete("")
                }
        }
    }
    
    /**
     * Create a new shared place (when no nearby place exists)
     */
    private fun createNewSharedPlace(
        placeName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        startTime: Date,
        photos: List<String>,
        onComplete: (String) -> Unit
    ) {
        // Process photos conversion in background
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Convert first photo to base64 for representative photo
            val representativePhoto = photos.firstOrNull()?.let { path ->
                convertPhotoToBase64(path)
            } ?: ""
            
            val sharedPlaceData = mapOf(
                "coupleId" to coupleId,
                "placeName" to placeName,
                "address" to address,
                "latitude" to latitude,
                "longitude" to longitude,
                "representativePhotoUrl" to representativePhoto,
                "visitDate" to startTime,
                "durationMinutes" to ((Date().time - startTime.time) / 60_000).toInt(),
                "photosCount" to 0, // Will be updated as photos are added
                "locationType" to detectLocationType(address).name,
                "photoUrls" to emptyList<String>() // Don't store paths, photos are in shared_place_photos
            )
            
            android.util.Log.d("LocationTrackingService", "Creating new shared place: $placeName at $address")
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                db.collection("shared_places")
                    .add(sharedPlaceData)
                    .addOnSuccessListener { docRef ->
                        android.util.Log.d("LocationTrackingService", ">>> SUCCESS! Created shared place: ${docRef.id}")
                        
                        // Convert and add photos to shared_place_photos collection with deduplication
                        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            var successfulPhotos = 0
                            // Track filenames we're adding to prevent duplicates within batch
                            val addedFilenames = mutableSetOf<String>()
                            
                            photos.forEach { photoPath ->
                                // Extract stable filename for reliable deduplication
                                val stableFilename = photoPath.substringAfterLast("/")
                                
                                // Skip .pending- files (camera hasn't finished writing)
                                if (photoPath.contains(".pending-")) {
                                    android.util.Log.d("LocationTrackingService", "SKIP_PENDING: Skipping pending file in createNewSharedPlace: $photoPath")
                                    return@forEach
                                }
                                
                                // Check for duplicate within this batch
                                if (addedFilenames.contains(stableFilename)) {
                                    android.util.Log.d("LocationTrackingService", "DEDUP_BATCH: Photo already in batch, skipping: $stableFilename")
                                    return@forEach
                                }
                                
                                // For new place, check if any photo with same filename was already added
                                val existingPhotos = try {
                                    db.collection("shared_place_photos")
                                        .whereEqualTo("placeId", docRef.id)
                                        .get()
                                        .await()
                                } catch (e: Exception) {
                                    android.util.Log.e("LocationTrackingService", "Error checking for existing photos", e)
                                    null
                                }
                                
                                val duplicateExists = existingPhotos?.documents?.any { doc ->
                                    val existingPath = doc.getString("originalPath") ?: ""
                                    val existingStableFilename = doc.getString("stableFilename") ?: ""
                                    existingPath.substringAfterLast("/") == stableFilename ||
                                    existingStableFilename == stableFilename
                                } ?: false
                                
                                if (duplicateExists) {
                                    android.util.Log.d("LocationTrackingService", "DEDUP: Photo already exists in new place (by filename check), skipping: $stableFilename")
                                    return@forEach
                                }
                                
                                addedFilenames.add(stableFilename)
                                
                                val base64Photo = convertPhotoToBase64(photoPath)
                                if (base64Photo != null) {
                                    val photoData = mapOf(
                                        "placeId" to docRef.id,
                                        "photoUrl" to base64Photo,
                                        "takenAt" to Date(),
                                        "takenByUserId" to userId,
                                        "caption" to null,
                                        "originalPath" to photoPath, // For dedup checking
                                        "stableFilename" to stableFilename // For reliable dedup
                                    )
                                    db.collection("shared_place_photos").add(photoData)
                                        .addOnSuccessListener {
                                            successfulPhotos++
                                            android.util.Log.d("LocationTrackingService", "Added photo to shared place ($successfulPhotos/${photos.size})")
                                            
                                            // Update photosCount after each successful photo add
                                            db.collection("shared_places").document(docRef.id)
                                                .update("photosCount", successfulPhotos)
                                        }
                                }
                            }
                        }
                        
                        onComplete(docRef.id)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("LocationTrackingService", ">>> FAILED to create shared place", e)
                        onComplete("")
                    }
            }
        }
    }
    
    /**
     * Convert photo file to base64 string for Firestore storage
     */
    private fun convertPhotoToBase64(photoPath: String): String? {
        return try {
            val file = java.io.File(photoPath)
            if (!file.exists()) {
                android.util.Log.e("LocationTrackingService", "Photo file not found: $photoPath")
                return null
            }
            
            // Read and compress the image
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = 2 // Scale down to 50%
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(photoPath, options) ?: return null
            
            // Resize if too large (max 1200px on longest side)
            val maxDimension = 1200
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
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
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
            val imageBytes = outputStream.toByteArray()
            
            // Clean up bitmaps
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            
            val base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64String"
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackingService", "Error converting photo to base64", e)
            null
        }
    }
    
    private fun endColocationSessionIfExists() {
        val sessionRef = db.collection("colocation_sessions").document("${coupleId}_active")
        sessionRef.get().addOnSuccessListener { doc ->
            if (doc.exists() && doc.getBoolean("isActive") == true) {
                android.util.Log.d("LocationTrackingService", "Ending colocation session")
                sessionRef.update(
                    mapOf(
                        "isActive" to false,
                        "endTime" to Date()
                    )
                )
            }
        }
    }
    
    // Photo monitoring - automatically collect photos taken during colocation
    private fun startPhotoMonitoring() {
        // First, stop any existing observer to prevent duplicates
        if (photoObserver != null) {
            android.util.Log.d("LocationTrackingService", "Photo observer already exists, stopping it first")
            stopPhotoMonitoring()
        }
        
        android.util.Log.d("LocationTrackingService", "Starting photo monitoring for colocation sessions")
        photoObserver = PhotoContentObserver(Handler(Looper.getMainLooper()), contentResolver)
        
        // Register for both internal and external storage
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            photoObserver!!
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                true,
                photoObserver!!
            )
        }
        
        android.util.Log.d("LocationTrackingService", "Photo monitoring started successfully")
    }
    
    private fun stopPhotoMonitoring() {
        photoObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        photoObserver = null
    }
    
    /**
     * Clean up old processed photo entries from SharedPreferences
     * Remove entries older than 24 hours to prevent unbounded growth
     */
    private fun cleanupOldProcessedPhotos() {
        try {
            val prefs = processedPhotosPrefs
            val allEntries = prefs.all
            val now = System.currentTimeMillis()
            val oneDayAgo = now - 24 * 60 * 60 * 1000L // 24 hours
            
            val editor = prefs.edit()
            var removedCount = 0
            
            for ((key, value) in allEntries) {
                if (value is Long && value < oneDayAgo) {
                    editor.remove(key)
                    removedCount++
                }
            }
            
            if (removedCount > 0) {
                editor.apply()
                android.util.Log.d("LocationTrackingService", "Cleaned up $removedCount old processed photo entries")
            } else {
                android.util.Log.d("LocationTrackingService", "No old processed photo entries to clean up, total: ${allEntries.size}")
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackingService", "Error cleaning up processed photos", e)
        }
    }
    
    /**
     * Content observer for monitoring new photos taken during colocation
     * When users are together and take photos, they are automatically added to the shared place
     * Also checks EXIF location data for photos taken from external camera apps
     */
    private inner class PhotoContentObserver(
        handler: Handler,
        private val contentResolver: ContentResolver
    ) : ContentObserver(handler) {
        
        // Note: Deduplication sets are in companion object to persist across observer recreations
        
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            
            uri?.let { 
                val uriString = it.toString()
                val currentTime = System.currentTimeMillis()
                
                // Check if this URI was recently processed (debounce)
                synchronized(photoProcessingLock) {
                    val lastProcessedTime = recentlyProcessedUris[uriString]
                    if (lastProcessedTime != null && (currentTime - lastProcessedTime) < DEBOUNCE_MS) {
                        android.util.Log.d("LocationTrackingService", "DEBOUNCE: Photo URI too recent, skipping: ${uriString.takeLast(50)}")
                        return@let
                    }
                    recentlyProcessedUris[uriString] = currentTime
                    
                    // Clean up old entries (older than 10 seconds)
                    recentlyProcessedUris.entries.removeAll { entry -> 
                        (currentTime - entry.value) > 10000L 
                    }
                }
                
                android.util.Log.d("LocationTrackingService", "New photo detected (passed debounce): $it")
                serviceScope.launch {
                    processNewPhoto(it)
                }
            }
        }
        
        private suspend fun processNewPhoto(uri: Uri) {
            try {
                android.util.Log.d("LocationTrackingService", ">>> processNewPhoto called for URI: $uri")
                
                // Extract media ID from URI for stable deduplication
                val mediaId = uri.lastPathSegment ?: ""
                
                // Get photo path and EXIF info
                val projection = arrayOf(
                    MediaStore.Images.Media.DATA, 
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                var photoPath: String? = null
                var displayName: String? = null
                
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        photoPath = cursor.getString(pathIndex)
                        val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                }
                
                if (photoPath == null) {
                    android.util.Log.d("LocationTrackingService", "Could not get photo path from URI")
                    return
                }
                
                // CRITICAL: Skip .pending- files - camera hasn't finished writing them yet
                // They will trigger another ContentObserver event once finalized
                if (photoPath!!.contains(".pending-")) {
                    android.util.Log.d("LocationTrackingService", "SKIP_PENDING: Photo is still pending (camera writing), will process when finalized: $photoPath")
                    return
                }
                
                // Extract stable filename for deduplication (handles renamed files)
                // This extracts just the filename like "IMG_20251218_034811.jpg" from the full path
                val stableFilename = photoPath!!.substringAfterLast("/")
                android.util.Log.d("LocationTrackingService", "Stable filename for dedup: $stableFilename, mediaId: $mediaId")
                
                android.util.Log.d("LocationTrackingService", "Photo path resolved: $photoPath")
                
                // FIRST: Check SharedPreferences for persistent deduplication (survives app restart/crash)
                // This is CRITICAL because memory sets get cleared when app restarts
                val prefs = this@LocationTrackingService.processedPhotosPrefs
                val prefKeyByFilename = "photo_filename:$stableFilename"
                val prefKeyByMediaId = if (mediaId.isNotEmpty()) "photo_mediaId:$mediaId" else ""
                
                if (prefs.contains(prefKeyByFilename) || 
                    (prefKeyByMediaId.isNotEmpty() && prefs.contains(prefKeyByMediaId))) {
                    android.util.Log.d("LocationTrackingService", "PREFS_DUPLICATE: Photo already processed (from SharedPreferences), skipping: $stableFilename")
                    return
                }
                
                // Check if this photo was already processed using MULTIPLE keys in memory:
                // 1. Full path (exact match)
                // 2. Stable filename (catches renamed files)
                // 3. Media ID (unique identifier from MediaStore)
                synchronized(photoProcessingLock) {
                    // Check all possible duplicate keys
                    val isDuplicate = processedPhotoPaths.contains(photoPath) ||
                                     processedPhotoPaths.contains(stableFilename) ||
                                     (mediaId.isNotEmpty() && processedPhotoPaths.contains("mediaId:$mediaId"))
                    
                    if (isDuplicate) {
                        android.util.Log.d("LocationTrackingService", "MEMORY_DUPLICATE: Photo already processed (path/filename/mediaId), skipping: $photoPath")
                        return
                    }
                    
                    // Add ALL keys for comprehensive deduplication
                    processedPhotoPaths.add(photoPath!!)
                    processedPhotoPaths.add(stableFilename)
                    if (mediaId.isNotEmpty()) {
                        processedPhotoPaths.add("mediaId:$mediaId")
                    }
                    android.util.Log.d("LocationTrackingService", "Added to processedPhotoPaths (path+filename+mediaId), current size: ${processedPhotoPaths.size}")
                    
                    // CRITICAL: Also save to SharedPreferences for persistence across app restarts
                    prefs.edit()
                        .putLong(prefKeyByFilename, System.currentTimeMillis())
                        .apply()
                    if (prefKeyByMediaId.isNotEmpty()) {
                        prefs.edit()
                            .putLong(prefKeyByMediaId, System.currentTimeMillis())
                            .apply()
                    }
                    android.util.Log.d("LocationTrackingService", "Saved to SharedPreferences for persistence: $stableFilename")
                    
                    // Keep set size manageable (max 300 entries to accommodate multiple keys per photo)
                    if (processedPhotoPaths.size > 300) {
                        val iterator = processedPhotoPaths.iterator()
                        repeat(150) { if (iterator.hasNext()) { iterator.next(); iterator.remove() } }
                        android.util.Log.d("LocationTrackingService", "Cleaned up processedPhotoPaths, new size: ${processedPhotoPaths.size}")
                    }
                }
                
                android.util.Log.d("LocationTrackingService", "Photo path is NEW, proceeding with processing: $photoPath")
                
                // Read EXIF location from photo
                val photoLocation = getPhotoExifLocation(photoPath!!)
                
                // Case 1: Active colocation session - add photo to session
                if (_isColocationActive.value) {
                    android.util.Log.d("LocationTrackingService", "Processing for active colocation session")
                    processPhotoForActiveColocation(photoPath!!)
                    return
                }
                
                // Case 2: Photo has GPS location - check if matches any shared place
                if (photoLocation != null) {
                    android.util.Log.d("LocationTrackingService", 
                        "Photo has EXIF location: ${photoLocation.latitude}, ${photoLocation.longitude}")
                    processPhotoWithLocation(photoPath!!, photoLocation)
                } else {
                    android.util.Log.d("LocationTrackingService", 
                        "Photo has no EXIF location and no active colocation - skipping")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("LocationTrackingService", "Error processing new photo", e)
            }
        }
        
        /**
         * Get EXIF GPS location from photo file
         */
        private fun getPhotoExifLocation(photoPath: String): LocationCoordinate? {
            return try {
                val exif = android.media.ExifInterface(photoPath)
                val latLong = FloatArray(2)
                
                if (exif.getLatLong(latLong)) {
                    LocationCoordinate(latLong[0].toDouble(), latLong[1].toDouble())
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.d("LocationTrackingService", "Could not read EXIF: ${e.message}")
                null
            }
        }
        
        /**
         * Process photo for active colocation session
         */
        private suspend fun processPhotoForActiveColocation(photoPath: String) {
            val sessionDoc = db.collection("colocation_sessions")
                .document("${coupleId}_active")
                .get()
                .await()
            
            if (!sessionDoc.exists() || sessionDoc.getBoolean("isActive") != true) {
                android.util.Log.d("LocationTrackingService", "No active colocation session found")
                return
            }
            
            android.util.Log.d("LocationTrackingService", "Processing photo for active colocation session")
            
            // Extract stable filename for comparison (handles renamed files)
            val stableFilename = photoPath.substringAfterLast("/")
            
            // Add photo path to colocation session (if not already present)
            @Suppress("UNCHECKED_CAST")
            val currentPhotos = sessionDoc.get("photosCollected") as? List<String> ?: emptyList()
            
            // Check if photo already exists in session by comparing filenames
            val photoAlreadyInSession = currentPhotos.any { existingPath ->
                val existingFilename = existingPath.substringAfterLast("/")
                existingPath == photoPath || existingFilename == stableFilename
            }
            
            if (photoAlreadyInSession) {
                android.util.Log.d("LocationTrackingService", "DEDUP_SESSION: Photo already in session (by filename), skipping: $stableFilename")
            } else {
                val updatedPhotos = currentPhotos.toMutableList().apply { add(photoPath) }
                
                db.collection("colocation_sessions")
                    .document("${coupleId}_active")
                    .update(mapOf(
                        "photosCollected" to updatedPhotos,
                        "lastPhotoAddedAt" to Date()
                    ))
                    .addOnSuccessListener {
                        android.util.Log.d("LocationTrackingService", "Photo added to colocation session")
                    }
            }
            
            // If already converted to shared place, add to shared place photos directly
            val sharedPlaceId = sessionDoc.getString("sharedPlaceId")
            if (!sharedPlaceId.isNullOrEmpty()) {
                addPhotoToSharedPlaceInternal(sharedPlaceId, photoPath)
            }
        }
        
        /**
         * Process photo that has GPS location - check if it matches any shared place
         * This enables adding photos from external camera apps to the appropriate album
         */
        private fun processPhotoWithLocation(photoPath: String, photoLocation: LocationCoordinate) {
            // Find shared place within 500m of photo location
            db.collection("shared_places")
                .whereEqualTo("coupleId", coupleId)
                .get()
                .addOnSuccessListener { snapshot ->
                    var nearestPlaceId: String? = null
                    var nearestDistance = Double.MAX_VALUE
                    
                    for (doc in snapshot.documents) {
                        val placeLat = doc.getDouble("latitude") ?: continue
                        val placeLng = doc.getDouble("longitude") ?: continue
                        
                        val distance = calculateDistance(
                            photoLocation,
                            LocationCoordinate(placeLat, placeLng)
                        )
                        
                        // Photo within 500m of a shared place
                        if (distance <= DUPLICATE_PLACE_DISTANCE_METERS && distance < nearestDistance) {
                            nearestDistance = distance
                            nearestPlaceId = doc.id
                        }
                    }
                    
                    if (nearestPlaceId != null) {
                        android.util.Log.d("LocationTrackingService", 
                            "Photo location matches shared place $nearestPlaceId at ${nearestDistance}m")
                        addPhotoToSharedPlaceInternal(nearestPlaceId, photoPath)
                    } else {
                        android.util.Log.d("LocationTrackingService", 
                            "Photo location doesn't match any shared place")
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("LocationTrackingService", "Error finding shared places", e)
                }
        }
        
        // Note: addingPhotos and photoProcessingLock are in companion object
        
        private fun addPhotoToSharedPlaceInternal(placeId: String, photoPath: String) {
            android.util.Log.d("LocationTrackingService", ">>> addPhotoToSharedPlaceInternal called for: $placeId, path: $photoPath")
            
            // Extract stable filename for reliable deduplication (handles renamed .pending- files)
            val stableFilename = photoPath.substringAfterLast("/")
            val photoKeyByPath = "${placeId}_${photoPath}"
            val photoKeyByFilename = "${placeId}_filename:${stableFilename}"
            
            // FIRST: Check SharedPreferences for persistent deduplication (survives process restarts)
            val prefs = this@LocationTrackingService.processedPhotosPrefs
            if (prefs.contains(photoKeyByPath) || prefs.contains(photoKeyByFilename)) {
                android.util.Log.d("LocationTrackingService", "PREFS_DUPLICATE: Photo already processed (from SharedPreferences), skipping: $stableFilename")
                return
            }
            
            // SECOND: Check memory set for current session deduplication
            synchronized(photoProcessingLock) {
                if (addingPhotos.contains(photoKeyByPath) || addingPhotos.contains(photoKeyByFilename)) {
                    android.util.Log.d("LocationTrackingService", "MEMORY_DUPLICATE: Photo already being added, skipping: $stableFilename")
                    return
                }
                addingPhotos.add(photoKeyByPath)
                addingPhotos.add(photoKeyByFilename)
                android.util.Log.d("LocationTrackingService", "Added to addingPhotos (path+filename), current size: ${addingPhotos.size}")
            }
            
            // Mark as processed in SharedPreferences immediately with BOTH keys
            prefs.edit()
                .putLong(photoKeyByPath, System.currentTimeMillis())
                .putLong(photoKeyByFilename, System.currentTimeMillis())
                .apply()
            android.util.Log.d("LocationTrackingService", "Marked photo as processing in SharedPreferences: $stableFilename")
            
            // Convert photo to base64 in background using outer class function
            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // THIRD: Check Firestore for existing photo with same filename
                    // We need to check ALL photos for this place and compare filenames manually
                    // because old documents might not have stableFilename field
                    android.util.Log.d("LocationTrackingService", "Checking Firestore for existing photo with filename: $stableFilename")
                    
                    val allPhotosForPlace = db.collection("shared_place_photos")
                        .whereEqualTo("placeId", placeId)
                        .get()
                        .await()
                    
                    // Check if any existing photo has the same filename (from originalPath or stableFilename)
                    val duplicateExists = allPhotosForPlace.documents.any { doc ->
                        val existingPath = doc.getString("originalPath") ?: ""
                        val existingStableFilename = doc.getString("stableFilename") ?: ""
                        val existingFilenameFromPath = existingPath.substringAfterLast("/")
                        
                        // Match by: exact path, stableFilename field, or filename extracted from path
                        existingPath == photoPath || 
                        existingStableFilename == stableFilename ||
                        existingFilenameFromPath == stableFilename
                    }
                    
                    if (duplicateExists) {
                        android.util.Log.d("LocationTrackingService", "FIRESTORE_DUPLICATE: Photo already exists in Firestore (by filename check), skipping: $stableFilename")
                        return@launch
                    }
                    
                    android.util.Log.d("LocationTrackingService", "No duplicate found in Firestore (checked ${allPhotosForPlace.size()} photos), proceeding to add photo")
                    
                    android.util.Log.d("LocationTrackingService", "Converting photo to base64: $photoPath")
                    val base64Photo = this@LocationTrackingService.convertPhotoToBase64(photoPath)
                    if (base64Photo == null) {
                        android.util.Log.e("LocationTrackingService", "Failed to convert photo to base64")
                        return@launch
                    }
                    
                    android.util.Log.d("LocationTrackingService", "Base64 conversion successful, saving to Firestore...")
                    
                    val photoData = mapOf(
                        "placeId" to placeId,
                        "photoUrl" to base64Photo,
                        "takenAt" to Date(),
                        "takenByUserId" to userId,
                        "caption" to null,
                        "originalPath" to photoPath, // Store original path for dedup checking
                        "stableFilename" to stableFilename // Store stable filename for reliable dedup
                    )
                    
                    // Use deterministic document ID based on placeId + stableFilename to prevent duplicates
                    // This ensures that even with race conditions, the same photo can only exist once
                    val photoDocId = "${placeId}_${stableFilename}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    android.util.Log.d("LocationTrackingService", "Using deterministic doc ID: $photoDocId")
                    
                    db.collection("shared_place_photos").document(photoDocId).set(photoData)
                        .addOnSuccessListener {
                            android.util.Log.d("LocationTrackingService", "SUCCESS: Photo added/updated in shared place photos, docId: $photoDocId")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("LocationTrackingService", "FAILED: Could not add photo to shared place", e)
                        }
                    
                    // Update photo count
                    android.util.Log.d("LocationTrackingService", "Updating photo count for place: $placeId")
                    val placeRef = db.collection("shared_places").document(placeId)
                    db.runTransaction { transaction ->
                        val placeSnapshot = transaction.get(placeRef)
                        val currentCount = placeSnapshot.getLong("photosCount") ?: 0
                        android.util.Log.d("LocationTrackingService", "Current count: $currentCount, updating to: ${currentCount + 1}")
                        transaction.update(placeRef, "photosCount", currentCount + 1)
                    }.addOnSuccessListener {
                        android.util.Log.d("LocationTrackingService", "Photo count updated successfully")
                    }.addOnFailureListener { e ->
                        android.util.Log.e("LocationTrackingService", "Failed to update photo count", e)
                    }
                } finally {
                    // Remove from adding set after completion (both keys)
                    synchronized(photoProcessingLock) {
                        addingPhotos.remove(photoKeyByPath)
                        addingPhotos.remove(photoKeyByFilename)
                        android.util.Log.d("LocationTrackingService", "Removed from addingPhotos: $stableFilename")
                    }
                }
            }
        }
    }
    
    // Helper functions
    private fun getAddressFromCoordinate(coordinate: LocationCoordinate): String {
        return try {
            val geocoder = android.location.Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                buildString {
                    // Try to get specific location name first
                    address.featureName?.let { feature ->
                        // Skip if it's just a number (street address number)
                        if (!feature.matches(Regex("^\\d+$"))) {
                            append(feature)
                        }
                    }
                    
                    // Add thoroughfare (street name) if not already included
                    address.thoroughfare?.let { street ->
                        if (isEmpty() || !contains(street)) {
                            if (isNotEmpty()) append(", ")
                            append(street)
                        }
                    }
                    
                    // Add sub-locality (district/ward)
                    address.subLocality?.let { subLocality ->
                        if (isEmpty()) {
                            append(subLocality)
                        } else if (!contains(subLocality)) {
                            append(", ")
                            append(subLocality)
                        }
                    }
                    
                    // Add locality (city) if still empty or very short
                    if (isEmpty() || length < 5) {
                        address.locality?.let { city ->
                            if (isEmpty()) {
                                append(city)
                            } else if (!contains(city)) {
                                append(", ")
                                append(city)
                            }
                        }
                    }
                    
                    // Add admin area if still empty
                    if (isEmpty()) {
                        address.adminArea?.let { admin ->
                            append(admin)
                        }
                    }
                }
            }?.takeIf { it.isNotBlank() } 
                ?: generateCoordinateBasedName(coordinate)
        } catch (e: Exception) {
            android.util.Log.e("LocationTrackingService", "Error getting address from coordinate", e)
            generateCoordinateBasedName(coordinate)
        }
    }
    
    /**
     * Generate a name based on coordinates when geocoder fails
     */
    private fun generateCoordinateBasedName(coordinate: LocationCoordinate): String {
        val latStr = String.format(Locale.US, "%.4f", coordinate.latitude)
        val lngStr = String.format(Locale.US, "%.4f", coordinate.longitude)
        return "Location ($latStr, $lngStr)"
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
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
                    lowerAddress.contains("center") || lowerAddress.contains("trung tâm") -> LocationType.SHOPPING
            lowerAddress.contains("park") || lowerAddress.contains("công viên") ||
                    lowerAddress.contains("hồ") -> LocationType.PARK
            lowerAddress.contains("cinema") || lowerAddress.contains("cgv") ||
                    lowerAddress.contains("lotte") -> LocationType.ENTERTAINMENT
            lowerAddress.contains("hospital") || lowerAddress.contains("bệnh viện") -> LocationType.HOSPITAL
            lowerAddress.contains("gym") || lowerAddress.contains("fitness") -> LocationType.GYM
            lowerAddress.contains("school") || lowerAddress.contains("university") ||
                    lowerAddress.contains("đại học") || lowerAddress.contains("trường") -> LocationType.SCHOOL
            else -> LocationType.OTHER
        }
    }
}

/**
 * Helper class for tracking a location history entry
 */
data class LocationHistoryEntry(
    val coordinate: LocationCoordinate,
    val address: String,
    val arrivalTimeMs: Long
)
