package br.com.financasconscientes.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderStateMachineTest {
    @Test
    fun `first open starts one session and duplicate resume is ignored`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val duplicate = ReminderStateMachine.open(opened.state, 1_100)

        assertEquals(ReminderPhase.PICPAY_ACTIVE, opened.state.phase)
        assertEquals(listOf(ReminderSignal.PIC_PAY_OPENED), opened.signals)
        assertTrue(duplicate.signals.isEmpty())
        assertEquals(opened.state.sessionId, duplicate.state.sessionId)
    }

    @Test
    fun `quick return cancels pending exit without a second opening notice`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val returned = ReminderStateMachine.open(exited.state, 4_000)

        assertEquals(ReminderPhase.PICPAY_ACTIVE, returned.state.phase)
        assertEquals(null, returned.state.exitAtMillis)
        assertTrue(returned.signals.isEmpty())
        assertEquals(opened.state.sessionId, returned.state.sessionId)
    }

    @Test
    fun `exit sends a missing reminder once after debounce`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val early = ReminderStateMachine.tick(exited.state, 7_999, unlocked = true)
        val due = ReminderStateMachine.tick(early.state, 8_000, unlocked = true)
        val duplicate = ReminderStateMachine.tick(due.state, 9_000, unlocked = true)

        assertTrue(early.signals.isEmpty())
        assertEquals(listOf(ReminderSignal.MISSING_RECEIPT), due.signals)
        assertEquals(ReminderPhase.REMINDER_SENT, due.state.phase)
        assertTrue(duplicate.signals.isEmpty())
    }

    @Test
    fun `locked device defers the missing reminder until unlocked`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val locked = ReminderStateMachine.tick(exited.state, 20_000, unlocked = false)
        val unlocked = ReminderStateMachine.tick(locked.state, 20_500, unlocked = true)

        assertTrue(locked.signals.isEmpty())
        assertEquals(ReminderPhase.EXIT_PENDING, locked.state.phase)
        assertEquals(listOf(ReminderSignal.MISSING_RECEIPT), unlocked.signals)
    }

    @Test
    fun `received proof suppresses exit reminder and is not financial data`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val received = ReminderStateMachine.receipt(exited.state, 3_000)
        val ticked = ReminderStateMachine.tick(received.state, 20_000, unlocked = true)

        assertEquals(ReminderPhase.RECEIPT_RECEIVED, ticked.state.phase)
        assertTrue(ticked.state.receiptReceived)
        assertTrue(ticked.signals.isEmpty())
    }

    @Test
    fun `proof received just after reminder clears the outstanding reminder`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val reminded = ReminderStateMachine.tick(exited.state, 8_000, unlocked = true)
        val received = ReminderStateMachine.receipt(reminded.state, 9_000)

        assertEquals(ReminderPhase.RECEIPT_RECEIVED, received.state.phase)
        assertTrue(received.state.receiptReceived)
        assertTrue(received.signals.isEmpty())
    }

    @Test
    fun `receipt received while PicPay stays open suppresses duplicate activity events`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val received = ReminderStateMachine.receipt(opened.state, 2_000)
        val duplicateActivity = ReminderStateMachine.open(received.state, 3_000)
        val exited = ReminderStateMachine.exit(duplicateActivity.state, 4_000)
        val quickReturn = ReminderStateMachine.open(exited.state, 5_000)
        val leftAgain = ReminderStateMachine.exit(quickReturn.state, 6_000)
        val closed = ReminderStateMachine.tick(leftAgain.state, 12_000, unlocked = true)
        val reopened = ReminderStateMachine.open(closed.state, 13_000)

        assertTrue(duplicateActivity.signals.isEmpty())
        assertEquals(ReminderPhase.EXIT_PENDING, exited.state.phase)
        assertEquals(ReminderPhase.RECEIPT_RECEIVED, quickReturn.state.phase)
        assertEquals(ReminderPhase.CLOSED, closed.state.phase)
        assertEquals(listOf(ReminderSignal.PIC_PAY_OPENED), reopened.signals)
    }

    @Test
    fun `reopening after a final reminder starts a new session immediately`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val reminded = ReminderStateMachine.tick(exited.state, 8_000, unlocked = true)
        val reopened = ReminderStateMachine.open(reminded.state, 9_000)

        assertNotEquals(reminded.state.sessionId, reopened.state.sessionId)
        assertEquals(ReminderPhase.PICPAY_ACTIVE, reopened.state.phase)
        assertEquals(listOf(ReminderSignal.PIC_PAY_OPENED), reopened.signals)
    }

    @Test
    fun `reopening after a confirmed exit starts a new session even when debounce was missed`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val reopened = ReminderStateMachine.open(exited.state, 9_000)

        assertNotEquals(opened.state.sessionId, reopened.state.sessionId)
        assertEquals(ReminderPhase.PICPAY_ACTIVE, reopened.state.phase)
        assertEquals(listOf(ReminderSignal.PIC_PAY_OPENED), reopened.signals)
    }

    @Test
    fun `reopening after proof received for a closed session starts a new session`() {
        val opened = ReminderStateMachine.open(ReminderState(), 1_000)
        val exited = ReminderStateMachine.exit(opened.state, 2_000)
        val received = ReminderStateMachine.receipt(exited.state, 3_000)
        val reopened = ReminderStateMachine.open(received.state, 9_000)

        assertNotEquals(opened.state.sessionId, reopened.state.sessionId)
        assertEquals(listOf(ReminderSignal.PIC_PAY_OPENED), reopened.signals)
    }
}
