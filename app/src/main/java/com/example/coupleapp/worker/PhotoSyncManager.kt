package com.example.coupleapp.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Helper class để quản lý Photo Sync với WorkManager
 * 
 * Cách sử dụng:
 * 1. Xin đủ quyền trước (READ_MEDIA_IMAGES, ACCESS_MEDIA_LOCATION)
 * 2. Gọi PhotoSyncManager.startPeriodicSync(context)
 */
object PhotoSyncManager {
    
    private const val TAG = "PhotoSyncManager"
    private const val WORK_NAME = "PhotoSyncWork"
    private const val SYNC_INTERVAL_MINUTES = 15L // WorkManager minimum is 15 minutes
    
    /**
     * Kiểm tra xem có đủ quyền để sync ảnh không
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        // Kiểm tra quyền đọc ảnh
        val hasReadPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        // Kiểm tra quyền đọc vị trí từ ảnh (bắt buộc để đọc GPS từ EXIF)
        val hasMediaLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permissions check - Read: $hasReadPermission, MediaLocation: $hasMediaLocationPermission")
        
        return hasReadPermission && hasMediaLocationPermission
    }
    
    /**
     * Lấy danh sách quyền cần xin
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        }
    }
    
    /**
     * Bắt đầu sync định kỳ (mỗi 15 phút)
     * Gọi sau khi đã có đủ quyền
     */
    fun startPeriodicSync(context: Context) {
        if (!hasRequiredPermissions(context)) {
            Log.w(TAG, "Missing required permissions, cannot start photo sync")
            return
        }
        
        Log.d(TAG, "Starting periodic photo sync (every ${SYNC_INTERVAL_MINUTES} minutes)")
        
        // Constraints: Chỉ chạy khi có mạng
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Tạo periodic work request
        val syncRequest = PeriodicWorkRequestBuilder<PhotoSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("photo_sync")
            .build()
        
        // Enqueue với policy KEEP (không tạo duplicate nếu đã có)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        
        Log.d(TAG, "Photo sync work scheduled successfully")
    }
    
    /**
     * Chạy sync một lần ngay lập tức
     * Hữu ích khi user vừa mở app hoặc sau khi cấp quyền
     */
    fun runSyncNow(context: Context) {
        if (!hasRequiredPermissions(context)) {
            Log.w(TAG, "Missing required permissions, cannot run photo sync")
            return
        }
        
        Log.d(TAG, "Running photo sync now (one-time)")
        
        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .addTag("photo_sync_immediate")
            .build()
        
        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }
    
    /**
     * Dừng sync định kỳ
     */
    fun stopPeriodicSync(context: Context) {
        Log.d(TAG, "Stopping periodic photo sync")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
    
    /**
     * Kiểm tra xem sync có đang được schedule không
     */
    fun isSyncScheduled(context: Context): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WORK_NAME)
            .get()
        
        return workInfos.any { !it.state.isFinished }
    }
}
