package br.com.financasconscientes.dashboard

import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.ExpenseDecisions
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyDashboardCalculatorTest {
    @Test
    fun `uses csv amounts and excludes provisional records from totals`() {
        val state = FinanceState(
            transactions = listOf(
                transaction("one", 1_234, "2026-10-02T12:00:00"),
                transaction("two", 5_000, "2026-10-03T12:00:00"),
                transaction("old", 9_999, "2026-09-30T23:00:00"),
            ),
            expenses = listOf(
                expense("linked", "one", 999_999, "Alimentação", "Planejado"),
                expense("draft", null, 88_00, "Lazer", "Impulso"),
            ),
            monthlyBudgetCents = 10_000,
        )

        val result = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)

        assertEquals(6_234, result.totalOutflowCents)
        assertEquals(2, result.outflowCount)
        assertEquals(1_234, result.plannedAmountCents)
        assertEquals(5_000, result.pendingAmountCents)
        assertEquals(1, result.pendingCount)
        assertEquals(1, result.provisionalCount)
        assertEquals(3_766L, result.remainingBudgetCents)
        assertEquals(listOf(CategoryTotal("Alimentação", 1_234)), result.categoryTotals)
        assertEquals("Pix", result.largestExpenses.first().description)
        assertEquals(5_000, result.largestExpenses.first().amountCents)
    }

    @Test
    fun `orders largest expenses by confirmed csv amount and includes pending context`() {
        val state = FinanceState(
            transactions = listOf(
                transaction("small", 450, "2026-10-01T12:00:00"),
                transaction("large", 9_500, "2026-10-02T12:00:00"),
            ),
                expenses = listOf(expense("small-expense", "small", 450, "Lazer", "Planejado")),
        )

        val largest = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)
            .largestExpenses

        assertEquals(listOf("Pix", "Teste"), largest.map { it.description })
        assertEquals(listOf(9_500L, 450L), largest.map { it.amountCents })
        assertEquals(listOf("Pendente", "Lazer"), largest.map { it.category })
    }

    @Test
    fun `separates planned impulse and unmarked confirmed transactions`() {
        val state = FinanceState(
            transactions = listOf(
                transaction("planned", 1_000, "2026-10-01T12:00:00"),
                transaction("impulse", 2_000, "2026-10-02T12:00:00"),
                transaction("unmarked", 3_000, "2026-10-03T12:00:00"),
            ),
            expenses = listOf(
                expense("a", "planned", 1, "Moradia", "Planejado"),
                expense("b", "impulse", 1, "Lazer", "Impulso"),
                expense("c", "unmarked", 1, "Outro", "Não informado"),
            ),
        )

        val result = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)

        assertEquals(3, result.classifiedCount)
        assertEquals(1_000, result.plannedAmountCents)
        assertEquals(1, result.plannedCount)
        assertEquals(2_000, result.impulseAmountCents)
        assertEquals(1, result.impulseCount)
        assertEquals(3_000, result.unmarkedAmountCents)
        assertEquals(1, result.unmarkedCount)
    }

    @Test
    fun `aggregates a newly configured decision under its own name`() {
        val customDecision = "Separar para viagem"
        val state = FinanceState(
            transactions = listOf(
                transaction("custom", 1_750, "2026-10-01T12:00:00"),
                transaction("default", 250, "2026-10-02T12:00:00"),
            ),
            expenses = listOf(
                expense("custom-record", "custom", 1_750, "Lazer", customDecision),
                expense("default-record", "default", 250, "Lazer", ExpenseDecisions.DEFAULT),
            ),
            decisions = ExpenseDecisions.all + customDecision,
        )

        val result = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)
        val customTotal = result.decisionTotals.single { it.decision == customDecision }

        assertEquals(1_750L, customTotal.amountCents)
        assertEquals(1, customTotal.count)
        assertEquals(250L, result.unmarkedAmountCents)
        assertEquals(1, result.unmarkedCount)
        assertEquals(ExpenseDecisions.DEFAULT, result.decisionTotals.last().decision)
    }

    @Test
    fun `includes a custom decision for a valid manual receipt`() {
        val customDecision = "Compra consciente"
        val state = FinanceState(
            expenses = listOf(
                expense(
                    id = "manual-receipt",
                    fingerprint = null,
                    amountCents = 3_250,
                    category = "Alimentação",
                    decision = customDecision,
                    receiptAttachmentId = "receipt-1",
                ),
            ),
            decisions = ExpenseDecisions.all + customDecision,
        )

        val result = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)
        val customTotal = result.decisionTotals.single { it.decision == customDecision }

        assertEquals(3_250L, customTotal.amountCents)
        assertEquals(1, customTotal.count)
        assertEquals(0, result.unmarkedCount)
    }

    @Test
    fun `counts receipt-backed manual record before csv but excludes manual record without proof`() {
        val state = FinanceState(
            expenses = listOf(
                expense("receipt", null, 2_500, "Alimentação", "Planejado", "receipt-1"),
                expense("unproven", null, 9_900, "Lazer", "Impulso"),
            ),
        )

        val result = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)

        assertEquals(2_500L, result.totalOutflowCents)
        assertEquals(1, result.outflowCount)
        assertEquals(1, result.classifiedCount)
        assertEquals(1, result.receiptBackedCount)
        assertEquals(0, result.possibleReconciliationCount)
        assertEquals(2_500L, result.plannedAmountCents)
    }

    @Test
    fun `receipt candidate and csv count once until user confirms the link`() {
        val receipt = expense("receipt", null, 2_500, "Alimentação", "Planejado", "receipt-1")
        val state = FinanceState(
            transactions = listOf(transaction("csv", 2_500, "2026-10-03T12:00:00")),
            expenses = listOf(receipt),
        )

        val result = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)

        assertEquals(2_500L, result.totalOutflowCents)
        assertEquals(1, result.outflowCount)
        assertEquals(1, result.pendingCount)
        assertEquals(0, result.plannedCount)
        assertEquals(1, result.receiptBackedCount)
        assertEquals(1, result.possibleReconciliationCount)
        assertEquals(null, state.expenses.single().transactionFingerprint)
    }

    @Test
    fun `old classified csv amount does not suppress a new receipt in a later month`() {
        val september = transaction("september-csv", 5_000, "2026-09-15T12:00:00")
        val receiptToday = expense("october-receipt", null, 5_000, "Lazer", "Planejado", "receipt-1")
        val septemberClassification = expense("september-classification", "september-csv", 5_000, "Lazer", "Planejado")
            .copy(createdAtEpochMillis = september.occurredAtEpochMillis!!)
        val state = FinanceState(
            transactions = listOf(september),
            expenses = listOf(septemberClassification, receiptToday),
        )

        val october = MonthlyDashboardCalculator.calculate(state, YearMonth.of(2026, 10), ZoneOffset.UTC)

        assertEquals(5_000L, october.totalOutflowCents)
        assertEquals(1, october.outflowCount)
        assertEquals(0, october.possibleReconciliationCount)
    }

    @Test
    fun `invalid attachment ids can be excluded from receipt totals`() {
        val state = FinanceState(
            expenses = listOf(expense("receipt", null, 2_500, "Alimentação", "Planejado", "blob")),
        )

        val result = MonthlyDashboardCalculator.calculate(
            state,
            YearMonth.of(2026, 10),
            ZoneOffset.UTC,
            validReceiptAttachmentIds = emptySet(),
        )

        assertEquals(0L, result.totalOutflowCents)
        assertEquals(0, result.receiptBackedCount)
    }

    private fun transaction(fingerprint: String, amountCents: Long, dateTime: String) = ImportedTransaction(
        fingerprint = fingerprint,
        importedAtEpochMillis = 0,
        occurredAtEpochMillis = LocalDateTime.parse(dateTime).toInstant(ZoneOffset.UTC).toEpochMilli(),
        dateText = dateTime,
        type = "Pix",
        description = "Pix",
        counterparty = "Pessoa",
        status = "",
        paymentMethod = "Saldo",
        amountCents = -amountCents,
        direction = TransactionDirection.OUTFLOW,
    )

    private fun expense(
        id: String,
        fingerprint: String?,
        amountCents: Long,
        category: String,
        decision: String,
        receiptAttachmentId: String? = null,
    ) = ExpenseRecord(
        id = id,
        createdAtEpochMillis = LocalDateTime.parse("2026-10-03T12:00:00")
            .toInstant(ZoneOffset.UTC).toEpochMilli(),
        transactionFingerprint = fingerprint,
        amountCents = amountCents,
        what = "Teste",
        where = "",
        category = category,
        decision = decision,
        source = "test",
        receiptAttachmentId = receiptAttachmentId,
        receiptMimeType = receiptAttachmentId?.let { "image/png" },
        receiptSizeBytes = receiptAttachmentId?.let { 20L },
    )
}
