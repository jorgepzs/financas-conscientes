package br.com.financasconscientes.dashboard

import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReceiptReconciliationTest {
    @Test
    fun `unique candidate is suggested without mutating the expense link`() {
        val receipt = expense("receipt", "csv")
        val state = FinanceState(
            transactions = listOf(transaction("csv", 12_300, "2026-10-03T12:00:00")),
            expenses = listOf(receipt),
        )

        val candidates = ReceiptReconciliation.findPossible(state)

        assertEquals(listOf("csv"), candidates.single().candidateFingerprints)
        assertEquals("receipt", candidates.single().expenseId)
        assertEquals(null, state.expenses.single().transactionFingerprint)
    }

    @Test
    fun `ambiguous candidates are surfaced and do not create an automatic link`() {
        val state = FinanceState(
            transactions = listOf(
                transaction("csv-a", 12_300, "2026-10-03T11:00:00"),
                transaction("csv-b", 12_300, "2026-10-03T13:00:00"),
            ),
            expenses = listOf(expense("receipt", "unused")),
        )

        val possible = ReceiptReconciliation.findPossible(state).single()

        assertFalse(possible.isUnique)
        assertEquals(setOf("csv-a", "csv-b"), possible.candidateFingerprints.toSet())
        assertEquals(null, state.expenses.single().transactionFingerprint)
    }

    @Test
    fun `different amount and incoming rows are not candidates`() {
        val state = FinanceState(
            transactions = listOf(
                transaction("different", 12_301, "2026-10-03T12:00:00"),
                transaction("incoming", 12_300, "2026-10-03T12:00:00", TransactionDirection.INFLOW),
            ),
            expenses = listOf(expense("receipt", "unused")),
        )

        assertEquals(emptyList<PossibleReconciliation>(), ReceiptReconciliation.findPossible(state))
    }

    @Test
    fun `old or already classified same amount rows never suppress receipt totals`() {
        val old = transaction("old", 12_300, "2026-09-01T12:00:00")
        val classified = expense("classified", null).copy(transactionFingerprint = "already-linked")
        val receipt = expense("receipt", "blob")
        val state = FinanceState(
            transactions = listOf(
                old,
                transaction("already-linked", 12_300, "2026-10-03T12:00:00"),
            ),
            expenses = listOf(classified, receipt),
        )

        assertEquals(emptyList<PossibleReconciliation>(), ReceiptReconciliation.findPossible(state, setOf("blob")))
    }

    @Test
    fun `historical receipt remains selectable for explicit review without dashboard suppression`() {
        val oldTransaction = transaction("old", 12_300, "2026-09-01T12:00:00")
        val receipt = expense("receipt", "blob")
        val state = FinanceState(transactions = listOf(oldTransaction), expenses = listOf(receipt))

        val reviewCandidates = ReceiptReconciliation.findForManualReview(
            oldTransaction,
            state,
            validReceiptAttachmentIds = setOf("blob"),
        )

        assertEquals(listOf("receipt"), reviewCandidates.map(ExpenseRecord::id))
        assertEquals(emptyList<PossibleReconciliation>(), ReceiptReconciliation.findPossible(state, setOf("blob")))
        assertEquals(null, state.expenses.single().transactionFingerprint)
    }

    private fun transaction(
        fingerprint: String,
        amountCents: Long,
        dateTime: String,
        direction: TransactionDirection = TransactionDirection.OUTFLOW,
    ) = ImportedTransaction(
        fingerprint = fingerprint,
        importedAtEpochMillis = 0,
        occurredAtEpochMillis = LocalDateTime.parse(dateTime).toInstant(ZoneOffset.UTC).toEpochMilli(),
        dateText = dateTime,
        type = "Pix",
        description = "Pix",
        counterparty = "Pessoa",
        status = "",
        paymentMethod = "Saldo",
        amountCents = if (direction == TransactionDirection.OUTFLOW) -amountCents else amountCents,
        direction = direction,
    )

    private fun expense(id: String, attachment: String?) = ExpenseRecord(
        id = id,
        createdAtEpochMillis = LocalDateTime.parse("2026-10-03T12:00:00")
            .toInstant(ZoneOffset.UTC).toEpochMilli(),
        transactionFingerprint = null,
        amountCents = 12_300,
        what = "Compra",
        where = "Loja",
        category = "Lazer",
        decision = "Planejado",
        source = "shared_receipt",
        receiptAttachmentId = attachment,
        receiptMimeType = attachment?.let { "application/pdf" },
        receiptSizeBytes = attachment?.let { 42L },
    )
}
