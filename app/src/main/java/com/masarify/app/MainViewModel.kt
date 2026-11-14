package com.masarify.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masarify.app.ai.ChatMessage
import com.masarify.app.ai.CloudAiClient
import com.masarify.app.ai.MoneySnapshot
import com.masarify.app.ai.cloudContext
import com.masarify.app.ai.localAnswer
import com.masarify.app.data.AccountEntity
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
    val keyStatus: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MasarifyApp
    private val repository = app.repository
    private val settingsRepository = app.settings

    private val chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val thinking = MutableStateFlow(false)
    private val keyStatus = MutableStateFlow("")

    val uiState = combine(
        repository.accounts,
        repository.transactions,
        repository.smsDrafts,
        settingsRepository.settings,
        chat,
        thinking,
        keyStatus
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        UiState(
            accounts = values[0] as List<AccountEntity>,
            transactions = values[1] as List<TransactionEntity>,
            smsDrafts = values[2] as List<SmsDraftEntity>,
            settings = values[3] as UserSettings,
            chatMessages = values[4] as List<ChatMessage>,
            aiThinking = values[5] as Boolean,
            keyStatus = values[6] as String
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

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
    fun setAiShareAmounts(value: Boolean) = viewModelScope.launch { settingsRepository.setAiShareAmounts(value) }

    fun testAiKey() = viewModelScope.launch {
        val key = uiState.value.settings.aiApiKey
        val model = uiState.value.settings.aiModel
        if (key.isBlank()) {
            keyStatus.value = "أضف مفتاح Gemini أولًا"
            return@launch
        }
        keyStatus.value = "جاري التحقق…"
        keyStatus.value = CloudAiClient.checkKey(key, model).fold(
            onSuccess = { "المفتاح يعمل ✅" },
            onFailure = { it.message ?: "تعذّر التحقق من المفتاح" }
        )
    }

    fun clearChat() {
        chat.value = emptyList()
    }

    /**
     * إرسال سؤال: التحليل المحلي يجاوب فورًا.
     * لو الوضع السحابي مفعّل بالمفتاح، نجيب رد أذكى من Gemini مع سياق مختصر،
     * ولو فشل الاتصال نرجع للجواب المحلي مع ملاحظة واضحة.
     */
    fun sendChat(question: String, snapshot: MoneySnapshot) = viewModelScope.launch {
        val text = question.trim()
        if (text.isEmpty()) return@launch

        val settings = uiState.value.settings
        val userMessage = ChatMessage(role = "user", text = text, source = null)
        chat.update { it + userMessage }
        thinking.value = true

        val localReply = if (settings.aiLocalEnabled) localAnswer(text, snapshot) else null

        if (settings.aiCloudReady) {
            val history = chat.value
                .filter { it.source != "error" }
                .dropLast(1)
                .map { CloudAiClient.Message(it.role, it.text) }
            val context = cloudContext(snapshot, settings.aiShareAmounts)
            val style = when (settings.aiStyle) {
                AiStyle.SIMPLE -> "بسيط ومباشر"
                AiStyle.FRIENDLY -> "ودّي وطبيعي"
                AiStyle.ANALYST -> "تحليلي مع أرقام"
            }
            val result = CloudAiClient.ask(
                apiKey = settings.aiApiKey,
                model = settings.aiModel,
                style = style,
                context = context,
                history = history + CloudAiClient.Message("user", text)
            )
            result.fold(
                onSuccess = { answer ->
                    chat.update { it + ChatMessage(role = "model", text = answer, source = "cloud") }
                },
                onFailure = { error ->
                    chat.update {
                        it + ChatMessage(
                            role = "model",
                            text = localReply ?: "تعذّر الوصول للمساعد السحابي.",
                            source = if (localReply != null) "local" else "error",
                            error = error.message
                        )
                    }
                }
            )
        } else {
            chat.update {
                it + ChatMessage(
                    role = "model",
                    text = localReply ?: "التحليل المحلي متوقف. فعّله من الإعدادات أو أضف مفتاح المساعد السحابي.",
                    source = "local"
                )
            }
        }
        thinking.value = false
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

    fun updateWidget() {
        viewModelScope.launch { MasarifyWidget().updateAll(getApplication()) }
    }
}
