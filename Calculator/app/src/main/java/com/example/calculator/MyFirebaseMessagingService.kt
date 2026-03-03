package com.example.calculator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFMService"
        private const val CHANNEL_ID = "calculator_channel"
        private const val NOTIFICATION_ID = 1001
    }

    /**
     * Вызывается когда приходит сообщение FCM
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "🔥 Уведомление получено!")
        Log.d("FCM", "From: ${remoteMessage.from}")

        if (remoteMessage.notification != null) {
            Log.d("FCM", "Title: ${remoteMessage.notification?.title}")
            Log.d("FCM", "Body: ${remoteMessage.notification?.body}")
            sendNotification(
                remoteMessage.notification?.title ?: "Калькулятор",
                remoteMessage.notification?.body ?: "Новое уведомление"
            )
        }
    }
    /**
     * Создает и отправляет уведомление
     */
    private fun sendNotification(title: String, message: String) {
        Log.d("FCM", "📱 Пытаюсь показать уведомление: $title - $message")

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "calculator_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Calculator Channel",
                NotificationManager.IMPORTANCE_HIGH  // Важно!
            )
            notificationManager.createNotificationChannel(channel)
            Log.d("FCM", "✅ Канал создан")
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // Важно!
            .build()

        notificationManager.notify(0, notification)
        Log.d("FCM", "✅ Уведомление отправлено")
    }
    /**
     * Вызывается когда FCM выдает новый токен устройства
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: $token")
        // Здесь можно отправить токен на ваш сервер, если нужно
    }
}