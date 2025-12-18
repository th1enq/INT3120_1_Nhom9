package com.example.coupleapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Location Widget Provider
 * Displays the distance between couple partners and their locations
 * Battery optimized with 30-minute update intervals
 * Location updates are handled by the app's LocationTrackingService
 */
class LocationWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "LocationWidgetProvider"
        private const val ACTION_WIDGET_CLICK = "com.example.coupleapp.LOCATION_WIDGET_CLICK"
        private const val ACTION_UPDATE_WIDGET = "com.example.coupleapp.UPDATE_LOCATION_WIDGET"
        private const val ACTION_SHARE_LOCATION = "com.example.coupleapp.SHARE_LOCATION_FROM_WIDGET"
        
        /**
         * Force update all widgets
         */
        fun updateWidgets(context: Context) {
            val intent = Intent(context, LocationWidgetProvider::class.java).apply {
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
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_CLICK, ACTION_SHARE_LOCATION -> {
                // Open app and navigate to Distance
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "distance")
                }
                context.startActivity(mainIntent)
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, LocationWidgetProvider::class.java)
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
            val views = RemoteViews(context.packageName, R.layout.widget_location)
            
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                
                if (currentUser == null) {
                    showNoDataState(views, "Đăng nhập để xem vị trí")
                } else {
                    val data = loadLocationData(currentUser.uid)
                    if (data != null) {
                        showLocationData(views, data)
                    } else {
                        showNoDataState(views, "Chia sẻ vị trí để bắt đầu")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
                showNoDataState(views, "Không thể tải vị trí")
            }
            
            // Set click intent for container
            val clickIntent = Intent(context, LocationWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.location_widget_container, clickPendingIntent)
            
            // Set share button intent
            val shareIntent = Intent(context, LocationWidgetProvider::class.java).apply {
                action = ACTION_SHARE_LOCATION
            }
            val sharePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1000,
                shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.location_share_button, sharePendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private suspend fun loadLocationData(userId: String): LocationWidgetData? {
        return withContext(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                
                // Get user info
                val userDoc = db.collection("users").document(userId).get().await()
                val userName = userDoc.getString("displayName") ?: "You"
                val partnerId = userDoc.getString("partnerId")
                val coupleId = userDoc.getString("coupleId")
                
                if (partnerId.isNullOrEmpty()) {
                    return@withContext null
                }
                
                // Get partner info
                val partnerDoc = db.collection("users").document(partnerId).get().await()
                val partnerName = partnerDoc.getString("displayName") ?: "Partner"
                
                // Generate coupleId if not set
                val effectiveCoupleId = coupleId ?: listOf(userId, partnerId).sorted().joinToString("_")
                
                // Get my location
                val myLocationDoc = db.collection("couple_locations")
                    .document(effectiveCoupleId)
                    .collection("user_locations")
                    .document(userId)
                    .get()
                    .await()
                
                // Get partner's location
                val partnerLocationDoc = db.collection("couple_locations")
                    .document(effectiveCoupleId)
                    .collection("user_locations")
                    .document(partnerId)
                    .get()
                    .await()
                
                val myLat = myLocationDoc.getDouble("latitude")
                val myLng = myLocationDoc.getDouble("longitude")
                val myLocationName = myLocationDoc.getString("locationName") ?: "Không rõ"
                val myLastUpdate = myLocationDoc.getTimestamp("updatedAt")
                
                val partnerLat = partnerLocationDoc.getDouble("latitude")
                val partnerLng = partnerLocationDoc.getDouble("longitude")
                val partnerLocationName = partnerLocationDoc.getString("locationName") ?: "Không rõ"
                val partnerLastUpdate = partnerLocationDoc.getTimestamp("updatedAt")
                
                // Calculate distance if both have locations
                val distance = if (myLat != null && myLng != null && partnerLat != null && partnerLng != null) {
                    calculateDistance(myLat, myLng, partnerLat, partnerLng)
                } else {
                    null
                }
                
                // Check if sharing is enabled
                val isSharing = myLat != null && myLng != null
                
                LocationWidgetData(
                    myName = userName,
                    partnerName = partnerName,
                    myLocation = myLocationName,
                    partnerLocation = partnerLocationName,
                    distance = distance,
                    isSharing = isSharing,
                    partnerLastUpdate = partnerLastUpdate?.toDate()?.time
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading location data", e)
                null
            }
        }
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun showLocationData(views: RemoteViews, data: LocationWidgetData) {
        views.setViewVisibility(R.id.location_content_container, View.VISIBLE)
        views.setViewVisibility(R.id.location_empty_container, View.GONE)
        
        // My location
        views.setTextViewText(R.id.location_my_name, data.myName)
        views.setTextViewText(R.id.location_my_location, data.myLocation)
        
        // Partner location
        views.setTextViewText(R.id.location_partner_name, data.partnerName)
        views.setTextViewText(R.id.location_partner_location, data.partnerLocation)
        
        // Distance
        val distanceText = if (data.distance != null) {
            if (data.distance < 1.0) {
                "${(data.distance * 1000).toInt()} m"
            } else {
                String.format("%.1f km", data.distance)
            }
        } else {
            "-- km"
        }
        views.setTextViewText(R.id.location_distance_text, distanceText)
        
        // Status indicator
        val statusText = if (data.isSharing) "📍 Đang chia sẻ" else "⭕ Tắt chia sẻ"
        views.setTextViewText(R.id.location_status, statusText)
        
        // Partner last update time
        val lastUpdateText = data.partnerLastUpdate?.let { timestamp ->
            val diff = System.currentTimeMillis() - timestamp
            val minutes = diff / (1000 * 60)
            when {
                minutes < 1 -> "Vừa xong"
                minutes < 60 -> "${minutes} phút trước"
                minutes < 1440 -> "${minutes / 60} giờ trước"
                else -> "${minutes / 1440} ngày trước"
            }
        } ?: ""
        views.setTextViewText(R.id.location_last_update, lastUpdateText)
    }

    private fun showNoDataState(views: RemoteViews, message: String) {
        views.setViewVisibility(R.id.location_content_container, View.GONE)
        views.setViewVisibility(R.id.location_empty_container, View.VISIBLE)
        views.setTextViewText(R.id.location_empty_message, message)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First Location widget added")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last Location widget removed")
    }
}

/**
 * Simple data class for widget display
 */
data class LocationWidgetData(
    val myName: String,
    val partnerName: String,
    val myLocation: String,
    val partnerLocation: String,
    val distance: Double?,
    val isSharing: Boolean,
    val partnerLastUpdate: Long?
)
