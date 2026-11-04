package br.com.financasconscientes.dashboard

import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.ExpenseDecisions
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.TransactionDirection
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

data class CategoryTotal(
    val category: String,
    val amountCents: Long,
)

data class DecisionTotal(
    /** Stored decision name; the UI may present the default as “Sem decisão”. */
    val decision: String,
    val amountCents: Long,
    val count: Int,
)

data class LargestExpense(
    val description: String,
    val amountCents: Long,
    val category: String,
    val occurredAtEpochMillis: Long?,
)

data class MonthlyDashboard(
    val totalOutflowCents: Long,
    val outflowCount: Int,
    val pendingAmountCents: Long,
    val pendingCount: Int,
    val classifiedCount: Int,
    val decisionTotals: List<DecisionTotal>,
    val categoryTotals: List<CategoryTotal>,
    val largestExpenses: List<LargestExpense>,
    val provisionalCount: Int,
    val monthlyBudgetCents: Long?,
    val receiptBackedCount: Int,
    val possibleReconciliationCount: Int,
) {
    val remainingBudgetCents: Long?
        get() = monthlyBudgetCents?.minus(totalOutflowCents)

    // Preserve the original summary accessors for existing internal callers and tests.
    val plannedAmountCents: Long get() = amountFor(ExpenseDecisions.PLANNED)
    val plannedCount: Int get() = countFor(ExpenseDecisions.PLANNED)
    val impulseAmountCents: Long get() = amountFor(ExpenseDecisions.IMPULSE)
    val impulseCount: Int get() = countFor(ExpenseDecisions.IMPULSE)
    val unmarkedAmountCents: Long get() = amountFor(ExpenseDecisions.DEFAULT)
    val unmarkedCount: Int get() = countFor(ExpenseDecisions.DEFAULT)

    private fun amountFor(decision: String): Long = decisionTotals.firstOrNull { it.decision == decision }?.amountCents ?: 0L
    private fun countFor(decision: String): Int = decisionTotals.firstOrNull { it.decision == decision }?.count ?: 0
}

/**
 * Imported outflows remain authoritative. Unlinked manual records contribute only when
 * backed by an encrypted receipt and not a possible duplicate of an imported transaction.
 */
