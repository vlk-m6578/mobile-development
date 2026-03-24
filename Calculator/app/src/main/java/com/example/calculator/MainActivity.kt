package com.example.calculator

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.database.*
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.appcompat.app.AlertDialog


class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var displayTextView: TextView
    private lateinit var btnLight: Button
    private lateinit var btnDark: Button
    private lateinit var btnVibration: Button
    private lateinit var btnSound: Button
    private lateinit var btnHistory: Button

    // Vibrator
    private lateinit var vibrator: Vibrator
    private var isVibrationEnabled = true

    // Sound
    private var isSoundEnabled = true
    private var mediaPlayer: MediaPlayer? = null

    // State Variables
    private var currentInput = StringBuilder("0")
    private var storedOperator: String? = null
    private var storedValue: Double = 0.0
    private var isNewInput = true

    // SharedPreferences
    private lateinit var settingsPrefs: android.content.SharedPreferences

    // Firebase
    private lateinit var database: DatabaseReference
    private lateinit var historyDatabase: DatabaseReference
    private lateinit var themeDatabase: DatabaseReference
    private var lastExpression = ""

    private var firstOperand: Double = 0.0
    private var secondOperand: Double = 0.0

    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Загружаем настройки
        settingsPrefs = getSharedPreferences("calculator_settings", MODE_PRIVATE)

        // Применяем тему
        applyTheme()

        setContentView(R.layout.activity_main)

        biometricHelper = BiometricHelper(this)
        checkPassCode()

        // Инициализация Firebase
         setupFirebase()

         clearHistoryOnce()

        // Инициализация
        initViews()
        initVibrator()
        initSound()
        loadSettings()
        setupButtons()
        updateButtonsText()

        if (savedInstanceState == null) {
            displayTextView.text = "0"
        }

        // Загружаем тему из облака
        loadThemeFromCloud()

        requestNotificationPermission()
    }

    private fun checkPassCode() {
        val prefs = getSharedPreferences("biometric_prefs", MODE_PRIVATE)
        val savedPass = prefs.getString("user_passcode", null)

        if (savedPass != null) {
            // Pass Code уже установлен, запрашиваем его
            val intent = Intent(this, PassCodeActivity::class.java)
            intent.putExtra(PassCodeActivity.EXTRA_MODE, PassCodeActivity.MODE_VERIFY)
            startActivityForResult(intent, 1001)
        } else {
            // Если не установлен, предлагаем установить
            showSetupPassCodeDialog()
        }
    }
    private fun showSetupPassCodeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Защита приложения")
            .setMessage("Установите PIN-код для защиты приложения")
            .setPositiveButton("Установить") { _, _ ->
                val intent = Intent(this, PassCodeActivity::class.java)
                intent.putExtra(PassCodeActivity.EXTRA_MODE, PassCodeActivity.MODE_SETUP)
                startActivity(intent)
            }
            .setNegativeButton("Пропустить") { _, _ ->
                Toast.makeText(this, "Вы можете установить PIN позже в настройках", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode != RESULT_OK) {
                // Если пользователь не подтвердил PIN, закрываем приложение
                finish()
            }
        }
    }

    private fun showSettingsMenu() {
        val items = arrayOf(
            if (biometricHelper.isPassCodeSet()) "Сменить PIN" else "Установить PIN",
            "Сбросить PIN (если забыли)"
        )

        AlertDialog.Builder(this)
            .setTitle("Настройки безопасности")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        if (biometricHelper.isPassCodeSet()) {
                            // Сменить PIN
                            val intent = Intent(this, PassCodeActivity::class.java)
                            intent.putExtra(PassCodeActivity.EXTRA_MODE, PassCodeActivity.MODE_CHANGE)
                            startActivity(intent)
                        } else {
                            // Установить PIN
                            val intent = Intent(this, PassCodeActivity::class.java)
                            intent.putExtra(PassCodeActivity.EXTRA_MODE, PassCodeActivity.MODE_SETUP)
                            startActivity(intent)
                        }
                    }
                    1 -> {
                        // Сброс PIN
                        AlertDialog.Builder(this)
                            .setTitle("Сброс PIN-кода")
                            .setMessage("Вы уверены? Это удалит текущий PIN-код")
                            .setPositiveButton("Да, сбросить") { _, _ ->
                                if (biometricHelper.resetPassCode()) {
                                    Toast.makeText(this, "PIN-код сброшен", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                    }
                }
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.d("NOTIFICATION", "Разрешение на уведомления получено")
                    Toast.makeText(this, "Разрешение на уведомления получено", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("NOTIFICATION", "Разрешение на уведомления отклонено")
                    Toast.makeText(this, "Без разрешения уведомления не будут показываться", Toast.LENGTH_LONG).show()
                }
            }
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

    private fun clearHistoryOnce() {
        if (!::historyDatabase.isInitialized) {
            Log.e("FIREBASE", "historyDatabase не инициализирована")
            return
        }
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)

        if (isFirstLaunch) {
            // Очищаем при первом запуске
            historyDatabase.removeValue()
                .addOnSuccessListener {
                    Log.d("FIREBASE", "История очищена при первом запуске")
                    prefs.edit().putBoolean("is_first_launch", false).apply()
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE", "Ошибка очистки", e)
                }
        }
    }

    private fun setupFirebase() {
        try {
            database = FirebaseDatabase.getInstance().reference
            historyDatabase = database.child("history")
            themeDatabase = database.child("theme")

            Log.d("FIREBASE", "✅ Firebase инициализирована")
            Log.d("FIREBASE", "URL: ${FirebaseDatabase.getInstance().reference}")

            val testItem = mapOf(
                "test" to "test",
                "timestamp" to System.currentTimeMillis()
            )
            database.child("test").setValue(testItem)
                .addOnSuccessListener {
                    Log.d("FIREBASE", "✅ Тестовая запись создана")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE", "❌ Тестовая запись не создана", e)
                }

        } catch (e: Exception) {
            Log.e("FIREBASE", "Ошибка инициализации", e)
        }
    }

    private fun initViews() {
        displayTextView = findViewById(R.id.displayTextView)
        btnLight = findViewById(R.id.button_light_theme)
        btnDark = findViewById(R.id.button_dark_theme)
        btnVibration = findViewById(R.id.button_vibration)
        btnSound = findViewById(R.id.button_sound)
        btnHistory = findViewById(R.id.button_history)
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun initSound() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.click)
        } catch (e: Exception) {
            isSoundEnabled = false
        }
    }

    private fun loadSettings() {
        isVibrationEnabled = settingsPrefs.getBoolean("vibration_enabled", true)
        isSoundEnabled = settingsPrefs.getBoolean("sound_enabled", true)
    }

    private fun applyTheme() {
        val themeMode = settingsPrefs.getString("theme_mode", "system")
        when (themeMode) {
            "light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                // Меняем цвет статус-бара
                window.statusBarColor = getColor(R.color.purple_700)
            }
            "dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                window.statusBarColor = getColor(R.color.purple_200)
            }
            "system" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun loadThemeFromCloud() {
        themeDatabase.child("mode").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudTheme = snapshot.getValue(String::class.java)
                if (cloudTheme != null && cloudTheme != settingsPrefs.getString("theme_mode", "system")) {

                    settingsPrefs.edit().putString("theme_mode", cloudTheme).apply()
                    applyTheme()
                    recreate()
                    Toast.makeText(this@MainActivity, "Тема синхронизирована с облаком", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    private fun saveThemeToCloud(themeMode: String) {
        themeDatabase.child("mode").setValue(themeMode)
            .addOnSuccessListener {
                Toast.makeText(this, "Тема сохранена в облаке", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка сохранения темы", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveToHistory(expression: String, result: String) {
        Log.d("FIREBASE", "🟡 Попытка сохранить в историю: $expression = $result")

        try {
            val historyItem = mapOf(
                "expression" to expression,
                "result" to result,
                "timestamp" to System.currentTimeMillis()
            )

            Log.d("FIREBASE", "📦 Объект создан: $historyItem")
            Log.d("FIREBASE", "📁 Путь в базе: ${historyDatabase.toString()}")

            historyDatabase.push().setValue(historyItem)
                .addOnSuccessListener {
                    Log.d("FIREBASE", "✅ УСПЕШНО сохранено в Firebase!")
                    Toast.makeText(this, "✅ Сохранено", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE", "❌ ОШИБКА сохранения: ${e.message}")
                    Toast.makeText(this, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("FIREBASE", "💥 Исключение при сохранении", e)
        }
    }
    private fun setupButtons() {
        // Кнопки тем
        btnLight.setOnClickListener {
            playFeedback()
            settingsPrefs.edit().putString("theme_mode", "light").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            window.statusBarColor = getColor(R.color.purple_700)
            saveThemeToCloud("light")
            recreate()
            Toast.makeText(this, "Светлая тема", Toast.LENGTH_SHORT).show()
        }

        btnDark.setOnClickListener {
            playFeedback()
            settingsPrefs.edit().putString("theme_mode", "dark").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            window.statusBarColor = getColor(R.color.purple_200)
            saveThemeToCloud("dark")
            recreate()
            Toast.makeText(this, "Темная тема", Toast.LENGTH_SHORT).show()
        }

        // Кнопка вибрации
        btnVibration.setOnClickListener {
            playFeedback()
            isVibrationEnabled = !isVibrationEnabled
            settingsPrefs.edit().putBoolean("vibration_enabled", isVibrationEnabled).apply()
            updateButtonsText()
            Toast.makeText(this,
                if (isVibrationEnabled) "Вибрация включена" else "Вибрация выключена",
                Toast.LENGTH_SHORT).show()
        }

        // Кнопка звука
        btnSound.setOnClickListener {
            playFeedback()
            isSoundEnabled = !isSoundEnabled
            settingsPrefs.edit().putBoolean("sound_enabled", isSoundEnabled).apply()
            updateButtonsText()
            Toast.makeText(this,
                if (isSoundEnabled) "Звук включен" else "Звук выключен",
                Toast.LENGTH_SHORT).show()
        }

        // Кнопка истории (ТОЛЬКО ОДИН РАЗ!)
        btnHistory.setOnClickListener {
            playFeedback()
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Кнопка настроек
        findViewById<Button>(R.id.button_settings).setOnClickListener {
            playFeedback()
            showSettingsMenu()
        }

        // Кнопки калькулятора
        setupNumberButtons()
        setupOperatorButtons()
        setupControlButtons()
    }
    private fun sendDirectNotification() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "calculator_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Calculator Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Уведомления")
            .setContentText("История вычислений открыта")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(999, notification)
        Toast.makeText(this, "Уведомление отправлено!", Toast.LENGTH_SHORT).show()
    }

    private fun sendTestNotification() {
        val intent = Intent(this, MainActivity::class.java)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Calculator Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Калькулятор")
            .setContentText("Это тестовое уведомление")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun updateButtonsText() {
        btnVibration.text = if (isVibrationEnabled) "📳 ВКЛ" else "📳 ВЫКЛ"
        btnSound.text = if (isSoundEnabled) "🔊 ВКЛ" else "🔊 ВЫКЛ"
    }

    private fun playFeedback() {
        if (isVibrationEnabled && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(50)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }

        if (isSoundEnabled && mediaPlayer != null) {
            try {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            } catch (e: Exception) {
                // Игнорируем
            }
        }
    }

    private fun setupNumberButtons() {
        val numberButtons = listOf(
            R.id.button_0, R.id.button_1, R.id.button_2, R.id.button_3,
            R.id.button_4, R.id.button_5, R.id.button_6, R.id.button_7,
            R.id.button_8, R.id.button_9
        )

        numberButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                playFeedback()
                val button = it as Button
                appendNumber(button.text.toString())
            }
        }

        findViewById<Button>(R.id.button_decimal).setOnClickListener {
            playFeedback()
            appendDecimal()
        }
    }

    private fun setupOperatorButtons() {
        val operatorButtons = listOf(
            R.id.button_add, R.id.button_subtract,
            R.id.button_multiply, R.id.button_divide
        )

        operatorButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                playFeedback()
                val button = it as Button
                lastExpression = currentInput.toString()
                setOperator(button.text.toString())
            }
        }
    }

    private fun setupControlButtons() {
        findViewById<Button>(R.id.button_equals).setOnClickListener {
            playFeedback()

            // Проверяем, что можно вычислять
            if (storedOperator == null) {
                Log.d("FIREBASE", "⚠️ Нет оператора, не сохраняем")
                return@setOnClickListener
            }

            // Сохраняем выражение ДО вычисления
            val firstNum = storedValue
            val secondNum = currentInput.toString()
            val operator = storedOperator ?: ""

            val expression = "$firstNum $operator $secondNum"

            // Вычисляем
            calculate()

            // Сохраняем в историю, если результат не ошибка
            val result = currentInput.toString()
            if (!result.contains("Ошибка") && !result.contains("Переполнение")) {
                saveToHistory(expression, result)
            }
        }

        findViewById<Button>(R.id.button_clear).setOnClickListener {
            playFeedback()
            clearAll()
        }
    }
    private fun appendNumber(number: String) {
        if (isNewInput) {
            currentInput.clear()
            currentInput.append(number)
            isNewInput = false
        } else {
            if (currentInput.toString() == "0") {
                currentInput.clear()
                currentInput.append(number)
            } else {
                currentInput.append(number)
            }
        }
        updateDisplay()
    }

    private fun appendDecimal() {
        if (isNewInput) {
            currentInput.clear()
            currentInput.append("0.")
            isNewInput = false
        } else if (!currentInput.contains(".")) {
            currentInput.append(".")
        }
        updateDisplay()
    }

    private fun setOperator(operator: String) {
        if (storedOperator != null && !isNewInput) {
            calculate()
        }

        firstOperand = currentInput.toString().toDouble()
        storedValue = currentInput.toString().toDouble()
        storedOperator = operator
        isNewInput = true
    }

    private fun calculate() {
        if (storedOperator == null) {
            return
        }

        val currentValue = try {
            currentInput.toString().toDouble()
        } catch (e: NumberFormatException) {
            displayTextView.text = "Ошибка ввода"
            clearAll()
            return
        }

        var result = 0.0

        try {
            result = when (storedOperator) {
                "+" -> storedValue + currentValue
                "-" -> storedValue - currentValue
                "*" -> storedValue * currentValue
                "/" -> {
                    if (Math.abs(currentValue) < 1e-10) {
                        throw ArithmeticException("Деление на ноль")
                    }
                    storedValue / currentValue
                }
                else -> currentValue
            }
        } catch (e: ArithmeticException) {
            displayTextView.text = "Ошибка: деление на 0"
            displayTextView.postDelayed({ clearAll() }, 2000)
            return
        }

        val formattedResult = formatResult(result)
        currentInput.clear()
        currentInput.append(formattedResult)
        updateDisplay()

        storedValue = result
        storedOperator = null
        isNewInput = true
    }
    private fun sendCalculationNotification(message: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_ONE_SHOT
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "calculator_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Calculator Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Новое вычисление")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun formatResult(result: Double): String {
        return try {
            if (result.isInfinite() || result.isNaN()) {
                "Переполнение"
            } else if (Math.abs(result % 1) < 1e-10) {
                result.toLong().toString()
            } else {
                String.format("%.8f", result)
                    .trimEnd('0')
                    .trimEnd('.')
                    .ifEmpty { "0" }
            }
        } catch (e: Exception) {
            "Ошибка формата"
        }
    }

    private fun clearAll() {
        currentInput.clear()
        currentInput.append("0")
        storedOperator = null
        storedValue = 0.0
        isNewInput = true
        updateDisplay()
    }

    private fun updateDisplay() {
        displayTextView.text = currentInput.toString()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("CURRENT_INPUT", currentInput.toString())
        outState.putString("STORED_OPERATOR", storedOperator)
        outState.putDouble("STORED_VALUE", storedValue)
        outState.putBoolean("IS_NEW_INPUT", isNewInput)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentInput = StringBuilder(savedInstanceState.getString("CURRENT_INPUT", "0"))
        storedOperator = savedInstanceState.getString("STORED_OPERATOR")
        storedValue = savedInstanceState.getDouble("STORED_VALUE", 0.0)
        isNewInput = savedInstanceState.getBoolean("IS_NEW_INPUT", true)
        updateDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}