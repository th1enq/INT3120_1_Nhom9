package com.example.coupleapp.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.coupleapp.service.SignificantLocationManager
import com.example.coupleapp.worker.BackgroundLocationWorker
import com.google.firebase.auth.FirebaseAuth

/**
 * AlarmManager-based fallback for location tracking.
 * 
 * ================================================================
 * LAYER 3: ALARMMANAGER (Last Resort - Most Reliable)
 * ================================================================
 * 
 * This is the "belt and suspenders" approach:
 * - Layer 1: SignificantLocationManager - PASSIVE piggyback (zero battery)
 * - Layer 2: BackgroundLocationWorker - WorkManager periodic (15-20 min)
 * - Layer 3: LocationAlarmManager - AlarmManager (25 min, RELIABLE)
 * 
 * When alarm fires:
 * 1. Re-register Layer 1 (SignificantLocationManager) if not active
 * 2. Ensure Layer 2 (BackgroundLocationWorker) is scheduled
 * 3. Trigger immediate one-time location update
 * 4. Reschedule next alarm
 * 
 * This ensures location tracking works even in Doze mode.
 */
class LocationAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "LocationAlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Layer 3 alarm triggered")
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, cancelling alarm")
            LocationAlarmManager.cancelLocationAlarm(context)
            return
        }
        
        // Recovery: Ensure Layer 1 (SignificantLocationManager) is still registered
        // This is PASSIVE mode - zero battery when no other app uses GPS
        val sigLocationManager = SignificantLocationManager.getInstance(context)
        if (!sigLocationManager.isTracking()) {
            Log.d(TAG, "Layer 1 not active, re-registering (PASSIVE mode)...")
            sigLocationManager.startTracking()
        } else {
            Log.d(TAG, "Layer 1 already active")
        }
        
        // Ensure Layer 2 (BackgroundLocationWorker) is scheduled
        BackgroundLocationWorker.schedule(context)
        
        // Trigger immediate one-time location update (using LOW_POWER)
        // This ensures we have a location update even if Layer 1 hasn't received any
        BackgroundLocationWorker.triggerOneTime(context)
        
        // Reschedule next alarm for reliability
        LocationAlarmManager.scheduleLocationAlarm(context)
        
        Log.d(TAG, "✅ Layer 3 alarm handled, all layers verified")
    }
}

/**
 * Manager for location alarm scheduling.
 * Uses inexact repeating alarms for battery efficiency.
 * 
 * ================================================================
 * LAYER 3: ALARMMANAGER (Last Resort - Most Reliable)
 * ================================================================
 * 
 * TIMING STRATEGY:
 * - Layer 1 (SignificantLocation): PASSIVE - chỉ piggyback, không tự bật GPS
 * - Layer 2 (WorkManager): 15-20 phút với LOW_POWER
 * - Layer 3 (AlarmManager): 25 phút với setAndAllowWhileIdle - RELIABLE
 * 
 * Vai trò:
 * 1. Backup cuối cùng nếu cả Layer 1 và Layer 2 fail
 * 2. Đảm bảo recovery sau clear RAM trong worst case 25 phút
 * 3. Chạy được cả trong Doze mode (Android 6+)
 * 4. Re-register Layer 1 nếu PendingIntent bị mất
 * 
 * CLEANUP:
 * - cancelLocationAlarm() PHẢI được gọi trong onUserLogout()
 * - Nếu không cancel, alarm sẽ tiếp tục fire và giữ GPS active
 * 
 * Battery impact: ~0.05%/giờ (chỉ wakeup để trigger worker)
 * ================================================================
 */
object LocationAlarmManager {
    private const val TAG = "LocationAlarmManager"
    private const val REQUEST_CODE = 7001
    
    // ================================================================
    // LAYER 3: ALARMMANAGER (Last Resort - Most Reliable)
    // ================================================================
    // Chạy mỗi 25 phút với setAndAllowWhileIdle
    // 
    // Vai trò:
    // 1. Backup cuối cùng nếu cả Layer 1 (PASSIVE) và Layer 2 (WorkManager) fail
    // 2. Đảm bảo recovery sau clear RAM trong worst case 25 phút
    // 3. Chạy được cả trong Doze mode (Android 6+)
    // 4. Re-register Layer 1 (SignificantLocationManager) nếu PendingIntent bị mất
    // 
    // Timing strategy với kiến trúc mới:
    // - Layer 1 (SignificantLocation): PASSIVE - piggyback từ apps khác
    // - Layer 2 (WorkManager): 15-20 phút với LOW_POWER
    // - Layer 3 (AlarmManager): 25 phút reliable backup
    // → Best case: Real-time từ Layer 1 | Worst case: 25 phút từ Layer 3
    // ================================================================
    private const val ALARM_INTERVAL_MS = 25 * 60 * 1000L // 25 minutes
    
    // Initial delay: stagger với các layer khác
    private const val INITIAL_DELAY_MS = 5 * 60 * 1000L // 5 minutes (để Layer 1,2 chạy trước)
    
    /**
     * Schedule periodic location alarm.
     * Uses setInexactRepeating for battery efficiency.
     */
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleLocationAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available")
            return
        }
        
        val pendingIntent = createPendingIntent(context)
        
        // Cancel any existing alarm first
        alarmManager.cancel(pendingIntent)
        
        // Schedule new alarm
        val triggerTime = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS
        
        // Use setAndAllowWhileIdle for better reliability in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6+, use setAndAllowWhileIdle for Doze mode support
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            // For older versions, use regular inexact repeating
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                ALARM_INTERVAL_MS,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Location alarm scheduled for ${ALARM_INTERVAL_MS / 60000} minutes from now")
    }
    
    /**
     * Schedule first alarm with initial delay.
     * Call this from Application.onCreate()
     */
    fun scheduleInitialAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available")
            return
        }
        
        val pendingIntent = createPendingIntent(context)
        
        // Check if alarm is already scheduled
        val existingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, LocationAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (existingIntent != null) {
            Log.d(TAG, "Location alarm already scheduled")
            return
        }
        
        // Schedule first alarm
        val triggerTime = SystemClock.elapsedRealtime() + INITIAL_DELAY_MS
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Initial location alarm scheduled for ${INITIAL_DELAY_MS / 60000} minutes from now")
    }
    
    /**
     * Cancel location alarm.
     * Call this on logout.
     */
    fun cancelLocationAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available")
            return
        }
        
        val pendingIntent = createPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        
        Log.d(TAG, "Location alarm cancelled")
    }
    
    /**
     * Check if alarm is scheduled.
     */
    fun isAlarmScheduled(context: Context): Boolean {
        val intent = Intent(context, LocationAlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags) != null
    }
    
    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationAlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
