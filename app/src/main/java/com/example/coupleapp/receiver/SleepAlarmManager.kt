package com.example.coupleapp.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.coupleapp.data.sleep.GoogleSleepApiManager
import com.example.coupleapp.worker.GoogleSleepSyncWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver cho Sleep Alarm - LAYER 3 của hệ thống Sleep Tracking
 * 
 * ================================================================
 * MULTI-LAYER SLEEP TRACKING ARCHITECTURE (Like Location)
 * ================================================================
 * 
 * LAYER 1: SleepReceiver (PendingIntent from Google Sleep API)
 * ├── Triggered by: Google Play Services Sleep API
 * ├── Mode: PASSIVE - receives sleep events when they occur
 * ├── Issue: PendingIntent bị mất sau Clear RAM/Reboot
 * └── Battery: ~0% (fully passive)
 * 
 * LAYER 2: GoogleSleepSyncWorker (WorkManager)
 * ├── Schedule: Morning syncs 5:30-11:30 AM + Periodic 2h
 * ├── Vai trò: Re-register Layer 1, sync missed data
 * ├── Issue: Có thể bị delay bởi Doze mode
 * └── Battery: ~0.1%/day
 * 
 * LAYER 3: SleepAlarmManager (This class - MOST RELIABLE)
 * ├── Schedule: Morning alarms 6:00, 8:00, 10:00 AM
 * ├── Vai trò: Backup cuối cùng, chạy cả trong Doze
 * ├── Uses: setAndAllowWhileIdle() for reliability
 * └── Battery: ~0.02%/day (chỉ 3 alarms)
 * 
 * ================================================================
 * TẠI SAO CẦN 3 LAYERS:
 * ================================================================
 * 1. Clear RAM → Layer 1 (PendingIntent) bị mất
 * 2. Doze mode → Layer 2 (WorkManager) bị delay
 * 3. Battery saver → Cả Layer 1 và 2 bị hạn chế
 * 
 * Layer 3 (AlarmManager setAndAllowWhileIdle) là reliable nhất,
 * nhưng không dùng làm primary vì hạn chế số lần chạy.
 * ================================================================
 */
class SleepAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SleepAlarmReceiver"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Sleep alarm triggered at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, skipping")
            return
        }
        
        scope.launch {
            try {
                // Step 1: Ensure Google Sleep API is registered (recovery from clear RAM)
                ensureSleepApiRegistered(context)
                
                // Step 2: Trigger WorkManager sync (Layer 2)
                GoogleSleepSyncWorker.triggerImmediateSync(context)
                
                // Step 3: Re-schedule next alarm
                SleepAlarmManager.scheduleNextMorningAlarm(context)
                
                Log.d(TAG, "✅ Sleep alarm handled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling sleep alarm", e)
            }
        }
    }
    
    private fun ensureSleepApiRegistered(context: Context) {
        try {
            val googleSleepManager = GoogleSleepApiManager(context)
            
            // Check if Google Sleep API is enabled in settings
            val prefs = context.getSharedPreferences("sleep_settings", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("google_sleep_api_enabled", false)
            
            if (!isEnabled) {
                Log.d(TAG, "Google Sleep API not enabled in settings")
                return
            }
            
            // Check permission
            if (!googleSleepManager.hasActivityRecognitionPermission()) {
                Log.w(TAG, "Activity Recognition permission not granted")
                return
            }
            
            // Re-register if not registered
            if (!googleSleepManager.isSleepTrackingRegistered()) {
                Log.d(TAG, "Re-registering Google Sleep API (Layer 1 recovery)")
                googleSleepManager.registerSleepUpdates()
            } else {
                Log.d(TAG, "Google Sleep API already registered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Sleep API registered", e)
        }
    }
}

/**
 * Manager for Sleep alarm scheduling.
 * Uses setAndAllowWhileIdle for battery efficiency while still being reliable.
 * 
 * ================================================================
 * TIMING STRATEGY:
 * ================================================================
 * Morning alarms: 6:00 AM, 8:00 AM, 10:00 AM
 * 
 * Tại sao 3 mốc thời gian:
 * - 6:00 AM: Người dậy sớm
 * - 8:00 AM: Người dậy bình thường
 * - 10:00 AM: Người ngủ muộn
 * 
 * Không cần nhiều hơn vì Layer 2 (WorkManager) đã cover đủ.
 * Layer 3 chỉ là backup cuối cùng.
 * ================================================================
 */
object SleepAlarmManager {
    private const val TAG = "SleepAlarmManager"
    private const val REQUEST_CODE_MORNING_6 = 8001
    private const val REQUEST_CODE_MORNING_8 = 8002
    private const val REQUEST_CODE_MORNING_10 = 8003
    
    // Morning alarm times
    private val MORNING_ALARMS = listOf(
        Pair(6, REQUEST_CODE_MORNING_6),   // 6:00 AM
        Pair(8, REQUEST_CODE_MORNING_8),   // 8:00 AM
        Pair(10, REQUEST_CODE_MORNING_10)  // 10:00 AM
    )
    
    /**
     * Schedule all morning alarms for sleep sync
     */
    fun scheduleAllMorningAlarms(context: Context) {
        MORNING_ALARMS.forEach { (hour, requestCode) ->
            scheduleAlarmAtHour(context, hour, requestCode)
        }
        Log.d(TAG, "✅ Scheduled all morning sleep alarms (6 AM, 8 AM, 10 AM)")
    }
    
    /**
     * Schedule next available morning alarm
     */
    fun scheduleNextMorningAlarm(context: Context) {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        // Find next alarm that hasn't passed yet
        val nextAlarm = MORNING_ALARMS.firstOrNull { (hour, _) -> hour > currentHour }
        
        if (nextAlarm != null) {
            // Schedule for today
            scheduleAlarmAtHour(context, nextAlarm.first, nextAlarm.second)
            Log.d(TAG, "Scheduled next morning alarm at ${nextAlarm.first}:00 AM (today)")
        } else {
            // All alarms passed, schedule first one for tomorrow
            val (hour, requestCode) = MORNING_ALARMS.first()
            scheduleAlarmAtHour(context, hour, requestCode, tomorrow = true)
            Log.d(TAG, "Scheduled next morning alarm at $hour:00 AM (tomorrow)")
        }
    }
    
    private fun scheduleAlarmAtHour(context: Context, hour: Int, requestCode: Int, tomorrow: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, SleepAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Calculate trigger time
        val calendar = java.util.Calendar.getInstance().apply {
            if (tomorrow || get(java.util.Calendar.HOUR_OF_DAY) >= hour) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        
        val triggerTime = calendar.timeInMillis
        
        try {
            // Use setAndAllowWhileIdle for Doze mode compatibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            
            val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(triggerTime))
            Log.d(TAG, "Scheduled sleep alarm at $timeStr (requestCode=$requestCode)")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm - need SCHEDULE_EXACT_ALARM permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling sleep alarm", e)
        }
    }
    
    /**
     * Cancel all sleep alarms.
     * MUST be called on user logout!
     */
    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        MORNING_ALARMS.forEach { (_, requestCode) ->
            val intent = Intent(context, SleepAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        
        Log.d(TAG, "Cancelled all sleep alarms")
    }
    
    /**
     * Check if alarms are scheduled (for debugging)
     */
    fun isAlarmScheduled(context: Context, requestCode: Int): Boolean {
        val intent = Intent(context, SleepAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent != null
    }
}
