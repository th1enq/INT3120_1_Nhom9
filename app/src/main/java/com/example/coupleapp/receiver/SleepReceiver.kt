package com.example.coupleapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.example.coupleapp.data.repository.SleepFirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

/**
 * BroadcastReceiver for Google Sleep API
 * Receives sleep segment and classification events from Google Play Services
 */
class SleepReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SleepReceiver"
        // These must match the intent filter in AndroidManifest.xml
        const val ACTION_SLEEP_SEGMENT = "com.google.android.gms.location.activity.SLEEP_SEGMENT"
        const val ACTION_SLEEP_CLASSIFY = "com.google.android.gms.location.activity.SLEEP_CLASSIFY"
    }
    
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        
        // Process Sleep Segment Events (sleep duration)
        if (SleepSegmentEvent.hasEvents(intent)) {
            val events = SleepSegmentEvent.extractEvents(intent)
            Log.d(TAG, "Received ${events.size} sleep segment events")
            
            events.forEach { event ->
                val startTimeMillis = event.startTimeMillis
                val endTimeMillis = event.endTimeMillis
                val durationMillis = event.segmentDurationMillis
                val status = event.status
                
                Log.d(TAG, "Sleep Segment:")
                Log.d(TAG, "  Start: $startTimeMillis")
                Log.d(TAG, "  End: $endTimeMillis")
                Log.d(TAG, "  Duration: ${durationMillis / 1000 / 60} minutes")
                Log.d(TAG, "  Status: $status")
                
                // Save to Firebase
                scope.launch {
                    try {
                        saveSleepSegmentToFirebase(
                            context = context,
                            startTimeMillis = startTimeMillis,
                            endTimeMillis = endTimeMillis,
                            durationMillis = durationMillis
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving sleep segment to Firebase", e)
                    }
                }
            }
        }
        
        // Process Sleep Classify Events (sleep quality indicators)
        if (SleepClassifyEvent.hasEvents(intent)) {
            val events = SleepClassifyEvent.extractEvents(intent)
            Log.d(TAG, "Received ${events.size} sleep classify events")
            
            events.forEach { event ->
                val confidence = event.confidence
                val motion = event.motion
                val light = event.light
                val timestampMillis = event.timestampMillis
                
                Log.d(TAG, "Sleep Classify:")
                Log.d(TAG, "  Confidence: $confidence%")
                Log.d(TAG, "  Motion: $motion")
                Log.d(TAG, "  Light: $light")
                Log.d(TAG, "  Timestamp: $timestampMillis")
                
                // Store classification data for quality calculation
                scope.launch {
                    try {
                        saveSleepClassificationToFirebase(
                            context = context,
                            confidence = confidence,
                            motion = motion,
                            light = light,
                            timestampMillis = timestampMillis
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving sleep classification to Firebase", e)
                    }
                }
            }
        }
    }
    
    /**
     * Save sleep segment data to Firebase
     */
    private suspend fun saveSleepSegmentToFirebase(
        context: Context,
        startTimeMillis: Long,
        endTimeMillis: Long,
        durationMillis: Long
    ) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return
        
        val repository = SleepFirebaseRepository(context)
        
        // Convert to sleep record
        repository.saveSleepSegmentFromGoogleApi(
            userId = userId,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            durationMillis = durationMillis
        )
        
        Log.d(TAG, "Sleep segment saved to Firebase successfully")
    }
    
    /**
     * Save sleep classification data to Firebase for quality calculation
     */
    private suspend fun saveSleepClassificationToFirebase(
        context: Context,
        confidence: Int,
        motion: Int,
        light: Int,
        timestampMillis: Long
    ) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return
        
        val repository = SleepFirebaseRepository(context)
        
        // Store classification data
        repository.saveSleepClassification(
            userId = userId,
            confidence = confidence,
            motion = motion,
            light = light,
            timestampMillis = timestampMillis
        )
        
        Log.d(TAG, "Sleep classification saved to Firebase successfully")
    }
}
