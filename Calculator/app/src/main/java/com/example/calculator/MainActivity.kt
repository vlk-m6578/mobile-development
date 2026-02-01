package com.example.calculator

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var displayTextView: TextView

    // State Variables
    private var currentInput = StringBuilder("0")
    private var storedOperator: String? = null
    private var storedValue: Double = 0.0
    private var isNewInput = true

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        displayTextView = findViewById(R.id.displayTextView)

        if (savedInstanceState == null) {
            displayTextView.text = "0"
        }

        // Настройка обработчиков для разных типов кнопок
        setupNumberButtons() // Цифры 0-9 и точка
        setupOperatorButtons() // Арифметические операторы
        setupControlButtons() // = и С
    }

    private fun setupNumberButtons() {
        val numberButtons = listOf(
            R.id.button_0, R.id.button_1, R.id.button_2, R.id.button_3,
            R.id.button_4, R.id.button_5, R.id.button_6, R.id.button_7,
            R.id.button_8, R.id.button_9
        )

        // Цифры
        numberButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                val button = it as Button
                appendNumber(button.text.toString()) // текст кнопки -> логика ввода
            }
        }

        // Точка
        findViewById<Button>(R.id.button_decimal).setOnClickListener {
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
                val button = it as Button
                setOperator(button.text.toString())
            }
        }
    }

    private fun setupControlButtons() {
        // Равно
        findViewById<Button>(R.id.button_equals).setOnClickListener {
            calculate()
        }

        // Очистка
        findViewById<Button>(R.id.button_clear).setOnClickListener {
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
            displayTextView.postDelayed({
                clearAll()
            }, 2000)
            return
        } catch (e: NumberFormatException) {
            displayTextView.text = "Ошибка ввода"
            clearAll()
            return
        } catch (e: Exception) {
            displayTextView.text = "Ошибка вычисления"
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
}