package com.example.calculator

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var displayTextView: TextView
    private var currentInput = StringBuilder("0")
    private var storedOperator: String? = null
    private var storedValue: Double = 0.0
    private var isNewInput = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        displayTextView = findViewById(R.id.displayTextView)
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

            }
        }

        // Точка
        findViewById<Button>(R.id.button_decimal).setOnClickListener {

        }
    }

    private fun setupOperatorButtons() {
        val operatorButtons = listOf(
            R.id.button_add, R.id.button_subtract,
            R.id.button_multiply, R.id.button_divide
        )

        operatorButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {

            }
        }
    }

    private fun setupControlButtons() {
        // Равно
        findViewById<Button>(R.id.button_equals).setOnClickListener {

        }

        // Очистка
        findViewById<Button>(R.id.button_clear).setOnClickListener {

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
    }

    private fun appendDecimal() {
        if (isNewInput) {
            currentInput.clear()
            currentInput.append("0.")
            isNewInput = false
        } else if (!currentInput.contains(".")) {
            currentInput.append(".")
        }
    }

    private fun setOperator(operator: String) {
        if (storedOperator != null && !isNewInput) {
        }

        storedValue = currentInput.toString().toDouble()
        storedOperator = operator
        isNewInput = true
    }

    private fun clearAll() {

    }

    private fun updateDisplay() {

    }
}