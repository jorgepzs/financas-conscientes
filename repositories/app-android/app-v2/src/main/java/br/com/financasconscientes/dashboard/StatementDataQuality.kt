package br.com.financasconscientes.dashboard

import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.TransactionDirection

data class StatementDataQuality(
    val pendingOutflowCount: Int,
    val unknownDirectionCount: Int,
    val missingDateCount: Int,
    val missingAmountCount: Int,
    val latestImportEpochMillis: Long?,
)

object StatementDataQualityCalculator {
    fun calculate(state: FinanceState): StatementDataQuality {
        val linkedFingerprints = state.expenses.mapNotNullTo(mutableSetOf()) { it.transactionFingerprint }
        return StatementDataQuality(
            pendingOutflowCount = state.transactions.count {
                it.direction == TransactionDirection.OUTFLOW && it.fingerprint !in linkedFingerprints
            },
            unknownDirectionCount = state.transactions.count { it.direction == TransactionDirection.UNKNOWN },
            missingDateCount = state.transactions.count { it.occurredAtEpochMillis == null },
            missingAmountCount = state.transactions.count { it.amountCents == null },
            latestImportEpochMillis = state.transactions.maxOfOrNull { it.importedAtEpochMillis },
        )
    }
}
