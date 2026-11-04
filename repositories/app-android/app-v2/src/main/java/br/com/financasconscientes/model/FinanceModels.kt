package br.com.financasconscientes.model

enum class TransactionDirection {
    OUTFLOW,
    INFLOW,
    UNKNOWN,
}

data class ImportedTransaction(
    val fingerprint: String,
    val importedAtEpochMillis: Long,
    val occurredAtEpochMillis: Long?,
    val dateText: String,
    val type: String,
    val description: String,
    val counterparty: String,
    val status: String,
    val paymentMethod: String,
    val amountCents: Long?,
    val direction: TransactionDirection,
)

data class ExpenseRecord(
    val id: String,
    val createdAtEpochMillis: Long,
    val transactionFingerprint: String?,
    val amountCents: Long?,
    val what: String,
    val where: String,
    val category: String,
    val decision: String,
    val source: String,
    /** Non-null only after a supported document was copied and encrypted in app-private storage. */
    val receiptAttachmentId: String? = null,
    val receiptMimeType: String? = null,
    val receiptSizeBytes: Long? = null,
)

enum class EvidenceType {
    NONE,
    /** User-supplied encrypted attachment; this does not verify issuer or transaction semantics. */
    RECEIPT,
    STATEMENT,
    BOTH,
}

object ExpenseEvidence {
    fun classify(
        expense: ExpenseRecord,
        hasValidReceipt: Boolean,
        importedFingerprints: Set<String>,
    ): EvidenceType {
        val receipt = hasValidReceipt && !expense.receiptAttachmentId.isNullOrBlank()
        val statement = expense.transactionFingerprint?.let(importedFingerprints::contains) == true
        return when {
            receipt && statement -> EvidenceType.BOTH
            receipt -> EvidenceType.RECEIPT
            statement -> EvidenceType.STATEMENT
            else -> EvidenceType.NONE
        }
    }
}

data class FinanceState(
    val transactions: List<ImportedTransaction> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val monthlyBudgetCents: Long? = null,
    val categories: List<String> = ExpenseCategories.all,
    val decisions: List<String> = ExpenseDecisions.all,
    val categoryColors: Map<String, String> = categories.associateWith { ExpenseCategoryPalette.defaultFor(it) },
)
