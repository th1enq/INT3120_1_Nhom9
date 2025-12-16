package com.example.coupleapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.view.View
import android.widget.RemoteViews
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.example.coupleapp.data.model.SleepQuality
import com.example.coupleapp.data.repository.SleepRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sleep Widget Provider
 * Displays sleep comparison between couple partners in a 4x2 widget
 */
class SleepWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_WIDGET_CLICK = "com.example.coupleapp.WIDGET_CLICK"
        private const val ACTION_UPDATE_WIDGET = "com.example.coupleapp.UPDATE_WIDGET"
        
        /**
         * Force update all widgets
         */
        fun updateWidgets(context: Context) {
            val intent = Intent(context, SleepWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
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
        CoroutineScope(Dispatchers.Main).launch {
            val views = RemoteViews(context.packageName, R.layout.widget_sleep_tracker)
            
            try {
                // Get current user and partner data
                val currentUser = SleepRepository.getCurrentUser()
                val partnerUser = SleepRepository.getPartnerUser()
                
                val currentUserRecord = SleepRepository.getTodaySleepRecord(currentUser.id)
                val partnerUserRecord = SleepRepository.getTodaySleepRecord(partnerUser.id)
                
                val currentSettings = SleepRepository.getSleepSettings(currentUser.id)
                
                // Check if we should show bedtime reminder
                val shouldShowReminder = checkBedtimeReminder(currentSettings.idealBedTime)
                
                if (shouldShowReminder) {
                    // Show bedtime reminder overlay
                    showBedtimeReminder(views, currentSettings.idealBedTime)
                } else {
                    // Show normal sleep comparison
                    showSleepComparison(
                        context,
                        views,
                        currentUserRecord,
                        partnerUserRecord,
                        currentUser.name,
                        partnerUser.name
                    )
                }
                
            } catch (e: Exception) {
                // Show error state
                views.setViewVisibility(R.id.widget_container, View.VISIBLE)
                views.setViewVisibility(R.id.bedtime_reminder_container, View.GONE)
            }
            
            // Set click intent to open app
            val clickIntent = Intent(context, SleepWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, clickPendingIntent)
            views.setOnClickPendingIntent(R.id.bedtime_reminder_container, clickPendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun checkBedtimeReminder(bedTime: LocalTime): Boolean {
        val now = LocalTime.now()
        val reminderStart = bedTime.minusMinutes(15)
        val reminderEnd = bedTime.plusMinutes(30)
        
        return now.isAfter(reminderStart) && now.isBefore(reminderEnd)
    }

    private fun showBedtimeReminder(views: RemoteViews, bedTime: LocalTime) {
        views.setViewVisibility(R.id.widget_container, View.GONE)
        views.setViewVisibility(R.id.bedtime_reminder_container, View.VISIBLE)
        
        val bedTimeFormatted = bedTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        views.setTextViewText(
            R.id.bedtime_text,
            "Sleep goal: $bedTimeFormatted"
        )
    }

    private fun showSleepComparison(
        context: Context,
        views: RemoteViews,
        leftRecord: com.example.coupleapp.data.model.SleepRecord?,
        rightRecord: com.example.coupleapp.data.model.SleepRecord?,
        leftName: String,
        rightName: String
    ) {
        views.setViewVisibility(R.id.widget_container, View.VISIBLE)
        views.setViewVisibility(R.id.bedtime_reminder_container, View.GONE)
        
        // Update left user
        leftRecord?.let { record ->
            updateUserSleepInfo(
                context,
                views,
                record,
                leftName,
                isLeft = true
            )
        }
        
        // Update right user
        rightRecord?.let { record ->
            updateUserSleepInfo(
                context,
                views,
                record,
                rightName,
                isLeft = false
            )
        }
    }

    private fun updateUserSleepInfo(
        context: Context,
        views: RemoteViews,
        record: com.example.coupleapp.data.model.SleepRecord,
        userName: String,
        isLeft: Boolean
    ) {
        // Set emoji icon
        val emojiText = if (userName.lowercase().contains("emma") || userName.lowercase().contains("e")) "😊" else "🥦"
        views.setTextViewText(
            if (isLeft) R.id.left_emoji_icon else R.id.right_emoji_icon,
            emojiText
        )
        
        // Set status circle image based on quality
        val statusImageRes = when (record.quality) {
            SleepQuality.EXCELLENT -> R.drawable.excellent
            SleepQuality.GOOD -> R.drawable.good
            SleepQuality.POOR -> R.drawable.bad
        }
        views.setImageViewResource(
            if (isLeft) R.id.left_status_circle else R.id.right_status_circle,
            statusImageRes
        )
        
        // Create progress circle bitmap
        val progressBitmap = createProgressCircleBitmap(record.achievementPercentage, record.quality)
        views.setImageViewBitmap(
            if (isLeft) R.id.left_progress_bg else R.id.right_progress_bg,
            progressBitmap
        )
        
        // Set status text with Vietnamese translation
        val statusText = when (record.quality) {
            SleepQuality.EXCELLENT -> "Xuất sắc"
            SleepQuality.GOOD -> "Tốt"
            SleepQuality.POOR -> "Kém"
        }
        val statusColor = when (record.quality) {
            SleepQuality.EXCELLENT -> Color.parseColor("#4CAF50")
            SleepQuality.GOOD -> Color.parseColor("#FF9800")
            SleepQuality.POOR -> Color.parseColor("#F44336")
        }
        views.setTextViewText(
            if (isLeft) R.id.left_status_text else R.id.right_status_text,
            statusText
        )
        views.setTextColor(
            if (isLeft) R.id.left_status_text else R.id.right_status_text,
            statusColor
        )
        
        // Set date in Vietnamese format
        val dateFormatter = DateTimeFormatter.ofPattern("'Tháng' M d")
        views.setTextViewText(
            if (isLeft) R.id.left_date_text else R.id.right_date_text,
            record.date.format(dateFormatter)
        )
        
        // Set duration
        val hours = record.actualSleepDuration / 60
        val minutes = record.actualSleepDuration % 60
        val durationText = "${hours}h ${minutes}min"
        views.setTextViewText(
            if (isLeft) R.id.left_duration_text else R.id.right_duration_text,
            durationText
        )
    }

    /**
     * Create a circular progress bitmap similar to SleepHistoryItem
     */
    private fun createProgressCircleBitmap(achievementPercentage: Float, quality: SleepQuality): Bitmap {
        val size = 240 // Higher resolution for widget
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val centerX = size / 2f
        val centerY = size / 2f
        val strokeWidth = 30f
        val radius = (size / 2f) - strokeWidth
        
        // Quality color
        val qualityColor = when (quality) {
            SleepQuality.EXCELLENT -> Color.parseColor("#4CAF50")
            SleepQuality.GOOD -> Color.parseColor("#FF9800")
            SleepQuality.POOR -> Color.parseColor("#F44336")
        }
        
        // Background circle (light gray)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        canvas.drawCircle(centerX, centerY, radius, bgPaint)
        
        // Progress arc
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
        // First widget added
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed
    }
}
