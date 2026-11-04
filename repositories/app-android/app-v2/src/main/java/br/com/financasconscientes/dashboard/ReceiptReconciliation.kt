package br.com.financasconscientes.dashboard

import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import kotlin.math.abs

data class PossibleReconciliation(
    val expenseId: String,
    val candidateFingerprints: List<String>,
) {
    val isUnique: Boolean get() = candidateFingerprints.size == 1
}

/** Suggests only close-date, still-unlinked exact-amount candidates; it never changes links. */
object ReceiptReconciliation {
    const val MATCH_WINDOW_MILLIS = 48L * 60 * 60 * 1000

    /** Broad review-only suggestions; historical exact-amount matches never suppress totals. */
    fun findForManualReview(
        transaction: ImportedTransaction,
        state: FinanceState,
        validReceiptAttachmentIds: Set<String>,
        limit: Int = 30,
    ): List<ExpenseRecord> {
        val amount = transaction.amountCents?.let(::safeAbsolute) ?: return emptyList()
        val possibleRecords = state.expenses.filter { expense ->
            expense.transactionFingerprint == null && expense.amountCents?.let(::safeAbsolute) == amount
        }
        val occurredAt = transaction.occurredAtEpochMillis
        val close = possibleRecords.filter { expense ->
            occurredAt?.let { distance(expense.createdAtEpochMillis, it) <= MATCH_WINDOW_MILLIS } == true
        }
        val proofed = possibleRecords.filter { expense ->
            expense.receiptAttachmentId?.let(validReceiptAttachmentIds::contains) == true
        }
        return (close + proofed)
            .distinctBy(ExpenseRecord::id)
            .sortedWith(
                compareBy<ExpenseRecord> { expense ->
                    occurredAt?.let { distance(expense.createdAtEpochMillis, it) } ?: Long.MAX_VALUE
                }.thenByDescending(ExpenseRecord::createdAtEpochMillis),
            )
            .take(limit.coerceAtLeast(0))
    }

    fun findPossible(
        state: FinanceState,
        validReceiptAttachmentIds: Set<String>? = null,
    ): List<PossibleReconciliation> {
        val linkedFingerprints = state.expenses.mapNotNullTo(mutableSetOf()) { it.transactionFingerprint }
        val candidatesByExpense = state.expenses
            .filter {
                it.transactionFingerprint == null &&
                    !it.receiptAttachmentId.isNullOrBlank() &&
                    (validReceiptAttachmentIds == null || it.receiptAttachmentId in validReceiptAttachmentIds)
            }
            .mapNotNull { expense ->
                val amount = expense.amountCents?.let(::safeAbsolute) ?: return@mapNotNull null
                val candidates = state.transactions.filter { transaction ->
                    transaction.direction == TransactionDirection.OUTFLOW &&
                        transaction.fingerprint !in linkedFingerprints &&
                        transaction.occurredAtEpochMillis?.let {
                            distance(expense.createdAtEpochMillis, it) <= MATCH_WINDOW_MILLIS
                        } == true &&
                        transaction.amountCents?.let(::safeAbsolute) == amount
                }.map { it.fingerprint }
                candidates.takeIf(List<String>::isNotEmpty)?.let {
                    PossibleReconciliation(expense.id, it)
                }
            }
        return candidatesByExpense
    }

    /** Candidate IDs are excluded from provisional totals so one receipt and one CSV row never add twice. */
    fun candidateExpenseIds(
        state: FinanceState,
        validReceiptAttachmentIds: Set<String>? = null,
    ): Set<String> = findPossible(state, validReceiptAttachmentIds).mapTo(mutableSetOf()) { it.expenseId }

    private fun distance(left: Long, right: Long): Long =
        if (left >= right) left - right else right - left

    private fun safeAbsolute(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)
}
