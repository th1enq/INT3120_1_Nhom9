package com.example.coupleapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.example.coupleapp.data.model.SleepQuality
import com.example.coupleapp.widget.data.SleepWidgetCachedData
import com.example.coupleapp.widget.data.WidgetDataRepository
import com.example.coupleapp.widget.worker.WidgetUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sleep Widget Provider - Battery Optimized
 * Displays sleep comparison between couple partners in a 4x2 widget
 * 
 * Battery Optimization Features:
 * - Aggressive caching via WidgetDataRepository
 * - WorkManager for periodic updates (respects Doze mode)
 * - 30-minute minimum update interval
 * - Smart data invalidation
 */
class SleepWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "SleepWidgetProvider"
        private const val ACTION_WIDGET_CLICK = "com.example.coupleapp.WIDGET_CLICK"
        private const val ACTION_UPDATE_WIDGET = "com.example.coupleapp.UPDATE_WIDGET"
        
        /**
         * Update all widgets using cached data (battery-efficient)
         */
        fun updateWidgets(context: Context) {
            Log.d(TAG, "Requesting widget update")
            val intent = Intent(context, SleepWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
        
        /**
         * Force update with fresh data from Firebase
         */
        fun forceUpdateWidgets(context: Context) {
            Log.d(TAG, "Force updating widgets")
            WidgetUpdateWorker.requestImmediateUpdate(context, WidgetUpdateWorker.WIDGET_TYPE_SLEEP)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_CLICK -> {
                // Open app and navigate to Sleep Tracker
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "sleep_tracker")
                }
                context.startActivity(mainIntent)
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, SleepWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, widgetIds)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Use GlobalScope for widget updates since widgets don't have a lifecycle
        // but use Dispatchers.Main.immediate for UI operations
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main.immediate) {
            val views = RemoteViews(context.packageName, R.layout.widget_sleep_tracker)
            
            try {
                // Check authentication first
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                
                if (currentUser == null) {
                    Log.d(TAG, "User not authenticated")
                    showEmptyState(views, "Đăng nhập để xem giấc ngủ")
                } else {
                    // Use cached data for battery efficiency
                    val sleepData = WidgetDataRepository.getSleepWidgetData(context)
                    
                    if (sleepData != null) {
                        Log.d(TAG, "Sleep widget data loaded successfully")
                        // Check if we should show bedtime reminder
                        val shouldShowReminder = checkBedtimeReminder(
                            sleepData.bedtimeHour,
                            sleepData.bedtimeMinute
                        )
                        
                        if (shouldShowReminder) {
                            showBedtimeReminder(views, sleepData.bedtimeHour, sleepData.bedtimeMinute)
                        } else {
                            showSleepComparisonCached(context, views, sleepData)
                        }
                    } else {
                        Log.d(TAG, "No sleep data available")
                        showEmptyState(views, "Chưa có dữ liệu giấc ngủ")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating sleep widget: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("auth", ignoreCase = true) == true -> "Lỗi xác thực"
                    e.message?.contains("network", ignoreCase = true) == true -> "Không có mạng"
                    else -> "Không thể tải dữ liệu"
                }
                showEmptyState(views, errorMessage)
            }
            
            // Set click intent to open app
            val clickIntent = Intent(context, SleepWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, clickPendingIntent)
            views.setOnClickPendingIntent(R.id.bedtime_reminder_container, clickPendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun checkBedtimeReminder(bedtimeHour: Int, bedtimeMinute: Int): Boolean {
        val now = LocalTime.now()
        val bedTime = LocalTime.of(bedtimeHour, bedtimeMinute)
        val reminderStart = bedTime.minusMinutes(15)
        val reminderEnd = bedTime.plusMinutes(30)
        
        return now.isAfter(reminderStart) && now.isBefore(reminderEnd)
    }
    
    private fun showEmptyState(views: RemoteViews, message: String) {
        views.setViewVisibility(R.id.widget_container, View.GONE)
        views.setViewVisibility(R.id.bedtime_reminder_container, View.GONE)
        views.setViewVisibility(R.id.sleep_empty_container, View.VISIBLE)
        views.setTextViewText(R.id.sleep_empty_message, message)
    }
    
    private fun showSleepComparisonCached(
        context: Context,
        views: RemoteViews,
        data: SleepWidgetCachedData
    ) {
        views.setViewVisibility(R.id.widget_container, View.VISIBLE)
        views.setViewVisibility(R.id.bedtime_reminder_container, View.GONE)
        views.setViewVisibility(R.id.sleep_empty_container, View.GONE)
        
        // Update goal and bedtime information - compact format for 2x2 widget
        views.setTextViewText(R.id.sleep_goal_text, "🎯 8h")
        views.setTextViewText(R.id.bedtime_goal_text, "🌙 ${String.format("%02d:%02d", data.bedtimeHour, data.bedtimeMinute)}")
        // sleep_date_header is now hidden in compact mode
        
        // Update my sleep info (left side) - compact format
        updateUserSleepInfoCached(
            context,
            views,
            data.mySleepDuration,
            data.mySleepQuality,
            data.myAchievement,
            data.myAvatarUrl,
            isLeft = true
        )
        
        // Update partner's sleep info (right side)
        if (data.partnerIsAsleep) {
            views.setTextViewText(R.id.right_emoji_icon, "😴")
            views.setTextViewText(R.id.right_status_text, "Đang ngủ...")
            views.setTextColor(R.id.right_status_text, Color.parseColor("#9C27B0"))
            views.setViewVisibility(R.id.right_date_text, View.GONE)
            views.setViewVisibility(R.id.right_duration_text, View.GONE)
        } else {
            updateUserSleepInfoCached(
                context,
                views,
                data.partnerSleepDuration,
                data.partnerSleepQuality,
                data.partnerAchievement,
                data.partnerAvatarUrl,
                isLeft = false
            )
        }
    }
    
    private fun updateUserSleepInfoCached(
        context: Context,
        views: RemoteViews,
        duration: Int,
        quality: String,
        achievement: Float,
        avatarUrl: String?,
        isLeft: Boolean
    ) {
        val emojiViewId = if (isLeft) R.id.left_emoji_icon else R.id.right_emoji_icon
        val emojiText = if (isLeft) "😊" else "🥦"
        
        // Show emoji (avatar removed - too complex for widget)
        views.setTextViewText(emojiViewId, emojiText)
        
        // Check if we have data for today (duration = 0 means no data)
        if (duration == 0) {
            // No sleep data for today
            views.setTextViewText(
                if (isLeft) R.id.left_duration_text else R.id.right_duration_text,
                "Chưa ngủ"
            )
            views.setTextViewText(
                if (isLeft) R.id.left_status_text else R.id.right_status_text,
                ""
            )
            views.setImageViewResource(
                if (isLeft) R.id.left_status_circle else R.id.right_status_circle,
                R.drawable.bad
            )
            return
        }
        
        // Set sleep quality icon
        val statusImageRes = when (quality) {
            "EXCELLENT" -> R.drawable.excellent
            "GOOD" -> R.drawable.good
            else -> R.drawable.bad
        }
        views.setImageViewResource(
            if (isLeft) R.id.left_status_circle else R.id.right_status_circle,
            statusImageRes
        )
        
        val sleepQuality = when (quality) {
            "EXCELLENT" -> SleepQuality.EXCELLENT
            "GOOD" -> SleepQuality.GOOD
            else -> SleepQuality.POOR
        }
        val progressBitmap = createProgressCircleBitmap(achievement, sleepQuality)
        views.setImageViewBitmap(
            if (isLeft) R.id.left_progress_bg else R.id.right_progress_bg,
            progressBitmap
        )
        
        // Compact status text - just one word
        val statusText = when (quality) {
            "EXCELLENT" -> "Tốt"
            "GOOD" -> "Ổn"
            else -> "Kém"
        }
        val statusColor = when (quality) {
            "EXCELLENT" -> Color.parseColor("#4CAF50")
            "GOOD" -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        views.setTextViewText(
            if (isLeft) R.id.left_status_text else R.id.right_status_text,
            statusText
        )
        views.setTextColor(
            if (isLeft) R.id.left_status_text else R.id.right_status_text,
            statusColor
        )
        
        // Legacy date fields are hidden in compact mode
        views.setViewVisibility(
            if (isLeft) R.id.left_date_text else R.id.right_date_text,
            View.GONE
        )
        views.setViewVisibility(
            if (isLeft) R.id.left_duration_text else R.id.right_duration_text,
            View.VISIBLE
        )
        
        // Compact duration format: 7h30 instead of "7h 30min"
        val hours = duration / 60
        val minutes = duration % 60
        val durationText = if (minutes > 0) "${hours}h${minutes}" else "${hours}h"
        views.setTextViewText(
            if (isLeft) R.id.left_duration_text else R.id.right_duration_text,
            durationText
        )
    }

    private fun checkBedtimeReminder(bedTime: LocalTime): Boolean {
        val now = LocalTime.now()
        val reminderStart = bedTime.minusMinutes(15)
        val reminderEnd = bedTime.plusMinutes(30)
        
        return now.isAfter(reminderStart) && now.isBefore(reminderEnd)
    }

    private fun showBedtimeReminder(views: RemoteViews, bedtimeHour: Int, bedtimeMinute: Int) {
        views.setViewVisibility(R.id.widget_container, View.GONE)
        views.setViewVisibility(R.id.bedtime_reminder_container, View.VISIBLE)
        views.setViewVisibility(R.id.sleep_empty_container, View.GONE)
        
        val bedTime = LocalTime.of(bedtimeHour, bedtimeMinute)
        val bedTimeFormatted = bedTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        views.setTextViewText(
            R.id.bedtime_text,
            "🌙 Mục tiêu ngủ: $bedTimeFormatted"
        )
    }

    private fun createProgressCircleBitmap(achievementPercentage: Float, quality: SleepQuality): Bitmap {
        val size = 240
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val centerX = size / 2f
        val centerY = size / 2f
        val strokeWidth = 30f
        val radius = (size / 2f) - strokeWidth
        
        val qualityColor = when (quality) {
            SleepQuality.EXCELLENT -> Color.parseColor("#4CAF50")
            SleepQuality.GOOD -> Color.parseColor("#FF9800")
            SleepQuality.POOR -> Color.parseColor("#F44336")
        }
        
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        canvas.drawCircle(centerX, centerY, radius, bgPaint)
        
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = qualityColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        val sweepAngle = (achievementPercentage / 100f) * 360f
        canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        
        return bitmap
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First Sleep widget added")
        // Start periodic updates when first widget is added
        WidgetUpdateWorker.schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last Sleep widget removed")
    }
}
