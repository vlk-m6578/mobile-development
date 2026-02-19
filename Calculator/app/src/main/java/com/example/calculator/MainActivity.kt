package com.example.calculator

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var displayTextView: TextView
    private lateinit var btnLight: Button
    private lateinit var btnDark: Button
    private lateinit var btnVibration: Button

    // Vibrator
    private lateinit var vibrator: Vibrator
    private var isVibrationEnabled = true

    // State Variables
    private var currentInput = StringBuilder("0")
    private var storedOperator: String? = null
    private var storedValue: Double = 0.0
    private var isNewInput = true

    // SharedPreferences
    private lateinit var settingsPrefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Загружаем настройки
        settingsPrefs = getSharedPreferences("calculator_settings", MODE_PRIVATE)

        // Применяем тему
        applyTheme()

        setContentView(R.layout.activity_main)

        // Инициализация
        initViews()
        initVibrator()
        loadSettings()
        setupButtons()
        updateVibrationButtonText()

        if (savedInstanceState == null) {
            displayTextView.text = "0"
        }
    }

    private fun initViews() {
        displayTextView = findViewById(R.id.displayTextView)
        btnLight = findViewById(R.id.button_light_theme)
        btnDark = findViewById(R.id.button_dark_theme)
        btnVibration = findViewById(R.id.button_vibration)
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

    private fun loadSettings() {
        isVibrationEnabled = settingsPrefs.getBoolean("vibration_enabled", true)
    }

    private fun applyTheme() {
        val themeMode = settingsPrefs.getString("theme_mode", "system")
        when (themeMode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun setupButtons() {
        // Кнопки тем
        btnLight.setOnClickListener {
            vibrate()
            settingsPrefs.edit().putString("theme_mode", "light").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            recreate()
            Toast.makeText(this, "Светлая тема", Toast.LENGTH_SHORT).show()
        }

        btnDark.setOnClickListener {
            vibrate()
            settingsPrefs.edit().putString("theme_mode", "dark").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            recreate()
            Toast.makeText(this, "Темная тема", Toast.LENGTH_SHORT).show()
        }

        // Кнопка вибрации
        btnVibration.setOnClickListener {
            vibrate()
            isVibrationEnabled = !isVibrationEnabled
            settingsPrefs.edit().putBoolean("vibration_enabled", isVibrationEnabled).apply()
            updateVibrationButtonText()
            Toast.makeText(this,
                if (isVibrationEnabled) "Вибрация включена" else "Вибрация выключена",
                Toast.LENGTH_SHORT).show()
        }

        // Кнопки калькулятора
        setupNumberButtons()
        setupOperatorButtons()
        setupControlButtons()
    }

    private fun updateVibrationButtonText() {
        btnVibration.text = if (isVibrationEnabled) "📳 ВКЛ" else "📳 ВЫКЛ"
    }

    private fun vibrate() {
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
    }

    private fun setupNumberButtons() {
        val numberButtons = listOf(
            R.id.button_0, R.id.button_1, R.id.button_2, R.id.button_3,
            R.id.button_4, R.id.button_5, R.id.button_6, R.id.button_7,
            R.id.button_8, R.id.button_9
        )

        numberButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                vibrate()
                val button = it as Button
                appendNumber(button.text.toString())
            }
        }

        findViewById<Button>(R.id.button_decimal).setOnClickListener {
            vibrate()
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
                vibrate()
                val button = it as Button
                setOperator(button.text.toString())
            }
        }
    }

    private fun setupControlButtons() {
        findViewById<Button>(R.id.button_equals).setOnClickListener {
            vibrate()
            calculate()
        }

        findViewById<Button>(R.id.button_clear).setOnClickListener {
            vibrate()
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
        storedValue = currentInput.toString().toDouble()
        storedOperator = operator
        isNewInput = true
    }

    private fun calculate() {
        if (storedOperator == null || isNewInput) {
            return
        }

        val currentValue = currentInput.toString().toDouble()
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
        } catch (e: NumberFormatException) {
            displayTextView.text = "Ошибка ввода"
            clearAll()
            return
        }

        val formattedResult = formatResult(result)
        currentInput.clear()
        currentInput.append(formattedResult)
        updateDisplay()
        storedValue = result
        storedOperator = null
        isNewInput = true
        vibrate()
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
}