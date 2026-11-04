package br.com.financasconscientes.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseEvidenceTest {
    @Test
    fun `legacy record without attachment migrates with no receipt evidence`() {
        val legacy = expense(fingerprint = null)

        assertEquals(null, legacy.receiptAttachmentId)
        assertEquals(EvidenceType.NONE, ExpenseEvidence.classify(legacy, hasValidReceipt = false, emptySet()))
    }

    @Test
    fun `receipt and statement evidence remain distinct and combine only when both exist`() {
        val receiptOnly = expense(fingerprint = null, attachmentId = "attachment")
        val statementOnly = expense(fingerprint = "csv-fingerprint")
        val both = expense(fingerprint = "csv-fingerprint", attachmentId = "attachment")
        val fingerprints = setOf("csv-fingerprint")

        assertEquals(EvidenceType.RECEIPT, ExpenseEvidence.classify(receiptOnly, true, fingerprints))
        assertEquals(EvidenceType.NONE, ExpenseEvidence.classify(receiptOnly, false, fingerprints))
        assertEquals(EvidenceType.STATEMENT, ExpenseEvidence.classify(statementOnly, false, fingerprints))
        assertEquals(EvidenceType.BOTH, ExpenseEvidence.classify(both, true, fingerprints))
    }

    @Test
    fun `stale transaction fingerprint does not claim statement proof`() {
        val expense = expense(fingerprint = "removed-row")

        assertEquals(EvidenceType.NONE, ExpenseEvidence.classify(expense, false, emptySet()))
    }

    private fun expense(fingerprint: String?, attachmentId: String? = null) = ExpenseRecord(
        id = "record",
        createdAtEpochMillis = 1,
        transactionFingerprint = fingerprint,
        amountCents = 500,
        what = "Teste",
        where = "",
        category = "Outro",
        decision = "Não informado",
        source = "manual",
        receiptAttachmentId = attachmentId,
        receiptMimeType = attachmentId?.let { "image/png" },
        receiptSizeBytes = attachmentId?.let { 12L },
    )
}
