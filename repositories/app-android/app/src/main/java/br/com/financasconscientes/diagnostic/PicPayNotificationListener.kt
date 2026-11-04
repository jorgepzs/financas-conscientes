package br.com.financasconscientes.diagnostic

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.com.financasconscientes.diagnostic.data.EncryptedEventStore
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PicPayNotificationListener : NotificationListenerService() {
    private val writer: ExecutorService = Executors.newSingleThreadExecutor()
    private val eventStore by lazy { EncryptedEventStore(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != PICPAY_PACKAGE) return

        // Do not inspect or retain notification fields until the exact package check passes.
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
            .take(MAX_FIELD_LENGTH)
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()
            .orEmpty()
            .take(MAX_FIELD_LENGTH)

        val event = DiagnosticEvent(
            id = UUID.randomUUID().toString(),
            postedAtEpochMillis = sbn.postTime,
            packageName = PICPAY_PACKAGE,
            title = title,
            text = body,
        )

        writer.execute {
            runCatching { eventStore.append(event) }
        }
    }

    override fun onDestroy() {
        writer.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val PICPAY_PACKAGE = "com.picpay"
        const val MAX_FIELD_LENGTH = 600
    }
}
