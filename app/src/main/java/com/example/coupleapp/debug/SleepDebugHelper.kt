package com.example.coupleapp.debug

import android.content.Context
import android.util.Log
import com.example.coupleapp.data.sleep.GoogleSleepApiManager
import com.example.coupleapp.utils.SleepTestHelper
import com.example.coupleapp.utils.hasActivityRecognitionPermission

/**
 * Debug utilities for Sleep Tracking
 * Always available for testing
 */
object SleepDebugHelper {
    private const val TAG = "SleepDebugHelper"
    
    /**
     * Initialize debug tools on app start (call in Application.onCreate)
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Sleep Debug Helper initialized")
        
        // Print test commands to logcat on startup
        printAllTestCommands()
        printGoogleSleepApiExplanation()
    }
    
    /**
     * Print all test commands for easy copy-paste
     */
    private fun printAllTestCommands() {
        Log.d(TAG, "\n" + "=".repeat(80))
        Log.d(TAG, "SLEEP TRACKING DEBUG COMMANDS")
        Log.d(TAG, "Copy these commands from Logcat and paste into Terminal")
        Log.d(TAG, "=".repeat(80) + "\n")
        
        SleepTestHelper.printTestCommands()
        
        Log.d(TAG, "\n" + "=".repeat(80))
        Log.d(TAG, "QUICK TEST STEPS:")
        Log.d(TAG, "1. Enable Google Sleep API in app Settings")
        Log.d(TAG, "2. Grant Activity Recognition permission")
        Log.d(TAG, "3. Copy ADB command from above")
        Log.d(TAG, "4. Paste into Terminal and press Enter")
        Log.d(TAG, "5. Check Logcat for SleepReceiver logs")
        Log.d(TAG, "6. Refresh Sleep Tracker Screen")
        Log.d(TAG, "=".repeat(80) + "\n")
    }
    
    /**
     * Explain how Google Sleep API works
     */
    private fun printGoogleSleepApiExplanation() {
        Log.d(TAG, "\n" + "=".repeat(80))
        Log.d(TAG, "HOW GOOGLE SLEEP API WORKS")
        Log.d(TAG, "=".repeat(80))
        Log.d(TAG, "")
        Log.d(TAG, "Google Sleep API is PASSIVE - it doesn't track sleep in real-time!")
        Log.d(TAG, "")
        Log.d(TAG, "📊 TWO TYPES OF EVENTS:")
        Log.d(TAG, "")
        Log.d(TAG, "1. SLEEP CLASSIFY EVENTS (real-time-ish):")
        Log.d(TAG, "   - Sent every few minutes while phone is still")
        Log.d(TAG, "   - Contains: confidence (0-100), motion level, light level")
        Log.d(TAG, "   - Higher confidence = more likely user is sleeping")
        Log.d(TAG, "   - NOT a complete sleep record, just classification data")
        Log.d(TAG, "")
        Log.d(TAG, "2. SLEEP SEGMENT EVENTS (delayed):")
        Log.d(TAG, "   - Sent AFTER user wakes up (hours later)")
        Log.d(TAG, "   - Contains: startTime, endTime, duration")
        Log.d(TAG, "   - Google waits until confident about full sleep period")
        Log.d(TAG, "   - Usually arrives 1-3 hours after waking up")
        Log.d(TAG, "")
        Log.d(TAG, "⚠️ IMPORTANT:")
        Log.d(TAG, "- Sleep tracking WON'T show immediate results")
        Log.d(TAG, "- Phone must be still (on bed/table, not moving)")
        Log.d(TAG, "- Phone should be charging or have sufficient battery")
        Log.d(TAG, "- Results typically arrive next morning")
        Log.d(TAG, "")
        Log.d(TAG, "🧪 TO TEST GOOGLE SLEEP API:")
        Log.d(TAG, "Use ADB to send fake sleep events:")
        Log.d(TAG, "adb shell am broadcast -a com.example.coupleapp.SLEEP_SEGMENT")
        Log.d(TAG, "")
        Log.d(TAG, "📱 YOUR APP HAS TWO TRACKING MODES:")
        Log.d(TAG, "")
        Log.d(TAG, "1. MANUAL TRACKING (Active):")
        Log.d(TAG, "   - User taps 'Sleep Now' → creates active_sleep_session")
        Log.d(TAG, "   - User taps 'I'm Awake' → calculates duration & creates record")
        Log.d(TAG, "   - IMMEDIATE results, user-controlled")
        Log.d(TAG, "")
        Log.d(TAG, "2. GOOGLE SLEEP API (Passive):")
        Log.d(TAG, "   - Runs in background via BroadcastReceiver")
        Log.d(TAG, "   - Google detects sleep automatically")
        Log.d(TAG, "   - Results delayed by hours")
        Log.d(TAG, "   - More accurate over time")
        Log.d(TAG, "")
        Log.d(TAG, "=".repeat(80) + "\n")
    }
    
    /**
     * Check current Google Sleep API status
     */
    fun checkGoogleSleepApiStatus(context: Context) {
        val manager = GoogleSleepApiManager(context)
        
        Log.d(TAG, "\n" + "=".repeat(50))
        Log.d(TAG, "GOOGLE SLEEP API STATUS CHECK")
        Log.d(TAG, "=".repeat(50))
        Log.d(TAG, "Permission granted: ${manager.hasActivityRecognitionPermission()}")
        Log.d(TAG, "Tracking registered: ${manager.isSleepTrackingRegistered()}")
        
        // Check SharedPreferences
        val prefs = context.getSharedPreferences("sleep_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("google_sleep_api_enabled", false)
        Log.d(TAG, "SharedPrefs enabled: $enabled")
        Log.d(TAG, "=".repeat(50) + "\n")
    }
    
    /**
     * Verify setup is correct
     */
    fun verifySetup(context: Context): SetupStatus {
        val status = SetupStatus()
        
        // Check permission
        status.hasActivityRecognitionPermission = 
            context.hasActivityRecognitionPermission()
        
        // Check Google Play Services
        try {
            val result = com.google.android.gms.common.GoogleApiAvailability
                .getInstance()
                .isGooglePlayServicesAvailable(context)
            status.hasGooglePlayServices = (result == com.google.android.gms.common.ConnectionResult.SUCCESS)
        } catch (e: Exception) {
            status.hasGooglePlayServices = false
        }
        
        // Log status
        Log.d(TAG, "Setup Status:")
        Log.d(TAG, "  Activity Recognition Permission: ${status.hasActivityRecognitionPermission}")
        Log.d(TAG, "  Google Play Services: ${status.hasGooglePlayServices}")
        
        return status
    }
    
    data class SetupStatus(
        var hasActivityRecognitionPermission: Boolean = false,
        var hasGooglePlayServices: Boolean = false
    ) {
        val isReady: Boolean
            get() = hasActivityRecognitionPermission && hasGooglePlayServices
    }
}

// Add this to CoupleApplication.kt
/**
 * In CoupleApplication.onCreate():
 * 
 * if (BuildConfig.DEBUG) {
 *     SleepDebugHelper.initialize(this)
 * }
 */
