package br.com.financasconscientes.csv

import br.com.financasconscientes.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class PicPayCsvParserTest {
    @Test
    fun parsesObservedPicPayFormat() {
        val csv = """
            data,hora,tipo,"origem / destino",valor,"forma de pagamento"
            2026-10-02,08:15,Pix enviado,PESSOA A,"−R${'$'} 12,34",Com saldo
            2026-10-02,09:20,Pix recebido,PESSOA B,"+R${'$'} 1.234,56",
        """.trimIndent()

        val result = PicPayCsvParser.parse(csv.toByteArray(), importedAtEpochMillis = 10L)

        assertEquals(',', result.delimiter)
        assertEquals(2, result.transactions.size)
        assertEquals(-1234L, result.transactions[0].amountCents)
        assertEquals(TransactionDirection.OUTFLOW, result.transactions[0].direction)
        assertEquals("Com saldo", result.transactions[0].paymentMethod)
        assertNotNull(result.transactions[0].occurredAtEpochMillis)
        assertEquals(123456L, result.transactions[1].amountCents)
        assertEquals(TransactionDirection.INFLOW, result.transactions[1].direction)
    }

    @Test
    fun preservesLegitimateIdenticalRowsWithStableFingerprints() {
        val csv = """
            data,hora,tipo,"origem / destino",valor,"forma de pagamento"
            2026-10-02,08:15,Pix enviado,PESSOA A,"−R${'$'} 10,00",Com saldo
            2026-10-02,08:15,Pix enviado,PESSOA A,"−R${'$'} 10,00",Com saldo
        """.trimIndent()

        val first = PicPayCsvParser.parse(csv.toByteArray()).transactions
        val second = PicPayCsvParser.parse(csv.toByteArray()).transactions

        assertEquals(2, first.size)
        assertNotEquals(first[0].fingerprint, first[1].fingerprint)
        assertEquals(first.map { it.fingerprint }, second.map { it.fingerprint })
    }

    @Test
    fun parsesBrazilianAndInternationalAmounts() {
        assertEquals(-123456L, PicPayCsvParser.parseAmountCents("−R${'$'} 1.234,56"))
        assertEquals(123456L, PicPayCsvParser.parseAmountCents("+R${'$'} 1.234,56"))
        assertEquals(123456L, PicPayCsvParser.parseAmountCents("1,234.56"))
        assertEquals(-1234L, PicPayCsvParser.parseAmountCents("(12,34)"))
    }

    @Test
    fun handlesQuotedDelimiterAndEscapedQuote() {
        val rows = PicPayCsvParser.parseRows("a,b\n\"um, dois\",\"texto \"\"citado\"\"\"", ',')
        assertEquals(listOf("um, dois", "texto \"citado\""), rows[1])
    }

    @Test
    fun rejectsCsvWithoutAmountColumn() {
        val csv = "data,tipo\n2026-10-02,Pix enviado"
        assertThrows(CsvImportException::class.java) {
            PicPayCsvParser.parse(csv.toByteArray())
        }
    }

    @Test
    fun parsesOptionalPrivateSampleWithoutLoggingContents() {
        val samplePath = System.getenv("PICPAY_CSV_SAMPLE")
        assumeTrue("PICPAY_CSV_SAMPLE não informado", !samplePath.isNullOrBlank())

        val result = PicPayCsvParser.parse(Files.readAllBytes(Path.of(samplePath)))

        assertEquals(
            listOf("data", "hora", "tipo", "origem / destino", "valor", "forma de pagamento"),
            result.headers,
        )
        assertEquals(true, result.transactions.isNotEmpty())
        assertEquals(true, result.transactions.any { it.direction == TransactionDirection.OUTFLOW })
        assertEquals(true, result.transactions.any { it.direction == TransactionDirection.INFLOW })
        assertEquals(true, result.transactions.all { it.occurredAtEpochMillis != null })
    }
}
