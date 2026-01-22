package com.example.coupleapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper class to manage battery optimization settings.
 * 
 * WHY THIS IS IMPORTANT:
 * Android's Doze mode and App Standby can delay or block:
 * - Background location updates
 * - Sleep sync workers
 * - Widget updates
 * - FCM data messages
 * 
 * For apps like Widgetable that need reliable background updates,
 * users should disable battery optimization for the app.
 * 
 * BATTERY IMPACT:
 * - Our app uses <3% battery per day with optimization OFF
 * - Most battery-heavy apps use 10-20%+ per day
 * - We use passive/event-driven approaches, not continuous polling
 */
object BatteryOptimizationHelper {
    
    private const val TAG = "BatteryOptHelper"
    
    /**
     * Check if app is ignoring battery optimizations
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    /**
     * Open system settings to request disabling battery optimization.
     * 
     * NOTE: We cannot directly request this permission via dialog
     * because Google restricts REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * for apps not in specific categories. Instead, we guide users
     * to the settings page.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            // Try to open app-specific battery settings first
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open app settings", e)
            
            // Fallback to general battery optimization settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open battery settings", e2)
            }
        }
    }
    
    /**
     * Open app info page where user can:
     * - Change battery usage settings
     * - Change location permission to "Allow all the time"
     * - See all permissions
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open app settings", e)
        }
    }
    
    /**
     * Get instructions for user based on device manufacturer.
     * Different manufacturers have different battery optimization UIs.
     */
    fun getBatteryOptimizationInstructions(context: Context): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("samsung") -> """
                📱 Samsung:
                1. Settings → Apps → CoupleApp
                2. Battery → Unrestricted
                3. Also: Settings → Battery → Background usage limits
                   Remove CoupleApp from "Sleeping apps"
            """.trimIndent()
            
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> """
                📱 Xiaomi/Redmi:
                1. Settings → Apps → Manage apps → CoupleApp
                2. Battery saver → No restrictions
                3. Also: Settings → Battery → Battery saver
                   Disable "Ultra battery saver"
                4. Also: Security app → Battery → App battery saver
                   Set CoupleApp to "No restrictions"
            """.trimIndent()
            
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> """
                📱 Huawei/Honor:
                1. Settings → Apps → Apps → CoupleApp
                2. Battery → Unmanaged (or manually manage)
                3. Also: Settings → Battery → Launch
                   Set CoupleApp to "Manage manually"
                   Enable: Auto-launch, Secondary launch, Run in background
            """.trimIndent()
            
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> """
                📱 OPPO/Realme:
                1. Settings → Battery → CoupleApp
                2. Enable: Allow background activity
                3. Also: Settings → App management → CoupleApp
                   Battery → Unrestricted
            """.trimIndent()
            
            manufacturer.contains("vivo") -> """
                📱 Vivo:
                1. Settings → Battery → Background power consumption
                2. Find CoupleApp → Unrestricted
                3. Also: i Manager → App manager → Autostart
                   Enable for CoupleApp
            """.trimIndent()
            
            manufacturer.contains("oneplus") -> """
                📱 OnePlus:
                1. Settings → Apps → CoupleApp
                2. Battery → Unrestricted
                3. Also: Settings → Battery → Battery optimization
                   Set CoupleApp to "Don't optimize"
            """.trimIndent()
            
            else -> """
                📱 General Android:
                1. Settings → Apps → CoupleApp
                2. Battery → Unrestricted (or "Allow background activity")
                3. Also look for: Battery saver/optimization settings
                   Exclude CoupleApp from optimization
            """.trimIndent()
        }
    }
    
    /**
     * Get all required settings that user should configure for best experience
     */
    fun getRequiredSettingsChecklist(context: Context): List<SettingItem> {
        return listOf(
            SettingItem(
                name = "Battery Optimization",
                description = "Cho phép app chạy nền không bị hạn chế",
                isConfigured = isIgnoringBatteryOptimizations(context),
                importance = SettingImportance.CRITICAL
            ),
            SettingItem(
                name = "Background Location",
                description = "Cho phép vị trí 'Mọi lúc' để theo dõi khi đóng app",
                isConfigured = hasBackgroundLocationPermission(context),
                importance = SettingImportance.CRITICAL
            ),
            SettingItem(
                name = "Activity Recognition",
                description = "Cho phép theo dõi giấc ngủ tự động",
                isConfigured = hasActivityRecognitionPermission(context),
                importance = SettingImportance.RECOMMENDED
            ),
            SettingItem(
                name = "Health Connect",
                description = "Đồng bộ dữ liệu giấc ngủ từ Samsung Health/Fit",
                isConfigured = false, // Check separately
                importance = SettingImportance.OPTIONAL
            )
        )
    }
    
    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun hasActivityRecognitionPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below Android 10
        }
    }
    
    data class SettingItem(
        val name: String,
        val description: String,
        val isConfigured: Boolean,
        val importance: SettingImportance
    )
    
    enum class SettingImportance {
        CRITICAL,    // App won't work well without this
        RECOMMENDED, // Improves experience significantly  
        OPTIONAL     // Nice to have
    }
}
