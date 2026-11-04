package br.com.financasconscientes.dashboard

import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class StatementDataQualityCalculatorTest {
    @Test
    fun `summarizes missing context and latest import from local state`() {
        val state = FinanceState(
            transactions = listOf(
                transaction("linked", TransactionDirection.OUTFLOW, importedAt = 100, amount = 500, hasDate = true),
                transaction("pending", TransactionDirection.OUTFLOW, importedAt = 200, amount = null, hasDate = false),
                transaction("unknown", TransactionDirection.UNKNOWN, importedAt = 150, amount = 300, hasDate = true),
            ),
            expenses = listOf(expense("linked")),
        )

        assertEquals(
            StatementDataQuality(
                pendingOutflowCount = 1,
                unknownDirectionCount = 1,
                missingDateCount = 1,
                missingAmountCount = 1,
                latestImportEpochMillis = 200,
            ),
            StatementDataQualityCalculator.calculate(state),
        )
    }

    private fun transaction(
        fingerprint: String,
        direction: TransactionDirection,
        importedAt: Long,
        amount: Long?,
        hasDate: Boolean,
    ) = ImportedTransaction(
        fingerprint = fingerprint,
        importedAtEpochMillis = importedAt,
        occurredAtEpochMillis = if (hasDate) importedAt else null,
        dateText = "",
        type = "",
        description = "",
        counterparty = "",
        status = "",
        paymentMethod = "",
        amountCents = amount,
        direction = direction,
    )

    private fun expense(fingerprint: String) = ExpenseRecord(
        id = "expense-$fingerprint",
        createdAtEpochMillis = 1,
        transactionFingerprint = fingerprint,
        amountCents = 500,
        what = "Teste",
        where = "",
        category = "Outro",
        decision = "Planejado",
        source = "test",
    )
}
