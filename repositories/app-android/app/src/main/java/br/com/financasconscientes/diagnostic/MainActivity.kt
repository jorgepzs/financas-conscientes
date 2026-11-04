package br.com.financasconscientes.diagnostic

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import br.com.financasconscientes.diagnostic.data.EncryptedEventStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var permissionStatus: TextView
    private lateinit var eventCount: TextView
    private lateinit var eventList: LinearLayout
    private val store by lazy { EncryptedEventStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshHistory()
    }

    private fun createScreen(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(28))
        }

        content.addView(label("Finanças Conscientes", 26, true))
        content.addView(label("Diagnóstico de notificações", 18, true), spaced(top = 6))
        content.addView(
            label(
                "Este experimento observa somente notificações do PicPay (com.picpay). " +
                    "O conteúdo fica criptografado neste aparelho e não é enviado a um servidor.",
                15,
            ),
            spaced(top = 12, bottom = 18),
        )

        content.addView(label("Acesso especial", 17, true))
        permissionStatus = label("Verificando permissão…", 15)
        content.addView(permissionStatus, spaced(top = 5))

        content.addView(
            Button(this).apply {
                text = getString(R.string.open_notification_access)
                isAllCaps = false
                setOnClickListener { openNotificationSettings() }
            },
            spaced(top = 10, bottom = 22),
        )

        content.addView(label("Histórico local", 17, true))
        eventCount = label("", 14)
        content.addView(eventCount, spaced(top = 4, bottom = 8))
        eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(eventList)

        content.addView(
            Button(this).apply {
                text = getString(R.string.clear_history)
                isAllCaps = false
                setOnClickListener { confirmClearHistory() }
            },
            spaced(top = 18, bottom = 10),
        )
        content.addView(
            label(
                "Uma notificação é apenas um aviso do sistema, não um extrato confirmado. " +
                    "O app conserva até 100 eventos por no máximo 30 dias.",
                13,
            ),
        )

        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun refreshPermissionStatus() {
        val expected = ComponentName(this, PicPayNotificationListener::class.java)
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(expected)
        } else {
            val configuredListeners = Settings.Secure.getString(
                contentResolver,
                ENABLED_NOTIFICATION_LISTENERS_SETTING,
            ).orEmpty()
            configuredListeners.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .contains(expected)
        }

        permissionStatus.text = if (enabled) {
            "Ativo. O Android autorizou este app a receber notificações."
        } else {
            "Desativado. O acesso precisa ser concedido manualmente nas configurações do Android."
        }
    }

    private fun refreshHistory() {
        eventList.removeAllViews()
        try {
            val events = store.load()
            eventCount.text = if (events.isEmpty()) {
                "Nenhum evento capturado ainda."
            } else {
                "${events.size} evento(s) armazenado(s) neste aparelho."
            }
            if (events.isEmpty()) {
                eventList.addView(label("Quando o PicPay publicar uma notificação, ela aparecerá aqui.", 14))
                return
            }

            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))
            events.forEach { event ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setBackgroundResource(R.drawable.event_background)
                }
                card.addView(label(formatter.format(Date(event.postedAtEpochMillis)), 12))
                card.addView(label(event.title.ifBlank { "(sem título na notificação)" }, 16, true), spaced(top = 4))
                card.addView(label(event.text.ifBlank { "(sem texto adicional)" }, 14), spaced(top = 3))
                card.addView(label("Pacote: ${event.packageName}", 11), spaced(top = 8))
                eventList.addView(card, spaced(top = 5, bottom = 5))
            }
        } catch (_: Exception) {
            eventCount.text = getString(R.string.encrypted_history_unreadable)
            eventList.addView(
                label(
                    "Apague o histórico para remover o arquivo inválido ou uma chave local indisponível.",
                    14,
                ),
            )
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir as configurações de notificações.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Apagar histórico local?")
            .setMessage("Os eventos guardados neste aparelho serão excluídos permanentemente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->
                runCatching { store.clear() }
                    .onSuccess { refreshHistory() }
                    .onFailure {
                        Toast.makeText(this, "Não foi possível apagar o histórico.", Toast.LENGTH_LONG).show()
                    }
            }
            .show()
    }

    private fun label(textValue: String, sizeSp: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = textValue
            textSize = sizeSp.toFloat()
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextIsSelectable(false)
        }

    private fun spaced(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ENABLED_NOTIFICATION_LISTENERS_SETTING = "enabled_notification_listeners"
    }
}
