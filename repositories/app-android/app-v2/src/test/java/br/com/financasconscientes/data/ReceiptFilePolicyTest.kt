package br.com.financasconscientes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFilePolicyTest {
    @Test
    fun `recognizes supported file signatures`() {
        assertEquals("application/pdf", ReceiptFilePolicy.detectMimeType("%PDF-1.7".toByteArray()))
        assertEquals(
            "image/png",
            ReceiptFilePolicy.detectMimeType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        )
        assertEquals("image/jpeg", ReceiptFilePolicy.detectMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals("image/webp", ReceiptFilePolicy.detectMimeType("RIFF0000WEBP".toByteArray()))
    }

    @Test
    fun `rejects empty truncated and unsupported signatures`() {
        assertEquals(null, ReceiptFilePolicy.detectMimeType(byteArrayOf()))
        assertEquals(null, ReceiptFilePolicy.detectMimeType("%PD".toByteArray()))
        assertEquals(null, ReceiptFilePolicy.detectMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertEquals(null, ReceiptFilePolicy.detectMimeType("RIFF00000000".toByteArray()))
        assertEquals(null, ReceiptFilePolicy.detectMimeType("text/plain".toByteArray()))
    }

    @Test
    fun `allows only nonempty attachments up to ten megabytes`() {
        assertFalse(ReceiptFilePolicy.isAllowedSize(0))
        assertFalse(ReceiptFilePolicy.isAllowedSize(-1))
        assertTrue(ReceiptFilePolicy.isAllowedSize(1))
        assertTrue(ReceiptFilePolicy.isAllowedSize(ReceiptFilePolicy.MAX_RECEIPT_BYTES))
        assertFalse(ReceiptFilePolicy.isAllowedSize(ReceiptFilePolicy.MAX_RECEIPT_BYTES + 1))
    }
}
