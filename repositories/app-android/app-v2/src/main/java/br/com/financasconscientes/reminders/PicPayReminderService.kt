package br.com.financasconscientes.reminders

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import br.com.financasconscientes.MainActivity
import br.com.financasconscientes.R
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PicPayReminderService : Service() {
    private val store by lazy { ReminderSessionStore(applicationContext) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var pollTask: ScheduledFuture<*>? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                store.withStateLock {
                    store.enabled = false
                    store.pendingEnable = false
                    store.clearSession()
                    cancelSessionNotifications()
                }
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_NOT_A_PIX -> {
                store.withStateLock {
                    store.transition { current ->
                        ReminderStateMachine.close(current, System.currentTimeMillis())
                    }
                    cancelSessionNotifications()
                }
                if (pollTask == null) stopSelf(startId)
                return START_STICKY
            }
            ACTION_START, null -> Unit
        }
        if (!store.enabled) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!hasUsageAccess() || !reminderNotificationsEnabled()) {
            store.withStateLock {
                store.enabled = false
                store.clearSession()
                cancelSessionNotifications()
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startMonitoringForeground()
        if (pollTask == null) {
            pollTask = scheduler.scheduleWithFixedDelay(
                { runCatching(::pollUsageEvents) },
                0L,
                POLL_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep monitoring only while the user has explicitly enabled it.
    }

    override fun onDestroy() {
        pollTask?.cancel(true)
        pollTask = null
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoringForeground() {
        val notification = serviceNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_SERVICE, notification)
        }
    }

    private fun stopMonitoring() {
        pollTask?.cancel(true)
        pollTask = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pollUsageEvents() {
        if (!store.enabled) {
            stopSelf()
            return
        }
        if (!hasUsageAccess() || !reminderNotificationsEnabled()) {
            store.withStateLock {
                store.enabled = false
                store.clearSession()
                cancelSessionNotifications()
            }
            stopSelf()
            return
        }
        store.serviceHeartbeatMillis = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val lastCursor = store.pollCursorMillis
        val begin = if (lastCursor <= 0L) now else maxOf(now - INITIAL_LOOKBACK_MILLIS, lastCursor - OVERLAP_MILLIS)
        val events = runCatching { manager.queryEvents(begin, now) }.getOrNull() ?: return
        val resumedType = if (Build.VERSION.SDK_INT >= 29) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        val pausedType = if (Build.VERSION.SDK_INT >= 29) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // Discard all non-PicPay events in memory; never persist or log other package names.
            if (event.packageName != PICPAY_PACKAGE) continue
            val transition = when (event.eventType) {
                resumedType -> store.transition { state -> ReminderStateMachine.open(state, event.timeStamp) }
                pausedType -> store.transition { state -> ReminderStateMachine.exit(state, event.timeStamp) }
                else -> null
            } ?: continue
            publishSignals(transition.signals, transition.state.sessionId)
        }
        store.pollCursorMillis = now

        val tick = store.transition { state ->
            ReminderStateMachine.tick(state, now, isDeviceUnlocked())
        }
        if (tick.state.phase == ReminderPhase.RECEIPT_RECEIVED || tick.state.phase == ReminderPhase.CLOSED) {
            store.withStateLock { current ->
                if (current.sessionId == tick.state.sessionId && current.phase == tick.state.phase) {
                    cancelSessionNotifications()
                }
            }
        }
        publishSignals(tick.signals, tick.state.sessionId)
    }

    private fun isDeviceUnlocked(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    private fun hasUsageAccess(): Boolean = runCatching {
        val appOps = getSystemService(android.app.AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName,
            )
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun reminderNotificationsEnabled(): Boolean {
        if (!notifications.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val hasDisabledChannel = listOf(CHANNEL_SERVICE_ID, CHANNEL_REMINDERS_ID)
                .any { channelId ->
                    notifications.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
                }
            if (hasDisabledChannel) return false
        }
        return true
    }

    private fun publishSignals(signals: List<ReminderSignal>, expectedSessionId: String?) {
        signals.forEach { signal ->
            store.withStateLock { state ->
                if (!store.enabled || state.sessionId != expectedSessionId) return@withStateLock
                when (signal) {
                    ReminderSignal.PIC_PAY_OPENED -> {
                        if (state.phase == ReminderPhase.PICPAY_ACTIVE || state.phase == ReminderPhase.EXIT_PENDING) {
                            notifications.cancel(NOTIFICATION_MISSING)
                            notifications.notify(NOTIFICATION_OPEN, openedNotification())
                        }
                    }
                    ReminderSignal.MISSING_RECEIPT -> {
                        if (state.phase == ReminderPhase.REMINDER_SENT && !state.receiptReceived) {
                            notifications.notify(NOTIFICATION_MISSING, missingReceiptNotification())
                        }
                    }
                }
            }
        }
    }

    private fun cancelSessionNotifications() {
        notifications.cancel(NOTIFICATION_OPEN)
        notifications.cancel(NOTIFICATION_MISSING)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE_ID, "Monitor local do PicPay", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aviso discreto enquanto o monitor opcional está ativo."
                setShowBadge(false)
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS_ID, "Lembretes do PicPay", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos locais para lembrar você de compartilhar um comprovante."
                setShowBadge(false)
            },
        )
    }

    private fun serviceNotification(): Notification = notificationBuilder(CHANNEL_SERVICE_ID)
        .setContentTitle("Lembretes do PicPay ativos")
        .setContentText("Monitoramento local habilitado por você.")
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setContentIntent(mainPendingIntent(REQUEST_MAIN))
        .build()

    private fun openedNotification(): Notification = notificationBuilder(CHANNEL_REMINDERS_ID)
        .setContentTitle("PicPay aberto")
        .setContentText("Se fizer um Pix, compartilhe o comprovante com o Finanças Conscientes.")
        .setAutoCancel(true)
        .setContentIntent(mainPendingIntent(REQUEST_MAIN))
        .build()

    private fun missingReceiptNotification(): Notification = notificationBuilder(CHANNEL_REMINDERS_ID)
        .setContentTitle("Faltou o comprovante?")
        .setContentText("Se você fez um Pix, volte ao PicPay e compartilhe o comprovante.")
        .setAutoCancel(true)
        .setContentIntent(mainPendingIntent(REQUEST_MAIN))
        .addAction(0, "Abrir PicPay", picPayPendingIntent())
        .addAction(0, "Registrar sem comprovante", registerPendingIntent())
        .addAction(0, "Não fiz Pix", servicePendingIntent(ACTION_NOT_A_PIX, REQUEST_NOT_A_PIX))
        .build()

    private fun notificationBuilder(channelId: String): Notification.Builder = (if (Build.VERSION.SDK_INT >= 26) {
        Notification.Builder(this, channelId)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
    }).setSmallIcon(R.drawable.ic_stat_reminder)
        .setColor(getColor(R.color.primary))
        .setPriority(Notification.PRIORITY_DEFAULT)

    private fun mainPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, requestCode, intent, pendingIntentFlags())
    }

    private fun registerPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_REGISTER_WITHOUT_RECEIPT)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, REQUEST_REGISTER, intent, pendingIntentFlags())
    }

    private fun picPayPendingIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(PICPAY_PACKAGE)
            ?: Intent(this, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, REQUEST_PICPAY, launch, pendingIntentFlags())
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, PicPayReminderService::class.java).setAction(action),
        pendingIntentFlags(),
    )

    private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    companion object Actions {
        const val CHANNEL_SERVICE_ID = "picpay_reminder_service"
        const val CHANNEL_REMINDERS_ID = "picpay_reminders"
        const val NOTIFICATION_SERVICE = 7201
        const val NOTIFICATION_OPEN = 7202
        const val NOTIFICATION_MISSING = 7203
        const val REQUEST_MAIN = 7210
        const val REQUEST_PICPAY = 7211
        const val REQUEST_REGISTER = 7212
        const val REQUEST_NOT_A_PIX = 7213
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val INITIAL_LOOKBACK_MILLIS = 60_000L
        const val OVERLAP_MILLIS = 2_000L
        const val PICPAY_PACKAGE = "com.picpay"
        const val ACTION_START = "br.com.financasconscientes.reminders.START"
        const val ACTION_STOP = "br.com.financasconscientes.reminders.STOP"
        const val ACTION_NOT_A_PIX = "br.com.financasconscientes.reminders.NOT_A_PIX"
        const val ACTION_REGISTER_WITHOUT_RECEIPT = "br.com.financasconscientes.reminders.REGISTER_WITHOUT_RECEIPT"

        fun markReceiptReceived(context: Context) {
            val sessionStore = ReminderSessionStore(context)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            sessionStore.withStateLock {
                sessionStore.transition { state -> ReminderStateMachine.receipt(state, System.currentTimeMillis()) }
                notificationManager.cancel(NOTIFICATION_OPEN)
                notificationManager.cancel(NOTIFICATION_MISSING)
            }
        }

        fun start(context: Context) {
            val sessionStore = ReminderSessionStore(context)
            if (sessionStore.pollCursorMillis <= 0L) sessionStore.pollCursorMillis = System.currentTimeMillis()
            val intent = Intent(context, PicPayReminderService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PicPayReminderService::class.java).setAction(ACTION_STOP))
        }
    }
}
