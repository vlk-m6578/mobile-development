package com.example.calculator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log

import android.app.Notification

data class HistoryItem(
    val expression: String = "",
    val result: String = "",
    val timestamp: Long = 0
)

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnClear: Button
    private lateinit var btnBack: Button
    private lateinit var emptyView: TextView
    private lateinit var database: DatabaseReference
    private lateinit var adapter: HistoryAdapter
    private val historyList = mutableListOf<HistoryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.recyclerView)
        btnClear = findViewById(R.id.btn_clear_history)
        btnBack = findViewById(R.id.btn_back)
        emptyView = findViewById(R.id.tv_empty_history)

        database = FirebaseDatabase.getInstance().getReference("history")

        adapter = HistoryAdapter(historyList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        createNotificationChannel()
        requestNotificationPermission()

        loadHistory()

        btnClear.setOnClickListener {
            database.removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
                    // ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ
                    sendClearNotification()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "calculator_channel"
            val channelName = "Calculator Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Уведомления калькулятора"
                enableLights(true)
                enableVibration(true)

                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    Notification.AUDIO_ATTRIBUTES_DEFAULT
                )

                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d("NOTIFICATION", "Канал создан с IMPORTANCE_HIGH")
        }
    }

    private fun loadHistory() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyList.clear()

                for (itemSnapshot in snapshot.children) {
                    val expression = itemSnapshot.child("expression").getValue(String::class.java) ?: ""
                    val result = itemSnapshot.child("result").getValue(String::class.java) ?: ""
                    val timestamp = itemSnapshot.child("timestamp").getValue(Long::class.java) ?: 0

                    historyList.add(HistoryItem(expression, result, timestamp))
                }

                historyList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()

                if (historyList.isEmpty()) {
                    emptyView.visibility = TextView.VISIBLE
                    recyclerView.visibility = TextView.GONE
                } else {
                    emptyView.visibility = TextView.GONE
                    recyclerView.visibility = TextView.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HistoryActivity, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendClearNotification() {
        Log.d("NOTIFICATION", "Начинаем отправку")

        val intent = Intent(this, HistoryActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "calculator_channel"

        // Для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Важно! Проверяем, есть ли уже канал
            var channel = notificationManager.getNotificationChannel(channelId)
            if (channel == null) {
                Log.d("NOTIFICATION", "📢 Создаем новый канал")
                channel = NotificationChannel(
                    channelId,
                    "Calculator Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомления калькулятора"
                    enableLights(true)
                    enableVibration(true)
                    setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d("NOTIFICATION", "✅ Канал создан")
            } else {
                Log.d("NOTIFICATION", "✅ Канал уже существует")
            }
        }

        Log.d("NOTIFICATION", "📱 Строим уведомление с кастомной иконкой")

        // ВАЖНО: используем свою иконку, а не системную
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)  // ← своя иконка!
            .setContentTitle("История калькулятора")
            .setContentText("История вычислений была очищена")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)  // звук, свет, вибрация
            .build()

        Log.d("NOTIFICATION", "📨 Отправляем уведомление с ID 100")
        notificationManager.notify(100, notification)
        Log.d("NOTIFICATION", "✅ Уведомление отправлено")
    }
}