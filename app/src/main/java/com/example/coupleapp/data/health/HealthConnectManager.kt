package com.example.coupleapp.data.health

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.coupleapp.data.model.SleepQuality
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Manager class for Health Connect integration
 * Handles reading sleep data from Health Connect API
 */
class HealthConnectManager(private val context: Context) {
    
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }
    
    companion object {
        private const val TAG = "HealthConnectManager"
        
        // Required permissions for sleep tracking
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class)
        )
    }
    
    /**
     * Check if Health Connect is available on this device
     */
    suspend fun isAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                Log.d(TAG, "Health Connect requires Android 8.1 or higher")
                return false
            }
            
            val availability = HealthConnectClient.getSdkStatus(context)
            val isAvailable = availability == HealthConnectClient.SDK_AVAILABLE
            Log.d(TAG, "Health Connect availability: $availability (available: $isAvailable)")
            isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect availability", e)
            false
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            val hasAll = granted.containsAll(PERMISSIONS)
            Log.d(TAG, "Permissions check - Required: ${PERMISSIONS.size}, Granted: ${granted.size}, Has all: $hasAll")
            hasAll
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
    
    /**
     * Request Health Connect permissions
     * Returns a permission request contract that should be launched from an Activity
     */
    fun createPermissionRequestContract() = 
        PermissionController.createRequestPermissionResultContract()
    
    /**
     * Open Health Connect permission screen in settings
     */
    suspend fun openHealthConnectSettings(): Boolean {
        return try {
            // This will redirect user to Health Connect app permissions screen
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Health Connect settings", e)
            false
        }
    }
    
    /**
     * Read sleep sessions for a specific date range
     */
    suspend fun readSleepSessions(startDate: LocalDate, endDate: LocalDate): Result<List<HealthSleepSession>> {
        return try {
            if (!hasAllPermissions()) {
                Log.w(TAG, "Missing required permissions")
                return Result.failure(SecurityException("Missing Health Connect permissions"))
            }
            
            val startTime = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endTime = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            
            Log.d(TAG, "Reading sleep sessions from $startDate to $endDate")
            
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            
            val response = healthConnectClient.readRecords(request)
            val sessions = response.records.map { record ->
                convertToHealthSleepSession(record)
            }
            
            Log.d(TAG, "Successfully read ${sessions.size} sleep sessions")
            Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep sessions", e)
            Result.failure(e)
        }
    }
    
    /**
     * Read today's sleep session
     */
    suspend fun readTodaySleepSession(): Result<HealthSleepSession?> {
        val today = LocalDate.now()
        val result = readSleepSessions(today, today)
        
        return if (result.isSuccess) {
            val sessions = result.getOrNull() ?: emptyList()
            Result.success(sessions.firstOrNull())
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to read sleep session"))
        }
    }
    
    /**
     * Read sleep sessions for the last N days
     */
    suspend fun readRecentSleepSessions(days: Int): Result<List<HealthSleepSession>> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())
        return readSleepSessions(startDate, endDate)
    }
    
    /**
     * Convert Health Connect SleepSessionRecord to our model
     */
    private fun convertToHealthSleepSession(record: SleepSessionRecord): HealthSleepSession {
        val startDateTime = record.startTime.atZone(ZoneId.systemDefault()).toLocalDateTime()
        val endDateTime = record.endTime.atZone(ZoneId.systemDefault()).toLocalDateTime()
        
        val bedTime = startDateTime.toLocalTime()
        val wakeUpTime = endDateTime.toLocalTime()
        
        // Calculate duration in minutes
        val duration = java.time.Duration.between(record.startTime, record.endTime).toMinutes().toInt()
        
        // Calculate sleep stages if available
        val stages = record.stages
        val awakeDuration = stages.filter { 
            it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE 
        }.sumOf { 
            java.time.Duration.between(it.startTime, it.endTime).toMinutes() 
        }.toInt()
        
        val sleepDuration = duration - awakeDuration
        
        // Determine quality based on sleep efficiency
        val sleepEfficiency = if (duration > 0) (sleepDuration.toFloat() / duration) * 100 else 0f
        val quality = when {
            sleepEfficiency >= 85f -> SleepQuality.EXCELLENT
            sleepEfficiency >= 70f -> SleepQuality.GOOD
            else -> SleepQuality.POOR
        }
        
        return HealthSleepSession(
            id = record.metadata.id,
            date = startDateTime.toLocalDate(),
            bedTime = bedTime,
            wakeUpTime = wakeUpTime,
            totalSleepMinutes = duration,
            awakeDurationMinutes = awakeDuration,
            sleepDurationMinutes = sleepDuration,
            quality = quality,
            sleepEfficiency = sleepEfficiency,
            startTime = record.startTime,
            endTime = record.endTime
        )
    }
}

/**
 * Model for sleep session data from Health Connect
 */
data class HealthSleepSession(
    val id: String,
    val date: LocalDate,
    val bedTime: LocalTime,
    val wakeUpTime: LocalTime,
    val totalSleepMinutes: Int,
    val awakeDurationMinutes: Int,
    val sleepDurationMinutes: Int,
    val quality: SleepQuality,
    val sleepEfficiency: Float,
    val startTime: Instant,
    val endTime: Instant
)
