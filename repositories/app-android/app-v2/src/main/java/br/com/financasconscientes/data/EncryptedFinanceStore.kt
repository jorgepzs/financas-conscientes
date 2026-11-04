package br.com.financasconscientes.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import br.com.financasconscientes.model.ExpenseCategories
import br.com.financasconscientes.model.ExpenseCategoryPalette
import br.com.financasconscientes.model.ExpenseDecisions
import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores normalized records locally; raw CSV is discarded and receipt documents are separate encrypted blobs. */
class EncryptedFinanceStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    val receiptAttachments = EncryptedReceiptAttachmentStore(context)

    fun load(): FinanceState = synchronized(processLock) {
        if (!atomicFile.baseFile.exists()) FinanceState() else readState()
    }

    fun importTransactions(incoming: List<ImportedTransaction>): Int = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        val known = current.transactions.mapTo(mutableSetOf()) { it.fingerprint }
        val additions = incoming.filter { known.add(it.fingerprint) }
        if (additions.isNotEmpty()) {
            writeState(
                current.copy(
                    transactions = (current.transactions + additions)
                        .sortedByDescending { it.occurredAtEpochMillis ?: it.importedAtEpochMillis }
                        .take(MAX_TRANSACTIONS),
                ),
            )
        }
        additions.size
    }

    fun upsertExpense(expense: ExpenseRecord) = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        val replacedAttachment = current.expenses.firstOrNull { it.id == expense.id }
            ?.receiptAttachmentId?.takeIf { it != expense.receiptAttachmentId }
        val updated = (current.expenses.filterNot { it.id == expense.id } + expense)
            .sortedByDescending { it.createdAtEpochMillis }
            .take(MAX_EXPENSES)
        writeState(current.copy(expenses = updated))
        val retainedIds = updated.mapNotNullTo(mutableSetOf()) { it.receiptAttachmentId }
        (current.expenses.mapNotNull { it.receiptAttachmentId } + listOfNotNull(replacedAttachment))
            .distinct()
            .filterNot(retainedIds::contains)
            .forEach(receiptAttachments::delete)
    }

    fun deleteExpense(expenseId: String) = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        val deletedAttachment = current.expenses.firstOrNull { it.id == expenseId }?.receiptAttachmentId
        writeState(current.copy(expenses = current.expenses.filterNot { it.id == expenseId }))
        deletedAttachment?.let(receiptAttachments::delete)
    }

    fun setMonthlyBudget(amountCents: Long?) = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        writeState(current.copy(monthlyBudgetCents = amountCents?.takeIf { it > 0 }))
    }

    fun updateExpenseSettings(update: (FinanceState) -> FinanceState) = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        val updated = update(current)
        if (updated != current) writeState(updated)
    }

    fun deleteImportedTransaction(fingerprint: String) = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        if (current.transactions.none { it.fingerprint == fingerprint }) return@synchronized
        writeState(FinanceStateMutations.removeTransaction(current, fingerprint))
    }

    fun clearImportedTransactions() = synchronized(processLock) {
        val current = if (atomicFile.baseFile.exists()) readState() else FinanceState()
        if (current.transactions.isEmpty()) return@synchronized
        writeState(FinanceStateMutations.removeAllTransactions(current))
    }

    fun clear() = synchronized(processLock) {
        atomicFile.delete()
        receiptAttachments.deleteAll()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun readState(): FinanceState {
        val encrypted = atomicFile.openRead().use { it.readBytes() }
        require(encrypted.size > IV_LENGTH) { "Arquivo financeiro inválido" }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val payload = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Chave financeira indisponível")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val root = JSONObject(cipher.doFinal(payload).toString(Charsets.UTF_8))
        val categories = root.optJSONArray("categories").toOptions(
            ExpenseCategories.all,
            listOf(ExpenseCategories.DEFAULT, ExpenseCategories.UNCATEGORIZED),
        )
        val decisions = root.optJSONArray("decisions").toOptions(
            ExpenseDecisions.all,
            listOf(ExpenseDecisions.DEFAULT),
        )
        return FinanceState(
            transactions = root.optJSONArray("transactions").toTransactions(),
            expenses = root.optJSONArray("expenses").toExpenses(),
            monthlyBudgetCents = root.nullableLong("monthlyBudgetCents")?.takeIf { it > 0 },
            categories = categories,
            decisions = decisions,
            categoryColors = root.optJSONObject("categoryColors").toCategoryColors(categories),
        )
    }

    private fun writeState(state: FinanceState) {
        val root = JSONObject()
            .put("schemaVersion", 4)
            .putNullable("monthlyBudgetCents", state.monthlyBudgetCents)
        val categories = sanitizeOptions(
            state.categories,
            ExpenseCategories.all,
            listOf(ExpenseCategories.DEFAULT, ExpenseCategories.UNCATEGORIZED),
        )
        val decisions = sanitizeOptions(state.decisions, ExpenseDecisions.all, listOf(ExpenseDecisions.DEFAULT))
        root.put("categories", JSONArray(categories))
        root.put("decisions", JSONArray(decisions))
        root.put(
            "categoryColors",
            JSONObject().apply {
                categories.forEach { category ->
                    put(
                        category,
                        ExpenseCategoryPalette.normalize(state.categoryColors[category])
                            ?: ExpenseCategoryPalette.defaultFor(category),
                    )
                }
            },
        )
        val transactions = JSONArray()
        state.transactions.take(MAX_TRANSACTIONS).forEach { transaction ->
            transactions.put(
                JSONObject()
                    .put("fingerprint", transaction.fingerprint.take(64))
                    .put("importedAt", transaction.importedAtEpochMillis)
                    .putNullable("occurredAt", transaction.occurredAtEpochMillis)
                    .put("dateText", transaction.dateText.safe())
                    .put("type", transaction.type.safe())
                    .put("description", transaction.description.safe())
                    .put("counterparty", transaction.counterparty.safe())
                    .put("status", transaction.status.safe())
                    .put("paymentMethod", transaction.paymentMethod.safe())
                    .putNullable("amountCents", transaction.amountCents)
                    .put("direction", transaction.direction.name),
            )
        }
        root.put("transactions", transactions)

        val expenses = JSONArray()
        state.expenses.take(MAX_EXPENSES).forEach { expense ->
            expenses.put(
                JSONObject()
                    .put("id", expense.id.take(64))
                    .put("createdAt", expense.createdAtEpochMillis)
                    .putNullable("transactionFingerprint", expense.transactionFingerprint?.take(64))
                    .putNullable("amountCents", expense.amountCents)
                    .put("what", expense.what.safe())
                    .put("where", expense.where.safe())
                    .put("category", expense.category.safe())
                    .put("decision", expense.decision.safe())
                    .put("source", expense.source.safe())
                    .putNullable("receiptAttachmentId", expense.receiptAttachmentId?.take(64))
                    .putNullable("receiptMimeType", expense.receiptMimeType?.take(64))
                    .putNullable("receiptSizeBytes", expense.receiptSizeBytes),
            )
        }
        root.put("expenses", expenses)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun JSONArray?.toTransactions(): List<ImportedTransaction> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val fingerprint = item.optString("fingerprint").take(64)
            if (fingerprint.isBlank()) return@mapNotNull null
            ImportedTransaction(
                fingerprint = fingerprint,
                importedAtEpochMillis = item.optLong("importedAt"),
                occurredAtEpochMillis = item.nullableLong("occurredAt"),
                dateText = item.optString("dateText").safe(),
                type = item.optString("type").safe(),
                description = item.optString("description").safe(),
                counterparty = item.optString("counterparty").safe(),
                status = item.optString("status").safe(),
                paymentMethod = item.optString("paymentMethod").safe(),
                amountCents = item.nullableLong("amountCents"),
                direction = runCatching {
                    TransactionDirection.valueOf(item.optString("direction"))
                }.getOrDefault(TransactionDirection.UNKNOWN),
            )
        }
    }

    private fun JSONArray?.toExpenses(): List<ExpenseRecord> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").take(64)
            if (id.isBlank()) return@mapNotNull null
            ExpenseRecord(
                id = id,
                createdAtEpochMillis = item.optLong("createdAt"),
                transactionFingerprint = item.nullableString("transactionFingerprint")?.take(64),
                amountCents = item.nullableLong("amountCents"),
                what = item.optString("what").safe(),
                where = item.optString("where").safe(),
                category = item.optString("category").safe(),
                decision = item.optString("decision").safe(),
                source = item.optString("source").safe(),
                receiptAttachmentId = item.nullableString("receiptAttachmentId")?.take(64),
                receiptMimeType = item.nullableString("receiptMimeType")?.take(64),
                receiptSizeBytes = item.nullableLong("receiptSizeBytes")?.takeIf { it > 0L },
            )
        }
    }

    private fun JSONArray?.toOptions(defaults: List<String>, required: List<String>): List<String> {
        if (this == null) return defaults
        val values = (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { value ->
                value.isNotEmpty() && value.length <= MAX_OPTION_LENGTH && value.none(Char::isISOControl)
            }
        }.distinctBy(String::lowercase).take(MAX_OPTIONS).toMutableList()
        required.forEachIndexed { index, value ->
            if (values.none { it == value }) values.add(index.coerceAtMost(values.size), value)
        }
        return values.take(MAX_OPTIONS)
    }

    private fun JSONObject?.toCategoryColors(categories: List<String>): Map<String, String> =
        categories.associateWith { category ->
            val stored = this?.takeIf { it.has(category) }?.optString(category)
            ExpenseCategoryPalette.normalize(stored) ?: ExpenseCategoryPalette.defaultFor(category)
        }

    private fun sanitizeOptions(values: List<String>, defaults: List<String>, required: List<String>): List<String> {
        val normalized = values.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_OPTION_LENGTH && it.none(Char::isISOControl) }
            .distinctBy(String::lowercase)
            .take(MAX_OPTIONS)
            .toMutableList()
        required.forEachIndexed { index, value ->
            if (normalized.none { it == value }) normalized.add(index.coerceAtMost(normalized.size), value)
        }
        return normalized.take(MAX_OPTIONS).ifEmpty { defaults }
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

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun String.safe(): String = take(MAX_FIELD_LENGTH)

    private companion object {
        val processLock = Any()
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "financas-conscientes-v2-local-data"
        const val FILE_NAME = "finance-state-v2.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val MAX_FIELD_LENGTH = 500
        const val MAX_TRANSACTIONS = 20_000
        const val MAX_EXPENSES = 5_000
        const val MAX_OPTIONS = 30
        const val MAX_OPTION_LENGTH = 36
    }
}
