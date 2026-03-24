package com.example.calculator

import android.content.Context
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import android.util.Log

class BiometricHelper(private val context: Context) {

    private val PREFS_NAME = "biometric_prefs"
    private val KEY_PASSCODE = "user_passcode"
    private val KEY_IV = "encryption_iv"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private val KEY_NAME = "calculator_biometric_key"
    private val ANDROID_KEYSTORE = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBiometricSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val fingerprintManager = context.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
            fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints()
        } else false
    }
    fun isPassCodeSet(): Boolean {
        return prefs.contains(KEY_PASSCODE)
    }

    fun savePassCode(passCode: String): Boolean {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedBytes = cipher.doFinal(passCode.toByteArray())
            val iv = cipher.iv

            // Сохраняем IV и зашифрованные данные отдельно
            val encryptedString = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.DEFAULT)
            val ivString = android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT)

            prefs.edit()
                .putString(KEY_PASSCODE, encryptedString)
                .putString(KEY_IV, ivString)
                .apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun validatePassCode(inputPassCode: String): Boolean {
        Log.d("PIN_DEBUG", "validatePassCode начинается, введено: $inputPassCode")

        val encrypted = prefs.getString(KEY_PASSCODE, null) ?: return false.also {
            Log.d("PIN_DEBUG", "Нет сохраненного PIN")
        }
        val ivString = prefs.getString(KEY_IV, null) ?: return false.also {
            Log.d("PIN_DEBUG", "Нет сохраненного IV")
        }

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            val encryptedBytes = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
            val iv = android.util.Base64.decode(ivString, android.util.Base64.DEFAULT)

            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decrypted = String(cipher.doFinal(encryptedBytes))
            Log.d("PIN_DEBUG", "Расшифровано: $decrypted")

            val result = decrypted == inputPassCode
            Log.d("PIN_DEBUG", "Результат сравнения: $result")
            result
        } catch (e: Exception) {
            Log.e("PIN_DEBUG", "Ошибка дешифровки", e)
            false
        }
    }

    fun resetPassCode(): Boolean {
        prefs.edit().remove(KEY_PASSCODE).remove(KEY_IV).apply()
        return true
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existingKey = ANDROID_KEYSTORE.getKey(KEY_NAME, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
}