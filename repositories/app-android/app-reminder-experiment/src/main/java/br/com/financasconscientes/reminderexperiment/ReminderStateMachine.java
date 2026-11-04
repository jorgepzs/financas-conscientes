package br.com.financasconscientes.reminderexperiment;

import java.util.UUID;

/** Pure state transitions for the local PicPay reminder experiment. */
final class ReminderStateMachine {
    static final long EXIT_DEBOUNCE_MS = 7_000L;
    static final long SESSION_COOLDOWN_MS = 45_000L;

    enum Phase {
        IDLE,
        PICPAY_ACTIVE,
        EXIT_PENDING,
        RECEIPT_RECEIVED,
        REMINDER_SENT,
        DISMISSED
    }

    static final class Snapshot {
        Phase phase = Phase.IDLE;
        String sessionId = "";
        long startedAtMillis;
        long exitDeadlineMillis;
        long lastSessionEndedAtMillis;
        boolean receiptReceived;
        boolean initialNotificationSent;
        boolean finalNotificationSent;

        Snapshot copy() {
            Snapshot copy = new Snapshot();
            copy.phase = phase;
            copy.sessionId = sessionId;
            copy.startedAtMillis = startedAtMillis;
            copy.exitDeadlineMillis = exitDeadlineMillis;
            copy.lastSessionEndedAtMillis = lastSessionEndedAtMillis;
            copy.receiptReceived = receiptReceived;
            copy.initialNotificationSent = initialNotificationSent;
            copy.finalNotificationSent = finalNotificationSent;
            return copy;
        }
    }

    private final Snapshot state;

    ReminderStateMachine(Snapshot initialState) {
        state = initialState == null ? new Snapshot() : initialState.copy();
    }

    /** Returns true exactly when this resume starts a session and sends its first reminder. */
    boolean onPicPayResumed(long nowMillis) {
        if (state.phase == Phase.PICPAY_ACTIVE) return false;
        if (state.phase == Phase.EXIT_PENDING) {
            state.phase = Phase.PICPAY_ACTIVE;
            state.exitDeadlineMillis = 0L;
            return false;
        }

        if (state.phase != Phase.IDLE
                && nowMillis - state.lastSessionEndedAtMillis < SESSION_COOLDOWN_MS) {
            return false;
        }

        state.phase = Phase.PICPAY_ACTIVE;
        state.sessionId = UUID.randomUUID().toString();
        state.startedAtMillis = nowMillis;
        state.exitDeadlineMillis = 0L;
        state.receiptReceived = false;
        state.initialNotificationSent = true;
        state.finalNotificationSent = false;
        return true;
    }

    void onPicPayLeft(long nowMillis) {
        if (state.phase != Phase.PICPAY_ACTIVE) return;
        state.phase = Phase.EXIT_PENDING;
        state.exitDeadlineMillis = nowMillis + EXIT_DEBOUNCE_MS;
    }

    /** Returns true when the final reminder should be posted. Locked screens defer it. */
    boolean onTimeAdvanced(long nowMillis, boolean screenUnlocked) {
        if (state.phase != Phase.EXIT_PENDING
                || !screenUnlocked
                || nowMillis < state.exitDeadlineMillis) {
            return false;
        }
        state.phase = Phase.REMINDER_SENT;
        state.finalNotificationSent = true;
        state.lastSessionEndedAtMillis = nowMillis;
        state.exitDeadlineMillis = 0L;
        return true;
    }

    /** CSV shares are ignored. Any other ACTION_SEND received while a session exists counts. */
    boolean onShareReceived(long nowMillis, boolean isCsv) {
        if (isCsv || state.sessionId.isEmpty() || state.receiptReceived) return false;
        if (state.phase != Phase.PICPAY_ACTIVE
                && state.phase != Phase.EXIT_PENDING
                && state.phase != Phase.REMINDER_SENT) {
            return false;
        }
        state.receiptReceived = true;
        state.phase = Phase.RECEIPT_RECEIVED;
        state.exitDeadlineMillis = 0L;
        state.lastSessionEndedAtMillis = nowMillis;
        return true;
    }

    void dismiss(long nowMillis) {
        if (state.phase == Phase.IDLE || state.phase == Phase.DISMISSED) return;
        state.phase = Phase.DISMISSED;
        state.lastSessionEndedAtMillis = nowMillis;
        state.exitDeadlineMillis = 0L;
    }

    void reset() {
        state.phase = Phase.IDLE;
        state.sessionId = "";
        state.startedAtMillis = 0L;
        state.exitDeadlineMillis = 0L;
        state.lastSessionEndedAtMillis = 0L;
        state.receiptReceived = false;
        state.initialNotificationSent = false;
        state.finalNotificationSent = false;
    }

    Snapshot snapshot() {
        return state.copy();
    }

    Phase phase() {
        return state.phase;
    }
}
