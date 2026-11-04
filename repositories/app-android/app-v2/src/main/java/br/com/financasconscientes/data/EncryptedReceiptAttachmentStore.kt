package br.com.financasconscientes.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ReceiptAttachmentMetadata(
    val id: String,
    val mimeType: String,
    val sizeBytes: Long,
)

data class DecryptedReceiptAttachment(
    val bytes: ByteArray,
    val mimeType: String,
)

/** AES-GCM encrypted receipt files in private, excluded-from-backup app storage. */
class EncryptedReceiptAttachmentStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, DIRECTORY_NAME)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private val verifiedBlobs = mutableMapOf<String, FileStamp>()

    fun copyAndEncrypt(uri: Uri): ReceiptAttachmentMetadata {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw ReceiptAttachmentException("Não foi possível abrir o comprovante selecionado.")
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = fileFor(id)
        val temporary = File(directory, "$id.tmp")
        var size = 0L
        val prefix = ByteArrayOutputStream(16)
        try {
            input.use { source ->
                val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, secretKey())
                }
                FileOutputStream(temporary).use { fileOutput ->
                    fileOutput.write(cipher.iv)
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        size += read
                        if (size > ReceiptFilePolicy.MAX_RECEIPT_BYTES) {
                            throw ReceiptAttachmentException("O comprovante excede o limite de 10 MB.")
                        }
                        val prefixBytes = minOf(read, ReceiptFilePolicy.SIGNATURE_BYTES - prefix.size())
                        if (prefixBytes > 0) prefix.write(buffer, 0, prefixBytes)
                        val encryptedBlock = cipher.update(buffer, 0, read)
                        if (encryptedBlock != null) fileOutput.write(encryptedBlock)
                    }
                    fileOutput.write(cipher.doFinal())
                    fileOutput.fd.sync()
                }
            }
            if (!ReceiptFilePolicy.isAllowedSize(size)) throw ReceiptAttachmentException("O comprovante está vazio.")
            val mimeType = ReceiptFilePolicy.detectMimeType(prefix.toByteArray())
                ?: throw ReceiptAttachmentException("Formato não suportado. Use PDF, PNG, JPEG ou WebP.")
            if (!temporary.renameTo(target)) {
                throw ReceiptAttachmentException("Não foi possível guardar o comprovante cifrado.")
            }
            synchronized(verifiedBlobs) { verifiedBlobs[id] = target.stamp() }
            return ReceiptAttachmentMetadata(id, mimeType, size)
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            if (error is ReceiptAttachmentException) throw error
            throw ReceiptAttachmentException("Falha ao cifrar o comprovante selecionado.", error)
        }
    }

    /** Authenticates the GCM tag before returning plaintext to the in-app viewer. */
    fun decrypt(id: String): DecryptedReceiptAttachment {
        val file = fileFor(id)
        if (!file.isFile || file.length() !in (IV_LENGTH + GCM_TAG_BYTES)..(ReceiptFilePolicy.MAX_RECEIPT_BYTES + IV_LENGTH + GCM_TAG_BYTES)) {
            throw ReceiptAttachmentException("O comprovante cifrado não está disponível.")
        }
        val encrypted = file.readBytes()
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        val plaintext = runCatching { cipher.doFinal(encrypted, IV_LENGTH, encrypted.size - IV_LENGTH) }
            .getOrElse { throw ReceiptAttachmentException("A integridade do comprovante não pôde ser verificada.", it) }
        val mimeType = ReceiptFilePolicy.detectMimeType(
            plaintext.copyOfRange(0, minOf(plaintext.size, ReceiptFilePolicy.SIGNATURE_BYTES)),
        )
            ?: throw ReceiptAttachmentException("O comprovante cifrado não contém um formato reconhecido.")
        return DecryptedReceiptAttachment(plaintext, mimeType)
    }

    fun hasStoredBlob(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        val file = runCatching { fileFor(id) }.getOrNull() ?: return false
        if (!file.isFile || file.length() !in (IV_LENGTH + GCM_TAG_BYTES)..(ReceiptFilePolicy.MAX_RECEIPT_BYTES + IV_LENGTH + GCM_TAG_BYTES)) {
            synchronized(verifiedBlobs) { verifiedBlobs.remove(id) }
            return false
        }
        val stamp = file.stamp()
        if (synchronized(verifiedBlobs) { verifiedBlobs[id] == stamp }) return true
        val verified = verifyEncryptedBlob(file)
        synchronized(verifiedBlobs) {
            if (verified) verifiedBlobs[id] = stamp else verifiedBlobs.remove(id)
        }
        return verified
    }

    /** Deletes only UUID-named blobs that are not referenced by a successfully loaded finance state. */
    fun deleteUnreferenced(referencedIds: Set<String>) {
        val referenced = referencedIds.filter(ATTACHMENT_ID::matches).toSet()
        directory.listFiles()?.forEach { file ->
            val name = file.name
            when {
                name.endsWith(".enc") -> {
                    val id = name.removeSuffix(".enc")
                    if (ATTACHMENT_ID.matches(id) && id !in referenced) delete(id)
                }
                name.endsWith(".tmp") -> {
                    val id = name.removeSuffix(".tmp")
                    if (ATTACHMENT_ID.matches(id)) file.delete()
                }
            }
        }
    }

    fun delete(id: String?) {
        if (id.isNullOrBlank()) return
        synchronized(verifiedBlobs) { verifiedBlobs.remove(id) }
        runCatching { fileFor(id).delete() }
    }

    fun deleteAll() {
        synchronized(verifiedBlobs) { verifiedBlobs.clear() }
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun fileFor(id: String): File {
        require(ATTACHMENT_ID.matches(id)) { "Invalid attachment ID" }
        return File(directory, "$id.enc")
    }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** Streams through the GCM tag without retaining plaintext, then validates the file signature. */
    private fun verifyEncryptedBlob(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val iv = ByteArray(IV_LENGTH)
            var offset = 0
            while (offset < iv.size) {
                val read = input.read(iv, offset, iv.size - offset)
                if (read < 0) return@runCatching false
                offset += read
            }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            val prefix = ByteArrayOutputStream(ReceiptFilePolicy.SIGNATURE_BYTES)
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                cipher.update(buffer, 0, read)?.let { appendPrefix(prefix, it) }
            }
            cipher.doFinal().let { appendPrefix(prefix, it) }
            ReceiptFilePolicy.detectMimeType(prefix.toByteArray()) != null
        }
    }.getOrDefault(false)

    private fun appendPrefix(prefix: ByteArrayOutputStream, bytes: ByteArray) {
        val remaining = ReceiptFilePolicy.SIGNATURE_BYTES - prefix.size()
        if (remaining > 0) prefix.write(bytes, 0, minOf(remaining, bytes.size))
    }

    private fun File.stamp(): FileStamp = FileStamp(length(), lastModified())

    private companion object {
        const val DIRECTORY_NAME = "receipt-attachments"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "financas-conscientes-receipt-attachments-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val STREAM_BUFFER_BYTES = 8 * 1024
        val ATTACHMENT_ID = Regex("[0-9a-fA-F-]{36}")
    }

    private data class FileStamp(val length: Long, val modifiedAt: Long)
}

class ReceiptAttachmentException(message: String, cause: Throwable? = null) : Exception(message, cause)
