package com.example.coupleapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.coupleapp.CoupleApplication
import com.example.coupleapp.MainActivity
import com.example.coupleapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service for handling push notifications
 * This service receives notifications when the app is in background or killed
 */
class CoupleFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID_MESSAGES = "messages_channel"
        private const val CHANNEL_NAME_MESSAGES = "Tin nhắn"
        private const val NOTIFICATION_ID_MESSAGE = 2001
        
        private const val CHANNEL_ID_MISSING = "missing_channel"
        private const val CHANNEL_NAME_MISSING = "Missing"
        private const val NOTIFICATION_ID_MISSING = 2002
        
        private const val CHANNEL_ID_LOCKET = "locket_channel"
        private const val CHANNEL_NAME_LOCKET = "Locket"
        private const val NOTIFICATION_ID_LOCKET = 2003
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Called when a new FCM token is generated
     * Save this token to Firestore for the current user
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveTokenToFirestore(token)
    }

    /**
     * Called when a message is received from FCM
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if app is in foreground - if so, local notifications will handle it
        if (CoupleApplication.isAppInForeground) {
            Log.d(TAG, "App in foreground, skipping FCM notification")
            return
        }

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Handle notification payload (when app is in background, system handles this automatically)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification: ${notification.title} - ${notification.body}")
            showNotification(
                title = notification.title ?: "Couple App",
                body = notification.body ?: "Bạn có thông báo mới",
                type = "general"
            )
        }
    }

    /**
     * Handle data messages (custom logic based on message type)
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: "message"
        val senderName = data["senderName"] ?: "Người yêu"
        val senderId = data["senderId"] ?: ""
        
        // Don't show notification if sender is current user
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (senderId == currentUserId) {
            Log.d(TAG, "Ignoring notification from self")
            return
        }

        when (type) {
            "message" -> {
                showNotification(
                    title = senderName,
                    body = "Bạn có tin nhắn mới",
                    type = type,
                    notificationId = NOTIFICATION_ID_MESSAGE
                )
            }
            "missing" -> {
                showNotification(
                    title = "$senderName đang nhớ bạn! 💕",
                    body = "Tap để gửi nhớ lại",
                    type = type,
                    notificationId = NOTIFICATION_ID_MISSING
                )
            }
            "locket" -> {
                showNotification(
                    title = "Locket từ $senderName 📸",
                    body = "Bạn có ảnh mới từ người yêu",
                    type = type,
                    notificationId = NOTIFICATION_ID_LOCKET
                )
            }
            else -> {
                showNotification(
                    title = "Couple App",
                    body = data["body"] ?: "Bạn có thông báo mới",
                    type = type
                )
            }
        }
    }

    /**
     * Show local notification
     */
    private fun showNotification(
        title: String,
        body: String,
        type: String,
        notificationId: Int = NOTIFICATION_ID_MESSAGE
    ) {
        val channelId = when (type) {
            "message" -> CHANNEL_ID_MESSAGES
            "missing" -> CHANNEL_ID_MISSING
            "locket" -> CHANNEL_ID_LOCKET
            else -> CHANNEL_ID_MESSAGES
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", type)
            when (type) {
                "message" -> putExtra("open_chat", true)
                "missing" -> putExtra("navigate_to", "missing")
                "locket" -> putExtra("navigate_to", "locket")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
        
        Log.d(TAG, "Notification shown: $title - $body")
    }

    /**
     * Create notification channels for Android O and above
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Messages channel
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                CHANNEL_NAME_MESSAGES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo tin nhắn mới từ người yêu"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(messagesChannel)

            // Missing channel
            val missingChannel = NotificationChannel(
                CHANNEL_ID_MISSING,
                CHANNEL_NAME_MISSING,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi người yêu nhớ bạn"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(missingChannel)

            // Locket channel
            val locketChannel = NotificationChannel(
                CHANNEL_ID_LOCKET,
                CHANNEL_NAME_LOCKET,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo ảnh Locket mới"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(locketChannel)
        }
    }

    /**
     * Save FCM token to Firestore for the current user
     */
    private fun saveTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token", e)
            }
    }
}
