package com.qoder.stealthflash.vault

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sealed local store for the private area. Everything stays on the device; no installed-app
 * data is ever sent anywhere, and there is no networking code in this project at all.
 *
 * Backed by [EncryptedSharedPreferences] (AES-256-GCM under an Android Keystore master key).
 * Only the PIN digest, the package list and lock counters live here.
 */
class VaultStore(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    data class Snapshot(
        val pinConfigured: Boolean,
        val biometricEnabled: Boolean,
        val lockedUntilMillis: Long,
        val failedAttempts: Int,
        val privatePackages: List<String>,
        val addedOrder: Map<String, Long>,
    )

    suspend fun snapshot(): Snapshot = withContext(io) {
        Snapshot(
            pinConfigured = prefs.contains(KEY_PIN_HASH),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
            lockedUntilMillis = prefs.getLong(KEY_LOCKED_UNTIL, 0L),
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            privatePackages = readPackages(),
            addedOrder = readAddedOrder(),
        )
    }

    /** @return false when the PIN is too weak to store. */
    suspend fun setPin(pin: String): Boolean {
        if (!PinHasher.isValid(pin)) return false
        val sealed = PinHasher.hash(pin)
        withContext(io) {
            prefs.edit {
                putString(KEY_PIN_SALT, sealed.saltBase64)
                putString(KEY_PIN_HASH, sealed.hashBase64)
                remove(KEY_FAILED_ATTEMPTS)
                remove(KEY_LOCKED_UNTIL)
            }
        }
        return true
    }

    /** Verifies off the main thread: PBKDF2 at 120k iterations is deliberately slow. */
    suspend fun verifyPin(pin: String): Boolean = withContext(io) {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return@withContext false
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return@withContext false
        PinHasher.matches(pin, PinHasher.Sealed(salt, hash))
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = withContext(io) {
        prefs.edit { putBoolean(KEY_BIOMETRIC, enabled) }
    }

    /**
     * Applies exponential backoff after repeated wrong PINs.
     * @return the wait the caller must impose, in millis; 0 when still allowed to retry.
     */
    suspend fun registerFailedAttempt(): Long = withContext(io) {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val penalty = if (attempts >= MAX_FREE_ATTEMPTS) {
            BACKOFF_BASE_MILLIS * (1L shl (attempts - MAX_FREE_ATTEMPTS).coerceAtMost(5))
        } else {
            0L
        }
        val lockedUntil = if (penalty > 0) System.currentTimeMillis() + penalty else 0L
        prefs.edit {
            putInt(KEY_FAILED_ATTEMPTS, attempts)
            putLong(KEY_LOCKED_UNTIL, lockedUntil)
        }
        penalty
    }

    suspend fun clearFailedAttempts() = withContext(io) {
        prefs.edit {
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKED_UNTIL)
        }
    }

    suspend fun remainingLockoutMillis(): Long = withContext(io) {
        (prefs.getLong(KEY_LOCKED_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    suspend fun contains(pkg: String): Boolean = withContext(io) { pkg in readPackages() }

    /** @return true when this call changed membership. */
    suspend fun setPrivate(pkg: String, private: Boolean): Boolean = withContext(io) {
        val current = readPackages().toMutableSet()
        val changed = if (private) current.add(pkg) else current.remove(pkg)
        if (changed) {
            writePackages(current.toList())
            val order = readAddedOrder().toMutableMap()
            if (private) order[pkg] = System.currentTimeMillis() else order.remove(pkg)
            writeAddedOrder(order)
        }
        changed
    }

    /** Wipes the private area completely, including the master-keyed file. */
    suspend fun reset() = withContext(io) {
        appContext.deleteSharedPreferences(FILE_NAME)
    }

    private fun readPackages(): List<String> {
        val raw = prefs.getString(KEY_PACKAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (i in 0 until array.length()) add(array.getString(i)) }
        }.getOrDefault(emptyList())
    }

    private fun writePackages(packages: List<String>) {
        prefs.edit { putString(KEY_PACKAGES, JSONArray(packages).toString()) }
    }

    /**
     * Order of the private list, persisted as an "added at" stamp per package so the vault can
     * offer a stable "recently added" sort without touching MediaStore or any server.
     */
    private fun readAddedOrder(): Map<String, Long> {
        val raw = prefs.getString(KEY_ADDED_ORDER, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    private fun writeAddedOrder(order: Map<String, Long>) {
        val obj = JSONObject()
        order.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit { putString(KEY_ADDED_ORDER, obj.toString()) }
    }

    companion object {
        private const val FILE_NAME = "stealth_vault.xml"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_ADDED_ORDER = "added_order"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val MAX_FREE_ATTEMPTS = 5
        private const val BACKOFF_BASE_MILLIS = 5_000L
    }
}
