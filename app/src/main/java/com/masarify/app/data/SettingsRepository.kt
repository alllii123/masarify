package com.masarify.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeColor { GREEN, BLUE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeColor: ThemeColor = ThemeColor.GREEN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val salaryAmount: Long = 0,
    val salaryDay: Int = 27,
    val savingsTarget: Long = 0,
    val upcomingCommitments: Long = 0,
    val smsEnabled: Boolean = false,
    val smsSenders: String = ""
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val color = stringPreferencesKey("theme_color")
        val mode = stringPreferencesKey("theme_mode")
        val salary = longPreferencesKey("salary_amount")
        val salaryDay = intPreferencesKey("salary_day")
        val savings = longPreferencesKey("savings_target")
        val commitments = longPreferencesKey("upcoming_commitments")
        val smsEnabled = booleanPreferencesKey("sms_enabled")
        val smsSenders = stringPreferencesKey("sms_senders")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeColor = runCatching { ThemeColor.valueOf(p[Keys.color] ?: "GREEN") }.getOrDefault(ThemeColor.GREEN),
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.mode] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            salaryAmount = p[Keys.salary] ?: 0,
            salaryDay = p[Keys.salaryDay] ?: 27,
            savingsTarget = p[Keys.savings] ?: 0,
            upcomingCommitments = p[Keys.commitments] ?: 0,
            smsEnabled = p[Keys.smsEnabled] ?: false,
            smsSenders = p[Keys.smsSenders] ?: ""
        )
    }

    suspend fun setThemeColor(value: ThemeColor) = context.dataStore.edit { it[Keys.color] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.mode] = value.name }
    suspend fun setSmsEnabled(value: Boolean) = context.dataStore.edit { it[Keys.smsEnabled] = value }
    suspend fun setSmsSenders(value: String) = context.dataStore.edit { it[Keys.smsSenders] = value.trim() }

    suspend fun updatePlan(salary: Long, day: Int, savings: Long, commitments: Long) {
        context.dataStore.edit {
            it[Keys.salary] = salary.coerceAtLeast(0)
            it[Keys.salaryDay] = day.coerceIn(1, 31)
            it[Keys.savings] = savings.coerceAtLeast(0)
            it[Keys.commitments] = commitments.coerceAtLeast(0)
        }
    }
}
