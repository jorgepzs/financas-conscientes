package br.com.financasconscientes

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.provider.OpenableColumns
import android.text.InputType
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import br.com.financasconscientes.csv.CsvImportException
import br.com.financasconscientes.csv.PicPayCsvParser
import br.com.financasconscientes.data.EncryptedFinanceStore
import br.com.financasconscientes.data.ExpenseSettingsMutations
import br.com.financasconscientes.dashboard.MonthlyDashboardCalculator
import br.com.financasconscientes.dashboard.ReceiptReconciliation
import br.com.financasconscientes.dashboard.StatementDataQuality
import br.com.financasconscientes.dashboard.StatementDataQualityCalculator
import br.com.financasconscientes.model.ExpenseCategories
import br.com.financasconscientes.model.ExpenseCategoryPalette
import br.com.financasconscientes.model.ExpenseDecisions
import br.com.financasconscientes.model.EvidenceType
import br.com.financasconscientes.model.ExpenseEvidence
import br.com.financasconscientes.model.ExpenseRecord
import br.com.financasconscientes.model.FinanceState
import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import br.com.financasconscientes.money.BankCurrencyMask
import br.com.financasconscientes.navigation.PrimaryDestination
import br.com.financasconscientes.reminders.PicPayReminderService
import br.com.financasconscientes.reminders.ReminderPhase
import br.com.financasconscientes.reminders.ReminderSessionStore
import br.com.financasconscientes.reminders.ReminderStateMachine
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : ThemeAwareActivity() {
    private lateinit var screenContent: LinearLayout
    private lateinit var monthTitle: TextView
    private lateinit var monthDashboard: LinearLayout
    private lateinit var nextMonthButton: Button
    private lateinit var pendingTitle: TextView
    private lateinit var pendingList: LinearLayout
    private lateinit var recordsTitle: TextView
    private lateinit var recordsList: LinearLayout
    private val navigationButtons = mutableMapOf<PrimaryDestination, Button>()
    private val store by lazy { EncryptedFinanceStore(applicationContext) }
    private val receiptVerificationWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
    private var selectedMonth = YearMonth.now()
    private var selectedDestination = PrimaryDestination.HOME
    private var settingsOpen = false
    private var settingsBackCallback: OnBackInvokedCallback? = null
    private var loadedState = FinanceState()
    private var attachmentPickerDraft: ExpenseFormDraft? = null
    private var activeFormDraftSnapshot: (() -> ExpenseFormDraft)? = null
    private var pendingVisibleLimit = MAX_VISIBLE_ITEMS
    private var recordVisibleLimit = MAX_VISIBLE_ITEMS
    private var verifiedReceiptReferenceIds: Set<String>? = null
    private var validReceiptIdsCache: Set<String> = emptySet()
    private var receiptVerificationInFlightFor: Set<String>? = null
    private val transientAttachmentIds = mutableSetOf<String>()
    private var waitingForUsageAccessReturn = false
    private var waitingForNotificationSettingsReturn = false

    private data class ExpenseFormDraft(
        val existing: ExpenseRecord?,
        val transaction: ImportedTransaction?,
        val source: String,
        val amountText: String,
        val what: String,
        val where: String,
        val category: String,
        val decision: String,
        val pendingAttachment: br.com.financasconscientes.data.ReceiptAttachmentMetadata? = null,
        val removeExistingAttachment: Boolean = false,
        val shareNotice: String? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedDestination = PrimaryDestination.fromSavedKey(savedInstanceState?.getString(STATE_DESTINATION))
        settingsOpen = savedInstanceState?.getBoolean(STATE_SETTINGS_OPEN) == true
        savedInstanceState?.getString(STATE_MONTH)?.let { savedMonth ->
            runCatching { YearMonth.parse(savedMonth) }.getOrNull()?.let { selectedMonth = it }
        }
        val restoredDraft = savedInstanceState?.getBundle(STATE_EXPENSE_DRAFT)?.let(::restoreExpenseDraft)
        val pickerWasOpen = savedInstanceState?.getBoolean(STATE_DRAFT_IN_PICKER) == true
        pendingVisibleLimit = savedInstanceState?.getInt(STATE_PENDING_VISIBLE_LIMIT, MAX_VISIBLE_ITEMS)
            ?.coerceAtLeast(MAX_VISIBLE_ITEMS) ?: MAX_VISIBLE_ITEMS
        recordVisibleLimit = savedInstanceState?.getInt(STATE_RECORD_VISIBLE_LIMIT, MAX_VISIBLE_ITEMS)
            ?.coerceAtLeast(MAX_VISIBLE_ITEMS) ?: MAX_VISIBLE_ITEMS
        restoredDraft?.pendingAttachment?.id?.let(transientAttachmentIds::add)
        if (pickerWasOpen) attachmentPickerDraft = restoredDraft
        setContentView(createScreen())
        cleanupOrphanedAttachmentsOnce(restoredDraft?.pendingAttachment?.id?.let { setOf(it) }.orEmpty())
        when {
            savedInstanceState == null -> handleIncomingIntent(intent)
            pickerWasOpen -> Unit // The document picker returns to onActivityResult.
            restoredDraft != null && savedInstanceState.getBoolean(STATE_FORM_OPEN) -> {
                window.decorView.post { showExpenseDialog(restoredDraft = restoredDraft) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DESTINATION, selectedDestination.savedKey)
        outState.putBoolean(STATE_SETTINGS_OPEN, settingsOpen)
        outState.putString(STATE_MONTH, selectedMonth.toString())
        outState.putInt(STATE_PENDING_VISIBLE_LIMIT, pendingVisibleLimit)
        outState.putInt(STATE_RECORD_VISIBLE_LIMIT, recordVisibleLimit)
        val formDraft = activeFormDraftSnapshot?.invoke()
        val draftToSave = formDraft ?: attachmentPickerDraft
        if (draftToSave != null) {
            outState.putBundle(STATE_EXPENSE_DRAFT, saveExpenseDraft(draftToSave))
            outState.putBoolean(STATE_FORM_OPEN, formDraft != null)
            outState.putBoolean(STATE_DRAFT_IN_PICKER, formDraft == null && attachmentPickerDraft != null)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    @Deprecated("Fallback for older Android versions; newer versions use the predictive back dispatcher.")
    override fun onBackPressed() {
        if (settingsOpen) {
            settingsOpen = false
            renderCurrentScreen(loadedState)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshScreen()
        val reminderStore = ReminderSessionStore(applicationContext)
        if (waitingForNotificationSettingsReturn) {
            waitingForNotificationSettingsReturn = false
            if (hasNotificationPermission()) {
                continuePicPayReminderSetup()
            } else {
                ReminderSessionStore(applicationContext).pendingEnable = false
                Toast.makeText(this, "As notificações seguem bloqueadas. Os lembretes continuam desligados.", Toast.LENGTH_LONG).show()
            }
        } else if (waitingForUsageAccessReturn) {
            waitingForUsageAccessReturn = false
            if (hasPicPayUsageAccess()) {
                continuePicPayReminderSetup()
            } else {
                reminderStore.pendingEnable = false
                Toast.makeText(this, "O acesso ao uso não foi ativado. Os lembretes continuam desligados.", Toast.LENGTH_LONG).show()
            }
        } else if (reminderStore.enabled) {
            if (hasPicPayUsageAccess() && hasNotificationPermission()) {
                runCatching { PicPayReminderService.start(this) }
                    .onFailure {
                        reminderStore.enabled = false
                        reminderStore.clearSession()
                        Toast.makeText(this, "O monitor foi interrompido; abra Lembretes do PicPay para configurar novamente.", Toast.LENGTH_LONG).show()
                    }
            } else {
                reminderStore.enabled = false
                runCatching { PicPayReminderService.stop(this) }
            }
        }
    }

    @Deprecated("Uses runtime notification permission to keep the optional reminder visible.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return
        val reminderStore = ReminderSessionStore(applicationContext)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && reminderStore.pendingEnable) {
            continuePicPayReminderSetup()
        } else {
            reminderStore.pendingEnable = false
            Toast.makeText(this, "Sem notificações, os lembretes não podem ser ativados.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses the platform activity result API to avoid external dependencies in the local-only MVP.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_CSV && resultCode == RESULT_OK) {
            data?.data?.let(::importCsv)
        } else if (requestCode == REQUEST_OPEN_ATTACHMENT) {
            handleAttachmentPickerResult(resultCode, data)
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            transientAttachmentIds.toList().forEach(store.receiptAttachments::delete)
            transientAttachmentIds.clear()
        }
        activeFormDraftSnapshot = null
        receiptVerificationWorker.shutdownNow()
        super.onDestroy()
    }

    private fun createScreen(): LinearLayout {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.app_background))
            fitsSystemWindows = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(16), dp(8))
            setBackgroundColor(getColor(R.color.surface))
        }
        val utilityButton = quietButton("☰") { showMainMenu() }.apply {
            contentDescription = "Abrir utilidades"
            textSize = 19f
            minHeight = dp(44)
            setPadding(0, 0, 0, 0)
        }
        header.addView(utilityButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val mark = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_app)
                contentDescription = "Logo Finanças Conscientes"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(mark, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(9) })
            val wordmark = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Finanças Conscientes", 16, true))
                addView(mutedLabel("Seu dinheiro, com contexto.", 11), spaced(top = 1))
            }
            addView(wordmark)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(7)
        })
        shell.addView(header)
        shell.addView(View(this).apply { setBackgroundColor(getColor(R.color.border)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        screenContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(26))
        }
        scrollView.addView(
            screenContent,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        shell.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottomNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(8))
            setBackgroundColor(getColor(R.color.surface))
            elevation = dp(4).toFloat()
        }
        PrimaryDestination.values().forEach { destination ->
            val button = styledButton(
                navigationLabel(destination),
                R.drawable.button_quiet,
                R.color.text_secondary,
            ) { selectDestination(destination) }.apply {
                textSize = 12f
                contentDescription = destination.label
                minHeight = dp(48)
                compoundDrawablePadding = dp(3)
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            navigationButtons[destination] = button
            bottomNavigation.addView(button, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                if (destination != PrimaryDestination.HOME) marginStart = dp(6)
            })
        }
        shell.addView(bottomNavigation)
        updateBottomNavigation()
        renderCurrentScreen(loadedState)
        return shell
    }

    private fun renderCurrentScreen(state: FinanceState) {
        syncSettingsBackCallback()
        screenContent.removeAllViews()
        (screenContent.parent as? ScrollView)?.scrollTo(0, 0)
        if (settingsOpen) {
            renderSettingsScreen(state)
        } else {
            when (selectedDestination) {
                PrimaryDestination.HOME -> renderHomeScreen(state)
                PrimaryDestination.RESULTS -> renderResultsScreen(state)
                PrimaryDestination.RECORDS -> renderRecordsScreen(state)
            }
        }
        updateBottomNavigation()
    }

    private fun syncSettingsBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return
        val current = settingsBackCallback
        if (settingsOpen && current == null) {
            val callback = OnBackInvokedCallback {
                settingsOpen = false
                renderCurrentScreen(loadedState)
            }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            settingsBackCallback = callback
        } else if (!settingsOpen && current != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(current)
            settingsBackCallback = null
        }
    }

    private fun renderHomeScreen(state: FinanceState) {
        screenContent.addView(sectionHeading("Início", "Um passo simples para manter seus gastos em contexto."))

        val captureCard = card(R.drawable.capture_card_background)
        captureCard.addView(overline("REGISTRO RÁPIDO"))
        captureCard.addView(label("Acabou de gastar?", 21, true), spaced(top = 4))
        captureCard.addView(
            mutedLabel("Registre o motivo enquanto a decisão ainda está fresca.", 13),
            spaced(top = 5),
        )
        captureCard.addView(
            primaryButton("Como compartilhar comprovante") { showReceiptInstructions() },
            spaced(top = 12),
        )
        captureCard.addView(
            secondaryButton("Registrar manualmente") { showExpenseDialog(source = SOURCE_MANUAL) },
            spaced(top = 7),
        )
        screenContent.addView(captureCard, spaced(top = 14))

        val quality = StatementDataQualityCalculator.calculate(state)
        if (quality.pendingOutflowCount > 0) {
            val pendingCard = messageCard(
                "Saídas aguardando contexto",
                "${quantity(quality.pendingOutflowCount, "movimentação precisa", "movimentações precisam")} de uma categoria ou decisão.",
                warning = true,
            )
            pendingCard.addView(
                secondaryButton("Revisar pendências") { selectDestination(PrimaryDestination.RECORDS) },
                spaced(top = 10),
            )
            screenContent.addView(pendingCard, spaced(top = 12))
        } else if (state.transactions.isNotEmpty()) {
            screenContent.addView(
                messageCard(
                    "Nenhuma saída pendente",
                    "As saídas importadas já têm contexto.",
                    warning = false,
                ),
                spaced(top = 12),
            )
        }

        val statementCard = card()
        statementCard.addView(overline("EXTRATO DO PICPAY"))
        statementCard.addView(label("Atualize seus resultados", 17, true), spaced(top = 4))
        statementCard.addView(mutedLabel(latestImportText(quality), 12), spaced(top = 4))
        statementCard.addView(
            primaryButton(getString(R.string.import_csv)) { openCsvPicker() },
            spaced(top = 11),
        )
        if (state.transactions.isNotEmpty()) {
            statementCard.addView(
                quietButton(getString(R.string.manage_statement)) { showImportedTransactionsDialog() },
                spaced(top = 6),
            )
        }
        screenContent.addView(statementCard, spaced(top = 12))

        val statusCard = card()
        statusCard.addView(overline("SEUS DADOS"))
        statusCard.addView(
            label(
                "${quantity(state.transactions.size, "movimentação importada", "movimentações importadas")} · " +
                    quantity(state.expenses.size, "registro", "registros"),
                14,
                true,
            ),
            spaced(top = 5),
        )
        statusCard.addView(mutedLabel("Tudo fica cifrado e armazenado neste aparelho.", 12), spaced(top = 3))
        statusCard.addView(
            quietButton("Privacidade e funcionamento") { showPrivacyDialog() },
            spaced(top = 8),
        )
        screenContent.addView(statusCard, spaced(top = 12))
    }

    private fun renderResultsScreen(state: FinanceState) {
        screenContent.addView(sectionHeading(
            "Resultados",
            "Uma visão clara do mês, com a origem dos valores.",
        ))
        val monthNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        monthNavigation.addView(
            quietButton("‹") {
                selectedMonth = selectedMonth.minusMonths(1)
                renderCurrentScreen(loadedState)
            }.apply { contentDescription = "Mês anterior"; textSize = 20f },
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        monthTitle = label("", 15, true).apply { gravity = Gravity.CENTER }
        monthNavigation.addView(monthTitle, LinearLayout.LayoutParams(0, dp(46), 1f))
        nextMonthButton = quietButton("›") {
            if (selectedMonth < YearMonth.now()) {
                selectedMonth = selectedMonth.plusMonths(1)
                renderCurrentScreen(loadedState)
            }
        }.apply { contentDescription = "Próximo mês"; textSize = 20f }
        monthNavigation.addView(nextMonthButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        screenContent.addView(monthNavigation, spaced(top = 8))
        monthDashboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screenContent.addView(monthDashboard, spaced(top = 8))
        renderDashboard(state)
    }

    private fun renderRecordsScreen(state: FinanceState) {
        screenContent.addView(sectionHeading("Registros", "Revise saídas e encontre seus lançamentos."))
        pendingTitle = label("Pendências do extrato", 20, true)
        screenContent.addView(pendingTitle, spaced(top = 15))
        screenContent.addView(mutedLabel("Saídas confirmadas que ainda precisam de contexto.", 12), spaced(top = 3))
        if (isReceiptVerificationPending(state)) {
            screenContent.addView(mutedLabel("Validando a integridade dos anexos cifrados…", 12), spaced(top = 5))
        }
        pendingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screenContent.addView(pendingList, spaced(top = 10, bottom = 18))

        recordsTitle = label("Seus registros", 20, true)
        screenContent.addView(recordsTitle)
        screenContent.addView(mutedLabel("Anexos, lançamentos manuais e classificações do extrato.", 12), spaced(top = 3))
        recordsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screenContent.addView(recordsList, spaced(top = 10, bottom = 18))
        val linked = state.expenses.mapNotNullTo(mutableSetOf()) { it.transactionFingerprint }
        renderPending(
            state.transactions.filter { it.direction == TransactionDirection.OUTFLOW && it.fingerprint !in linked },
            state,
        )
        renderRecords(state)
    }

    private fun renderSettingsScreen(state: FinanceState) {
        screenContent.addView(quietButton("‹  Voltar") {
            settingsOpen = false
            renderCurrentScreen(loadedState)
        })
        screenContent.addView(
            sectionHeading("Configurações", "Adapte categorias, cores e decisões ao seu jeito de acompanhar os gastos."),
            spaced(top = 14),
        )
        screenContent.addView(
            messageCard(
                "Seus dados continuam no aparelho",
                "As opções e cores são salvas junto aos dados cifrados. Renomear preserva os registros; remover uma opção mostra quantos serão atualizados.",
                warning = false,
            ),
            spaced(top = 12),
        )

        screenContent.addView(label("Aparência", 17, true), spaced(top = 20))
        val appearanceCard = card()
        appearanceCard.addView(label("Tema do aplicativo", 14, true))
        appearanceCard.addView(
            mutedLabel("Atual: ${appearanceModeLabel()}. Você pode seguir o sistema ou escolher claro/escuro.", 12),
            spaced(top = 4),
        )
        appearanceCard.addView(
            secondaryButton("Alterar tema") { showAppearanceDialog() },
            spaced(top = 10),
        )
        screenContent.addView(appearanceCard, spaced(top = 8))

        screenContent.addView(label("Categorias", 17, true), spaced(top = 20))
        screenContent.addView(
            mutedLabel("As cores aparecem na distribuição por categoria em Resultados.", 12),
            spaced(top = 3),
        )
        if (state.categories.size < MAX_SETTINGS_OPTIONS) {
            screenContent.addView(
                primaryButton("＋  Adicionar categoria") { showAddCategoryDialog() },
                spaced(top = 10),
            )
        } else {
            screenContent.addView(mutedLabel("Você atingiu o limite de $MAX_SETTINGS_OPTIONS categorias.", 12), spaced(top = 8))
        }
        state.categories.forEach { category ->
            val recordCount = state.expenses.count { it.category == category }
            val protected = category in REQUIRED_CATEGORIES
            val categoryCard = card()
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val swatch = View(this@MainActivity).apply {
                    background = categorySwatchBackground(ExpenseCategoryPalette.colorFor(category, state.categoryColors))
                    contentDescription = "Cor de $category"
                }
                addView(swatch, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(11) })
                val names = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(category, 14, true))
                    addView(
                        mutedLabel(
                            if (protected) "Opção protegida • ${quantity(recordCount, "registro", "registros")}"
                            else quantity(recordCount, "registro usando esta categoria", "registros usando esta categoria"),
                            11,
                        ),
                        spaced(top = 2),
                    )
                }
                addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            categoryCard.addView(heading)
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    settingsActionButton("Cor") { showCategoryColorDialog(category) },
                    LinearLayout.LayoutParams(0, dp(44), 1f),
                )
                if (!protected) {
                    addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(6), 1))
                    addView(
                        settingsActionButton("Editar") { showRenameCategoryDialog(category) },
                        LinearLayout.LayoutParams(0, dp(44), 1f),
                    )
                    addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(6), 1))
                    addView(
                        settingsActionButton("Remover", danger = true) { confirmDeleteCategory(category) },
                        LinearLayout.LayoutParams(0, dp(44), 1f),
                    )
                }
            }
            categoryCard.addView(actions, spaced(top = 11))
            screenContent.addView(categoryCard, spaced(top = 8))
        }

        screenContent.addView(label("Decisões", 17, true), spaced(top = 22))
        screenContent.addView(mutedLabel("Use para notar se um gasto foi planejado ou por impulso.", 12), spaced(top = 3))
        if (state.decisions.size < MAX_SETTINGS_OPTIONS) {
            screenContent.addView(
                primaryButton("＋  Adicionar decisão") { showAddDecisionDialog() },
                spaced(top = 10),
            )
        } else {
            screenContent.addView(mutedLabel("Você atingiu o limite de $MAX_SETTINGS_OPTIONS decisões.", 12), spaced(top = 8))
        }
        state.decisions.forEach { decision ->
            val recordCount = state.expenses.count { it.decision == decision }
            val protected = decision == ExpenseDecisions.DEFAULT
            val decisionCard = card()
            decisionCard.addView(label(decision, 14, true))
            decisionCard.addView(
                mutedLabel(
                    if (protected) "Opção protegida • ${quantity(recordCount, "registro", "registros")}"
                    else quantity(recordCount, "registro usando esta decisão", "registros usando esta decisão"),
                    11,
                ),
                spaced(top = 2),
            )
            if (!protected) {
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        settingsActionButton("Editar") { showRenameDecisionDialog(decision) },
                        LinearLayout.LayoutParams(0, dp(44), 1f),
                    )
                    addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(8), 1))
                    addView(
                        settingsActionButton("Remover", danger = true) { confirmDeleteDecision(decision) },
                        LinearLayout.LayoutParams(0, dp(44), 1f),
                    )
                }
                decisionCard.addView(actions, spaced(top = 10))
            }
            screenContent.addView(decisionCard, spaced(top = 8))
        }
        screenContent.addView(
            mutedLabel("“Outro”, “Sem categoria” e “Não informado” ficam disponíveis para manter classificações consistentes.", 11),
            spaced(top = 14, bottom = 10),
        )
    }

    private fun selectDestination(destination: PrimaryDestination) {
        if (selectedDestination == destination && !settingsOpen) return
        settingsOpen = false
        selectedDestination = destination
        renderCurrentScreen(loadedState)
    }

    private fun updateBottomNavigation() {
        navigationButtons.forEach { (destination, button) ->
            val selected = !settingsOpen && destination == selectedDestination
            button.background = getDrawable(if (selected) R.drawable.button_navigation_selected else R.drawable.button_quiet)
            val tint = getColor(if (selected) R.color.primary else R.color.text_secondary)
            button.setTextColor(tint)
            val icon = getDrawable(navigationIcon(destination))?.mutate()?.apply { setTint(tint) }
            button.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null)
            button.isSelected = selected
            button.contentDescription = if (selected) "${destination.label}, selecionada" else destination.label
        }
    }

    private fun navigationLabel(destination: PrimaryDestination): String = destination.label

    private fun navigationIcon(destination: PrimaryDestination): Int = when (destination) {
        PrimaryDestination.HOME -> R.drawable.ic_nav_home
        PrimaryDestination.RESULTS -> R.drawable.ic_nav_results
        PrimaryDestination.RECORDS -> R.drawable.ic_nav_records
    }

    private fun qualityRow(labelText: String, count: Int): TextView =
        mutedLabel("$labelText • $count", 12)

    private fun decisionMetricRow(
        title: String,
        amountCents: Long,
        count: Int,
        colorResource: Int,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val marker = View(this@MainActivity).apply {
            background = roundedBackground(getColor(colorResource), dp(10).toFloat())
            contentDescription = title
        }
        addView(marker, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(10) })
        val details = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 13, true))
            addView(mutedLabel(quantity(count, "gasto", "gastos"), 11), spaced(top = 2))
        }
        addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label(formatCurrency(amountCents), 13, true))
    }

    private fun decisionColorResource(decision: String): Int = when (decision) {
        ExpenseDecisions.PLANNED -> R.color.success
        ExpenseDecisions.IMPULSE -> R.color.warning
        ExpenseDecisions.DEFAULT -> R.color.text_secondary
        else -> R.color.primary
    }

    private fun latestImportText(quality: StatementDataQuality): String =
        quality.latestImportEpochMillis?.let { "Extrato atualizado em ${dateFormatter.format(Date(it))}." }
            ?: "Nenhum extrato importado ainda."

    private fun refreshScreen() {
        try {
            loadedState = store.load()
            ensureReceiptAttachmentsVerified(loadedState)
            renderCurrentScreen(loadedState)
        } catch (_: Exception) {
            screenContent.removeAllViews()
            screenContent.addView(card().apply {
                addView(statusChip("DADOS INDISPONÍVEIS", R.color.danger, R.color.warning_soft))
                addView(label(getString(R.string.encrypted_data_unreadable), 15, true), spaced(top = 10))
                addView(mutedLabel("Seus registros locais permanecem protegidos.", 12), spaced(top = 4))
            })
        }
    }

    private fun saveExpenseDraft(draft: ExpenseFormDraft): Bundle = Bundle().apply {
        putString(DRAFT_EXISTING_ID, draft.existing?.id)
        putString(DRAFT_TRANSACTION_FINGERPRINT, draft.transaction?.fingerprint)
        putString(DRAFT_SOURCE, draft.source)
        putString(DRAFT_AMOUNT, draft.amountText)
        putString(DRAFT_WHAT, draft.what)
        putString(DRAFT_WHERE, draft.where)
        putString(DRAFT_CATEGORY, draft.category)
        putString(DRAFT_DECISION, draft.decision)
        putString(DRAFT_ATTACHMENT_ID, draft.pendingAttachment?.id)
        putString(DRAFT_ATTACHMENT_MIME, draft.pendingAttachment?.mimeType)
        putLong(DRAFT_ATTACHMENT_SIZE, draft.pendingAttachment?.sizeBytes ?: -1L)
        putBoolean(DRAFT_REMOVE_EXISTING_ATTACHMENT, draft.removeExistingAttachment)
        putString(DRAFT_SHARE_NOTICE, draft.shareNotice)
    }

    private fun restoreExpenseDraft(bundle: Bundle): ExpenseFormDraft? = runCatching {
        val state = store.load()
        val existingId = bundle.getString(DRAFT_EXISTING_ID)
        val existing = existingId?.let { id -> state.expenses.firstOrNull { it.id == id } }
        if (existingId != null && existing == null) return null
        val fingerprint = bundle.getString(DRAFT_TRANSACTION_FINGERPRINT)
        val transaction = fingerprint?.let { value -> state.transactions.firstOrNull { it.fingerprint == value } }
        if (fingerprint != null && transaction == null) return null
        val attachmentId = bundle.getString(DRAFT_ATTACHMENT_ID)
        val attachmentMime = bundle.getString(DRAFT_ATTACHMENT_MIME)
        val attachmentSize = bundle.getLong(DRAFT_ATTACHMENT_SIZE, -1L)
        val pendingAttachment = if (attachmentId != null && attachmentMime != null && attachmentSize >= 0L) {
            br.com.financasconscientes.data.ReceiptAttachmentMetadata(attachmentId, attachmentMime, attachmentSize)
        } else {
            null
        }
        ExpenseFormDraft(
            existing = existing,
            transaction = transaction,
            source = bundle.getString(DRAFT_SOURCE).orEmpty().ifBlank { SOURCE_MANUAL },
            amountText = bundle.getString(DRAFT_AMOUNT).orEmpty(),
            what = bundle.getString(DRAFT_WHAT).orEmpty(),
            where = bundle.getString(DRAFT_WHERE).orEmpty(),
            category = bundle.getString(DRAFT_CATEGORY).orEmpty().ifBlank {
                state.categories.firstOrNull() ?: ExpenseCategories.DEFAULT
            },
            decision = bundle.getString(DRAFT_DECISION).orEmpty().ifBlank {
                state.decisions.firstOrNull() ?: ExpenseDecisions.DEFAULT
            },
            pendingAttachment = pendingAttachment,
            removeExistingAttachment = bundle.getBoolean(DRAFT_REMOVE_EXISTING_ATTACHMENT),
            shareNotice = bundle.getString(DRAFT_SHARE_NOTICE),
        )
    }.getOrNull()

    private fun cleanupOrphanedAttachmentsOnce(preservedPendingIds: Set<String> = emptySet()) {
        if (processAttachmentCleanupCompleted) return
        runCatching {
            val state = store.load()
            val referencedIds = state.expenses.mapNotNullTo(mutableSetOf()) { it.receiptAttachmentId }
            referencedIds += preservedPendingIds
            store.receiptAttachments.deleteUnreferenced(referencedIds)
            processAttachmentCleanupCompleted = true
        }
    }

    private fun ensureReceiptAttachmentsVerified(state: FinanceState) {
        val referenceIds = receiptReferenceIds(state)
        if (verifiedReceiptReferenceIds == referenceIds || receiptVerificationInFlightFor == referenceIds) return
        if (referenceIds.isEmpty()) {
            verifiedReceiptReferenceIds = emptySet()
            validReceiptIdsCache = emptySet()
            receiptVerificationInFlightFor = null
            return
        }
        receiptVerificationInFlightFor = referenceIds
        receiptVerificationWorker.execute {
            val verified = referenceIds.filter(store.receiptAttachments::hasStoredBlob).toSet()
            runOnUiThread {
                if (isFinishing || isDestroyed || receiptVerificationInFlightFor != referenceIds) return@runOnUiThread
                receiptVerificationInFlightFor = null
                verifiedReceiptReferenceIds = referenceIds
                validReceiptIdsCache = verified
                if (receiptReferenceIds(loadedState) == referenceIds) renderCurrentScreen(loadedState)
            }
        }
    }

    private fun receiptReferenceIds(state: FinanceState): Set<String> =
        state.expenses.mapNotNullTo(mutableSetOf()) { it.receiptAttachmentId }

    private fun validReceiptAttachmentIds(state: FinanceState): Set<String> =
        if (verifiedReceiptReferenceIds == receiptReferenceIds(state)) validReceiptIdsCache else emptySet()

    private fun isReceiptVerificationPending(state: FinanceState): Boolean =
        verifiedReceiptReferenceIds != receiptReferenceIds(state)

    private fun renderDashboard(state: FinanceState) {
        val now = YearMonth.now()
        if (selectedMonth > now) selectedMonth = now
        nextMonthButton.isEnabled = selectedMonth < now
        monthTitle.text = selectedMonth.format(monthFormatter)
            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }

        val validReceiptIds = validReceiptAttachmentIds(state)
        val dashboard = MonthlyDashboardCalculator.calculate(
            state,
            selectedMonth,
            ZoneId.systemDefault(),
            validReceiptAttachmentIds = validReceiptIds,
        )
        monthDashboard.removeAllViews()
        if (isReceiptVerificationPending(state)) {
            monthDashboard.addView(
                messageCard(
                    "Validando anexos",
                    "Os arquivos cifrados estão sendo autenticados antes de entrarem nos resultados.",
                    warning = false,
                ),
                spaced(bottom = 10),
            )
        }

        val totalCard = card(R.drawable.dashboard_background)
        totalCard.addView(label("SAÍDAS CONFIRMADAS", 11, true).apply {
            setTextColor(getColor(R.color.dashboard_overline))
            letterSpacing = 0.08f
        })
        totalCard.addView(
            label(formatCurrency(dashboard.totalOutflowCents), 30, true).apply {
                setTextColor(getColor(R.color.on_dashboard))
            },
            spaced(top = 5),
        )
        totalCard.addView(
            label(quantity(dashboard.outflowCount, "item contabilizado", "itens contabilizados"), 12).apply {
                setTextColor(getColor(R.color.dashboard_secondary))
            },
            spaced(top = 3),
        )

        val budget = dashboard.monthlyBudgetCents
        if (budget == null) {
            val budgetAction = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    label("Acompanhe seu limite mensal", 12).apply {
                        setTextColor(getColor(R.color.dashboard_secondary))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    quietButton("Definir") { showBudgetDialog() }.apply {
                        setTextColor(Color.WHITE)
                        background = roundedBackground(withAlpha(Color.WHITE, 40), dp(12).toFloat())
                        textSize = 12f
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)),
                )
            }
            totalCard.addView(budgetAction, spaced(top = 13))
        } else {
            val usedPercent = if (budget <= 0L) 0 else {
                ((dashboard.totalOutflowCents.toDouble() / budget.toDouble()) * 100).toInt()
            }
            val remaining = dashboard.remainingBudgetCents ?: 0L
            val budgetLine = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val details = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        label(
                            if (remaining >= 0L) "$usedPercent% do limite utilizado" else "Limite mensal",
                            11,
                        ).apply { setTextColor(getColor(R.color.dashboard_secondary)) },
                    )
                    addView(
                        label(formatCurrency(budget), 15, true).apply {
                            setTextColor(Color.WHITE)
                        },
                        spaced(top = 2),
                    )
                }
                addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(
                    quietButton("Editar") { showBudgetDialog() }.apply {
                        setTextColor(Color.WHITE)
                        background = roundedBackground(withAlpha(Color.WHITE, 40), dp(12).toFloat())
                        textSize = 12f
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)),
                )
            }
            totalCard.addView(budgetLine, spaced(top = 13))
            totalCard.addView(
                ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    progress = if (budget <= 0L) 0 else {
                        ((dashboard.totalOutflowCents.toDouble() / budget.toDouble()) * 1000)
                            .toInt().coerceIn(0, 1000)
                    }
                    progressTintList = ColorStateList.valueOf(getColor(R.color.dashboard_progress))
                    progressBackgroundTintList = ColorStateList.valueOf(getColor(R.color.dashboard_track))
                    contentDescription = "Orçamento utilizado: $usedPercent por cento"
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                    topMargin = dp(8)
                },
            )
            val remainingRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val remainingTitle = if (remaining >= 0L) "Disponível neste mês" else "Acima do limite"
                val remainingAmount = formatCurrency(abs(remaining))
                val amountColor = getColor(if (remaining >= 0L) R.color.dashboard_available else R.color.dashboard_over_limit)
                addView(
                    label(remainingTitle, 11).apply { setTextColor(getColor(R.color.dashboard_secondary)) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(label(remainingAmount, 16, true).apply { setTextColor(amountColor) })
            }
            totalCard.addView(remainingRow, spaced(top = 9))
        }
        totalCard.addView(
            label("CSV + anexos válidos · informações guardadas no aparelho", 11).apply {
                setTextColor(getColor(R.color.dashboard_secondary))
            },
            spaced(top = 11),
        )
        monthDashboard.addView(totalCard)

        if (dashboard.pendingCount > 0) {
            val pendingCard = messageCard(
                quantity(dashboard.pendingCount, "saída aguarda contexto", "saídas aguardam contexto"),
                "Revise as movimentações do extrato para classificar o que aconteceu.",
                warning = true,
            )
            pendingCard.addView(
                secondaryButton("Revisar registros") { selectDestination(PrimaryDestination.RECORDS) },
                spaced(top = 10),
            )
            monthDashboard.addView(pendingCard, spaced(top = 10))
        }

        if (dashboard.categoryTotals.isNotEmpty()) {
            val categoriesCard = card()
            categoriesCard.addView(label("Para onde foi", 16, true))
            val sortedCategories = dashboard.categoryTotals.sortedByDescending { it.amountCents }
            val hiddenRows = mutableListOf<View>()
            sortedCategories.forEachIndexed { index, item ->
                val color = Color.parseColor(
                    ExpenseCategoryPalette.normalize(
                        ExpenseCategoryPalette.colorFor(item.category, state.categoryColors),
                    ) ?: String.format(Locale.ROOT, "#%06X", 0xFFFFFF and getColor(R.color.category_fallback)),
                )
                val share = if (dashboard.totalOutflowCents > 0L) {
                    ((item.amountCents.toDouble() / dashboard.totalOutflowCents) * 100).toInt()
                } else {
                    0
                }
                val categoryGroup = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val heading = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            label(item.category, 13, true),
                            LinearLayout.LayoutParams(0, dp(26), 1f),
                        )
                        addView(
                            mutedLabel("$share%", 11),
                            LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT),
                        )
                        addView(label(formatCurrency(item.amountCents), 13, true))
                    }
                    addView(heading)
                    addView(
                        ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                            max = 1000
                            progress = if (dashboard.totalOutflowCents > 0L) {
                                ((item.amountCents.toDouble() / dashboard.totalOutflowCents) * 1000)
                                    .toInt().coerceIn(0, 1000)
                            } else {
                                0
                            }
                            progressTintList = ColorStateList.valueOf(color)
                            progressBackgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_soft))
                            contentDescription = "$share por cento dos gastos do mês"
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)).apply {
                            topMargin = dp(2)
                        },
                    )
                }
                categoryGroup.setPadding(0, dp(2), 0, dp(2))
                if (index >= 5) {
                    categoryGroup.visibility = View.GONE
                    hiddenRows.add(categoryGroup)
                }
                categoriesCard.addView(categoryGroup, spaced(top = 9))
            }
            if (hiddenRows.isNotEmpty()) {
                var expanded = false
                lateinit var toggle: Button
                toggle = quietButton("Ver todas as categorias") {
                    expanded = !expanded
                    hiddenRows.forEach { it.visibility = if (expanded) View.VISIBLE else View.GONE }
                    toggle.text = if (expanded) "Mostrar menos" else "Ver todas as categorias"
                }
                categoriesCard.addView(toggle, spaced(top = 8))
            }
            monthDashboard.addView(categoriesCard, spaced(top = 10))
        } else {
            val emptyCategories = card()
            emptyCategories.addView(label("Para onde foi", 16, true))
            emptyCategories.addView(
                mutedLabel("As categorias aparecem depois da primeira saída confirmada.", 12),
                spaced(top = 4),
            )
            monthDashboard.addView(emptyCategories, spaced(top = 10))
        }

        val decisionsCard = card()
        decisionsCard.addView(label("Decisões do mês", 16, true))
        decisionsCard.addView(mutedLabel("Uma leitura sem julgamento das escolhas registradas.", 12), spaced(top = 3))
        dashboard.decisionTotals.forEachIndexed { index, total ->
            val title = if (total.decision == ExpenseDecisions.DEFAULT) "Sem decisão" else total.decision
            decisionsCard.addView(
                decisionMetricRow(
                    title = title,
                    amountCents = total.amountCents,
                    count = total.count,
                    colorResource = decisionColorResource(total.decision),
                ),
                spaced(top = if (index == 0) 12 else 8),
            )
        }
        monthDashboard.addView(decisionsCard, spaced(top = 10))

        val largestCard = card()
        largestCard.addView(label("Maiores gastos", 16, true))
        if (dashboard.largestExpenses.isEmpty()) {
            largestCard.addView(mutedLabel("Nenhuma saída confirmada neste mês.", 12), spaced(top = 5))
        } else {
            dashboard.largestExpenses.forEachIndexed { index, expense ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val details = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(expense.description, 13, true))
                        val dateText = expense.occurredAtEpochMillis?.let {
                            dateFormatter.format(Date(it))
                        } ?: "Data não identificada"
                        addView(mutedLabel(expense.category + " · " + dateText, 11), spaced(top = 2))
                    }
                    addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(label(formatCurrency(expense.amountCents), 13, true))
                }
                largestCard.addView(row, spaced(top = if (index == 0) 9 else 12))
            }
        }
        monthDashboard.addView(largestCard, spaced(top = 10))

        val quality = StatementDataQualityCalculator.calculate(state)
        val qualityCard = card()
        qualityCard.addView(label("Qualidade do extrato", 15, true))
        qualityCard.addView(mutedLabel(latestImportText(quality), 12), spaced(top = 4))
        if (isReceiptVerificationPending(state)) {
            qualityCard.addView(mutedLabel("Verificando anexos cifrados…", 11), spaced(top = 4))
        }
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.visibility = View.GONE
        details.addView(qualityRow("Saídas sem contexto", quality.pendingOutflowCount), spaced(top = 8))
        details.addView(qualityRow("Direção não identificada", quality.unknownDirectionCount), spaced(top = 4))
        details.addView(qualityRow("Data não reconhecida", quality.missingDateCount), spaced(top = 4))
        details.addView(qualityRow("Valor não reconhecido", quality.missingAmountCount), spaced(top = 4))
        val knownFingerprints = state.transactions.mapTo(mutableSetOf()) { it.fingerprint }
        val unlinkedExpenses = state.expenses.count { it.transactionFingerprint !in knownFingerprints }
        if (unlinkedExpenses > 0) {
            details.addView(
                mutedLabel(
                    quantity(unlinkedExpenses, "registro sem vínculo ao CSV", "registros sem vínculo ao CSV") +
                        ". Anexos válidos contam nos resultados até uma possível conciliação.",
                    11,
                ),
                spaced(top = 8),
            )
        }
        if (dashboard.provisionalCount > 0) {
            details.addView(
                mutedLabel(
                    quantity(dashboard.provisionalCount, "registro provisório", "registros provisórios") +
                        " ainda sem vínculo explícito ao extrato.",
                    11,
                ),
                spaced(top = 7),
            )
        }
        if (dashboard.receiptBackedCount > 0) {
            details.addView(
                mutedLabel(quantity(dashboard.receiptBackedCount, "registro com anexo", "registros com anexo"), 11),
                spaced(top = 4),
            )
        }
        if (dashboard.possibleReconciliationCount > 0) {
            details.addView(
                messageCard(
                    "Possível conciliação",
                    quantity(dashboard.possibleReconciliationCount, "anexo tem", "anexos têm") +
                        " uma saída no CSV com valor igual. O total conta o CSV uma vez; revise antes de vincular.",
                    warning = true,
                ),
                spaced(top = 8),
            )
        }
        details.addView(
            mutedLabel(
                "Os anexos são conferidos por formato e integridade; o app não verifica origem ou conteúdo bancário.",
                11,
            ),
            spaced(top = 8),
        )
        lateinit var detailsButton: Button
        detailsButton = quietButton("Ver detalhes do extrato") {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            detailsButton.text = if (details.visibility == View.VISIBLE) "Ocultar detalhes" else "Ver detalhes do extrato"
        }
        qualityCard.addView(detailsButton, spaced(top = 7))
        qualityCard.addView(details, spaced(top = 2))
        monthDashboard.addView(qualityCard, spaced(top = 10))
    }
    private fun renderPending(pending: List<ImportedTransaction>, state: FinanceState) {
        pendingList.removeAllViews()
        pendingTitle.text = getString(R.string.pending_count, pending.size)
        if (pending.isEmpty()) {
            val empty = card()
            empty.addView(statusChip("EM DIA", R.color.success, R.color.success_soft))
            empty.addView(label("Nenhuma saída aguardando contexto", 15, true), spaced(top = 10))
            empty.addView(mutedLabel("Importe um novo CSV quando quiser atualizar esta lista.", 12), spaced(top = 4))
            empty.addView(
                primaryButton(getString(R.string.import_csv)) { openCsvPicker() },
                spaced(top = 11),
            )
            pendingList.addView(empty)
            return
        }
        pending.take(pendingVisibleLimit).forEach { transaction ->
            val card = card()
            card.addView(statusChip("PENDENTE", R.color.warning, R.color.warning_soft))
            card.addView(label(transactionHeading(transaction), 15, true), spaced(top = 8))
            transaction.counterparty.takeIf(String::isNotBlank)?.let {
                card.addView(mutedLabel("Onde: $it", 12), spaced(top = 4))
            }
            transaction.paymentMethod.takeIf(String::isNotBlank)?.let {
                card.addView(mutedLabel("Forma: $it", 11), spaced(top = 3))
            }
            card.addView(mutedLabel("Data do extrato: ${transaction.dateText}", 12), spaced(top = 3))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(
                primaryButton(
                    when (findDraftCandidates(transaction, state).size) {
                        1 -> "Revisar e vincular registro"
                        in 2..Int.MAX_VALUE -> "Escolher possível registro"
                        else -> "Classificar saída"
                    },
                ) { classifyTransaction(transaction) },
                LinearLayout.LayoutParams(0, dp(50), 1f),
            )
            actions.addView(
                quietButton(getString(R.string.delete), danger = true) {
                    confirmDeleteImportedTransaction(transaction)
                },
                LinearLayout.LayoutParams(0, dp(50), 0.55f).apply {
                    marginStart = dp(8)
                },
            )
            card.addView(actions, spaced(top = 8))
            pendingList.addView(card, spaced(bottom = 10))
        }
        if (pending.size > pendingVisibleLimit) {
            val remaining = pending.size - pendingVisibleLimit
            val nextPageSize = minOf(MAX_VISIBLE_ITEMS, remaining)
            pendingList.addView(
                secondaryButton("Carregar mais $nextPageSize pendências") {
                    pendingVisibleLimit += nextPageSize
                    renderPending(pending, state)
                },
                spaced(top = 4, bottom = 8),
            )
        }
    }

    private fun renderRecords(state: FinanceState) {
        val expenses = state.expenses
        val transactions = state.transactions.associateBy { it.fingerprint }
        val validReceiptIds = validReceiptAttachmentIds(state)
        val possibleReceiptIds = ReceiptReconciliation.candidateExpenseIds(state, validReceiptIds)
        recordsList.removeAllViews()
        recordsTitle.text = getString(R.string.classified_count, expenses.size)
        if (expenses.isEmpty()) {
            val empty = card()
            empty.addView(label("Sua história começa no próximo registro", 15, true))
            empty.addView(mutedLabel("Compartilhe pelo PicPay ou faça um registro manual.", 12), spaced(top = 4))
            recordsList.addView(empty)
            return
        }
        val sortedExpenses = expenses.sortedByDescending { it.createdAtEpochMillis }
        sortedExpenses.take(recordVisibleLimit).forEach { expense ->
            val card = card()
            val transaction = expense.transactionFingerprint?.let(transactions::get)
            val amountCents = transaction?.amountCents?.let(::abs) ?: expense.amountCents
            val amount = amountCents?.let(::formatCurrency) ?: "valor não informado"
            val evidenceType = ExpenseEvidence.classify(
                expense,
                hasValidReceipt = expense.receiptAttachmentId?.let(validReceiptIds::contains) == true,
                importedFingerprints = transactions.keys,
            )
            val evidenceChip = when (evidenceType) {
                EvidenceType.NONE -> statusChip("SEM COMPROVAÇÃO", R.color.warning, R.color.warning_soft)
                EvidenceType.RECEIPT -> statusChip("ANEXO DO USUÁRIO", R.color.success, R.color.success_soft)
                EvidenceType.STATEMENT -> statusChip("EXTRATO CSV", R.color.success, R.color.success_soft)
                EvidenceType.BOTH -> statusChip("ANEXO + CSV", R.color.success, R.color.success_soft)
            }
            card.addView(evidenceChip)
            card.addView(label("${expense.what} • $amount", 16, true), spaced(top = 10))
            card.addView(mutedLabel("${expense.category} • ${expense.decision}", 13), spaced(top = 4))
            expense.where.takeIf(String::isNotBlank)?.let {
                card.addView(mutedLabel("Onde: $it", 13), spaced(top = 3))
            }
            val origin = when (expense.source) {
                SOURCE_SHARED_RECEIPT -> "via compartilhamento"
                SOURCE_CSV -> "classificado pelo extrato"
                else -> "registro manual"
            }
            card.addView(
                mutedLabel("$origin • ${dateFormatter.format(Date(expense.createdAtEpochMillis))}", 11),
                spaced(top = 6),
            )
            if (expense.id in possibleReceiptIds && evidenceType != EvidenceType.STATEMENT && evidenceType != EvidenceType.BOTH) {
                card.addView(
                    messageCard(
                        "Possível conciliação",
                        "Existe uma saída no CSV com valor igual. A prova por extrato só aparece depois de você revisá-la e salvar o vínculo.",
                        warning = true,
                    ),
                    spaced(top = 8),
                )
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(
                secondaryButton(getString(R.string.edit)) {
                    showExpenseDialog(
                        existing = expense,
                        transaction = transaction,
                        source = expense.source.ifBlank { SOURCE_MANUAL },
                    )
                },
                LinearLayout.LayoutParams(0, dp(50), 1f),
            )
            actions.addView(
                quietButton(getString(R.string.delete), danger = true) { confirmDeleteExpense(expense) },
                LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                    marginStart = dp(8)
                },
            )
            card.addView(actions, spaced(top = 7))
            expense.receiptAttachmentId?.takeIf { it in validReceiptIds }?.let { attachmentId ->
                card.addView(
                    secondaryButton("Abrir anexo • ${formatAttachmentKind(expense.receiptMimeType.orEmpty())}") {
                        openReceiptViewer(attachmentId)
                    },
                    spaced(top = 6),
                )
            }
            recordsList.addView(card, spaced(bottom = 10))
        }
        if (sortedExpenses.size > recordVisibleLimit) {
            val remaining = sortedExpenses.size - recordVisibleLimit
            val nextPageSize = minOf(MAX_VISIBLE_ITEMS, remaining)
            recordsList.addView(
                secondaryButton("Carregar mais $nextPageSize registros") {
                    recordVisibleLimit += nextPageSize
                    renderRecords(state)
                },
                spaced(top = 4, bottom = 8),
            )
        }
    }

    private fun showBudgetDialog() {
        val current = runCatching { store.load().monthlyBudgetCents }.getOrNull()
        val input = EditText(this).apply {
            id = View.generateViewId()
            hint = "Valor mensal (ex.: 2500,00)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current?.let(::formatAmountInput).orEmpty())
            selectAll()
            minHeight = dp(48)
        }
        installBankCurrencyMask(input)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(label("Este mesmo orçamento será usado em todos os meses até você alterá-lo.", 12))
            addView(mutedLabel("Limite mensal", 12).apply { labelFor = input.id }, spaced(top = 8))
            addView(input, spaced(top = 6))
            addView(mutedLabel("Digite como no banco: 250000 corresponde a R$ 2.500,00.", 11), spaced(top = 4))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("Orçamento mensal")
            .setView(body)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
        if (current != null) {
            builder.setNeutralButton("Remover") { _, _ ->
                runCatching { store.setMonthlyBudget(null) }
                    .onSuccess { refreshScreen() }
                    .onFailure { Toast.makeText(this, "Não foi possível remover o orçamento.", Toast.LENGTH_LONG).show() }
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = BankCurrencyMask.parsePastedAmount(input.text.toString().trim())?.let(::abs)
                if (amount == null || amount <= 0) {
                    input.error = "Informe um valor maior que zero."
                    return@setOnClickListener
                }
                runCatching { store.setMonthlyBudget(amount) }
                    .onSuccess {
                        dialog.dismiss()
                        refreshScreen()
                    }
                    .onFailure { Toast.makeText(this, "Não foi possível salvar o orçamento.", Toast.LENGTH_LONG).show() }
            }
        }
        dialog.showStyled()
    }

    private fun confirmDeleteExpense(expense: ExpenseRecord) {
        AlertDialog.Builder(this)
            .setTitle("Excluir classificação?")
            .setMessage(
                if (expense.transactionFingerprint == null) {
                    "O registro manual será excluído, junto com o comprovante cifrado anexado, se houver."
                } else {
                    "A movimentação continuará no extrato e voltará para a lista de pendências. O comprovante cifrado anexado a esta classificação também será excluído."
                },
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                runCatching { store.deleteExpense(expense.id) }
                    .onSuccess { refreshScreen() }
                    .onFailure { Toast.makeText(this, "Não foi possível excluir a classificação.", Toast.LENGTH_LONG).show() }
            }
            .showStyled()
    }

    private fun showReceiptInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Registrar pelo comprovante")
            .setMessage(
                "1. Abra a movimentação no PicPay.\n\n" +
                    "2. Toque em Compartilhar comprovante.\n\n" +
                    "3. Escolha Finanças Conscientes.\n\n" +
                    "O app confere o formato e a integridade do arquivo escolhido, cifra a cópia no armazenamento privado e permite reabri-la dentro do app. " +
                    "Isso não verifica a origem PicPay nem confirma que o documento representa um Pix; essa informação depende de você. " +
                    "Compartilhar texto ou apenas o nome do arquivo não conta como comprovante.",
            )
            .setPositiveButton("Entendi", null)
            .showStyled()
    }

    private fun showMainMenu() {
        val dialog = android.app.Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(20))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.surface))
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    dp(24).toFloat(), dp(24).toFloat(),
                    dp(24).toFloat(), dp(24).toFloat(),
                    0f, 0f,
                )
            }
        }
        val menuHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val mark = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_app)
                contentDescription = "Logo Finanças Conscientes"
            }
            addView(mark, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(11) })
            val title = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Utilidades", 21, true))
                addView(mutedLabel("Atalhos e controles", 12), spaced(top = 2))
            }
            addView(title)
        }
        panel.addView(menuHeading, spaced(bottom = 12))

        val menuScroll = ScrollView(this).apply { isFillViewport = true }
        val menuItems = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menuScroll.addView(menuItems)
        panel.addView(menuScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        fun group(title: String) {
            menuItems.addView(overline(title), spaced(top = 12, bottom = 4))
        }
        fun item(title: String, detail: String, danger: Boolean = false, action: () -> Unit) {
            val row = card().apply {
                isClickable = true
                isFocusable = true
                addView(label(title, 15, true).apply {
                    if (danger) setTextColor(getColor(R.color.danger))
                })
                addView(mutedLabel(detail, 12), spaced(top = 3))
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            menuItems.addView(row, spaced(top = 5))
        }

        group("CAPTURA")
        item("Registrar por comprovante", "Anexar PDF ou imagem cifrados") { showReceiptInstructions() }
        item("Novo registro manual", "Registrar um gasto com ou sem comprovante") {
            showExpenseDialog(source = SOURCE_MANUAL)
        }
        group("EXTRATO")
        item("Importar extrato CSV", "Atualizar movimentações do PicPay") { openCsvPicker() }
        item("Gerenciar extrato", "Revisar ou remover movimentações importadas") { showImportedTransactionsDialog() }
        group("PLANEJAMENTO")
        item("Orçamento mensal", "Definir ou remover seu limite mensal") { showBudgetDialog() }
        group("PERSONALIZAÇÃO")
        item("Aparência", "Tema atual: ${appearanceModeLabel()}") { showAppearanceDialog() }
        item("Categorias e decisões", "Editar opções e cores exibidas nos resultados") {
            settingsOpen = true
            renderCurrentScreen(loadedState)
        }
        group("LEMBRETES")
        item("Lembretes do PicPay", reminderStatusLabel()) { showPicPayReminderSettings() }
        group("PRIVACIDADE")
        item("Privacidade e funcionamento", "Como os arquivos e registros ficam neste aparelho") { showPrivacyDialog() }
        group("DADOS LOCAIS")
        item("Apagar todos os dados", "Remove registros, comprovantes, extrato e sessão de lembrete", danger = true) {
            confirmClearData()
        }

        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.58f)
            setGravity(Gravity.START or Gravity.TOP)
            setLayout(minOf(dp(380), (resources.displayMetrics.widthPixels * 0.84f).toInt()),
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(dp(380), (resources.displayMetrics.widthPixels * 0.84f).toInt()),
            ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun appearanceModeLabel(): String = when (AppAppearance.selectedMode(this)) {
        AppAppearance.MODE_DARK -> "Escuro"
        AppAppearance.MODE_SYSTEM -> if (
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) "Sistema (escuro agora)" else "Sistema (claro agora)"
        else -> "Claro"
    }

    private fun showAppearanceDialog() {
        val modes = arrayOf(AppAppearance.MODE_SYSTEM, AppAppearance.MODE_LIGHT, AppAppearance.MODE_DARK)
        val labels = arrayOf("Seguir o sistema", "Claro", "Escuro")
        val selectedIndex = modes.indexOf(AppAppearance.selectedMode(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Tema do aplicativo")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val selected = modes[which]
                if (selected != AppAppearance.selectedMode(this)) {
                    AppAppearance.saveMode(this, selected)
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancelar", null)
            .showStyled()
    }

    private fun settingsActionButton(text: String, danger: Boolean = false, action: () -> Unit): Button =
        quietButton(text, danger, action).apply {
            textSize = 12f
            setPadding(dp(4), 0, dp(4), 0)
            minHeight = dp(44)
        }

    private fun categorySwatchBackground(hex: String): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.parseColor(ExpenseCategoryPalette.normalize(hex) ?: "#90A4AE"))
        cornerRadius = dp(50).toFloat()
        setStroke(dp(1), getColor(R.color.border))
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun showAddCategoryDialog() {
        showOptionNameDialog(
            title = "Nova categoria",
            helper = "Ela ficará disponível nos registros e nos resultados.",
            existing = loadedState.categories,
            onSave = { name ->
                store.updateExpenseSettings { ExpenseSettingsMutations.addCategory(it, name) }
                "Categoria adicionada."
            },
        )
    }

    private fun showRenameCategoryDialog(category: String) {
        showOptionNameDialog(
            title = "Renomear categoria",
            helper = "Os registros já classificados serão atualizados e a cor atual será mantida.",
            initial = category,
            existing = loadedState.categories,
            unchanged = category,
            onSave = { name ->
                store.updateExpenseSettings { ExpenseSettingsMutations.renameCategory(it, category, name) }
                "Categoria e registros atualizados."
            },
        )
    }

    private fun showAddDecisionDialog() {
        showOptionNameDialog(
            title = "Nova decisão",
            helper = "Ela aparecerá no campo Decisão dos registros.",
            existing = loadedState.decisions,
            onSave = { name ->
                store.updateExpenseSettings { ExpenseSettingsMutations.addDecision(it, name) }
                "Decisão adicionada."
            },
        )
    }

    private fun showRenameDecisionDialog(decision: String) {
        showOptionNameDialog(
            title = "Renomear decisão",
            helper = "Os registros já classificados serão atualizados.",
            initial = decision,
            existing = loadedState.decisions,
            unchanged = decision,
            onSave = { name ->
                store.updateExpenseSettings { ExpenseSettingsMutations.renameDecision(it, decision, name) }
                "Decisão e registros atualizados."
            },
        )
    }

    private fun showOptionNameDialog(
        title: String,
        helper: String,
        existing: List<String>,
        initial: String? = null,
        unchanged: String? = null,
        onSave: (String) -> String,
    ) {
        if (existing.size >= MAX_SETTINGS_OPTIONS && unchanged == null) {
            Toast.makeText(this, "Você atingiu o limite de $MAX_SETTINGS_OPTIONS opções.", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(MAX_OPTION_NAME_LENGTH))
            setSingleLine(true)
            initial?.let(::setText)
            background = formFieldBackground()
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(50)
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            hint = "Nome"
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(12))
            addView(mutedLabel(helper, 12))
            addView(input, spaced(top = 12))
            addView(mutedLabel("Até $MAX_OPTION_NAME_LENGTH caracteres.", 11), spaced(top = 5))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(body)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = input.text.toString().trim().replace(OPTION_WHITESPACE, " ")
                val error = optionNameError(candidate, existing, unchanged)
                if (error != null) {
                    input.error = error
                    return@setOnClickListener
                }
                runCatching { onSave(candidate) }
                    .onSuccess { message ->
                        dialog.dismiss()
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        refreshScreen()
                    }
                    .onFailure {
                        Toast.makeText(this, "Não foi possível salvar esta opção.", Toast.LENGTH_LONG).show()
                    }
            }
        }
        dialog.showStyled()
    }

    private fun optionNameError(candidate: String, existing: List<String>, unchanged: String?): String? = when {
        candidate.isBlank() -> "Informe um nome."
        candidate.length > MAX_OPTION_NAME_LENGTH -> "Use até $MAX_OPTION_NAME_LENGTH caracteres."
        candidate.any(Char::isISOControl) -> "O nome contém caracteres inválidos."
        existing.any { it != unchanged && it.equals(candidate, ignoreCase = true) } -> "Essa opção já existe."
        else -> null
    }

    private fun showCategoryColorDialog(category: String) {
        val currentColor = ExpenseCategoryPalette.colorFor(category, loadedState.categoryColors)
        val currentName = ExpenseCategoryPalette.choices.firstOrNull { it.hex == currentColor }?.name ?: "Personalizada"
        lateinit var colorDialog: AlertDialog
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(14))
            addView(mutedLabel("Selecione a cor que identifica $category em Resultados.", 12))
            addView(mutedLabel("Selecionada: $currentName", 12), spaced(top = 5))
        }
        ExpenseCategoryPalette.choices.chunked(4).forEachIndexed { rowIndex, rowChoices ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowChoices.forEach { choice ->
                val selected = choice.hex == currentColor
                val chip = TextView(this).apply {
                    text = if (selected) "✓" else ""
                    gravity = Gravity.CENTER
                    textSize = 20f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(contrastingTextColor(Color.parseColor(choice.hex)))
                    background = categorySwatchBackground(choice.hex)
                    contentDescription = "${choice.name}${if (selected) ", selecionada" else ", escolher"}"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        runCatching {
                            store.updateExpenseSettings {
                                ExpenseSettingsMutations.setCategoryColor(it, category, choice.hex)
                            }
                        }.onSuccess {
                            colorDialog.dismiss()
                            refreshScreen()
                        }.onFailure {
                            Toast.makeText(this@MainActivity, "Não foi possível salvar a cor.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                row.addView(chip, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                })
            }
            body.addView(row, spaced(top = if (rowIndex == 0) 12 else 8))
        }
        colorDialog = AlertDialog.Builder(this)
            .setTitle("Cor da categoria")
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Fechar", null)
            .create()
        colorDialog.showStyled()
    }

    private fun contrastingTextColor(color: Int): Int {
        val luminance = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return if (luminance > 160) Color.BLACK else Color.WHITE
    }

    private fun confirmDeleteCategory(category: String) {
        if (category in REQUIRED_CATEGORIES) return
        val affectedCount = loadedState.expenses.count { it.category == category }
        val details = if (affectedCount > 0) {
            "${quantity(affectedCount, "registro está classificado", "registros estão classificados")} como $category. " +
                "Se remover a categoria, ${quantity(affectedCount, "ele ficará", "eles ficarão")} como “Sem categoria”. " +
                "Os valores, comprovantes e demais dados desses registros serão preservados."
        } else {
            "Nenhum registro usa esta categoria."
        }
        AlertDialog.Builder(this)
            .setTitle("Remover categoria?")
            .setMessage("$details\n\nEsta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ ->
                runCatching {
                    store.updateExpenseSettings { ExpenseSettingsMutations.deleteCategory(it, category) }
                }.onSuccess {
                    refreshScreen()
                    Toast.makeText(this, "Categoria removida; registros preservados.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Não foi possível remover a categoria.", Toast.LENGTH_LONG).show()
                }
            }
            .showStyled()
    }

    private fun confirmDeleteDecision(decision: String) {
        if (decision == ExpenseDecisions.DEFAULT) return
        val affectedCount = loadedState.expenses.count { it.decision == decision }
        val details = if (affectedCount > 0) {
            "${quantity(affectedCount, "registro está", "registros estão")} com a decisão $decision. " +
                "Se remover, ${quantity(affectedCount, "ele ficará", "eles ficarão")} como “${ExpenseDecisions.DEFAULT}”. " +
                "As categorias, os valores e os comprovantes serão preservados."
        } else {
            "Nenhum registro usa esta decisão."
        }
        AlertDialog.Builder(this)
            .setTitle("Remover decisão?")
            .setMessage("$details\n\nEsta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ ->
                runCatching {
                    store.updateExpenseSettings { ExpenseSettingsMutations.deleteDecision(it, decision) }
                }.onSuccess {
                    refreshScreen()
                    Toast.makeText(this, "Decisão removida; registros preservados.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Não foi possível remover a decisão.", Toast.LENGTH_LONG).show()
                }
            }
            .showStyled()
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacidade e funcionamento")
            .setMessage(
                "• PDFs e imagens escolhidos por você são copiados e cifrados em armazenamento privado sem backup.\n\n" +
                    "• O app confere formato e integridade do arquivo, não sua origem nem se o conteúdo é um comprovante PicPay/Pix. Texto ou nome de arquivo não conta como anexo.\n\n" +
                    "• O CSV confirma movimentações; possíveis conciliações com registros manuais exigem sua revisão.\n\n" +
                    "• Registros, CSV normalizado e referências a anexos são cifrados com Android Keystore. Se você ativar os lembretes opcionais, ficam separados apenas flags e horários locais da sessão — sem valores ou conteúdo financeiro.\n\n" +
                    "• O app não acessa Internet, não lê notificações e não pede credenciais bancárias.\n\n" +
                    "• Anexos não identificam automaticamente um Pix. PDFs usam cópia temporária no cache privado para visualização; ela é removida ao fechar ou na próxima abertura após encerramento abrupto.",
            )
            .setPositiveButton("Entendi", null)
            .showStyled()
    }

    private fun showPicPayReminderSettings() {
        val sessionStore = ReminderSessionStore(applicationContext)
        val usageGranted = hasPicPayUsageAccess()
        val notificationsGranted = hasNotificationPermission()
        val monitorResponsive = sessionStore.serviceHeartbeatMillis > 0L &&
            System.currentTimeMillis() - sessionStore.serviceHeartbeatMillis <= REMINDER_HEARTBEAT_TIMEOUT_MILLIS
        val status = when {
            !sessionStore.enabled -> "Desativado"
            !usageGranted -> "Falta acesso ao uso dos aplicativos"
            !notificationsGranted -> "Notificações ou canal desativado"
            !monitorResponsive -> "Interrompido pelo sistema"
            else -> "Ativo"
        }
        val detail = when {
            !sessionStore.enabled -> "Desativado. Com sua autorização, o app percebe apenas quando o pacote oficial do PicPay entra em primeiro plano."
            !usageGranted -> "Interrompido: reative o acesso ao uso dos aplicativos para continuar."
            !notificationsGranted -> "Interrompido: permita notificações e reative os canais ‘Lembretes do PicPay’ e ‘Monitor local do PicPay’."
            !monitorResponsive -> "O serviço não informou atividade recente. Toque em Retomar para iniciar novamente o monitor local."
            else -> "Ativo. O monitor observa localmente apenas o pacote com.picpay e mantém um aviso discreto enquanto estiver ligado."
        }
        AlertDialog.Builder(this)
            .setTitle("Lembretes do PicPay • $status")
            .setMessage(
                "$detail\n\n" +
                    "Ao abrir o PicPay, você recebe um lembrete. Se sair sem compartilhar um PDF ou imagem que o app consiga cifrar, aguardamos 6 segundos e perguntamos se faltou o comprovante. Uma nova abertura após esse intervalo inicia outro ciclo. CSV, texto e nome de arquivo não contam.\n\n" +
                    "O monitor não lê notificações, não captura tela, não acessa Internet nem cria despesas. Guarda apenas a sessão atual e seus horários neste aparelho."
            )
            .setNegativeButton("Fechar", null)
            .setPositiveButton(if (sessionStore.enabled && usageGranted && notificationsGranted && monitorResponsive) "Desativar" else "Ativar / Retomar") { _, _ ->
                if (sessionStore.enabled && usageGranted && notificationsGranted && monitorResponsive) {
                    sessionStore.enabled = false
                    sessionStore.pendingEnable = false
                    sessionStore.clearSession()
                    runCatching { PicPayReminderService.stop(this) }
                    Toast.makeText(this, "Lembretes desativados e sessão apagada.", Toast.LENGTH_SHORT).show()
                } else {
                    sessionStore.pendingEnable = true
                    continuePicPayReminderSetup()
                }
            }
            .showStyled()
    }

    private fun reminderStatusLabel(): String {
        val sessionStore = ReminderSessionStore(applicationContext)
        return when {
            !sessionStore.enabled -> "Desativado • monitoramento opcional e local"
            !hasPicPayUsageAccess() -> "Falta acesso ao uso"
            !hasNotificationPermission() -> "Falta permissão de notificações"
            sessionStore.serviceHeartbeatMillis <= 0L ||
                System.currentTimeMillis() - sessionStore.serviceHeartbeatMillis > REMINDER_HEARTBEAT_TIMEOUT_MILLIS -> "Interrompido pelo sistema • toque para retomar"
            else -> "Ativo • só observa o PicPay"
        }
    }

    private fun continuePicPayReminderSetup() {
        val sessionStore = ReminderSessionStore(applicationContext)
        if (!sessionStore.pendingEnable) return
        if (!hasRuntimeNotificationPermission() && Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
            return
        }
        if (!hasNotificationPermission()) {
            waitingForNotificationSettingsReturn = true
            runCatching {
                startActivity(notificationSettingsIntent())
            }.onFailure {
                waitingForNotificationSettingsReturn = false
                sessionStore.pendingEnable = false
                Toast.makeText(this, "Não foi possível abrir as configurações de notificações.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!hasPicPayUsageAccess()) {
            waitingForUsageAccessReturn = true
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure {
                    waitingForUsageAccessReturn = false
                    sessionStore.pendingEnable = false
                    Toast.makeText(this, "Não foi possível abrir o acesso ao uso. Ative-o nas Configurações do Android.", Toast.LENGTH_LONG).show()
                }
            return
        }
        sessionStore.pendingEnable = false
        sessionStore.enabled = true
        sessionStore.pollCursorMillis = System.currentTimeMillis()
        runCatching { PicPayReminderService.start(this) }
            .onSuccess { Toast.makeText(this, "Lembretes do PicPay ativados neste aparelho.", Toast.LENGTH_LONG).show() }
            .onFailure {
                sessionStore.enabled = false
                Toast.makeText(this, "O Android não iniciou o monitor. Confira as permissões e tente novamente.", Toast.LENGTH_LONG).show()
            }
    }

    private fun hasPicPayUsageAccess(): Boolean = runCatching {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun hasRuntimeNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean {
        if (!hasRuntimeNotificationPermission()) return false
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val hasDisabledChannel = listOf(
                PicPayReminderService.CHANNEL_REMINDERS_ID,
                PicPayReminderService.CHANNEL_SERVICE_ID,
            ).any { channelId ->
                manager.getNotificationChannel(channelId)?.importance == android.app.NotificationManager.IMPORTANCE_NONE
            }
            if (hasDisabledChannel) return false
        }
        return true
    }

    private fun notificationSettingsIntent(): Intent {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        val disabledChannel = if (Build.VERSION.SDK_INT >= 26) {
            listOf(
                PicPayReminderService.CHANNEL_REMINDERS_ID,
                PicPayReminderService.CHANNEL_SERVICE_ID,
            ).firstOrNull { channelId ->
                manager.getNotificationChannel(channelId)?.importance == android.app.NotificationManager.IMPORTANCE_NONE
            }
        } else {
            null
        }
        return if (Build.VERSION.SDK_INT >= 26 && disabledChannel != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, disabledChannel)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    }

    private fun showImportedTransactionsDialog() {
        val state = runCatching { store.load() }.getOrElse {
            Toast.makeText(this, "Não foi possível ler as movimentações.", Toast.LENGTH_LONG).show()
            return
        }
        val linked = state.expenses.mapNotNullTo(mutableSetOf()) { it.transactionFingerprint }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(12))
            addView(
                label(
                    "${quantity(state.transactions.size, "movimentação", "movimentações")}. " +
                        "Apagar aqui não apaga o CSV original; você pode importá-lo novamente.",
                    12,
                ),
            )
        }

        lateinit var dialog: AlertDialog
        if (state.transactions.isEmpty()) {
            body.addView(label("Nenhuma movimentação importada.", 14), spaced(top = 12))
        } else {
            var shownCount = 0
            var appendNextPage: () -> Unit = {}
            val loadMoreButton = secondaryButton("Carregar mais movimentações") { appendNextPage() }
            appendNextPage = {
                (loadMoreButton.parent as? ViewGroup)?.removeView(loadMoreButton)
                val end = (shownCount + MANAGED_TRANSACTIONS_PAGE_SIZE).coerceAtMost(state.transactions.size)
                for (index in shownCount until end) {
                    val transaction = state.transactions[index]
                    val item = card()
                    item.addView(label(transactionHeading(transaction), 14, true))
                    item.addView(
                        label("${transactionDirectionLabel(transaction)} • ${transaction.dateText}", 12),
                        spaced(top = 4),
                    )
                    transaction.counterparty.takeIf(String::isNotBlank)?.let {
                        item.addView(label("Onde: $it", 12), spaced(top = 3))
                    }
                    if (transaction.fingerprint in linked) {
                        item.addView(
                            label("A classificação ligada será mantida sem vínculo se você remover esta movimentação.", 11),
                            spaced(top = 4),
                        )
                    }
                    item.addView(
                        quietButton(getString(R.string.remove_from_statement), danger = true) {
                            dialog.dismiss()
                            confirmDeleteImportedTransaction(transaction)
                        },
                        spaced(top = 7),
                    )
                    body.addView(item, spaced(top = 8))
                }
                shownCount = end
                if (shownCount < state.transactions.size) {
                    val nextCount = minOf(MANAGED_TRANSACTIONS_PAGE_SIZE, state.transactions.size - shownCount)
                    loadMoreButton.text = "Carregar mais $nextCount movimentações"
                    body.addView(loadMoreButton, spaced(top = 10))
                }
            }
            appendNextPage()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Movimentações importadas")
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Fechar", null)
        if (state.transactions.isNotEmpty()) {
            builder.setNeutralButton("Apagar extrato") { _, _ -> confirmClearImportedTransactions() }
        }
        dialog = builder.create()
        dialog.showStyled()
    }

    private fun confirmDeleteImportedTransaction(transaction: ImportedTransaction) {
        AlertDialog.Builder(this)
            .setTitle("Apagar movimentação importada?")
            .setMessage(
                "Ela sairá do painel e das pendências. Uma classificação ligada será mantida sem vínculo; o comprovante anexado continua preservado. " +
                    "Você poderá restaurar a movimentação importando o CSV novamente.",
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->
                runCatching { store.deleteImportedTransaction(transaction.fingerprint) }
                    .onSuccess {
                        Toast.makeText(this, "Movimentação removida do extrato local.", Toast.LENGTH_SHORT).show()
                        refreshScreen()
                    }
                    .onFailure {
                        Toast.makeText(this, "Não foi possível apagar a movimentação.", Toast.LENGTH_LONG).show()
                    }
            }
            .showStyled()
    }

    private fun confirmClearImportedTransactions() {
        AlertDialog.Builder(this)
            .setTitle("Apagar todo o extrato importado?")
            .setMessage(
                "Todas as movimentações importadas serão removidas. Suas classificações, comprovantes cifrados e orçamento serão preservados, " +
                    "mas as classificações ficarão sem vínculo ao extrato. Os CSVs poderão ser importados novamente.",
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar extrato") { _, _ ->
                runCatching { store.clearImportedTransactions() }
                    .onSuccess {
                        Toast.makeText(this, "Extrato importado removido.", Toast.LENGTH_SHORT).show()
                        refreshScreen()
                    }
                    .onFailure { Toast.makeText(this, "Não foi possível apagar o extrato.", Toast.LENGTH_LONG).show() }
            }
            .showStyled()
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming?.action == PicPayReminderService.ACTION_REGISTER_WITHOUT_RECEIPT) {
            incoming.action = null
            val reminderStore = ReminderSessionStore(applicationContext)
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            reminderStore.withStateLock {
                reminderStore.transition { current ->
                    ReminderStateMachine.close(current, System.currentTimeMillis())
                }
                notificationManager.cancel(PicPayReminderService.NOTIFICATION_OPEN)
                notificationManager.cancel(PicPayReminderService.NOTIFICATION_MISSING)
            }
            window.decorView.post { showExpenseDialog(source = SOURCE_MANUAL) }
            return
        }
        if (incoming?.action != Intent.ACTION_SEND) return
        val mimeType = incoming.type.orEmpty().lowercase(Locale.ROOT)
        val stream = incoming.sharedStream()
        incoming.action = null
        if (
            stream != null &&
            (mimeType.contains("csv") || mimeType.contains("excel") || mimeType.contains("comma-separated-values"))
        ) {
            importCsv(stream)
            return
        }
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(MAX_SHARED_TEXT)
        val draft = ExpenseFormDraft(
            existing = null,
            transaction = null,
            source = SOURCE_SHARED_RECEIPT,
            amountText = "",
            what = if (mimeType == "text/plain") sharedText else "",
            where = "",
            category = loadedState.categories.firstOrNull() ?: ExpenseCategories.DEFAULT,
            decision = loadedState.decisions.firstOrNull() ?: ExpenseDecisions.DEFAULT,
            shareNotice = "Texto ou nome de arquivo não comprova uma compra. Anexe um arquivo; o app só confere formato e integridade, não a origem ou o conteúdo.",
        )
        if (stream == null) {
            window.decorView.post { showExpenseDialog(restoredDraft = draft) }
            return
        }

        val progress = ProgressBar(this).apply { isIndeterminate = true }
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Cifrando comprovante")
            .setMessage("O formato do arquivo será conferido; a cópia será cifrada no armazenamento privado. O app não verifica a origem nem o conteúdo bancário.")
            .setView(progress)
            .setCancelable(false)
            .create()
        progressDialog.showStyled()
        Thread {
            val result = runCatching { store.receiptAttachments.copyAndEncrypt(stream) }
            runOnUiThread {
                progressDialog.dismiss()
                result.onSuccess { attachment ->
                    transientAttachmentIds += attachment.id
                    if (attachment.mimeType in SUPPORTED_RECEIPT_MIME_TYPES) {
                        PicPayReminderService.markReceiptReceived(applicationContext)
                    }
                    showExpenseDialog(restoredDraft = draft.copy(
                        pendingAttachment = attachment,
                        shareNotice = "Formato reconhecido e cópia cifrada. Isso não confirma a origem ou o conteúdo; o arquivo será vinculado ao registro ao salvar.",
                    ))
                }.onFailure { error ->
                    showExpenseDialog(restoredDraft = draft.copy(
                        shareNotice = error.message ?: "O arquivo não foi anexado; nenhum dado do documento foi guardado.",
                    ))
                }
            }
        }
            .start()
    }

    private fun handleAttachmentPickerResult(resultCode: Int, data: Intent?) {
        val draft = attachmentPickerDraft ?: return
        attachmentPickerDraft = null
        val uri = if (resultCode == RESULT_OK) data?.data else null
        if (uri == null) {
            showExpenseDialog(restoredDraft = draft)
            return
        }
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Cifrando comprovante")
            .setMessage("Conferindo o formato e cifrando a cópia no armazenamento privado.")
            .setView(progress)
            .setCancelable(false)
            .create()
        progressDialog.showStyled()
        Thread {
            val result = runCatching { store.receiptAttachments.copyAndEncrypt(uri) }
            runOnUiThread {
                progressDialog.dismiss()
                result.onSuccess { attachment ->
                    draft.pendingAttachment?.id?.let { previous ->
                        store.receiptAttachments.delete(previous)
                        transientAttachmentIds.remove(previous)
                    }
                    transientAttachmentIds += attachment.id
                    showExpenseDialog(restoredDraft = draft.copy(
                        pendingAttachment = attachment,
                        removeExistingAttachment = false,
                        shareNotice = "Formato reconhecido e cópia cifrada. Isso não confirma a origem ou o conteúdo; o arquivo será vinculado ao registro ao salvar.",
                    ))
                }.onFailure { error ->
                    showExpenseDialog(restoredDraft = draft.copy(
                        shareNotice = error.message ?: "Não foi possível anexar esse arquivo.",
                    ))
                }
            }
        }.start()
    }

    private fun openCsvPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv",
                    "application/vnd.ms-excel",
                    "text/plain",
                ),
            )
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_OPEN_CSV)
    }

    private fun importCsv(uri: Uri) {
        Toast.makeText(this, "Processando CSV localmente…", Toast.LENGTH_SHORT).show()
        Thread {
            val outcome = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use(::readLimited)
                    ?: throw CsvImportException("Não foi possível abrir o arquivo.")
                val parsed = PicPayCsvParser.parse(bytes)
                val added = store.importTransactions(parsed.transactions)
                "${quantity(parsed.transactions.size, "linha válida", "linhas válidas")}; " +
                    quantity(added, "nova", "novas") + "."
            }
            runOnUiThread {
                outcome.onSuccess { message ->
                    Toast.makeText(this, "Importação concluída: $message", Toast.LENGTH_LONG).show()
                    refreshScreen()
                }.onFailure { error ->
                    val message = error.message ?: "Falha desconhecida ao importar o CSV."
                    AlertDialog.Builder(this)
                        .setTitle("CSV não importado")
                        .setMessage(message)
                        .setPositiveButton("Entendi", null)
                        .showStyled()
                }
            }
        }.start()
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_CSV_BYTES) throw CsvImportException("O CSV excede o limite de 5 MB.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun classifyTransaction(transaction: ImportedTransaction) {
        val state = runCatching { store.load() }.getOrElse {
            Toast.makeText(this, "Não foi possível ler os dados locais.", Toast.LENGTH_LONG).show()
            return
        }
        val candidates = findDraftCandidates(transaction, state)
        if (candidates.isNotEmpty()) {
            showDraftCandidatePicker(transaction, candidates)
            return
        }
        showExpenseDialog(
            transaction = transaction,
            prefillWhat = transaction.description.ifBlank { transaction.type },
            prefillWhere = transaction.counterparty,
            source = SOURCE_CSV,
        )
    }

    private fun findDraftCandidates(transaction: ImportedTransaction, state: FinanceState): List<ExpenseRecord> {
        return ReceiptReconciliation.findForManualReview(
            transaction = transaction,
            state = state,
            validReceiptAttachmentIds = validReceiptAttachmentIds(state),
            limit = MAX_RECONCILIATION_CANDIDATES,
        )
    }

    private fun showDraftCandidatePicker(transaction: ImportedTransaction, candidates: List<ExpenseRecord>) {
        lateinit var dialog: AlertDialog
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(12))
            addView(messageCard(
                "Escolha para revisar",
                "${quantity(candidates.size, "registro possível", "registros possíveis")} com valor igual. " +
                    "Datas próximas aparecem primeiro; comprovantes antigos também podem ser escolhidos. Nada será vinculado até você salvar, e você pode classificar como novo gasto.",
                warning = true,
            ))
            candidates.forEach { expense ->
                val dayDifference = transaction.occurredAtEpochMillis?.let {
                    distanceBetween(expense.createdAtEpochMillis, it) / (24L * 60 * 60 * 1000)
                }
                val item = card().apply {
                    isClickable = true
                    isFocusable = true
                    addView(label(expense.what.ifBlank { "Registro sem descrição" }, 15, true))
                    addView(mutedLabel(
                        "${expense.category} • ${formatCurrency(expense.amountCents?.let(::abs) ?: 0L)} • " +
                            "${dateFormatter.format(Date(expense.createdAtEpochMillis))}",
                        12,
                    ), spaced(top = 4))
                    addView(mutedLabel(
                        when {
                            dayDifference == null -> "Data do CSV não reconhecida"
                            dayDifference == 0L -> "Mesmo dia do extrato"
                            else -> "$dayDifference dias de diferença"
                        },
                        11,
                    ), spaced(top = 3))
                    setOnClickListener {
                        dialog.dismiss()
                        showExpenseDialog(
                            existing = expense,
                            transaction = transaction,
                            prefillWhat = transaction.description.ifBlank { transaction.type },
                            prefillWhere = transaction.counterparty,
                            source = SOURCE_CSV,
                        )
                    }
                }
                addView(item, spaced(top = 8))
            }
            val newExpense = card().apply {
                isClickable = true
                isFocusable = true
                addView(label("Nenhum destes — classificar como novo gasto", 15, true))
                addView(mutedLabel("Não vincular a um registro existente.", 12), spaced(top = 3))
                setOnClickListener {
                    dialog.dismiss()
                    showExpenseDialog(
                        transaction = transaction,
                        prefillWhat = transaction.description.ifBlank { transaction.type },
                        prefillWhere = transaction.counterparty,
                        source = SOURCE_CSV,
                    )
                }
            }
            addView(newExpense, spaced(top = 8))
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Revise antes de vincular")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.showStyled()
    }

    private fun distanceBetween(first: Long, second: Long): Long =
        if (first >= second) first - second else second - first

    private fun showExpenseDialog(
        existing: ExpenseRecord? = null,
        transaction: ImportedTransaction? = null,
        prefillWhat: String = "",
        prefillWhere: String = "",
        source: String = SOURCE_MANUAL,
        restoredDraft: ExpenseFormDraft? = null,
    ) {
        val draft = restoredDraft ?: ExpenseFormDraft(
            existing = existing,
            transaction = transaction,
            source = source,
            amountText = (existing?.amountCents ?: transaction?.amountCents?.let(::abs))
                ?.let(::formatAmountInput).orEmpty(),
            what = existing?.what ?: prefillWhat.take(MAX_TEXT_FIELD),
            where = existing?.where ?: prefillWhere.take(MAX_TEXT_FIELD),
            category = existing?.category ?: loadedState.categories.firstOrNull() ?: ExpenseCategories.DEFAULT,
            decision = existing?.decision ?: loadedState.decisions.firstOrNull() ?: ExpenseDecisions.DEFAULT,
        )
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(12))
        }
        lateinit var dialog: AlertDialog

        val handle = View(this).apply {
            background = roundedBackground(getColor(R.color.border), dp(4).toFloat())
        }
        body.addView(handle, LinearLayout.LayoutParams(dp(38), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })
        val formHeading = when {
            draft.existing != null && draft.transaction != null -> "Revisar classificação"
            draft.existing != null -> "Editar registro"
            draft.transaction != null -> "Classificar saída"
            draft.source == SOURCE_SHARED_RECEIPT -> "Completar compartilhamento"
            else -> "Detalhes do gasto"
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(overline(if (draft.transaction != null) "REVISÃO DO EXTRATO" else "NOVO REGISTRO"))
            addView(label(formHeading, 20, true), spaced(top = 2))
        }
        header.addView(headerText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val closeButton = label("×", 25, false).apply {
            gravity = Gravity.CENTER
            contentDescription = "Fechar formulário"
            minWidth = dp(44)
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            setTextColor(getColor(R.color.text_secondary))
            setOnClickListener { dialog.cancel() }
        }
        header.addView(closeButton)
        body.addView(header, spaced(bottom = 14))

        draft.shareNotice?.let { notice ->
            body.addView(
                messageCard(
                    title = if (draft.pendingAttachment != null) "Arquivo íntegro" else "Comprovante não confirmado",
                    message = notice,
                    warning = draft.pendingAttachment == null,
                ),
                spaced(bottom = 12),
            )
        }
        val isSuggestedMatch = draft.existing != null && draft.existing.transactionFingerprint == null && draft.transaction != null
        if (isSuggestedMatch) {
            val isCloseDateMatch = draft.transaction?.occurredAtEpochMillis?.let {
                distanceBetween(draft.existing!!.createdAtEpochMillis, it) <= MATCH_WINDOW_MILLIS
            } == true
            body.addView(messageCard(
                title = "Possível conciliação",
                message = if (isCloseDateMatch) {
                    "Encontramos um registro com valor igual e data próxima. A movimentação só será vinculada se você salvar esta revisão."
                } else {
                    "Este comprovante foi registrado em outra data e tem o mesmo valor. Confira os detalhes; a movimentação só será vinculada se você salvar esta revisão."
                },
                warning = true,
            ), spaced(bottom = 14))
        }

        val amountInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Valor (ex.: 12,34)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(draft.amountText)
            isEnabled = draft.transaction == null
            setSelectAllOnFocus(true)
            setSingleLine(true)
            background = formFieldBackground()
            setPadding(dp(11), 0, dp(11), 0)
            minimumHeight = dp(48)
            textSize = 20f
            setTextColor(getColor(R.color.primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        if (draft.transaction == null) installBankCurrencyMask(amountInput)
        val whatInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Ex.: almoço, mercado, assinatura"
            setText(draft.what)
            setSingleLine(true)
            background = formFieldBackground()
            setPadding(dp(11), 0, dp(11), 0)
            minimumHeight = dp(48)
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        val whereInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Loja, pessoa ou serviço"
            setText(draft.where)
            setSingleLine(true)
            background = formFieldBackground()
            setPadding(dp(11), 0, dp(11), 0)
            minHeight = dp(48)
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        val categoryOptions = (loadedState.categories + draft.category.takeIf { it.isNotBlank() }.orEmpty()).distinct()
        val decisionOptions = (loadedState.decisions + draft.decision.takeIf { it.isNotBlank() }.orEmpty()).distinct()
        val categorySpinner = Spinner(this).apply {
            id = View.generateViewId()
            adapter = darkSpinnerAdapter(categoryOptions)
            background = formFieldBackground()
            setPadding(dp(8), 0, dp(8), 0)
            setMinimumHeight(dp(48))
            setPopupBackgroundDrawable(roundedBackground(getColor(R.color.surface), dp(14).toFloat()))
            setSelection(categoryOptions.indexOf(draft.category).takeIf { it >= 0 } ?: 0)
        }
        val decisionSpinner = Spinner(this).apply {
            id = View.generateViewId()
            adapter = darkSpinnerAdapter(decisionOptions)
            background = formFieldBackground()
            setPadding(dp(8), 0, dp(8), 0)
            setMinimumHeight(dp(48))
            setPopupBackgroundDrawable(roundedBackground(getColor(R.color.surface), dp(14).toFloat()))
            setSelection(decisionOptions.indexOf(draft.decision).takeIf { it >= 0 } ?: 0)
        }

        fun fieldGroup(title: String, field: View): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(mutedLabel(title, 12).apply { labelFor = field.id })
            addView(field, spaced(top = 6))
        }
        fun fieldRow(first: View, second: View): LinearLayout = LinearLayout(this).apply {
            val narrowPhoneLayout = resources.configuration.screenWidthDp <= 420
            orientation = if (narrowPhoneLayout) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (narrowPhoneLayout) {
                addView(first)
                addView(second, spaced(top = 10))
            } else {
                addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
                addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        val amountField = fieldGroup("Valor", amountInput).apply {
            addView(
                mutedLabel("Como no banco: 1234 = R$ 12,34. Você também pode colar 0.1 = R$ 0,10.", 10),
                spaced(top = 4),
            )
        }
        body.addView(amountField)
        if (draft.transaction != null) {
            body.addView(mutedLabel("Confirmado pelo CSV; não pode ser alterado aqui.", 11), spaced(top = 2))
        }
        body.addView(fieldGroup("O que foi?", whatInput), spaced(top = 12))
        body.addView(fieldGroup("Onde foi?", whereInput), spaced(top = 12))
        body.addView(fieldRow(fieldGroup("Categoria", categorySpinner), fieldGroup("Decisão", decisionSpinner)), spaced(top = 12))
        body.addView(mutedLabel("Sem julgamento: use estas escolhas para perceber padrões.", 11), spaced(top = 5))

        activeFormDraftSnapshot = {
            draft.copy(
                amountText = amountInput.text.toString(),
                what = whatInput.text.toString(),
                where = whereInput.text.toString(),
                category = categorySpinner.selectedItem.toString(),
                decision = decisionSpinner.selectedItem.toString(),
            )
        }

        val existingAttachmentId = draft.existing?.receiptAttachmentId
            ?.takeIf { !draft.removeExistingAttachment && it in validReceiptAttachmentIds(loadedState) }
        val selectedAttachment = draft.pendingAttachment
        val attachmentCard = card()
        attachmentCard.addView(label("Comprovante anexado por você", 14, true))
        attachmentCard.addView(
            mutedLabel("PDF, PNG, JPEG ou WebP • até 10 MB • cifrado neste aparelho. O conteúdo não é verificado com o PicPay.", 11),
            spaced(top = 3),
        )
        val attachmentDescription = when {
            selectedAttachment != null -> "${formatAttachmentKind(selectedAttachment.mimeType)} • " +
                "${formatFileSize(selectedAttachment.sizeBytes)} • cifrado, aguardando salvar"
            draft.removeExistingAttachment -> "O comprovante será removido ao salvar."
            existingAttachmentId != null -> "${formatAttachmentKind(draft.existing?.receiptMimeType.orEmpty())} • " +
                "${formatFileSize(draft.existing?.receiptSizeBytes ?: 0L)} • comprovante cifrado"
            draft.existing?.receiptAttachmentId != null -> "Arquivo ausente ou inválido; não conta como anexo íntegro."
            else -> "Sem arquivo anexado. Texto ou nome de arquivo não contam como comprovante."
        }
        attachmentCard.addView(mutedLabel(attachmentDescription, 12), spaced(top = 8))
        attachmentCard.addView(
            secondaryButton(if (selectedAttachment != null || existingAttachmentId != null) "Substituir comprovante" else "Selecionar PDF ou imagem") {
                val pickerDraft = activeFormDraftSnapshot?.invoke() ?: draft
                attachmentPickerDraft = pickerDraft
                activeFormDraftSnapshot = null
                dialog.dismiss()
                openAttachmentPicker()
            },
            spaced(top = 10),
        )
        val viewerAttachmentId = selectedAttachment?.id ?: existingAttachmentId
        if (viewerAttachmentId != null) {
            attachmentCard.addView(
                quietButton("Abrir anexo") { openReceiptViewer(viewerAttachmentId) },
                spaced(top = 4),
            )
            attachmentCard.addView(
                quietButton("Remover comprovante", danger = true) {
                    val editedDraft = activeFormDraftSnapshot?.invoke() ?: draft
                    selectedAttachment?.id?.let { selected ->
                        store.receiptAttachments.delete(selected)
                        transientAttachmentIds.remove(selected)
                    }
                    activeFormDraftSnapshot = null
                    dialog.dismiss()
                    showExpenseDialog(restoredDraft = editedDraft.copy(
                        pendingAttachment = null,
                        removeExistingAttachment = draft.existing?.receiptAttachmentId != null,
                    ))
                },
                spaced(top = 4),
            )
        }
        body.addView(attachmentCard, spaced(top = 16))

        val scroll = ScrollView(this).apply {
            addView(body)
            isFillViewport = false
        }
        dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("Cancelar") { _, _ ->
                activeFormDraftSnapshot = null
                discardPendingAttachment(draft.pendingAttachment)
            }
            .setPositiveButton("Salvar registro", null)
            .create()
        dialog.setOnCancelListener {
            activeFormDraftSnapshot = null
            if (!isChangingConfigurations) discardPendingAttachment(draft.pendingAttachment)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val what = whatInput.text.toString().trim()
                if (what.isBlank()) {
                    whatInput.error = "Informe o que foi o gasto."
                    return@setOnClickListener
                }
                val amountText = amountInput.text.toString().trim()
                val parsedAmount = if (amountText.isBlank()) null else BankCurrencyMask.parsePastedAmount(amountText)?.let(::abs)
                if (parsedAmount == null || parsedAmount <= 0) {
                    amountInput.error = "Informe o valor do registro."
                    return@setOnClickListener
                }
                val transaction = draft.transaction
                val existing = draft.existing
                val receiptId = when {
                    draft.removeExistingAttachment -> null
                    selectedAttachment != null -> selectedAttachment.id
                    existingAttachmentId != null -> existingAttachmentId
                    else -> null
                }
                val record = ExpenseRecord(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
                    transactionFingerprint = transaction?.fingerprint ?: existing?.transactionFingerprint,
                    amountCents = transaction?.amountCents?.let(::abs) ?: parsedAmount,
                    what = what.take(MAX_TEXT_FIELD),
                    where = whereInput.text.toString().trim().take(MAX_TEXT_FIELD),
                    category = categorySpinner.selectedItem.toString(),
                    decision = decisionSpinner.selectedItem.toString(),
                    source = existing?.source ?: draft.source,
                    receiptAttachmentId = receiptId,
                    receiptMimeType = when {
                        receiptId == null -> null
                        selectedAttachment != null -> selectedAttachment.mimeType
                        else -> existing?.receiptMimeType
                    },
                    receiptSizeBytes = when {
                        receiptId == null -> null
                        selectedAttachment != null -> selectedAttachment.sizeBytes
                        else -> existing?.receiptSizeBytes
                    },
                )
                runCatching { store.upsertExpense(record) }
                    .onSuccess {
                        selectedAttachment?.id?.let(transientAttachmentIds::remove)
                        activeFormDraftSnapshot = null
                        dialog.dismiss()
                        if (draft.source == SOURCE_SHARED_RECEIPT && existing == null) {
                            Toast.makeText(
                                this,
                                if (receiptId != null) {
                                    "Arquivo escolhido salvo e cifrado. O conteúdo não foi verificado com o PicPay; a classificação de Pix depende da revisão e do extrato."
                                } else {
                                    "Registro salvo sem arquivo anexado; ele não entra nos resultados por anexo."
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        refreshScreen()
                    }
                    .onFailure {
                        Toast.makeText(this, "Não foi possível salvar o registro.", Toast.LENGTH_LONG).show()
                    }
            }
        }
        dialog.showFormSheet()
    }

    private fun openAttachmentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_OPEN_ATTACHMENT)
    }

    private fun discardPendingAttachment(attachment: br.com.financasconscientes.data.ReceiptAttachmentMetadata?) {
        attachment?.id?.let { id ->
            if (transientAttachmentIds.remove(id)) store.receiptAttachments.delete(id)
        }
    }

    private fun openReceiptViewer(attachmentId: String) {
        startActivity(Intent(this, ReceiptViewerActivity::class.java).putExtra(ReceiptViewerActivity.EXTRA_ATTACHMENT_ID, attachmentId))
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("Apagar todos os dados locais?")
            .setMessage("Movimentações importadas, classificações, orçamento, comprovantes cifrados e sessão de lembrete serão removidos deste aparelho. Os lembretes também serão desativados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->
                runCatching { store.clear() }
                    .onSuccess {
                        ReminderSessionStore(applicationContext).clearAll()
                        runCatching { PicPayReminderService.stop(this) }
                        refreshScreen()
                    }
                    .onFailure {
                        Toast.makeText(this, "Não foi possível apagar os dados.", Toast.LENGTH_LONG).show()
                    }
            }
            .showStyled()
    }

    private fun transactionHeading(transaction: ImportedTransaction): String {
        val name = transaction.description.ifBlank { transaction.type.ifBlank { "Saída" } }
        val amount = transaction.amountCents?.let { formatCurrency(abs(it)) } ?: "valor desconhecido"
        return "$name • $amount"
    }

    private fun transactionDirectionLabel(transaction: ImportedTransaction): String = when (transaction.direction) {
        TransactionDirection.OUTFLOW -> "Saída"
        TransactionDirection.INFLOW -> "Entrada"
        TransactionDirection.UNKNOWN -> "Direção não identificada"
    }

    private fun formatCurrency(cents: Long): String =
        currencyFormatter.format(java.math.BigDecimal.valueOf(cents, 2))

    private fun quantity(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    private fun formatAmountInput(cents: Long): String = BankCurrencyMask.format(cents)

    private fun installBankCurrencyMask(input: EditText) {
        var cents = BankCurrencyMask.parsePastedAmount(input.text.toString())?.let(::abs) ?: 0L
        var isRendering = false
        var oldText = ""
        var editStart = 0
        var removedCount = 0
        var insertedCount = 0
        var oldSelectionStart = 0
        var oldSelectionEnd = 0

        fun render() {
            isRendering = true
            input.setText(BankCurrencyMask.format(cents))
            input.setSelection(input.text.length)
            isRendering = false
        }

        input.setSelectAllOnFocus(true)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
                if (isRendering) return
                oldText = text?.toString().orEmpty()
                editStart = start
                removedCount = count
                insertedCount = after
                oldSelectionStart = input.selectionStart.coerceIn(0, oldText.length)
                oldSelectionEnd = input.selectionEnd.coerceIn(0, oldText.length)
            }

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (isRendering) return
                val newText = editable?.toString().orEmpty()
                val inserted = newText.substring(
                    editStart.coerceIn(0, newText.length),
                    (editStart + insertedCount).coerceIn(0, newText.length),
                )
                val replacedSelection = oldSelectionStart == 0 && oldSelectionEnd == oldText.length
                val clearedValue = newText.isBlank() ||
                    (insertedCount == 0 && removedCount == oldText.length && editStart == 0)

                cents = when {
                    clearedValue -> 0L
                    insertedCount == 1 && removedCount == 0 && editStart == oldText.length && inserted.singleOrNull()?.isDigit() == true ->
                        BankCurrencyMask.appendDigit(cents, inserted.single()) ?: cents
                    insertedCount == 0 && removedCount == 1 && editStart == oldText.length - 1 ->
                        BankCurrencyMask.removeLastDigit(cents)
                    inserted.isNotEmpty() && inserted.all(Char::isDigit) && replacedSelection ->
                        BankCurrencyMask.appendDigits(0L, inserted) ?: cents
                    inserted.isNotEmpty() && inserted.all(Char::isDigit) && editStart == oldText.length ->
                        BankCurrencyMask.appendDigits(cents, inserted) ?: cents
                    else -> BankCurrencyMask.parsePastedAmount(newText)?.let(::abs) ?: cents
                }
                render()
            }
        })

        render()
    }

    private fun card(backgroundResource: Int = R.drawable.card_background): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        setBackgroundResource(backgroundResource)
        elevation = dp(1).toFloat()
    }

    private fun label(textValue: String, sizeSp: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = textValue
            textSize = sizeSp.toFloat()
            setTextColor(getColor(R.color.text_primary))
            setLineSpacing(0f, 1.08f)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextIsSelectable(false)
        }

    private fun mutedLabel(textValue: String, sizeSp: Int): TextView = label(textValue, sizeSp).apply {
        setTextColor(getColor(R.color.text_secondary))
    }

    private fun overline(textValue: String): TextView = label(textValue, 11, true).apply {
        setTextColor(getColor(R.color.primary))
        letterSpacing = 0.06f
    }

    private fun sectionHeading(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, 24, true).apply { letterSpacing = -0.03f })
        addView(mutedLabel(subtitle, 13), spaced(top = 4))
    }

    private fun statusChip(textValue: String, textColor: Int, backgroundColor: Int): TextView =
        label(textValue, 10, true).apply {
            setTextColor(getColor(textColor))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBackground(getColor(backgroundColor), dp(50).toFloat())
        }

    private fun messageCard(title: String, message: String, warning: Boolean): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = roundedBackground(
                getColor(if (warning) R.color.warning_soft else R.color.primary_soft),
                dp(17).toFloat(),
            )
            addView(label(title, 13, true).apply {
                setTextColor(getColor(if (warning) R.color.warning else R.color.primary))
            })
            addView(mutedLabel(message, 12), spaced(top = 4))
        }

    private fun formatAttachmentKind(mimeType: String): String = when (mimeType) {
        "application/pdf" -> "PDF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        "image/webp" -> "WebP"
        else -> "Arquivo cifrado"
    }

    private fun darkSpinnerAdapter(values: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                styleSpinnerRow(super.getView(position, convertView, parent), dropdown = false)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                styleSpinnerRow(super.getDropDownView(position, convertView, parent), dropdown = true)
        }

    private fun styleSpinnerRow(view: View, dropdown: Boolean): View = view.apply {
        (this as? TextView)?.apply {
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(48)
        }
        if (dropdown) background = roundedBackground(getColor(R.color.surface), dp(8).toFloat())
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        bytes > 0L -> "$bytes bytes"
        else -> "tamanho indisponível"
    }

    private fun AlertDialog.showStyled(): AlertDialog {
        show()
        window?.apply {
            setBackgroundDrawable(roundedBackground(getColor(R.color.surface), dp(22).toFloat()))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.62f)
            setLayout(
                minOf(dp(560), resources.displayMetrics.widthPixels - dp(32)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        styleDialogButtons()
        return this
    }

    private fun AlertDialog.Builder.showStyled(): AlertDialog = create().showStyled()

    private fun AlertDialog.showFormSheet(): AlertDialog {
        show()
        val maximumHeight = minOf(dp(780), resources.displayMetrics.heightPixels - dp(72))
        window?.apply {
            val sheetBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(getColor(R.color.app_background))
                cornerRadii = floatArrayOf(
                    dp(24).toFloat(), dp(24).toFloat(),
                    dp(24).toFloat(), dp(24).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }
            setBackgroundDrawable(sheetBackground)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.66f)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maximumHeight)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        styleDialogButtons()
        return this
    }

    private fun AlertDialog.styleDialogButtons() {
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { which ->
                getButton(which)?.let { button ->
                    button.isAllCaps = false
                    button.textSize = 14f
                    button.setTypeface(button.typeface, android.graphics.Typeface.BOLD)
                    button.minHeight = dp(48)
                    val label = button.text?.toString().orEmpty()
                    val color = if (label in DESTRUCTIVE_LABELS) R.color.danger else {
                        if (which == AlertDialog.BUTTON_POSITIVE) R.color.primary else R.color.text_secondary
                    }
                    button.setTextColor(getColor(color))
                }
            }
    }

    private fun primaryButton(textValue: String, onClick: () -> Unit): Button =
        styledButton(textValue, R.drawable.button_primary, R.color.on_primary, onClick)

    private fun secondaryButton(textValue: String, onClick: () -> Unit): Button =
        styledButton(textValue, R.drawable.button_secondary, R.color.primary, onClick)

    private fun quietButton(textValue: String, danger: Boolean = false, onClick: () -> Unit): Button =
        styledButton(
            textValue,
            R.drawable.button_quiet,
            if (danger) R.color.danger else R.color.text_primary,
            onClick,
        )

    private fun styledButton(
        textValue: String,
        backgroundResource: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): Button = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 14f
        setTextColor(getColor(textColor))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = getDrawable(backgroundResource)
        stateListAnimator = null
        minimumHeight = 0
        minHeight = dp(50)
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun formFieldBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(getColor(R.color.surface))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), getColor(R.color.border))
    }

    private fun metricTile(
        title: String,
        value: String,
        detail: String,
        colorResource: Int,
        backgroundResource: Int,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = roundedBackground(getColor(backgroundResource), dp(14).toFloat())
        addView(label(title, 11, true).apply { setTextColor(getColor(colorResource)) })
        addView(label(value, 15, true), spaced(top = 5))
        addView(mutedLabel(detail, 10), spaced(top = 2))
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

    @Suppress("DEPRECATION")
    private fun Intent.sharedStream(): Uri? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private companion object {
        const val REQUEST_OPEN_CSV = 2001
        const val REQUEST_OPEN_ATTACHMENT = 2002
        const val REQUEST_POST_NOTIFICATIONS = 2003
        const val REMINDER_HEARTBEAT_TIMEOUT_MILLIS = 10_000L
        const val STATE_DESTINATION = "primary_destination"
        const val STATE_SETTINGS_OPEN = "settings_open"
        const val STATE_MONTH = "selected_month"
        const val STATE_EXPENSE_DRAFT = "expense_draft"
        const val STATE_FORM_OPEN = "expense_form_open"
        const val STATE_DRAFT_IN_PICKER = "expense_draft_in_picker"
        const val STATE_PENDING_VISIBLE_LIMIT = "pending_visible_limit"
        const val STATE_RECORD_VISIBLE_LIMIT = "record_visible_limit"
        const val DRAFT_EXISTING_ID = "existing_id"
        const val DRAFT_TRANSACTION_FINGERPRINT = "transaction_fingerprint"
        const val DRAFT_SOURCE = "source"
        const val DRAFT_AMOUNT = "amount"
        const val DRAFT_WHAT = "what"
        const val DRAFT_WHERE = "where"
        const val DRAFT_CATEGORY = "category"
        const val DRAFT_DECISION = "decision"
        const val DRAFT_ATTACHMENT_ID = "attachment_id"
        const val DRAFT_ATTACHMENT_MIME = "attachment_mime"
        const val DRAFT_ATTACHMENT_SIZE = "attachment_size"
        const val DRAFT_REMOVE_EXISTING_ATTACHMENT = "remove_existing_attachment"
        const val DRAFT_SHARE_NOTICE = "share_notice"
        const val MAX_CSV_BYTES = 5 * 1024 * 1024
        const val MAX_VISIBLE_ITEMS = 30
        const val MAX_RECONCILIATION_CANDIDATES = 30
        const val MAX_SHARED_TEXT = 500
        const val MAX_TEXT_FIELD = 500
        const val MANAGED_TRANSACTIONS_PAGE_SIZE = 100
        const val MATCH_WINDOW_MILLIS = 48L * 60 * 60 * 1000
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_SHARED_RECEIPT = "shared_receipt"
        const val SOURCE_CSV = "csv"
        val SUPPORTED_RECEIPT_MIME_TYPES = setOf("application/pdf", "image/png", "image/jpeg", "image/webp")

        @Volatile
        var processAttachmentCleanupCompleted = false

        val DESTRUCTIVE_LABELS = setOf(
            "Excluir",
            "Apagar",
            "Apagar extrato",
            "Remover",
            "Remover comprovante",
        )
        val REQUIRED_CATEGORIES = setOf(ExpenseCategories.DEFAULT, ExpenseCategories.UNCATEGORIZED)
        const val MAX_SETTINGS_OPTIONS = 30
        const val MAX_OPTION_NAME_LENGTH = 36
        val OPTION_WHITESPACE = Regex("\\s+")
    }
}
