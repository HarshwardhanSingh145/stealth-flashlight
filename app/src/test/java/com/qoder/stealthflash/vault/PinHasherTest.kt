package com.qoder.stealthflash.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `a sealed pin verifies`() {
        val sealed = PinHasher.hash("482913")
        assertTrue(PinHasher.matches("482913", sealed))
        assertFalse(PinHasher.matches("482914", sealed))
    }

    @Test
    fun `the same pin hashes differently each time because of the salt`() {
        val a = PinHasher.hash("482913")
        val b = PinHasher.hash("482913")
        assertNotEquals(a.hashBase64, b.hashBase64)
        assertTrue(PinHasher.matches("482913", a))
        assertTrue(PinHasher.matches("482913", b))
    }

    @Test
    fun `length bounds are enforced`() {
        assertFalse(PinHasher.isValid("123"))
        assertTrue(PinHasher.isValid("1234"))
        assertTrue(PinHasher.isValid("123456789012"))
        assertFalse(PinHasher.isValid("1234567890123"))
    }

    @Test
    fun `non digits are rejected`() {
        assertFalse(PinHasher.isValid("12a4"))
        assertFalse(PinHasher.isValid("12 45"))
        assertFalse(PinHasher.isValid(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hashing a weak pin throws`() {
        PinHasher.hash("12a4")
    }

    @Test
    fun `a corrupt seal never authenticates`() {
        val sealed = PinHasher.hash("482913")
        assertFalse(PinHasher.matches("482913", sealed.copy(hashBase64 = "not-base64!!")))
        assertFalse(PinHasher.matches("482913", sealed.copy(saltBase64 = "###")))
    }

    @Test
    fun `a truncated digest of the same length still fails`() {
        val sealed = PinHasher.hash("482913")
        val other = PinHasher.hash("999999")
        assertFalse(PinHasher.matches("482913", other))
        assertEquals(sealed.hashBase64.length, other.hashBase64.length)
    }
}
