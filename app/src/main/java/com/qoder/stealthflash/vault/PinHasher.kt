package com.qoder.stealthflash.vault

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PINs are never stored. Only a salted PBKDF2 digest is kept, so a stolen preferences file
 * yields no usable secret without an offline brute-force against a 6+ digit space.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val MAX_PIN_LENGTH = 12

    data class Sealed(val saltBase64: String, val hashBase64: String)

    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

    const val MIN_LENGTH = 4

    fun hash(pin: String, salt: ByteArray = newSalt()): Sealed {
        require(isValid(pin)) { "PIN must be $MIN_LENGTH..$MAX_PIN_LENGTH digits" }
        return Sealed(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(derive(pin, salt)),
        )
    }

    /** Constant-time comparison so a wrong PIN cannot be probed one digit at a time. */
    fun matches(pin: String, sealed: Sealed): Boolean {
        val expected = runCatching { Base64.getDecoder().decode(sealed.hashBase64) }.getOrNull()
            ?: return false
        val salt = runCatching { Base64.getDecoder().decode(sealed.saltBase64) }.getOrNull()
            ?: return false
        if (!isValid(pin)) return false
        val actual = derive(pin, salt)
        return constantTimeEquals(actual, expected)
    }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
