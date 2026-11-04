package br.com.financasconscientes.money

import br.com.financasconscientes.csv.PicPayCsvParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Cent-entry behavior used by banking apps: each digit shifts the amount left by one place. */
object BankCurrencyMask {
    private val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))

    fun appendDigit(currentCents: Long, digit: Char): Long? {
        if (!digit.isDigit()) return null
        val value = digit.digitToInt().toLong()
        if (currentCents < 0L || currentCents > (Long.MAX_VALUE - value) / 10L) return null
        return currentCents * 10L + value
    }

    fun appendDigits(currentCents: Long, digits: CharSequence): Long? {
        var result = currentCents
        digits.forEach { digit -> result = appendDigit(result, digit) ?: return null }
        return result
    }

    fun removeLastDigit(currentCents: Long): Long = (currentCents.coerceAtLeast(0L)) / 10L

    /** Paste/accessibility input keeps ordinary decimal meaning, so 0.1 and 0,1 are ten cents. */
    fun parsePastedAmount(raw: String): Long? =
        runCatching { PicPayCsvParser.parseAmountCents(raw) }.getOrNull()

    fun format(cents: Long): String = formatter.format(BigDecimal.valueOf(cents, 2))
}
