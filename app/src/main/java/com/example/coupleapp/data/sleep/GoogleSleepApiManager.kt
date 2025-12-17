package com.example.coupleapp.data.sleep

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.coupleapp.receiver.SleepReceiver
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import kotlinx.coroutines.tasks.await

/**
 * Manager for Google Sleep API
 * Handles registration and unregistration of sleep tracking
 */
class GoogleSleepApiManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GoogleSleepApiManager"
        private const val SLEEP_SEGMENT_REQUEST_CODE = 1001
        private const val SLEEP_CLASSIFY_REQUEST_CODE = 1002
    }
    
    /**
     * Check if activity recognition permission is granted
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required below Android 10
        }
    }
    
    /**
     * Register for sleep segment updates
     */
    suspend fun registerSleepUpdates(): Result<Unit> {
        return try {
            if (!hasActivityRecognitionPermission()) {
                return Result.failure(SecurityException("Activity recognition permission not granted"))
            }
            
            Log.d(TAG, "Registering sleep segment updates")
            
            val pendingIntent = createSleepSegmentPendingIntent()
            val client = ActivityRecognition.getClient(context)
            
            client.requestSleepSegmentUpdates(
                pendingIntent,
                SleepSegmentRequest.getDefaultSleepSegmentRequest()
            ).await()
            
            Log.d(TAG, "Successfully registered for sleep segment updates")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering sleep segment updates", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unregister sleep segment updates
     */
    suspend fun unregisterSleepUpdates(): Result<Unit> {
        return try {
            Log.d(TAG, "Unregistering sleep segment updates")
            
            val pendingIntent = createSleepSegmentPendingIntent()
            val client = ActivityRecognition.getClient(context)
            
            client.removeSleepSegmentUpdates(pendingIntent).await()
            
            Log.d(TAG, "Successfully unregistered sleep segment updates")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sleep segment updates", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create PendingIntent for sleep segment events
     */
    private fun createSleepSegmentPendingIntent(): PendingIntent {
        val intent = Intent(context, SleepReceiver::class.java).apply {
            action = SleepReceiver.ACTION_SLEEP_SEGMENT
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        return PendingIntent.getBroadcast(
            context,
            SLEEP_SEGMENT_REQUEST_CODE,
            intent,
            flags
        )
    }
    
    /**
     * Create PendingIntent for sleep classification events
     */
    private fun createSleepClassifyPendingIntent(): PendingIntent {
        val intent = Intent(context, SleepReceiver::class.java).apply {
            action = SleepReceiver.ACTION_SLEEP_CLASSIFY
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        return PendingIntent.getBroadcast(
            context,
            SLEEP_CLASSIFY_REQUEST_CODE,
            intent,
            flags
        )
    }
    
    /**
     * Check if sleep tracking is registered
     */
    fun isSleepTrackingRegistered(): Boolean {
        // Check if PendingIntent exists
        val intent = Intent(context, SleepReceiver::class.java).apply {
            action = SleepReceiver.ACTION_SLEEP_SEGMENT
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SLEEP_SEGMENT_REQUEST_CODE,
            intent,
            flags
        )
        
        return pendingIntent != null
    }
}
