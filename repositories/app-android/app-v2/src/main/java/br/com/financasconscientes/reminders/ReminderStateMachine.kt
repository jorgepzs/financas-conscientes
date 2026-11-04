package br.com.financasconscientes.reminders

import java.util.UUID

enum class ReminderPhase { IDLE, PICPAY_ACTIVE, EXIT_PENDING, RECEIPT_RECEIVED, REMINDER_SENT, CLOSED }

enum class ReminderSignal { PIC_PAY_OPENED, MISSING_RECEIPT }

data class ReminderState(
    val sessionId: String? = null,
    val phase: ReminderPhase = ReminderPhase.IDLE,
    val openedAtMillis: Long? = null,
    val exitAtMillis: Long? = null,
    val receiptReceived: Boolean = false,
    val initialReminderSent: Boolean = false,
    val finalReminderSent: Boolean = false,
    val lastClosedAtMillis: Long? = null,
)

data class ReminderTransition(val state: ReminderState, val signals: List<ReminderSignal> = emptyList())

/** Pure local state machine. It creates no financial records and receives no transaction data. */
object ReminderStateMachine {
    const val EXIT_DEBOUNCE_MILLIS = 6_000L

    fun open(state: ReminderState, nowMillis: Long): ReminderTransition {
        when (state.phase) {
            ReminderPhase.PICPAY_ACTIVE -> return ReminderTransition(state)
            ReminderPhase.EXIT_PENDING -> {
                val exitedAt = state.exitAtMillis ?: nowMillis
                if (nowMillis - exitedAt < EXIT_DEBOUNCE_MILLIS) {
                    return ReminderTransition(
                        state.copy(
                            phase = if (state.receiptReceived) ReminderPhase.RECEIPT_RECEIVED else ReminderPhase.PICPAY_ACTIVE,
                            exitAtMillis = null,
                        ),
                    )
                }
            }
            ReminderPhase.RECEIPT_RECEIVED -> {
                if (state.exitAtMillis == null) return ReminderTransition(state)
                if (nowMillis - state.exitAtMillis < EXIT_DEBOUNCE_MILLIS) {
                    return ReminderTransition(state.copy(exitAtMillis = null))
                }
            }
            ReminderPhase.REMINDER_SENT, ReminderPhase.CLOSED -> Unit
            ReminderPhase.IDLE -> Unit
        }

        val next = ReminderState(
            sessionId = UUID.randomUUID().toString(),
            phase = ReminderPhase.PICPAY_ACTIVE,
            openedAtMillis = nowMillis,
            initialReminderSent = true,
            lastClosedAtMillis = state.lastClosedAtMillis,
        )
        return ReminderTransition(next, listOf(ReminderSignal.PIC_PAY_OPENED))
    }

    fun exit(state: ReminderState, nowMillis: Long): ReminderTransition = when (state.phase) {
        ReminderPhase.PICPAY_ACTIVE -> ReminderTransition(
            state.copy(phase = ReminderPhase.EXIT_PENDING, exitAtMillis = nowMillis),
        )
        ReminderPhase.RECEIPT_RECEIVED -> ReminderTransition(
            state.copy(phase = ReminderPhase.EXIT_PENDING, exitAtMillis = nowMillis),
        )
        else -> ReminderTransition(state)
    }

    fun receipt(state: ReminderState, nowMillis: Long): ReminderTransition = when (state.phase) {
        ReminderPhase.PICPAY_ACTIVE, ReminderPhase.EXIT_PENDING, ReminderPhase.REMINDER_SENT -> ReminderTransition(
            state.copy(
                phase = ReminderPhase.RECEIPT_RECEIVED,
                receiptReceived = true,
                exitAtMillis = when (state.phase) {
                    ReminderPhase.PICPAY_ACTIVE -> null
                    ReminderPhase.EXIT_PENDING -> state.exitAtMillis
                    ReminderPhase.REMINDER_SENT -> state.exitAtMillis ?: nowMillis
                    else -> null
                },
                lastClosedAtMillis = nowMillis,
            ),
        )
        else -> ReminderTransition(state)
    }

    fun tick(
        state: ReminderState,
        nowMillis: Long,
        unlocked: Boolean,
        debounceMillis: Long = EXIT_DEBOUNCE_MILLIS,
    ): ReminderTransition {
        if (state.phase != ReminderPhase.EXIT_PENDING) return ReminderTransition(state)
        val exitedAt = state.exitAtMillis ?: return ReminderTransition(state)
        if (nowMillis - exitedAt < debounceMillis || !unlocked) return ReminderTransition(state)
        if (state.receiptReceived) {
            return ReminderTransition(
                state.copy(phase = ReminderPhase.CLOSED, exitAtMillis = null, lastClosedAtMillis = nowMillis),
            )
        }
        return ReminderTransition(
            state.copy(
                phase = ReminderPhase.REMINDER_SENT,
                exitAtMillis = null,
                finalReminderSent = true,
                lastClosedAtMillis = nowMillis,
            ),
            listOf(ReminderSignal.MISSING_RECEIPT),
        )
    }

    fun close(state: ReminderState, nowMillis: Long): ReminderTransition = ReminderTransition(
        state.copy(
            phase = ReminderPhase.CLOSED,
            exitAtMillis = null,
            lastClosedAtMillis = nowMillis,
        ),
    )
}
