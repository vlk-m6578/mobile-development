package com.example.calculator

import android.content.Context
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
class PassCodeActivity : AppCompatActivity() {

    private lateinit var etPassCode: EditText
    private lateinit var etConfirmPassCode: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnBiometric: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvForgot: TextView

    private lateinit var biometricHelper: BiometricHelper
    private var cancellationSignal: CancellationSignal? = null

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SETUP = "setup"
        const val MODE_VERIFY = "verify"
        const val MODE_CHANGE = "change"
        const val PREFS_NAME = "biometric_prefs"
        const val KEY_PASSCODE = "user_passcode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode)

        initViews()
        biometricHelper = BiometricHelper(this)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VERIFY
        setupUIForMode(mode)
    }

    private fun initViews() {
        etPassCode = findViewById(R.id.et_passcode)
        etConfirmPassCode = findViewById(R.id.et_confirm_passcode)
        btnSubmit = findViewById(R.id.btn_submit)
        btnBiometric = findViewById(R.id.btn_biometric)
        tvTitle = findViewById(R.id.tv_title)
        tvDescription = findViewById(R.id.tv_description)
        tvForgot = findViewById(R.id.tv_forgot)
    }

    private fun setupUIForMode(mode: String) {
        when (mode) {
            MODE_SETUP -> {
                tvTitle.text = "Установка PIN-кода"
                tvDescription.text = "Введите PIN-код для защиты приложения"
                etConfirmPassCode.visibility = TextView.VISIBLE
                btnSubmit.text = "Сохранить"
                btnBiometric.visibility = TextView.GONE
                tvForgot.visibility = TextView.GONE
            }

            MODE_CHANGE -> {
                tvTitle.text = "Смена PIN-кода"
                tvDescription.text = "Введите старый PIN-код"
                etConfirmPassCode.visibility = TextView.GONE
                btnSubmit.text = "Далее"
                btnBiometric.visibility = TextView.GONE
                tvForgot.visibility = TextView.VISIBLE
                tvForgot.setOnClickListener { showForgotDialog() }
            }

            else -> { // MODE_VERIFY
                tvTitle.text = "Введите PIN-код"
                tvDescription.text = "Приложение заблокировано"
                etConfirmPassCode.visibility = TextView.GONE
                btnSubmit.text = "Войти"
                tvForgot.visibility = TextView.VISIBLE
                tvForgot.setOnClickListener { showForgotDialog() }

                // ПОДКЛЮЧАЕМ БИОМЕТРИЮ
                if (biometricHelper.isBiometricSupported() && biometricHelper.isPassCodeSet()) {
                    btnBiometric.visibility = TextView.VISIBLE
                    btnBiometric.setOnClickListener { startFingerprintAuth() }
                } else {
                    btnBiometric.visibility = TextView.GONE
                }
            }
        }

        btnSubmit.setOnClickListener { handleSubmit(mode) }
    }

    private fun handleSubmit(mode: String) {
        val passCode = etPassCode.text.toString()

        when (mode) {
            MODE_SETUP -> {
                val confirmPassCode = etConfirmPassCode.text.toString()
                if (passCode.length < 4) {
                    Toast.makeText(this, "PIN должен быть не менее 4 символов", Toast.LENGTH_SHORT).show()
                    return
                }
                if (passCode != confirmPassCode) {
                    Toast.makeText(this, "PIN-коды не совпадают", Toast.LENGTH_SHORT).show()
                    return
                }
                // Сохраняем с шифрованием через BiometricHelper
                if (biometricHelper.savePassCode(passCode)) {
                    Toast.makeText(this, "PIN-код установлен", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                }
            }
            MODE_CHANGE -> {
                if (biometricHelper.validatePassCode(passCode)) {
                    showChangeDialog()
                } else {
                    Toast.makeText(this, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                if (biometricHelper.validatePassCode(passCode)) {
                    Log.d("PIN_DEBUG", "PIN верный, вызываем setResult и finish")
                    Toast.makeText(this, "Доступ разрешен", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Log.d("PIN_DEBUG", "PIN неверный")
                    Toast.makeText(this, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                    etPassCode.text.clear()
                }
            }
        }
    }

    private fun showChangeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_passcode, null)
        val etNewPass = dialogView.findViewById<EditText>(R.id.et_new_passcode)
        val etConfirmNew = dialogView.findViewById<EditText>(R.id.et_confirm_new_passcode)

        AlertDialog.Builder(this)
            .setTitle("Новый PIN-код")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newPass = etNewPass.text.toString()
                val confirmNew = etConfirmNew.text.toString()

                if (newPass.length < 4) {
                    Toast.makeText(this, "PIN должен быть не менее 4 символов", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmNew) {
                    Toast.makeText(this, "PIN-коды не совпадают", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (biometricHelper.savePassCode(newPass)) {
                    Toast.makeText(this, "PIN-код изменен", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showForgotDialog() {
        AlertDialog.Builder(this)
            .setTitle("Сброс PIN-кода")
            .setMessage("Вы уверены? Это удалит текущий PIN-код")
            .setPositiveButton("Да, сбросить") { _, _ ->
                if (biometricHelper.resetPassCode()) {
                    Toast.makeText(this, "PIN-код сброшен", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startFingerprintAuth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Биометрия не поддерживается на этой версии", Toast.LENGTH_SHORT).show()
            return
        }

        val fingerprintManager = getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager

        if (!fingerprintManager.isHardwareDetected) {
            Toast.makeText(this, "Сканер отпечатков не обнаружен", Toast.LENGTH_SHORT).show()
            return
        }

        if (!fingerprintManager.hasEnrolledFingerprints()) {
            Toast.makeText(this, "Добавьте отпечаток пальца в настройках", Toast.LENGTH_SHORT).show()
            return
        }

        cancellationSignal = CancellationSignal()

        val callback = object : FingerprintManager.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
                runOnUiThread {
                    Toast.makeText(this@PassCodeActivity, "Отпечаток подтвержден", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                runOnUiThread {
                    Toast.makeText(this@PassCodeActivity, "Ошибка: $errString", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                runOnUiThread {
                    Toast.makeText(this@PassCodeActivity, "Отпечаток не распознан", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fingerprintManager.authenticate(null, cancellationSignal, 0, callback, null)
    }
    override fun onDestroy() {
        super.onDestroy()
        cancellationSignal?.cancel()
    }
}