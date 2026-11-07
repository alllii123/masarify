package com.masarify.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.masarify.app.data.AccountEntity
import com.masarify.app.data.SmsDraftEntity
import com.masarify.app.data.ThemeColor
import com.masarify.app.data.ThemeMode
import com.masarify.app.data.TransactionEntity
import com.masarify.app.data.UserSettings
import com.masarify.app.widget.MasarifyWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val smsDrafts: List<SmsDraftEntity> = emptyList(),
    val settings: UserSettings = UserSettings()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MasarifyApp
    private val repository = app.repository
    private val settingsRepository = app.settings

    val uiState = combine(
        repository.accounts,
        repository.transactions,
        repository.smsDrafts,
        settingsRepository.settings
    ) { accounts, transactions, drafts, settings ->
        UiState(accounts, transactions, drafts, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch { repository.ensureSetup() }
    }

    fun setThemeColor(value: ThemeColor) = viewModelScope.launch {
        settingsRepository.setThemeColor(value)
        updateWidget()
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(value)
        updateWidget()
    }

    fun updatePlan(salary: Long, day: Int, savings: Long, commitments: Long) = viewModelScope.launch {
        settingsRepository.updatePlan(salary, day, savings, commitments)
        updateWidget()
    }

    fun setSmsEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setSmsEnabled(value) }
    fun setSmsSenders(value: String) = viewModelScope.launch { settingsRepository.setSmsSenders(value) }

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
