package br.com.financasconscientes.reminderexperiment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 401;
    private static final int REQUEST_USAGE_SETTINGS = 402;

    private ReminderSessionStore store;
    private TextView statusText;
    private TextView shareResultText;
    private Button configureButton;
    private Button disableButton;
    private String pendingShareMessage = "";
    private boolean pendingEnable;
    private boolean waitingForUsageSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ReminderSessionStore(this);
        processSharedIntent(getIntent());
        buildContent();
        shareResultText.setText(pendingShareMessage);
        refreshStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processSharedIntent(intent);
        shareResultText.setText(pendingShareMessage);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForUsageSettings) {
            waitingForUsageSettings = false;
            if (AndroidPermissions.hasUsageAccess(this)) {
                continueEnableFlow();
            } else {
                pendingEnable = false;
            }
        }
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (AndroidPermissions.hasNotifications(this)) {
                startMonitor();
            } else {
                pendingEnable = false;
                Toast.makeText(this, "Ative as notificações para usar os lembretes.", Toast.LENGTH_LONG).show();
                refreshStatus();
            }
        }
    }

    private void buildContent() {
        int padding = dp(24);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, dp(36), padding, padding);
        content.setBackgroundColor(0xFFF4F6F3);
        scrollView.addView(content);

        TextView title = text("LEMBRETES DO PICPAY", 22, true);
        content.addView(title, matchWrap());

        TextView description = text(
                "Lembre quando o PicPay abrir e novamente se você sair sem compartilhar um comprovante. "
                        + "O monitor observa somente eventos de abertura do pacote com.picpay. "
                        + "Não lê telas, notificações, contas ou valores e não cria despesas.",
                16, false);
        description.setPadding(0, dp(14), 0, dp(22));
        content.addView(description, matchWrap());

        TextView consent = text(
                "Ao ativar, o Android mantém uma notificação discreta enquanto o monitor estiver ligado. "
                        + "O estado local contém apenas ID da sessão, horários e indicadores. "
                        + "Um compartilhamento ACTION_SEND que não seja CSV conta como comprovante recebido; "
                        + "nenhum conteúdo ou origem é guardado.",
                14, false);
        consent.setPadding(dp(14), dp(14), dp(14), dp(14));
        consent.setBackgroundColor(0xFFE8EEE9);
        content.addView(consent, matchWrap());

        statusText = text("Estado: ...", 17, true);
        statusText.setPadding(0, dp(24), 0, dp(20));
        content.addView(statusText, matchWrap());

        shareResultText = text("", 14, false);
        shareResultText.setPadding(0, 0, 0, dp(12));
        content.addView(shareResultText, matchWrap());

        configureButton = new Button(this);
        configureButton.setText("Configurar lembretes");
        configureButton.setOnClickListener(view -> showOptIn());
        content.addView(configureButton, matchWrap());

        disableButton = new Button(this);
        disableButton.setText("Desativar e apagar sessões");
        disableButton.setOnClickListener(view -> disableMonitor());
        content.addView(disableButton, matchWrap());

        TextView footer = text("Protótipo independente • Android 15 ou superior", 13, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(18), 0, 0);
        content.addView(footer, matchWrap());
        setContentView(scrollView);
    }

    private void showOptIn() {
        if (Build.VERSION.SDK_INT < 35) {
            new AlertDialog.Builder(this)
                    .setTitle("Android 15 ou superior necessário")
                    .setMessage("A consulta filtrada por pacote usada neste experimento requer Android 15 ou superior.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Ativar lembretes locais?")
                .setMessage("O monitor usa o acesso de uso do Android para observar somente quando com.picpay "
                        + "entra ou sai do primeiro plano. Também exige notificações para mostrar os lembretes e "
                        + "uma notificação permanente do serviço. Nenhum dado financeiro é lido ou salvo.")
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    pendingEnable = true;
                    continueEnableFlow();
                })
                .show();
    }

    private void continueEnableFlow() {
        if (!AndroidPermissions.hasUsageAccess(this)) {
            waitingForUsageSettings = true;
            Intent usageSettings = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            try {
                startActivityForResult(usageSettings, REQUEST_USAGE_SETTINGS);
            } catch (RuntimeException exception) {
                waitingForUsageSettings = false;
                pendingEnable = false;
                Toast.makeText(this, "Não foi possível abrir o acesso de uso do Android.", Toast.LENGTH_LONG).show();
            }
            refreshStatus();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !AndroidPermissions.hasNotifications(this)) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        if (AndroidPermissions.hasNotifications(this)) startMonitor();
    }

    private void startMonitor() {
        if (!pendingEnable || !AndroidPermissions.hasUsageAccess(this) || !AndroidPermissions.hasNotifications(this)) {
            pendingEnable = false;
            refreshStatus();
            return;
        }
        store.clearSession();
        store.setInterrupted(false);
        store.setEnabled(true);
        Intent service = new Intent(this, PicPayReminderService.class);
        service.setAction(PicPayReminderService.ACTION_START);
        startForegroundService(service);
        pendingEnable = false;
        Toast.makeText(this, "Lembretes do PicPay ativados.", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void disableMonitor() {
        new AlertDialog.Builder(this)
                .setTitle("Desativar lembretes?")
                .setMessage("O serviço será encerrado e as sessões locais serão apagadas.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desativar", (dialog, which) -> {
                    store.setEnabled(false);
                    store.clearSession();
                    stopService(new Intent(this, PicPayReminderService.class));
                    ReminderNotifications.cancelReminders(this);
                    refreshStatus();
                })
                .show();
    }

    private void processSharedIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        boolean csv = ReceiptClassifier.isCsv(this, intent);
        if (csv) {
            pendingShareMessage = "CSV recebido: ele não conta como comprovante nesta sessão.";
            return;
        }

        ReminderStateMachine machine = store.loadMachine();
        boolean received = machine.onShareReceived(System.currentTimeMillis(), false);
        if (received) {
            store.saveMachine(machine); // Commit before rendering any share result.
            ReminderNotifications.cancelReminders(this);
            if (store.isEnabled()) {
                Intent refresh = new Intent(this, PicPayReminderService.class);
                refresh.setAction(PicPayReminderService.ACTION_REFRESH);
                startForegroundService(refresh);
            }
            pendingShareMessage = "Compartilhamento recebido; o lembrete desta sessão foi cancelado. "
                    + "Este protótipo não importa valores nem cria despesas.");
        } else {
            pendingShareMessage = "Compartilhamento recebido fora de uma sessão monitorada; "
                    + "nenhum dado foi guardado.";
        }
    }

    private void refreshStatus() {
        if (statusText == null) return;
        boolean enabled = store.isEnabled();
        String status;
        if (!enabled) {
            status = "Desativado";
        } else if (!AndroidPermissions.hasUsageAccess(this)) {
            status = "Falta acesso ao uso";
        } else if (!AndroidPermissions.hasNotifications(this)) {
            status = "Falta permissão de notificações";
        } else if (store.wasInterrupted()
                || System.currentTimeMillis() - store.heartbeatAtMillis() > 12_000L) {
            status = "Interrompido pelo sistema — toque em Configurar lembretes para retomar";
        } else {
            status = "Ativo";
        }
        statusText.setText("Estado: " + status);
        boolean active = enabled && !status.contains("Interrompido")
                && AndroidPermissions.hasUsageAccess(this) && AndroidPermissions.hasNotifications(this);
        configureButton.setText(status.contains("Interrompido") ? "Retomar monitoramento" : "Configurar lembretes");
        configureButton.setEnabled(!active);
        disableButton.setEnabled(enabled);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF19231E);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
