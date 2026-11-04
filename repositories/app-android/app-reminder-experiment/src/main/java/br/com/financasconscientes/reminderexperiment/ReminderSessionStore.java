package br.com.financasconscientes.reminderexperiment;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores only the current session's ID, timestamps, phase, and booleans. */
final class ReminderSessionStore {
    private static final String PREFS = "picpay_reminder_experiment";
    private static final String KEY_ENABLED = "monitor_enabled";
    private static final String KEY_HEARTBEAT = "service_heartbeat";
    private static final String KEY_INTERRUPTED = "monitor_interrupted";
    private static final String KEY_PHASE = "session_phase";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_STARTED = "session_started";
    private static final String KEY_EXIT_DEADLINE = "exit_deadline";
    private static final String KEY_LAST_ENDED = "last_session_ended";
    private static final String KEY_RECEIPT = "receipt_received";
    private static final String KEY_INITIAL_SENT = "initial_notification_sent";
    private static final String KEY_FINAL_SENT = "final_notification_sent";

    private final SharedPreferences preferences;

    ReminderSessionStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isEnabled() { return preferences.getBoolean(KEY_ENABLED, false); }

    void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    long heartbeatAtMillis() { return preferences.getLong(KEY_HEARTBEAT, 0L); }

    boolean wasInterrupted() { return preferences.getBoolean(KEY_INTERRUPTED, false); }

    void setInterrupted(boolean interrupted) {
        preferences.edit().putBoolean(KEY_INTERRUPTED, interrupted).commit();
    }

    void setHeartbeat(long timestamp) {
        preferences.edit().putLong(KEY_HEARTBEAT, timestamp).apply();
    }

    ReminderStateMachine loadMachine() {
        ReminderStateMachine.Snapshot snapshot = new ReminderStateMachine.Snapshot();
        String phase = preferences.getString(KEY_PHASE, ReminderStateMachine.Phase.IDLE.name());
        try {
            snapshot.phase = ReminderStateMachine.Phase.valueOf(phase);
        } catch (IllegalArgumentException ignored) {
            snapshot.phase = ReminderStateMachine.Phase.IDLE;
        }
        snapshot.sessionId = preferences.getString(KEY_SESSION_ID, "");
        snapshot.startedAtMillis = preferences.getLong(KEY_STARTED, 0L);
        snapshot.exitDeadlineMillis = preferences.getLong(KEY_EXIT_DEADLINE, 0L);
        snapshot.lastSessionEndedAtMillis = preferences.getLong(KEY_LAST_ENDED, 0L);
        snapshot.receiptReceived = preferences.getBoolean(KEY_RECEIPT, false);
        snapshot.initialNotificationSent = preferences.getBoolean(KEY_INITIAL_SENT, false);
        snapshot.finalNotificationSent = preferences.getBoolean(KEY_FINAL_SENT, false);
        return new ReminderStateMachine(snapshot);
    }

    boolean saveMachine(ReminderStateMachine machine) {
        ReminderStateMachine.Snapshot snapshot = machine.snapshot();
        return preferences.edit()
                .putString(KEY_PHASE, snapshot.phase.name())
                .putString(KEY_SESSION_ID, snapshot.sessionId)
                .putLong(KEY_STARTED, snapshot.startedAtMillis)
                .putLong(KEY_EXIT_DEADLINE, snapshot.exitDeadlineMillis)
                .putLong(KEY_LAST_ENDED, snapshot.lastSessionEndedAtMillis)
                .putBoolean(KEY_RECEIPT, snapshot.receiptReceived)
                .putBoolean(KEY_INITIAL_SENT, snapshot.initialNotificationSent)
                .putBoolean(KEY_FINAL_SENT, snapshot.finalNotificationSent)
                .commit();
    }

    void clearSession() {
        preferences.edit()
                .remove(KEY_PHASE)
                .remove(KEY_SESSION_ID)
                .remove(KEY_STARTED)
                .remove(KEY_EXIT_DEADLINE)
                .remove(KEY_LAST_ENDED)
                .remove(KEY_RECEIPT)
                .remove(KEY_INITIAL_SENT)
                .remove(KEY_FINAL_SENT)
                .remove(KEY_HEARTBEAT)
                .remove(KEY_INTERRUPTED)
                .apply();
    }
}
