package br.com.financasconscientes.diagnostic.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import br.com.financasconscientes.diagnostic.DiagnosticEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only bounded, encrypted diagnostic metadata in the app's no-backup directory. */
class EncryptedEventStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun load(): List<DiagnosticEvent> = synchronized(processLock) {
        if (!atomicFile.baseFile.exists()) return@synchronized emptyList()
        val allEvents = readEvents()
        val retained = allEvents.retained()
        if (retained.size != allEvents.size) writeEvents(retained)
        retained.sortedByDescending { it.postedAtEpochMillis }
    }

    fun append(event: DiagnosticEvent) = synchronized(processLock) {
        val existing = if (atomicFile.baseFile.exists()) readEvents() else emptyList()
        writeEvents((existing + event).retained())
    }

    fun clear() = synchronized(processLock) {
        atomicFile.delete()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun readEvents(): List<DiagnosticEvent> {
        val encrypted = atomicFile.openRead().use { it.readBytes() }
        require(encrypted.size > IV_LENGTH) { "Arquivo de diagnóstico inválido" }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val payload = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val storedKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Chave de diagnóstico indisponível")
        cipher.init(Cipher.DECRYPT_MODE, storedKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val json = cipher.doFinal(payload).toString(Charsets.UTF_8)
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val packageName = item.optString("packageName")
            if (packageName != PICPAY_PACKAGE) return@mapNotNull null
            DiagnosticEvent(
                id = item.optString("id"),
                postedAtEpochMillis = item.optLong("postedAtEpochMillis"),
                packageName = PICPAY_PACKAGE,
                title = item.optString("title").take(MAX_FIELD_LENGTH),
                text = item.optString("text").take(MAX_FIELD_LENGTH),
            )
        }
    }

    private fun writeEvents(events: List<DiagnosticEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            if (event.packageName != PICPAY_PACKAGE) return@forEach
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("postedAtEpochMillis", event.postedAtEpochMillis)
                    .put("packageName", PICPAY_PACKAGE)
                    .put("title", event.title.take(MAX_FIELD_LENGTH))
                    .put("text", event.text.take(MAX_FIELD_LENGTH)),
            )
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        var stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
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

    private fun List<DiagnosticEvent>.retained(): List<DiagnosticEvent> {
        val oldestAllowed = System.currentTimeMillis() - RETENTION_MILLIS
        return asSequence()
            .filter { it.packageName == PICPAY_PACKAGE && it.postedAtEpochMillis >= oldestAllowed }
            .sortedByDescending { it.postedAtEpochMillis }
            .take(MAX_EVENTS)
            .toList()
    }

    private companion object {
        val processLock = Any()
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "financas-conscientes-notification-diagnostic-v1"
        const val FILE_NAME = "picpay-notification-events.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PICPAY_PACKAGE = "com.picpay"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val MAX_FIELD_LENGTH = 600
        const val MAX_EVENTS = 100
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
