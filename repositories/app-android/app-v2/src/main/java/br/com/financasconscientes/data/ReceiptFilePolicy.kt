package br.com.financasconscientes.data

/** Format and size checks for user-selected attachments; this does not validate document semantics. */
internal object ReceiptFilePolicy {
    const val MAX_RECEIPT_BYTES = 10L * 1024 * 1024
    const val SIGNATURE_BYTES = 16

    fun isAllowedSize(sizeBytes: Long): Boolean = sizeBytes in 1..MAX_RECEIPT_BYTES

    fun detectMimeType(signature: ByteArray): String? {
        if (signature.startsWith(PDF_SIGNATURE)) return "application/pdf"
        if (signature.startsWith(PNG_SIGNATURE)) return "image/png"
        if (signature.size >= 3 && signature[0] == 0xFF.toByte()
            && signature[1] == 0xD8.toByte() && signature[2] == 0xFF.toByte()) return "image/jpeg"
        if (signature.size >= 12 && signature.asciiEquals(0, "RIFF") && signature.asciiEquals(8, "WEBP")) {
            return "image/webp"
        }
        return null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.asciiEquals(offset: Int, value: String): Boolean =
        size >= offset + value.length && value.indices.all { this[offset + it].toInt().toChar() == value[it] }

    private val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
