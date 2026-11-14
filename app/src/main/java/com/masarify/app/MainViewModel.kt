package com.masarify.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masarify.app.ai.ChatMessage
import com.masarify.app.ai.CloudAiClient
import com.masarify.app.ai.MoneySnapshot
import com.masarify.app.ai.NetDiag
import com.masarify.app.update.UpdateChecker
import com.masarify.app.ai.cloudContext
import com.masarify.app.ai.localAnswer
import com.masarify.app.data.AccountEntity
import com.masarify.app.backup.BackupCodec
import com.masarify.app.backup.BackupManager
import com.masarify.app.data.AiMode
import com.masarify.app.data.AiStyle
import com.masarify.app.data.SmsDraftEntity
import com.masarify.app.data.ThemeColor
import com.masarify.app.data.ThemeMode
import com.masarify.app.data.TransactionEntity
import com.masarify.app.data.UserSettings
import com.masarify.app.widget.MasarifyWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val smsDrafts: List<SmsDraftEntity> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val aiThinking: Boolean = false,
    val keyStatus: String = "",
    val diagLines: List<NetDiag.Line> = emptyList(),
    val diagRunning: Boolean = false,
    val backupStatus: String = "",
    val updateStatus: String = "",
    val updateAvailable: UpdateChecker.Release? = null,
    val updateProgress: Int = -1,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val backupBusy: Boolean = false,
    val restoreCandidate: BackupCodec.Parsed? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MasarifyApp
    private val repository = app.repository
    private val settingsRepository = app.settings

    private val chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val thinking = MutableStateFlow(false)
    private val keyStatus = MutableStateFlow("")
    private val diagLines = MutableStateFlow<List<NetDiag.Line>>(emptyList())
    private val diagRunning = MutableStateFlow(false)
    private val updateStatus = MutableStateFlow("")
    private val updateAvailable = MutableStateFlow<UpdateChecker.Release?>(null)
    private val updateProgress = MutableStateFlow(-1)
    private val backupStatus = MutableStateFlow("")
    private val backupBusy = MutableStateFlow(false)
    private val restoreCandidate = MutableStateFlow<BackupCodec.Parsed?>(null)

    /** آخر لقطة مالية محفوظة، حتى تستخدم الشاشات السريعة نفس الأرقام. */
    @Volatile private var lastSnapshot: MoneySnapshot? = null

    private data class CoreState(
        val accounts: List<AccountEntity>,
        val transactions: List<TransactionEntity>,
        val drafts: List<SmsDraftEntity>,
        val settings: UserSettings
    )

    private data class ChatState(val messages: List<ChatMessage>, val thinking: Boolean)

    /** هل يوجد اتصال فعلي بالإنترنت الآن؟ (بدون إجراء طلب شبكة) */
    private fun hasInternet(): Boolean = runCatching {
        val manager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private val extra = combine(
        keyStatus, backupStatus, backupBusy, restoreCandidate, diagLines, diagRunning,
        updateStatus, updateAvailable, updateProgress
    ) { values ->
        ExtraState(
            keyStatus = values[0] as String,
            backupStatus = values[1] as String,
            backupBusy = values[2] as Boolean,
            restoreCandidate = values[3] as BackupCodec.Parsed?,
            diagLines = values[4] as List<NetDiag.Line>,
            diagRunning = values[5] as Boolean,
            updateStatus = values[6] as String,
            updateAvailable = values[7] as UpdateChecker.Release?,
            updateProgress = values[8] as Int
        )
    }

    private val coreFlow = combine(
        repository.accounts,
        repository.transactions,
        repository.smsDrafts,
        settingsRepository.settings
    ) { accounts, transactions, drafts, settings -> CoreState(accounts, transactions, drafts, settings) }

    private val chatFlow = combine(chat, thinking) { messages, isThinking -> ChatState(messages, isThinking) }

    val uiState = combine(coreFlow, chatFlow, extra) { core, chatState, extras ->
        UiState(
            accounts = core.accounts,
            transactions = core.transactions,
            smsDrafts = core.drafts,
            settings = core.settings,
            chatMessages = chatState.messages,
            aiThinking = chatState.thinking,
            keyStatus = extras.keyStatus,
            diagLines = extras.diagLines,
            diagRunning = extras.diagRunning,
            backupStatus = extras.backupStatus,
            updateStatus = extras.updateStatus,
            updateAvailable = extras.updateAvailable,
            updateProgress = extras.updateProgress,
            backupBusy = extras.backupBusy,
            restoreCandidate = extras.restoreCandidate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private data class ExtraState(
        val keyStatus: String,
        val backupStatus: String,
        val backupBusy: Boolean,
        val restoreCandidate: BackupCodec.Parsed?,
        val diagLines: List<NetDiag.Line>,
        val diagRunning: Boolean,
        val updateStatus: String,
        val updateAvailable: UpdateChecker.Release?,
        val updateProgress: Int
    )

    init {
        viewModelScope.launch { repository.ensureSetup() }
    }

    // ── المظهر ───────────────────────────────────────────────────────────────
    fun setThemeColor(value: ThemeColor) = viewModelScope.launch {
        settingsRepository.setThemeColor(value)
        updateWidget()
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(value)
        updateWidget()
    }

    // ── الخطة ────────────────────────────────────────────────────────────────
    fun updatePlan(salary: Long, day: Int, savings: Long, commitments: Long) = viewModelScope.launch {
        settingsRepository.updatePlan(salary, day, savings, commitments)
        updateWidget()
    }

    // ── الرسائل النصية ───────────────────────────────────────────────────────
    fun setSmsEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setSmsEnabled(value) }
    fun setSmsSenders(value: String) = viewModelScope.launch { settingsRepository.setSmsSenders(value) }

    // ── الذكاء الاصطناعي ─────────────────────────────────────────────────────
    fun setAiLocalEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setAiLocalEnabled(value) }
    fun setAiCloudEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setAiCloudEnabled(value) }
    fun setAiApiKey(value: String) = viewModelScope.launch {
        settingsRepository.setAiApiKey(value)
        keyStatus.value = ""
    }
    fun setAiModel(value: String) = viewModelScope.launch {
        settingsRepository.setAiModel(value)
        keyStatus.value = ""
    }
    fun setAiStyle(value: AiStyle) = viewModelScope.launch { settingsRepository.setAiStyle(value) }
    fun setAiMode(value: AiMode) = viewModelScope.launch { settingsRepository.setAiMode(value) }

    /** يجلب أسماء الموديلات المتاحة فعلًا على مفتاح المستخدم ويخزّنها. */
    fun refreshModels() = viewModelScope.launch {
        val key = uiState.value.settings.aiApiKey
        if (key.isBlank()) {
            keyStatus.value = "أضف مفتاح Gemini أولًا"
            return@launch
        }
        keyStatus.value = "أجلب الموديلات…"
        CloudAiClient.listModels(key).fold(
            onSuccess = { models ->
                settingsRepository.setAiAvailableModels(models)
                models.firstOrNull { it.contains("flash") }?.let { settingsRepository.setAiModel(it) }
                keyStatus.value = if (models.isEmpty()) "المفتاح يعمل لكن ما رجعت موديلات" else "تم: ${models.size} موديل متاح ✅"
            },
            onFailure = { keyStatus.value = it.message ?: "تعذّر جلب الموديلات" }
        )
    }
    fun setAiShareAmounts(value: Boolean) = viewModelScope.launch { settingsRepository.setAiShareAmounts(value) }

    fun testAiKey() = viewModelScope.launch {
        val key = uiState.value.settings.aiApiKey
        val model = uiState.value.settings.aiModel
        if (key.isBlank()) {
            keyStatus.value = "أضف مفتاح Gemini أولًا"
            return@launch
        }
        keyStatus.value = "جاري التحقق…"
        CloudAiClient.checkKey(key, model).fold(
            onSuccess = { reply -> keyStatus.value = "المفتاح يعمل ✅ — ${reply.take(40)}" },
            onFailure = { keyStatus.value = it.message ?: "تعذّر التحقق من المفتاح" }
        )
    }

    /** تشخيص شامل للشبكة والمفتاح، بقائمة قابلة للنسخ. */
    fun runDiagnostics() = viewModelScope.launch {
        if (diagRunning.value) return@launch
        diagRunning.value = true
        diagLines.value = listOf(NetDiag.Line(true, "جاري الفحص…"))
        diagLines.value = NetDiag.run(
            uiState.value.settings.aiApiKey,
            uiState.value.settings.aiModel
        )
        diagRunning.value = false
    }

    fun diagnosticsText(): String = diagLines.value.joinToString("\n") {
        (if (it.ok) "✅ " else "❌ ") + it.text
    }

    fun clearChat() {
        chat.value = emptyList()
    }

    /**
     * إرسال سؤال حسب الوضع المختار:
     * - LOCAL: يجاوب محليًا فورًا دائمًا.
     * - AUTO: سحابي لو المفتاح جاهز، وإلا محلي — ومع فشل السحابة يرجع للمحلي.
     * - CLOUD: سحابي فقط، مع رسالة خطأ واضحة لو فشل.
     * المحلي يُحسب أولًا ويُحفظ كخطة بديلة، فلا يبقى المستخدم بدون رد.
     */
    fun sendChat(question: String, snapshot: MoneySnapshot) = viewModelScope.launch {
        val text = question.trim()
        if (text.isEmpty()) return@launch
        lastSnapshot = snapshot

        val settings = uiState.value.settings
        chat.update { it + ChatMessage(role = "user", text = text, source = null) }
        thinking.value = true
        try {
            val localReply = try {
                localAnswer(text, snapshot)
            } catch (t: Throwable) {
                "تعذّر التحليل المحلي: ${t.javaClass.simpleName}"
            }

            val useCloud = when (settings.aiMode) {
                AiMode.LOCAL -> false
                AiMode.CLOUD -> true
                AiMode.AUTO -> settings.aiCloudReady
            }

            if (!useCloud) {
                val note = when (settings.aiMode) {
                    AiMode.CLOUD -> "الوضع السحابي مختار لكن المفتاح غير مضاف. رديت عليك محليًا:\n\n"
                    else -> ""
                }
                chat.update { it + ChatMessage(role = "model", text = note + localReply, source = "local") }
                return@launch
            }

            if (!settings.aiCloudReady) {
                chat.update {
                    it + ChatMessage(
                        role = "model",
                        text = "الوضع السحابي يحتاج مفتاح Gemini. أضفه من الإعدادات، أو بدّل الوضع إلى «محلي».\n\n$localReply",
                        source = "local",
                        error = "لا يوجد مفتاح"
                    )
                }
                return@launch
            }

            val history = chat.value
                .filter { it.role == "user" || it.source == "cloud" || it.source == "local" }
                .dropLast(1)
                .map { CloudAiClient.Message(it.role, it.text) }
            val context = cloudContext(snapshot, settings.aiShareAmounts)
            val style = when (settings.aiStyle) {
                AiStyle.SIMPLE -> "بسيط ومباشر"
                AiStyle.FRIENDLY -> "ودّي وطبيعي"
                AiStyle.ANALYST -> "تحليلي مع أرقام"
            }

            // لو ما فيه إنترنت فعلي، لا نجرّب السحابة أصلًا: نجاوب محليًا فورًا
            if (!hasInternet()) {
                chat.update {
                    it + ChatMessage(
                        role = "model",
                        text = localReply + "\n\n(جاوبتك محليًا لأن الاتصال بالإنترنت غير متاح الآن.)",
                        source = "local"
                    )
                }
                return@launch
            }

            val startedAt = System.currentTimeMillis()
            CloudAiClient.ask(
                apiKey = settings.aiApiKey,
                model = settings.aiModel,
                style = style,
                context = context,
                history = history + CloudAiClient.Message("user", text)
            ).fold(
                onSuccess = { answer ->
                    chat.update {
                        it + ChatMessage(
                            role = "model",
                            text = answer,
                            source = "cloud",
                            elapsedMs = System.currentTimeMillis() - startedAt,
                            model = settings.aiModel
                        )
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: "تعذّر الوصول للمساعد السحابي."
                    // الجواب المحلي صحيح ومكتمل — نعرض سبب تعذّر السحابة كملاحظة صغيرة فقط
                    chat.update {
                        it + ChatMessage(
                            role = "model",
                            text = localReply,
                            source = "local",
                            error = "تعذّر الوصول للسحابة: $message"
                        )
                    }
                }
            )
        } finally {
            thinking.value = false
        }
    }

    // ── الحسابات والعمليات ───────────────────────────────────────────────────
    fun addAccount(name: String, openingBalance: Long) = viewModelScope.launch {
        if (name.isNotBlank()) {
            repository.addAccount(name, openingBalance)
            updateWidget()
        }
    }

    fun setBalance(accountId: Long, balance: Long) = viewModelScope.launch {
        repository.setBalance(accountId, balance)
        updateWidget()
    }

    fun addTransaction(
        type: String,
        amount: Long,
        accountId: Long,
        toAccountId: Long?,
        category: String,
        merchant: String,
        note: String
    ) = viewModelScope.launch {
        if (amount > 0) {
            repository.addTransaction(type, amount, accountId, toAccountId, category, merchant, note)
            updateWidget()
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) = viewModelScope.launch {
        repository.deleteTransaction(transaction)
        updateWidget()
    }

    fun confirmDraft(draft: SmsDraftEntity, accountId: Long) = viewModelScope.launch {
        repository.confirmDraft(draft, accountId)
        updateWidget()
    }

    fun dismissDraft(draft: SmsDraftEntity) = viewModelScope.launch { repository.dismissDraft(draft) }

    // ── النسخ الاحتياطي والاستعادة ───────────────────────────────────────────
    fun exportBackup(summary: Boolean = false) = viewModelScope.launch {
        backupBusy.value = true
        backupStatus.value = "جاري تجهيز الملف…"
        runCatching {
            val file = if (summary) {
                BackupManager.writeSummary(getApplication(), repository, settingsRepository)
            } else {
                BackupManager.writeFullBackup(getApplication(), repository, settingsRepository)
            }
            BackupManager.shareFile(getApplication(), file)
            file
        }.fold(
            onSuccess = { file ->
                val kb = (file.length() / 1024).coerceAtLeast(1)
                backupStatus.value = "الملف جاهز ($kb ك.ب). اختر «Drive» من قائمة المشاركة لحفظه هناك."
            },
            onFailure = { backupStatus.value = "تعذّر تجهيز النسخة: ${it.message}" }
        )
        backupBusy.value = false
    }

    fun prepareRestore(uri: Uri) = viewModelScope.launch {
        backupBusy.value = true
        backupStatus.value = "جاري قراءة الملف…"
        BackupManager.readBackup(getApplication(), uri).fold(
            onSuccess = { parsed ->
                restoreCandidate.value = parsed
                backupStatus.value =
                    "الملف صالح: ${parsed.accounts.size} حساب، ${parsed.transactions.size} عملية، ${parsed.smsDrafts.size} مسودة. اختر طريقة الاستعادة."
            },
            onFailure = { backupStatus.value = "الملف غير صالح: ${it.message}" }
        )
        backupBusy.value = false
    }

    fun applyRestore(replaceExisting: Boolean) = viewModelScope.launch {
        val parsed = restoreCandidate.value ?: run {
            backupStatus.value = "اختر ملف نسخة احتياطية أولًا"
            return@launch
        }
        backupBusy.value = true
        runCatching {
            repository.restore(parsed, replaceExisting)
            parsed.settings?.let { restored ->
                // نستعيد التفضيلات لكن نُبقي مفتاح API الحالي
                val current = uiState.value.settings
                settingsRepository.updatePlan(
                    restored.salaryAmount, restored.salaryDay, restored.savingsTarget, restored.upcomingCommitments
                )
                settingsRepository.setThemeColor(restored.themeColor)
                settingsRepository.setThemeMode(restored.themeMode)
                settingsRepository.setAiMode(restored.aiMode)
                if (current.aiApiKey.isBlank()) settingsRepository.setAiModel(restored.aiModel)
            }
        }.fold(
            onSuccess = {
                backupStatus.value = if (replaceExisting) "تمت الاستعادة بالكامل ✅" else "تمت الاستعادة بدمج البيانات ✅"
                restoreCandidate.value = null
                updateWidget()
            },
            onFailure = { backupStatus.value = "تعذّرت الاستعادة: ${it.message}" }
        )
        backupBusy.value = false
    }

    fun cancelRestore() {
        restoreCandidate.value = null
        backupStatus.value = ""
    }

    fun clearBackupStatus() { backupStatus.value = "" }

    // ── التحديث الداخلي ──────────────────────────────────────────────────────
    fun checkForUpdate() = viewModelScope.launch {
        updateStatus.value = "أفحص التحديثات…"
        UpdateChecker.latest().fold(
            onSuccess = { release ->
                when {
                    release == null -> updateStatus.value = "لا يوجد ملف تحديث منشور بعد."
                    UpdateChecker.isNewer(release.versionName, BuildConfig.VERSION_NAME) -> {
                        updateAvailable.value = release
                        updateStatus.value = "يتوفر إصدار ${release.versionName} (الحالي ${BuildConfig.VERSION_NAME})."
                    }
                    else -> {
                        updateAvailable.value = null
                        updateStatus.value = "أنت على آخر إصدار (${BuildConfig.VERSION_NAME})."
                    }
                }
            },
            onFailure = { updateStatus.value = "تعذّر فحص التحديثات: ${it.message}" }
        )
    }

    fun downloadUpdate() = viewModelScope.launch {
        val release = updateAvailable.value ?: return@launch
        updateProgress.value = 0
        updateStatus.value = "جاري التنزيل…"
        UpdateChecker.download(getApplication(), release) { progress ->
            updateProgress.value = progress
        }.fold(
            onSuccess = { file ->
                updateStatus.value = "تم التنزيل. تفتح شاشة التثبيت الآن — اضغط «تحديث»."
                updateProgress.value = -1
                runCatching { UpdateChecker.install(getApplication(), file) }
                    .onFailure { updateStatus.value = "تعذّر فتح شاشة التثبيت: ${it.message}" }
            },
            onFailure = {
                updateStatus.value = "فشل التنزيل: ${it.message}"
                updateProgress.value = -1
            }
        )
    }

    fun updateWidget() {
        viewModelScope.launch { MasarifyWidget().updateAll(getApplication()) }
    }
}
