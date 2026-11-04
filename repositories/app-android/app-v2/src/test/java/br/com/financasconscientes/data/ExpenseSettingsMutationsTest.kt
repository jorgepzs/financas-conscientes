package br.com.financasconscientes.data

import br.com.financasconscientes.model.ExpenseCategories
import br.com.financasconscientes.model.ExpenseDecisions
import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseSettingsMutationsTest {
    @Test
    fun `renaming a category preserves its color and updates existing records`() {
        val originalColor = "#123456"
        val state = state().copy(categoryColors = state().categoryColors + ("Lazer" to originalColor))

        val updated = ExpenseSettingsMutations.renameCategory(state, "Lazer", "Bem-estar")

        assertFalse("Lazer" in updated.categories)
        assertTrue("Bem-estar" in updated.categories)
        assertEquals(originalColor, updated.categoryColors["Bem-estar"])
        assertEquals("Bem-estar", updated.expenses.single().category)
        assertEquals("Impulso", updated.expenses.single().decision)
    }

    @Test
    fun `removing a used category explicitly leaves records uncategorized`() {
        val updated = ExpenseSettingsMutations.deleteCategory(state(), "Lazer")

        assertFalse("Lazer" in updated.categories)
        assertEquals(ExpenseCategories.UNCATEGORIZED, updated.expenses.single().category)
        assertEquals(10_00L, updated.expenses.single().amountCents)
    }

    @Test
    fun `removing a used decision returns records to not informed`() {
        val updated = ExpenseSettingsMutations.deleteDecision(state(), "Impulso")

        assertFalse("Impulso" in updated.decisions)
        assertEquals(ExpenseDecisions.DEFAULT, updated.expenses.single().decision)
        assertEquals("Lazer", updated.expenses.single().category)
    }

    @Test
    fun `category color changes persist as normalized hex`() {
        val updated = ExpenseSettingsMutations.setCategoryColor(state(), "Lazer", " #abcdef ")

        assertEquals("#ABCDEF", updated.categoryColors["Lazer"])
        assertEquals(state(), ExpenseSettingsMutations.setCategoryColor(state(), "Lazer", "red"))
    }

    @Test
    fun `reserved fallbacks cannot be renamed or deleted`() {
        val initial = state()

        assertEquals(initial, ExpenseSettingsMutations.renameCategory(initial, "Outro", "Geral"))
        assertEquals(initial, ExpenseSettingsMutations.deleteCategory(initial, ExpenseCategories.UNCATEGORIZED))
        assertEquals(initial, ExpenseSettingsMutations.renameDecision(initial, "Não informado", "Pendente"))
    }

    @Test
    fun `new option names are normalized and duplicates rejected`() {
        val initial = state()
        val added = ExpenseSettingsMutations.addCategory(initial, "  Café   e  lanche ")

        assertTrue("Café e lanche" in added.categories)
        assertNotEquals(initial, added)
        assertEquals(added, ExpenseSettingsMutations.addCategory(added, "café e lanche"))
    }

    private fun state(): FinanceState = FinanceState(
        expenses = listOf(
            ExpenseRecord(
                id = "expense-1",
                createdAtEpochMillis = 1L,
                transactionFingerprint = null,
                amountCents = 1_000L,
                what = "Cinema",
                where = "Centro",
                category = "Lazer",
                decision = "Impulso",
                source = "manual",
                receiptAttachmentId = "receipt-1",
                receiptMimeType = "image/jpeg",
                receiptSizeBytes = 100L,
            ),
        ),
    )
}
