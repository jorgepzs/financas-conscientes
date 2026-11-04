package br.com.financasconscientes.reminders

import android.content.Context

/** Stores only opt-in, current-session timestamps/flags and a poll cursor. */
class ReminderSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_ENABLED, value).apply() }

    var pendingEnable: Boolean
        get() = preferences.getBoolean(KEY_PENDING_ENABLE, false)
        set(value) { preferences.edit().putBoolean(KEY_PENDING_ENABLE, value).apply() }

    var pollCursorMillis: Long
        get() = preferences.getLong(KEY_POLL_CURSOR, 0L)
        set(value) { preferences.edit().putLong(KEY_POLL_CURSOR, value).apply() }

    var serviceHeartbeatMillis: Long
        get() = preferences.getLong(KEY_SERVICE_HEARTBEAT, 0L)
        set(value) { preferences.edit().putLong(KEY_SERVICE_HEARTBEAT, value).apply() }

    fun load(): ReminderState = synchronized(STATE_LOCK) {
        val phase = runCatching {
            ReminderPhase.valueOf(preferences.getString(KEY_PHASE, null) ?: ReminderPhase.IDLE.name)
        }.getOrDefault(ReminderPhase.IDLE)
        ReminderState(
            sessionId = preferences.getString(KEY_SESSION_ID, null),
            phase = phase,
            openedAtMillis = preferences.getLong(KEY_OPENED_AT, -1L).takeIf { it >= 0L },
            exitAtMillis = preferences.getLong(KEY_EXIT_AT, -1L).takeIf { it >= 0L },
            receiptReceived = preferences.getBoolean(KEY_RECEIPT, false),
            initialReminderSent = preferences.getBoolean(KEY_INITIAL_SENT, false),
            finalReminderSent = preferences.getBoolean(KEY_FINAL_SENT, false),
            lastClosedAtMillis = preferences.getLong(KEY_CLOSED_AT, -1L).takeIf { it >= 0L },
        )
    }

    /** Performs a complete read/transition/write as one in-process transaction. */
    fun transition(transform: (ReminderState) -> ReminderTransition): ReminderTransition =
        synchronized(STATE_LOCK) {
            val current = load()
            val result = transform(current)
            if (result.state != current) saveLocked(result.state)
            result
        }

    /** Serializes notification side effects with state transitions such as receipt arrival. */
    fun withStateLock(action: (ReminderState) -> Unit) = synchronized(STATE_LOCK) {
        action(load())
    }

    private fun saveLocked(state: ReminderState) {
        preferences.edit()
            .putString(KEY_SESSION_ID, state.sessionId)
            .putString(KEY_PHASE, state.phase.name)
            .putLong(KEY_OPENED_AT, state.openedAtMillis ?: -1L)
            .putLong(KEY_EXIT_AT, state.exitAtMillis ?: -1L)
            .putBoolean(KEY_RECEIPT, state.receiptReceived)
            .putBoolean(KEY_INITIAL_SENT, state.initialReminderSent)
            .putBoolean(KEY_FINAL_SENT, state.finalReminderSent)
            .putLong(KEY_CLOSED_AT, state.lastClosedAtMillis ?: -1L)
            .apply()
    }

    fun clearSession() = synchronized(STATE_LOCK) {
        preferences.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_PHASE)
            .remove(KEY_OPENED_AT)
            .remove(KEY_EXIT_AT)
            .remove(KEY_RECEIPT)
            .remove(KEY_INITIAL_SENT)
            .remove(KEY_FINAL_SENT)
            .remove(KEY_CLOSED_AT)
            .remove(KEY_POLL_CURSOR)
            .remove(KEY_SERVICE_HEARTBEAT)
            .apply()
    }

    fun clearAll() = synchronized(STATE_LOCK) {
        preferences.edit().clear().apply()
    }

    private companion object {
        val STATE_LOCK = Any()
        const val FILE_NAME = "picpay_reminder_local"
        const val KEY_ENABLED = "enabled"
        const val KEY_PENDING_ENABLE = "pending_enable"
        const val KEY_POLL_CURSOR = "poll_cursor"
        const val KEY_SERVICE_HEARTBEAT = "service_heartbeat"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_PHASE = "phase"
        const val KEY_OPENED_AT = "opened_at"
        const val KEY_EXIT_AT = "exit_at"
        const val KEY_RECEIPT = "receipt_received"
        const val KEY_INITIAL_SENT = "initial_sent"
        const val KEY_FINAL_SENT = "final_sent"
        const val KEY_CLOSED_AT = "closed_at"
    }
}
