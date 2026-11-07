package com.masarify.app

import android.app.Application
import com.masarify.app.data.FinanceRepository
import com.masarify.app.data.MasarifyDatabase
import com.masarify.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MasarifyApp : Application() {
    val database by lazy { MasarifyDatabase.get(this) }
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { FinanceRepository(database) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.ensureSetup() }
    }
}
