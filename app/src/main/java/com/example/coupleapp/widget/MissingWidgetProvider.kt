package com.example.coupleapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.example.coupleapp.widget.data.MissingWidgetCachedData
import com.example.coupleapp.widget.data.WidgetDataRepository
import com.example.coupleapp.widget.worker.WidgetUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Missing Widget Provider - Battery Optimized
 * Displays the couple's missing streak and allows quick "I miss you" tap
 * 
 * Battery Optimization Features:
 * - Aggressive caching with 15-minute expiry
 * - Immediate cache update on tap for responsive feel
 * - WorkManager for periodic updates (respects Doze mode)
 * - Haptic feedback for better UX
 */
class MissingWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "MissingWidgetProvider"
        private const val ACTION_WIDGET_CLICK = "com.example.coupleapp.MISSING_WIDGET_CLICK"
        private const val ACTION_UPDATE_WIDGET = "com.example.coupleapp.UPDATE_MISSING_WIDGET"
        private const val ACTION_SEND_MISSING = "com.example.coupleapp.SEND_MISSING_FROM_WIDGET"
        
        /**
         * Update all widgets using cached data (battery-efficient)
         */
        fun updateWidgets(context: Context) {
            Log.d(TAG, "Requesting Missing widget update")
            val intent = Intent(context, MissingWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
        
        /**
         * Force update with fresh data
         */
        fun forceUpdateWidgets(context: Context) {
            Log.d(TAG, "Force updating Missing widgets")
            WidgetDataRepository.invalidateMissingCache(context)
            WidgetUpdateWorker.requestImmediateUpdate(context, WidgetUpdateWorker.WIDGET_TYPE_MISSING)
        }
        
        /**
         * Notify that missing was received from partner - update widget
         */
        fun onMissingReceived(context: Context) {
            Log.d(TAG, "Missing received, invalidating cache")
            WidgetDataRepository.invalidateMissingCache(context)
            updateWidgets(context)
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
                // Open app and navigate to Missing
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "missing")
                }
                context.startActivity(mainIntent)
            }
            ACTION_SEND_MISSING -> {
                // Quick send missing from widget with haptic feedback
                CoroutineScope(Dispatchers.IO).launch {
                    val success = sendMissingFromWidgetOptimized(context)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            // Haptic feedback
                            provideHapticFeedback(context)
                            // Show toast
                            Toast.makeText(context, "💕 Đã gửi nhớ!", Toast.LENGTH_SHORT).show()
                        }
                        // Update widget immediately
                        updateWidgets(context)
                    }
                }
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, MissingWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, widgetIds)
            }
        }
    }
    
    private fun provideHapticFeedback(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic feedback failed", e)
        }
    }
    
    private suspend fun sendMissingFromWidgetOptimized(context: Context): Boolean {
        return WidgetDataRepository.incrementMissingCount(context)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val views = RemoteViews(context.packageName, R.layout.widget_missing)
            
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                
                if (currentUser == null) {
                    showData(views, 0, 0, 0, 0, false)
                } else {
                    // Use cached data for battery efficiency
                    val cachedData = WidgetDataRepository.getMissingWidgetData(context)
                    
                    if (cachedData != null) {
                        showDataCached(views, cachedData)
                    } else {
                        // Fallback to direct Firebase query
                        val data = loadMissingData(currentUser.uid)
                        showData(
                            views,
                            data.currentStreak,
                            data.longestStreak,
                            data.myTodayCount,
                            data.partnerTodayCount,
                            data.hasSentToday
                        )
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
                showData(views, 0, 0, 0, 0, false)
            }
            
            // Set click intent for container
            val clickIntent = Intent(context, MissingWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.missing_widget_container, clickPendingIntent)
            
            // Set send missing button intent
            val sendIntent = Intent(context, MissingWidgetProvider::class.java).apply {
                action = ACTION_SEND_MISSING
            }
            val sendPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1000,
                sendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.missing_heart_button, sendPendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun showDataCached(views: RemoteViews, data: MissingWidgetCachedData) {
        showData(
            views,
            data.currentStreak,
            data.longestStreak,
            data.myTodayCount,
            data.partnerTodayCount,
            data.hasSentToday
        )
    }

    private suspend fun loadMissingData(userId: String): MissingWidgetData {
        return withContext(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                
                // Get user's partner
                val userDoc = db.collection("users").document(userId).get().await()
                val partnerId = userDoc.getString("partnerId")
                
                if (partnerId.isNullOrEmpty()) {
                    return@withContext MissingWidgetData(0, 0, 0, 0, false)
                }
                
                val coupleId = listOf(userId, partnerId).sorted().joinToString("_")
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Load today's counts
                val myRecordId = "${coupleId}_${userId}_$today"
                val partnerRecordId = "${coupleId}_${partnerId}_$today"
                
                val myRecord = db.collection("missing_records").document(myRecordId).get().await()
                val partnerRecord = db.collection("missing_records").document(partnerRecordId).get().await()
                
                val myTodayCount = myRecord.getLong("count")?.toInt() ?: 0
                val partnerTodayCount = partnerRecord.getLong("count")?.toInt() ?: 0
                
                // Calculate streak - load last 30 days
                var currentStreak = 0
                var longestStreak = 0
                var tempStreak = 0
                
                for (dayOffset in 0 until 30) {
                    val date = LocalDate.now().minusDays(dayOffset.toLong())
                    val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    
                    val myDayRecordId = "${coupleId}_${userId}_$dateString"
                    val partnerDayRecordId = "${coupleId}_${partnerId}_$dateString"
                    
                    val myDayRecord = db.collection("missing_records").document(myDayRecordId).get().await()
                    val partnerDayRecord = db.collection("missing_records").document(partnerDayRecordId).get().await()
                    
                    val myDayCount = myDayRecord.getLong("count")?.toInt() ?: 0
                    val partnerDayCount = partnerDayRecord.getLong("count")?.toInt() ?: 0
                    
                    if (myDayCount > 0 && partnerDayCount > 0) {
                        tempStreak++
                        if (dayOffset == 0 || currentStreak > 0) {
                            currentStreak = tempStreak
                        }
                    } else {
                        longestStreak = maxOf(longestStreak, tempStreak)
                        if (currentStreak == 0 && dayOffset > 0) {
                            // First gap found, current streak is what we counted
                        }
                        tempStreak = 0
                    }
                }
                longestStreak = maxOf(longestStreak, tempStreak)
                
                val hasSentToday = myTodayCount > 0 && partnerTodayCount > 0
                
                MissingWidgetData(
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    myTodayCount = myTodayCount,
                    partnerTodayCount = partnerTodayCount,
                    hasSentToday = hasSentToday
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading missing data", e)
                MissingWidgetData(0, 0, 0, 0, false)
            }
        }
    }

    private suspend fun sendMissingFromWidget(context: Context) {
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser ?: return
            val userId = currentUser.uid
            
            val db = FirebaseFirestore.getInstance()
            
            // Get partner
            val userDoc = db.collection("users").document(userId).get().await()
            val partnerId = userDoc.getString("partnerId") ?: return
            
            val coupleId = listOf(userId, partnerId).sorted().joinToString("_")
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val recordId = "${coupleId}_${userId}_$today"
            
            // Get current count
            val currentRecord = db.collection("missing_records").document(recordId).get().await()
            val currentCount = currentRecord.getLong("count")?.toInt() ?: 0
            val newCount = currentCount + 1
            
            // Update count
            val recordData = hashMapOf(
                "id" to recordId,
                "coupleId" to coupleId,
                "userId" to userId,
                "date" to today,
                "count" to newCount,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
            
            db.collection("missing_records").document(recordId).set(recordData).await()
            
            Log.d(TAG, "Missing sent from widget, new count: $newCount")
            
            // Update widget
            withContext(Dispatchers.Main) {
                updateWidgets(context)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending missing from widget", e)
        }
    }

    private fun showData(
        views: RemoteViews,
        currentStreak: Int,
        longestStreak: Int,
        myTodayCount: Int,
        partnerTodayCount: Int,
        hasSentToday: Boolean
    ) {
        // Streak display with fire emoji
        val streakText = if (currentStreak > 0) "🔥 $currentStreak ngày" else "Chưa có streak"
        views.setTextViewText(R.id.missing_streak_text, streakText)
        
        // Longest streak
        views.setTextViewText(R.id.missing_longest_streak, "Kỷ lục: $longestStreak ngày")
        
        // Today's count
        views.setTextViewText(R.id.missing_my_count, "Bạn: $myTodayCount ❤️")
        views.setTextViewText(R.id.missing_partner_count, "Người yêu: $partnerTodayCount ❤️")
        
        // Heart button state
        val heartEmoji = if (hasSentToday) "💕" else "❤️"
        views.setTextViewText(R.id.missing_heart_button, heartEmoji)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First Missing widget added")
        WidgetUpdateWorker.schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last Missing widget removed")
    }
}

/**
 * Simple data class for widget display
 */
data class MissingWidgetData(
    val currentStreak: Int,
    val longestStreak: Int,
    val myTodayCount: Int,
    val partnerTodayCount: Int,
    val hasSentToday: Boolean
)
