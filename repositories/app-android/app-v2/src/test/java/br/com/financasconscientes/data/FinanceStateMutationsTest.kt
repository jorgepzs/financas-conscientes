package br.com.financasconscientes.data

import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceStateMutationsTest {
    @Test
    fun `removing a transaction preserves its classification as provisional`() {
        val state = FinanceState(
            transactions = listOf(transaction("remove"), transaction("keep")),
            expenses = listOf(expense("linked", "remove"), expense("other", "keep")),
            monthlyBudgetCents = 100_00,
        )

        val result = FinanceStateMutations.removeTransaction(state, "remove")

        assertEquals(listOf("keep"), result.transactions.map { it.fingerprint })
        assertNull(result.expenses.first { it.id == "linked" }.transactionFingerprint)
        assertEquals("keep", result.expenses.first { it.id == "other" }.transactionFingerprint)
        assertEquals(100_00L, result.monthlyBudgetCents)
    }

    @Test
    fun `clearing imported transactions keeps every classification and budget`() {
        val state = FinanceState(
            transactions = listOf(transaction("one"), transaction("two")),
            expenses = listOf(expense("linked", "one"), expense("draft", null)),
            monthlyBudgetCents = 200_00,
        )

        val result = FinanceStateMutations.removeAllTransactions(state)

        assertEquals(emptyList<ImportedTransaction>(), result.transactions)
        assertEquals(2, result.expenses.size)
        result.expenses.forEach { assertNull(it.transactionFingerprint) }
        assertEquals(200_00L, result.monthlyBudgetCents)
    }

    private fun transaction(fingerprint: String) = ImportedTransaction(
        fingerprint = fingerprint,
        importedAtEpochMillis = 1,
        occurredAtEpochMillis = 1,
        dateText = "2026-10-03",
        type = "Pix",
        description = "Pix",
        counterparty = "",
        status = "",
        paymentMethod = "",
        amountCents = -1_000,
        direction = TransactionDirection.OUTFLOW,
    )

    private fun expense(id: String, fingerprint: String?) = ExpenseRecord(
        id = id,
        createdAtEpochMillis = 1,
        transactionFingerprint = fingerprint,
        amountCents = 1_000,
        what = "Teste",
        where = "",
        category = "Outro",
        decision = "Não informado",
        source = "test",
    )
}
