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

        Log.d(TAG, "From: ${remoteMessage.from}")

        // Проверяем, есть ли данные в сообщении
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Отправляем уведомление с данными из data payload
            val title = remoteMessage.data["title"] ?: "Калькулятор"
            val message = remoteMessage.data["body"] ?: remoteMessage.data["message"] ?: "Новое уведомление"
            sendNotification(title, message)
        }

        // Проверяем, есть ли notification payload (из Firebase Console)
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "Калькулятор", it.body ?: "Новое уведомление")
        }
    }

    /**
     * Создает и отправляет уведомление
     */
    private fun sendNotification(title: String, message: String) {
        // Создаем Intent для открытия MainActivity при нажатии на уведомление
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        // Создаем PendingIntent с правильными флагами для разных версий Android
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT
            )
        }

        // Получаем NotificationManager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаем канал для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calculator Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Канал для уведомлений калькулятора"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Строим уведомление
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // Временная иконка
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Отправляем уведомление
        notificationManager.notify(NOTIFICATION_ID, notification)
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