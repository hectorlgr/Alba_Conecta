package com.albaconecta.app

import com.albaconecta.app.util.PasswordHasher
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun generatedCredentialsVerifyOriginalPassword() {
        val result = PasswordHasher.generate("Clave123")

        assertTrue(PasswordHasher.verify("Clave123", result.hash, result.salt))
        assertEquals(32, Base64.decode(result.hash).size)
        assertEquals(16, Base64.decode(result.salt).size)
    }

    @Test
    fun differentPasswordDoesNotVerify() {
        val result = PasswordHasher.generate("Clave123")

        assertFalse(PasswordHasher.verify("OtraClave456", result.hash, result.salt))
    }

    @Test
    fun repeatedGenerationUsesDifferentSaltsAndHashes() {
        val first = PasswordHasher.generate("Clave123")
        val second = PasswordHasher.generate("Clave123")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
    }

    @Test
    fun passwordCaseAndWhitespaceArePreserved() {
        val result = PasswordHasher.generate("Clave123")

        assertFalse(PasswordHasher.verify("clave123", result.hash, result.salt))
        assertFalse(PasswordHasher.verify(" Clave123 ", result.hash, result.salt))
        val spaced = PasswordHasher.generate(" Clave123 ")
        assertTrue(PasswordHasher.verify(" Clave123 ", spaced.hash, spaced.salt))
        assertFalse(PasswordHasher.verify("Clave123", spaced.hash, spaced.salt))
    }

    @Test
    fun malformedCredentialsDoNotVerify() {
        val result = PasswordHasher.generate("Clave123")

        assertFalse(PasswordHasher.verify("Clave123", "!", result.salt))
        assertFalse(PasswordHasher.verify("Clave123", result.hash, "!"))
        assertFalse(PasswordHasher.verify("Clave123", "", result.salt))
        assertFalse(PasswordHasher.verify("Clave123", result.hash, ""))
    }
}
