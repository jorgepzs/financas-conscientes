package br.com.financasconscientes.reminderexperiment;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class ReminderNotifications {
    static final int FOREGROUND_ID = 2_041;
    static final int OPENED_ID = 2_042;
    static final int FINAL_ID = 2_043;
    private static final String SERVICE_CHANNEL = "picpay_monitor";
    private static final String REMINDER_CHANNEL = "picpay_reminders";

    private ReminderNotifications() { }

    static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(
                SERVICE_CHANNEL,
                "Monitor do PicPay",
                NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(
                REMINDER_CHANNEL,
                "Lembretes do PicPay",
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    static Notification foreground(Context context) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(
                context, 2041, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context, SERVICE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Lembretes do PicPay ativos")
                .setContentText("Monitoramento local de abertura habilitado.")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pendingOpen)
                .build();
    }

    static Notification opened(Context context, String sessionId) {
        return new Notification.Builder(context, REMINDER_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("PicPay aberto")
                .setContentText("Se fizer um Pix, compartilhe o comprovante com o Finanças Conscientes.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Se fizer um Pix, compartilhe o comprovante com o Finanças Conscientes."))
                .setAutoCancel(true)
                .setContentIntent(mainActivityPendingIntent(context, 2042, sessionId))
                .build();
    }

    static Notification finalReminder(Context context, String sessionId) {
        Notification.Builder builder = new Notification.Builder(context, REMINDER_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Faltou o comprovante?")
                .setContentText("Se você fez um Pix, volte ao PicPay e compartilhe o comprovante.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Se você fez um Pix, volte ao PicPay e compartilhe o comprovante."))
                .setAutoCancel(true)
                .setContentIntent(mainActivityPendingIntent(context, 2043, sessionId));

        Intent openPicPay = actionIntent(context, ActionRouterActivity.OPEN_PICPAY, sessionId);
        if (context.getPackageManager().getLaunchIntentForPackage(PicPayReminderService.PICPAY_PACKAGE) != null) {
            builder.addAction(android.R.drawable.ic_menu_view, "Abrir PicPay", PendingIntent.getActivity(
                    context, 2044, openPicPay, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        Intent register = actionIntent(context, ActionRouterActivity.REGISTER_WITHOUT_RECEIPT, sessionId);
        if (context.getPackageManager().getLaunchIntentForPackage(PicPayReminderService.MAIN_APP_PACKAGE) != null) {
            builder.addAction(android.R.drawable.ic_menu_edit, "Registrar sem comprovante", PendingIntent.getActivity(
                    context, 2045, register, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        Intent noPix = actionIntent(context, ActionRouterActivity.NO_PIX, sessionId);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Não fiz Pix", PendingIntent.getActivity(
                context, 2046, noPix, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return builder.build();
    }

    private static PendingIntent mainActivityPendingIntent(Context context, int requestCode, String sessionId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("reminder_session_id", sessionId);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent actionIntent(Context context, String action, String sessionId) {
        Intent intent = new Intent(context, ActionRouterActivity.class);
        intent.setAction(action);
        intent.putExtra("reminder_session_id", sessionId);
        return intent;
    }

    static void cancelReminders(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(OPENED_ID);
            manager.cancel(FINAL_ID);
        }
    }
}
