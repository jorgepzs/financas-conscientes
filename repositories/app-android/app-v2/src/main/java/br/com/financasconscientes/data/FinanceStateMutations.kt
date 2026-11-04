package br.com.financasconscientes.data

import br.com.financasconscientes.model.FinanceState

/** Pure state changes kept separate so destructive actions are explicit and testable. */
object FinanceStateMutations {
    fun removeTransaction(state: FinanceState, fingerprint: String): FinanceState = state.copy(
        transactions = state.transactions.filterNot { it.fingerprint == fingerprint },
        expenses = state.expenses.map { expense ->
            if (expense.transactionFingerprint == fingerprint) {
                expense.copy(transactionFingerprint = null)
            } else {
                expense
            }
        },
    )

    fun removeAllTransactions(state: FinanceState): FinanceState = state.copy(
        transactions = emptyList(),
        expenses = state.expenses.map { expense ->
            if (expense.transactionFingerprint == null) expense else expense.copy(transactionFingerprint = null)
        },
    )
}
