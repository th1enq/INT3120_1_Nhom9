package com.example.coupleapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.example.coupleapp.navigation.NavGraph
import com.example.coupleapp.service.LocationTrackingService
import com.example.coupleapp.ui.theme.CoupleAppTheme
import com.example.coupleapp.worker.PhotoSyncManager

/**
 * Main Activity - extends FragmentActivity for biometric authentication support
 */
class MainActivity : FragmentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // App came to foreground
            LocationTrackingService.updateTrackingMode(
                this@MainActivity,
                LocationTrackingService.TRACKING_MODE_FOREGROUND
            )
            
            // Run photo sync when app comes to foreground
            if (PhotoSyncManager.hasRequiredPermissions(this@MainActivity)) {
                PhotoSyncManager.runSyncNow(this@MainActivity)
            }
        }
        
        override fun onStop(owner: LifecycleOwner) {
            // App went to background
            LocationTrackingService.updateTrackingMode(
                this@MainActivity,
                LocationTrackingService.TRACKING_MODE_BACKGROUND
            )
        }
    }
    
    /**
     * Permission launcher for photo sync permissions
     */
    private val photoSyncPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All photo sync permissions granted")
            setupPhotoSync()
        } else {
            Log.w(TAG, "Some photo sync permissions denied: $permissions")
            // Vẫn có thể chạy app, nhưng tính năng auto-sync sẽ không hoạt động
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Register lifecycle observer for adaptive tracking
        lifecycle.addObserver(lifecycleObserver)
        
        // Make status bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Setup photo sync if permissions already granted, or request them
        checkAndRequestPhotoSyncPermissions()
        
        // Get navigation target from intent (for widget clicks)
        val navigateTo = intent.getStringExtra("navigate_to")
        
        setContent {
            CoupleAppTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    startDestination = if (navigateTo == "sleep_tracker") "sleep_tracker" else null
                )
            }
        }
    }
    
    /**
     * Kiểm tra và xin quyền cho photo sync
     */
    private fun checkAndRequestPhotoSyncPermissions() {
        val requiredPermissions = PhotoSyncManager.getRequiredPermissions()
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All photo sync permissions already granted")
            setupPhotoSync()
        } else {
            Log.d(TAG, "Requesting photo sync permissions: $missingPermissions")
            photoSyncPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    /**
     * Thiết lập photo sync sau khi có đủ quyền
     */
    private fun setupPhotoSync() {
        // Start periodic sync (every 15 minutes)
        PhotoSyncManager.startPeriodicSync(this)
        
        // Also run sync immediately to catch up on any new photos
        PhotoSyncManager.runSyncNow(this)
        
        Log.d(TAG, "Photo sync setup completed")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(lifecycleObserver)
    }
}
