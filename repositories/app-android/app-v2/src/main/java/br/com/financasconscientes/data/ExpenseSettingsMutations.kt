package br.com.financasconscientes.data

import br.com.financasconscientes.model.ExpenseCategoryPalette
import br.com.financasconscientes.model.ExpenseCategories
import br.com.financasconscientes.model.ExpenseDecisions
import br.com.financasconscientes.model.FinanceState

/** Pure, atomic taxonomy edits. Existing classifications follow a rename or explicit deletion replacement. */
object ExpenseSettingsMutations {
    fun addCategory(state: FinanceState, rawName: String): FinanceState {
        val name = validName(rawName, state.categories) ?: return state
        return state.copy(
            categories = state.categories + name,
            categoryColors = state.categoryColors + (name to ExpenseCategoryPalette.defaultFor(name)),
        )
    }

    fun renameCategory(state: FinanceState, oldName: String, rawName: String): FinanceState {
        if (oldName in REQUIRED_CATEGORIES || oldName !in state.categories) return state
        val name = validName(rawName, state.categories, oldName) ?: return state
        if (name == oldName) return state
        val color = ExpenseCategoryPalette.colorFor(oldName, state.categoryColors)
        return state.copy(
            categories = state.categories.map { if (it == oldName) name else it },
            categoryColors = (state.categoryColors - oldName) + (name to color),
            expenses = state.expenses.map { expense ->
                if (expense.category == oldName) expense.copy(category = name) else expense
            },
        )
    }

    fun deleteCategory(state: FinanceState, name: String): FinanceState {
        if (name in REQUIRED_CATEGORIES || name !in state.categories) return state
        return state.copy(
            categories = state.categories.filterNot { it == name },
            categoryColors = state.categoryColors - name,
            expenses = state.expenses.map { expense ->
                if (expense.category == name) expense.copy(category = ExpenseCategories.UNCATEGORIZED) else expense
            },
        )
    }

    fun addDecision(state: FinanceState, rawName: String): FinanceState {
        val name = validName(rawName, state.decisions) ?: return state
        return state.copy(decisions = state.decisions + name)
    }

    fun renameDecision(state: FinanceState, oldName: String, rawName: String): FinanceState {
        if (oldName == ExpenseDecisions.DEFAULT || oldName !in state.decisions) return state
        val name = validName(rawName, state.decisions, oldName) ?: return state
        if (name == oldName) return state
        return state.copy(
            decisions = state.decisions.map { if (it == oldName) name else it },
            expenses = state.expenses.map { expense ->
                if (expense.decision == oldName) expense.copy(decision = name) else expense
            },
        )
    }

    fun deleteDecision(state: FinanceState, name: String): FinanceState {
        if (name == ExpenseDecisions.DEFAULT || name !in state.decisions) return state
        return state.copy(
            decisions = state.decisions.filterNot { it == name },
            expenses = state.expenses.map { expense ->
                if (expense.decision == name) expense.copy(decision = ExpenseDecisions.DEFAULT) else expense
            },
        )
    }

    fun setCategoryColor(state: FinanceState, category: String, hex: String): FinanceState {
        val normalized = ExpenseCategoryPalette.normalize(hex) ?: return state
        if (category !in state.categories) return state
        if (state.categoryColors[category] == normalized) return state
        return state.copy(categoryColors = state.categoryColors + (category to normalized))
    }

    private fun validName(rawName: String, existing: List<String>, replacing: String? = null): String? {
        val name = rawName.trim().replace(WHITESPACE, " ")
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH || name.any(Char::isISOControl)) return null
        if (existing.any { it != replacing && it.equals(name, ignoreCase = true) }) return null
        if (existing.size >= MAX_OPTIONS && replacing == null) return null
        return name
    }

    private val WHITESPACE = Regex("\\s+")
    private val REQUIRED_CATEGORIES = setOf(ExpenseCategories.DEFAULT, ExpenseCategories.UNCATEGORIZED)
    private const val MAX_NAME_LENGTH = 36
    private const val MAX_OPTIONS = 30
}
