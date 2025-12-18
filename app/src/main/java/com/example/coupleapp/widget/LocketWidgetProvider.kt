package com.example.coupleapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.example.coupleapp.data.model.LocketPost
import com.example.coupleapp.data.model.LocketType
import com.example.coupleapp.widget.data.LocketWidgetCachedData
import com.example.coupleapp.widget.data.WidgetDataRepository
import com.example.coupleapp.widget.worker.WidgetUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Locket Widget Provider - Battery Optimized
 * Displays the latest Locket content from your partner in a 4x2 widget
 * 
 * Battery Optimization Features:
 * - Aggressive caching with 5-minute expiry for real-time feel
 * - WorkManager for periodic updates (respects Doze mode)
 * - Smart data invalidation on new Locket
 * - Fallback to cache when network unavailable
 */
class LocketWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "LocketWidgetProvider"
        private const val ACTION_WIDGET_CLICK = "com.example.coupleapp.LOCKET_WIDGET_CLICK"
        private const val ACTION_UPDATE_WIDGET = "com.example.coupleapp.UPDATE_LOCKET_WIDGET"
        private const val ACTION_SEND_LOCKET = "com.example.coupleapp.SEND_LOCKET_FROM_WIDGET"
        
        /**
         * Update all widgets using cached data (battery-efficient)
         */
        fun updateWidgets(context: Context) {
            Log.d(TAG, "Requesting Locket widget update")
            val intent = Intent(context, LocketWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
        
        /**
         * Force update with fresh data - call when new Locket received
         */
        fun forceUpdateWidgets(context: Context) {
            Log.d(TAG, "Force updating Locket widgets")
            WidgetDataRepository.invalidateLocketCache(context)
            WidgetUpdateWorker.requestImmediateUpdate(context, WidgetUpdateWorker.WIDGET_TYPE_LOCKET)
        }
        
        /**
         * Notify that new Locket was received - invalidate cache and update
         */
        fun onNewLocketReceived(context: Context) {
            Log.d(TAG, "New Locket received, invalidating cache")
            WidgetDataRepository.invalidateLocketCache(context)
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
            ACTION_WIDGET_CLICK, ACTION_SEND_LOCKET -> {
                // Open app and navigate to Locket
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "locket")
                }
                context.startActivity(mainIntent)
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, LocketWidgetProvider::class.java)
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
            val views = RemoteViews(context.packageName, R.layout.widget_locket)
            
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                
                if (currentUser == null) {
                    showEmptyState(views, "Đăng nhập để xem Locket")
                } else {
                    // Use cached data for battery efficiency
                    val cachedData = WidgetDataRepository.getLocketWidgetData(context)
                    
                    if (cachedData != null && cachedData.type != "EMPTY") {
                        showLocketContentCached(context, views, cachedData)
                    } else {
                        // Fallback to direct Firebase query
                        val latestLocket = loadLatestLocket(currentUser.uid)
                        if (latestLocket != null) {
                            showLocketContent(context, views, latestLocket)
                        } else {
                            showEmptyState(views, "Chưa có Locket mới")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
                showEmptyState(views, "Không thể tải Locket")
            }
            
            // Set click intents
            val clickIntent = Intent(context, LocketWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.locket_widget_container, clickPendingIntent)
            
            // Set send button intent
            val sendIntent = Intent(context, LocketWidgetProvider::class.java).apply {
                action = ACTION_SEND_LOCKET
            }
            val sendPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1000,
                sendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.locket_send_button, sendPendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun showLocketContentCached(context: Context, views: RemoteViews, data: LocketWidgetCachedData) {
        views.setViewVisibility(R.id.locket_content_container, View.VISIBLE)
        views.setViewVisibility(R.id.locket_empty_container, View.GONE)
        
        // Set sender name with new indicator
        val senderDisplay = if (data.hasNewLocket) "💌 ${data.senderName}" else data.senderName
        views.setTextViewText(R.id.locket_sender_name, senderDisplay)
        
        // Set timestamp
        val timeText = formatTimestamp(data.timestamp)
        views.setTextViewText(R.id.locket_timestamp, timeText)
        
        // Set content based on type
        when (data.type) {
            "EMOJI" -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.VISIBLE)
                views.setViewVisibility(R.id.locket_text_content, View.GONE)
                views.setViewVisibility(R.id.locket_image_content, View.GONE)
                views.setTextViewText(R.id.locket_emoji_content, data.content)
            }
            "TEXT" -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.GONE)
                views.setViewVisibility(R.id.locket_text_content, View.VISIBLE)
                views.setViewVisibility(R.id.locket_image_content, View.GONE)
                views.setTextViewText(R.id.locket_text_content, data.content)
            }
            "PHOTO", "DRAWING" -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.GONE)
                views.setViewVisibility(R.id.locket_text_content, View.GONE)
                views.setViewVisibility(R.id.locket_image_content, View.VISIBLE)
                views.setImageViewResource(R.id.locket_image_content, R.drawable.locket)
            }
            else -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.GONE)
                views.setViewVisibility(R.id.locket_text_content, View.VISIBLE)
                views.setViewVisibility(R.id.locket_image_content, View.GONE)
                views.setTextViewText(R.id.locket_text_content, data.caption ?: "❤️")
            }
        }
        
        // Set caption if available
        if (!data.caption.isNullOrEmpty() && data.type != "TEXT") {
            views.setViewVisibility(R.id.locket_caption, View.VISIBLE)
            views.setTextViewText(R.id.locket_caption, data.caption)
        } else {
            views.setViewVisibility(R.id.locket_caption, View.GONE)
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""
        
        val localDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val now = LocalDateTime.now()
        
        return when {
            localDateTime.toLocalDate() == now.toLocalDate() -> {
                "Hôm nay ${localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            }
            localDateTime.toLocalDate() == now.toLocalDate().minusDays(1) -> {
                "Hôm qua ${localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            }
            else -> {
                localDateTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
            }
        }
    }

    private suspend fun loadLatestLocket(userId: String): LocketData? {
        return withContext(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                
                // First get user's partnerId
                val userDoc = db.collection("users").document(userId).get().await()
                val partnerId = userDoc.getString("partnerId")
                
                if (partnerId.isNullOrEmpty()) {
                    Log.d(TAG, "No partner linked")
                    return@withContext null
                }
                
                // Get couple ID for querying lockets
                val coupleId = listOf(userId, partnerId).sorted().joinToString("_")
                
                // Query latest locket sent to current user (from partner)
                val locketQuery = db.collection("lockets")
                    .whereEqualTo("coupleId", coupleId)
                    .whereEqualTo("senderId", partnerId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                
                if (locketQuery.documents.isEmpty()) {
                    Log.d(TAG, "No lockets found from partner")
                    return@withContext null
                }
                
                val doc = locketQuery.documents.first()
                val senderName = doc.getString("senderName") ?: "Partner"
                val content = doc.getString("content") ?: ""
                val type = doc.getString("type") ?: "TEXT"
                val caption = doc.getString("caption")
                val timestamp = doc.getTimestamp("timestamp")?.toDate()
                
                LocketData(
                    senderName = senderName,
                    content = content,
                    type = type,
                    caption = caption,
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading locket", e)
                null
            }
        }
    }

    private fun showLocketContent(context: Context, views: RemoteViews, locket: LocketData) {
        views.setViewVisibility(R.id.locket_content_container, View.VISIBLE)
        views.setViewVisibility(R.id.locket_empty_container, View.GONE)
        
        // Set sender name
        views.setTextViewText(R.id.locket_sender_name, locket.senderName)
        
        // Set timestamp
        val timeText = locket.timestamp?.let { date ->
            val localDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            val now = LocalDateTime.now()
            when {
                localDateTime.toLocalDate() == now.toLocalDate() -> {
                    "Hôm nay ${localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                }
                localDateTime.toLocalDate() == now.toLocalDate().minusDays(1) -> {
                    "Hôm qua ${localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                }
                else -> {
                    localDateTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                }
            }
        } ?: ""
        views.setTextViewText(R.id.locket_timestamp, timeText)
        
        // Set content based on type
        when (locket.type) {
            "EMOJI" -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.VISIBLE)
                views.setViewVisibility(R.id.locket_text_content, View.GONE)
                views.setViewVisibility(R.id.locket_image_content, View.GONE)
                views.setTextViewText(R.id.locket_emoji_content, locket.content)
            }
            "TEXT" -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.GONE)
                views.setViewVisibility(R.id.locket_text_content, View.VISIBLE)
                views.setViewVisibility(R.id.locket_image_content, View.GONE)
                views.setTextViewText(R.id.locket_text_content, locket.content)
            }
            "PHOTO", "DRAWING" -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.GONE)
                views.setViewVisibility(R.id.locket_text_content, View.GONE)
                views.setViewVisibility(R.id.locket_image_content, View.VISIBLE)
                
                // Load image in background - for widget we'll show placeholder
                // Real image loading requires Glide AppWidgetTarget which is complex
                views.setImageViewResource(R.id.locket_image_content, R.drawable.locket)
            }
            else -> {
                views.setViewVisibility(R.id.locket_emoji_content, View.GONE)
                views.setViewVisibility(R.id.locket_text_content, View.VISIBLE)
                views.setViewVisibility(R.id.locket_image_content, View.GONE)
                views.setTextViewText(R.id.locket_text_content, locket.caption ?: "❤️")
            }
        }
        
        // Set caption if available
        if (!locket.caption.isNullOrEmpty() && locket.type != "TEXT") {
            views.setViewVisibility(R.id.locket_caption, View.VISIBLE)
            views.setTextViewText(R.id.locket_caption, locket.caption)
        } else {
            views.setViewVisibility(R.id.locket_caption, View.GONE)
        }
    }

    private fun showEmptyState(views: RemoteViews, message: String) {
        views.setViewVisibility(R.id.locket_content_container, View.GONE)
        views.setViewVisibility(R.id.locket_empty_container, View.VISIBLE)
        views.setTextViewText(R.id.locket_empty_message, message)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First Locket widget added")
        WidgetUpdateWorker.schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last Locket widget removed")
    }
}

/**
 * Simple data class for widget display
 */
data class LocketData(
    val senderName: String,
    val content: String,
    val type: String,
    val caption: String?,
    val timestamp: Date?
)
