package com.duduezn.budgettracker.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stocke localement le code PIN (jamais en clair) et les préférences de verrouillage.
 * Aucune donnée ne quitte l'appareil : ni réseau, ni sauvegarde cloud.
 */
class AppLockManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun isPinSet(): Boolean = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun pinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, DEFAULT_PIN_LENGTH)

    /** Enregistre un nouveau code PIN (4 à 6 chiffres). */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.hexToBytes() ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), hash(pin, salt).toByteArray())
    }

    fun isBiometricEnabled(): Boolean =
        isPinSet() && prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled && isPinSet()).apply()
    }

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

    companion object {
        private const val PREFS_NAME = "app_lock"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_BIOMETRIC = "biometric_lock"

        private const val MIN_PIN_LENGTH = 4
        private const val MAX_PIN_LENGTH = 6
        private const val DEFAULT_PIN_LENGTH = 4
        private const val SALT_BYTES = 16

        /** La biométrie n'est proposée qu'à partir d'Android 9 et si le matériel existe. */
        fun isBiometricSupported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            val pm = context.packageManager
            return pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    pm.hasSystemFeature(PackageManager.FEATURE_FACE))
        }

        private fun hash(pin: String, salt: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.update(pin.toByteArray(Charsets.UTF_8))
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02x".format(it.toInt() and 0xff) }

        private fun String.hexToBytes(): ByteArray =
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
