package com.example.coupleapp.utils

import android.util.Log
import java.time.Instant

/**
 * Helper functions for testing Sleep Tracking
 */
object SleepTestHelper {
    private const val TAG = "SleepTestHelper"
    
    /**
     * Generate ADB command for testing Sleep Segment
     * @param hoursAgo How many hours ago the sleep started
     * @param durationHours Duration of sleep in hours
     */
    fun generateSleepSegmentAdbCommand(hoursAgo: Int = 8, durationHours: Int = 8): String {
        val now = System.currentTimeMillis()
        val startTime = now - (hoursAgo * 60 * 60 * 1000L)
        val endTime = startTime + (durationHours * 60 * 60 * 1000L)
        
        val command = """
            adb shell am broadcast \
                -a com.google.android.gms.location.activity.SLEEP_SEGMENT \
                --es "com.google.android.location.internal.EXTRA_SLEEP_SEGMENT_RESULT" \
                "[{\"startTimeMillis\": $startTime, \"endTimeMillis\": $endTime, \"status\": 0}]"
        """.trimIndent()
        
        Log.d(TAG, "Sleep Segment Test Command:")
        Log.d(TAG, command)
        Log.d(TAG, "Start: ${Instant.ofEpochMilli(startTime)}")
        Log.d(TAG, "End: ${Instant.ofEpochMilli(endTime)}")
        Log.d(TAG, "Duration: $durationHours hours")
        
        return command
    }
    
    /**
     * Generate ADB command for testing Sleep Classification
     * @param confidence Sleep confidence (0-100)
     * @param motion Motion level (0-3)
     * @param light Light level (0-3)
     */
    fun generateSleepClassifyAdbCommand(
        confidence: Int = 85,
        motion: Int = 1,
        light: Int = 1
    ): String {
        val timestamp = System.currentTimeMillis()
        
        val command = """
            adb shell am broadcast \
                -a com.google.android.gms.location.activity.SLEEP_CLASSIFY \
                --es "com.google.android.location.internal.EXTRA_SLEEP_CLASSIFY_RESULT" \
                "[{\"confidence\": $confidence, \"motion\": $motion, \"light\": $light, \"timestampMillis\": $timestamp}]"
        """.trimIndent()
        
        Log.d(TAG, "Sleep Classify Test Command:")
        Log.d(TAG, command)
        Log.d(TAG, "Confidence: $confidence%")
        Log.d(TAG, "Motion: $motion")
        Log.d(TAG, "Light: $light")
        Log.d(TAG, "Timestamp: ${Instant.ofEpochMilli(timestamp)}")
        
        return command
    }
    
    /**
     * Print test commands to logcat for easy copy-paste
     */
    fun printTestCommands() {
        Log.d(TAG, "=".repeat(80))
        Log.d(TAG, "SLEEP TRACKING TEST COMMANDS")
        Log.d(TAG, "=".repeat(80))
        
        Log.d(TAG, "\n1. TEST SLEEP SEGMENT (Last night 8 hours):")
        generateSleepSegmentAdbCommand(hoursAgo = 8, durationHours = 8)
        
        Log.d(TAG, "\n2. TEST SLEEP SEGMENT (Short nap 2 hours):")
        generateSleepSegmentAdbCommand(hoursAgo = 2, durationHours = 2)
        
        Log.d(TAG, "\n3. TEST SLEEP CLASSIFY (Good quality):")
        generateSleepClassifyAdbCommand(confidence = 90, motion = 1, light = 0)
        
        Log.d(TAG, "\n4. TEST SLEEP CLASSIFY (Poor quality):")
        generateSleepClassifyAdbCommand(confidence = 50, motion = 3, light = 2)
        
        Log.d(TAG, "\n" + "=".repeat(80))
        Log.d(TAG, "Copy commands from logcat and run in terminal")
        Log.d(TAG, "=".repeat(80))
    }
    
    /**
     * Test timestamps for current time
     */
    fun getCurrentTimestamps() {
        val now = System.currentTimeMillis()
        val eightHoursAgo = now - (8 * 60 * 60 * 1000L)
        
        Log.d(TAG, "Current Timestamps:")
        Log.d(TAG, "Now: $now (${Instant.ofEpochMilli(now)})")
        Log.d(TAG, "8 hours ago: $eightHoursAgo (${Instant.ofEpochMilli(eightHoursAgo)})")
    }
}

// Extension để dễ dàng call từ ViewModel hoặc Activity
fun Any.printSleepTestCommands() {
    SleepTestHelper.printTestCommands()
}
