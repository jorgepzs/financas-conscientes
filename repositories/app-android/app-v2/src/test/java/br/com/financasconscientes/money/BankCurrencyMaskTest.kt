package br.com.financasconscientes.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankCurrencyMaskTest {
    @Test
    fun `digits append from cents to reais like a bank input`() {
        assertEquals(1L, BankCurrencyMask.appendDigit(0L, '1'))
        assertEquals(10L, BankCurrencyMask.appendDigit(1L, '0'))
        assertEquals(100L, BankCurrencyMask.appendDigit(10L, '0'))
        assertEquals("R$ 0,01", BankCurrencyMask.format(1L).replace('\u00A0', ' '))
        assertEquals("R$ 0,10", BankCurrencyMask.format(10L).replace('\u00A0', ' '))
        assertEquals("R$ 1,00", BankCurrencyMask.format(100L).replace('\u00A0', ' '))
    }

    @Test
    fun `backspace removes the least significant digit`() {
        assertEquals(10L, BankCurrencyMask.removeLastDigit(100L))
        assertEquals(1L, BankCurrencyMask.removeLastDigit(10L))
        assertEquals(0L, BankCurrencyMask.removeLastDigit(1L))
        assertEquals(0L, BankCurrencyMask.removeLastDigit(0L))
    }

    @Test
    fun `pasted decimal values preserve fractional reais`() {
        assertEquals(10L, BankCurrencyMask.parsePastedAmount("0.1"))
        assertEquals(10L, BankCurrencyMask.parsePastedAmount("0,1"))
        assertEquals(1L, BankCurrencyMask.parsePastedAmount("0.01"))
        assertEquals(1L, BankCurrencyMask.parsePastedAmount("0,01"))
        assertEquals(10L, BankCurrencyMask.parsePastedAmount(BankCurrencyMask.format(10L)))
        assertEquals(10L, BankCurrencyMask.appendDigits(0L, "10"))
        assertNull(BankCurrencyMask.appendDigit(10L, ','))
        assertNull(BankCurrencyMask.parsePastedAmount("999999999999999999999999999999"))
    }
}