object MonthlyDashboardCalculator {
    fun calculate(
        state: FinanceState,
        month: YearMonth,
        zoneId: ZoneId,
        validReceiptAttachmentIds: Set<String>? = null,
    ): MonthlyDashboard {
        val knownFingerprints = state.transactions.mapTo(mutableSetOf()) { it.fingerprint }
        val expensesByTransaction = state.expenses
            .filter { it.transactionFingerprint in knownFingerprints }
            .groupBy { it.transactionFingerprint }
            .mapValues { (_, records) -> records.maxByOrNull(ExpenseRecord::createdAtEpochMillis)!! }

        val receiptExpenses = state.expenses.filter { expense ->
            !expense.receiptAttachmentId.isNullOrBlank() &&
                (validReceiptAttachmentIds == null || expense.receiptAttachmentId in validReceiptAttachmentIds)
        }
        val possibleReconciliations = ReceiptReconciliation.findPossible(state, validReceiptAttachmentIds)
        val candidateExpenseIds = possibleReconciliations.mapTo(mutableSetOf()) { it.expenseId }
        val receiptBackedManual = receiptExpenses.filter { expense ->
            expense.transactionFingerprint !in knownFingerprints &&
                expense.id !in candidateExpenseIds &&
                expense.createdAtEpochMillis.belongsTo(month, zoneId)
        }

        val outflows = state.transactions.filter { transaction ->
            transaction.direction == TransactionDirection.OUTFLOW &&
                transaction.occurredAtEpochMillis?.let { occurredAt ->
                    YearMonth.from(Instant.ofEpochMilli(occurredAt).atZone(zoneId)) == month
                } == true
        }

        var pendingAmountCents = 0L
        var pendingCount = 0
        var classifiedCount = 0
        val categoryAmounts = mutableMapOf<String, Long>()
        val observedDecisions = outflows.mapNotNull { transaction ->
            expensesByTransaction[transaction.fingerprint]?.decision
        } + receiptBackedManual.map(ExpenseRecord::decision)
        val decisionOrder = (state.decisions + observedDecisions)
            .map { it.trim().ifBlank { ExpenseDecisions.DEFAULT } }
            .filterNot { it == ExpenseDecisions.DEFAULT }
            .distinct() + ExpenseDecisions.DEFAULT
        val decisionAmounts = mutableMapOf<String, Long>()
        val decisionCounts = mutableMapOf<String, Int>()

        fun recordDecision(rawDecision: String, amount: Long) {
            val decision = rawDecision.trim().ifBlank { ExpenseDecisions.DEFAULT }
            decisionAmounts[decision] = decisionAmounts.getOrDefault(decision, 0L) + amount
            decisionCounts[decision] = decisionCounts.getOrDefault(decision, 0) + 1
        }

        outflows.forEach { transaction ->
            val amount = transaction.amountCents?.let(::safeAbsolute) ?: 0L
            val expense = expensesByTransaction[transaction.fingerprint]
            if (expense == null) {
                pendingCount += 1
                pendingAmountCents += amount
                return@forEach
            }

            classifiedCount += 1
            val category = expense.category.ifBlank { "Outro" }
            categoryAmounts[category] = categoryAmounts.getOrDefault(category, 0L) + amount
            recordDecision(expense.decision, amount)
        }

        receiptBackedManual.forEach { expense ->
            val amount = expense.amountCents?.let(::safeAbsolute) ?: 0L
            classifiedCount += 1
            val category = expense.category.ifBlank { "Outro" }
            categoryAmounts[category] = categoryAmounts.getOrDefault(category, 0L) + amount
            recordDecision(expense.decision, amount)
        }

        return MonthlyDashboard(
            totalOutflowCents = outflows.sumOf { it.amountCents?.let(::safeAbsolute) ?: 0L } +
                receiptBackedManual.sumOf { it.amountCents?.let(::safeAbsolute) ?: 0L },
            outflowCount = outflows.size + receiptBackedManual.size,
            pendingAmountCents = pendingAmountCents,
            pendingCount = pendingCount,
            classifiedCount = classifiedCount,
            decisionTotals = decisionOrder.map { decision ->
                DecisionTotal(
                    decision = decision,
                    amountCents = decisionAmounts.getOrDefault(decision, 0L),
                    count = decisionCounts.getOrDefault(decision, 0),
                )
            },
            categoryTotals = categoryAmounts
                .map { (category, amount) -> CategoryTotal(category, amount) }
                .sortedByDescending(CategoryTotal::amountCents),
            largestExpenses = (outflows
                .map { transaction ->
                    val expense = expensesByTransaction[transaction.fingerprint]
                    LargestExpense(
                        description = expense?.what?.takeIf(String::isNotBlank)
                            ?: transaction.description.ifBlank { transaction.type.ifBlank { "Saída" } },
                        amountCents = transaction.amountCents?.let(::safeAbsolute) ?: 0L,
                        category = expense?.category?.ifBlank { "Outro" } ?: "Pendente",
                        occurredAtEpochMillis = transaction.occurredAtEpochMillis,
                    )
                }
                + receiptBackedManual.map { expense ->
                    LargestExpense(
                        description = expense.what.ifBlank { "Comprovante" },
                        amountCents = expense.amountCents?.let(::safeAbsolute) ?: 0L,
                        category = expense.category.ifBlank { "Outro" },
                        occurredAtEpochMillis = expense.createdAtEpochMillis,
                    )
                })
                .sortedByDescending(LargestExpense::amountCents)
                .take(MAX_LARGEST_EXPENSES),
            provisionalCount = state.expenses.count { it.transactionFingerprint !in knownFingerprints },
            monthlyBudgetCents = state.monthlyBudgetCents,
            receiptBackedCount = receiptExpenses.count { it.createdAtEpochMillis.belongsTo(month, zoneId) },
            possibleReconciliationCount = possibleReconciliations.count { possible ->
                state.expenses.firstOrNull { it.id == possible.expenseId }
                    ?.createdAtEpochMillis?.belongsTo(month, zoneId) == true
            },
        )
    }

    private fun Long.belongsTo(month: YearMonth, zoneId: ZoneId): Boolean =
        YearMonth.from(Instant.ofEpochMilli(this).atZone(zoneId)) == month

    private fun safeAbsolute(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)

    private const val MAX_LARGEST_EXPENSES = 5
}
