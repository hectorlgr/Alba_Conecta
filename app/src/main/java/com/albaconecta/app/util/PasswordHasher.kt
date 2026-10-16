package com.albaconecta.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64

data class PasswordHashResult(
    val hash: String,
    val salt: String
)

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val ITERATIONS = 120000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    fun generate(password: String): PasswordHashResult {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        val hash = deriveHash(password, salt)
        return PasswordHashResult(
            hash = Base64.encode(hash),
            salt = Base64.encode(salt)
        )
    }

    fun verify(password: String, storedHash: String, storedSalt: String): Boolean {
        val hash: ByteArray
        val salt: ByteArray
        try {
            hash = Base64.decode(storedHash)
            salt = Base64.decode(storedSalt)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (hash.size != KEY_LENGTH_BITS / 8 || salt.size != SALT_LENGTH_BYTES) {
            return false
        }
        return MessageDigest.isEqual(deriveHash(password, salt), hash)
    }

    private fun deriveHash(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        try {
            val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } finally {
            chars.fill('\u0000')
        }
    }
}
