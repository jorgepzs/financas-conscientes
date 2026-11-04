package br.com.financasconscientes.model

import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategoriesTest {
    @Test
    fun `required fallbacks remain available to selectors`() {
        assertTrue(ExpenseCategories.DEFAULT in ExpenseCategories.all)
        assertTrue(ExpenseCategories.UNCATEGORIZED in ExpenseCategories.all)
        assertTrue(ExpenseDecisions.DEFAULT in ExpenseDecisions.all)
    }
}
