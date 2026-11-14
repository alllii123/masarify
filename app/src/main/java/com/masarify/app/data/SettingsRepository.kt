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
enum class AiStyle { SIMPLE, FRIENDLY, ANALYST }

data class UserSettings(
    val themeColor: ThemeColor = ThemeColor.GREEN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val salaryAmount: Long = 0,
    val salaryDay: Int = 27,
    val savingsTarget: Long = 0,
    val upcomingCommitments: Long = 0,
    val smsEnabled: Boolean = false,
    val smsSenders: String = "",
    /** التحليل المحلي يعمل دائمًا بدون إنترنت. */
    val aiLocalEnabled: Boolean = true,
    /** المساعد السحابي اختياري، ولا يعمل إلا إذا أضاف المستخدم مفتاحه الخاص. */
    val aiCloudEnabled: Boolean = false,
    val aiApiKey: String = "",
    val aiModel: String = "gemini-2.0-flash",
    val aiStyle: AiStyle = AiStyle.SIMPLE,
    /** لا يُرسل أي رقم مالي للسحابة إلا بعد موافقة صريحة داخل المحادثة. */
    val aiShareAmounts: Boolean = false
) {
    val aiCloudReady: Boolean get() = aiCloudEnabled && aiApiKey.isNotBlank()
}

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
        val aiLocal = booleanPreferencesKey("ai_local_enabled")
        val aiCloud = booleanPreferencesKey("ai_cloud_enabled")
        val aiKey = stringPreferencesKey("ai_api_key")
        val aiModel = stringPreferencesKey("ai_model")
        val aiStyle = stringPreferencesKey("ai_style")
        val aiShare = booleanPreferencesKey("ai_share_amounts")
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
            smsSenders = p[Keys.smsSenders] ?: "",
            aiLocalEnabled = p[Keys.aiLocal] ?: true,
            aiCloudEnabled = p[Keys.aiCloud] ?: false,
            aiApiKey = p[Keys.aiKey] ?: "",
            aiModel = p[Keys.aiModel] ?: "gemini-2.0-flash",
            aiStyle = runCatching { AiStyle.valueOf(p[Keys.aiStyle] ?: "SIMPLE") }.getOrDefault(AiStyle.SIMPLE),
            aiShareAmounts = p[Keys.aiShare] ?: false
        )
    }

    suspend fun setThemeColor(value: ThemeColor) = context.dataStore.edit { it[Keys.color] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.mode] = value.name }
    suspend fun setSmsEnabled(value: Boolean) = context.dataStore.edit { it[Keys.smsEnabled] = value }
    suspend fun setSmsSenders(value: String) = context.dataStore.edit { it[Keys.smsSenders] = value.trim() }

    suspend fun setAiLocalEnabled(value: Boolean) = context.dataStore.edit { it[Keys.aiLocal] = value }
    suspend fun setAiCloudEnabled(value: Boolean) = context.dataStore.edit { it[Keys.aiCloud] = value }
    suspend fun setAiApiKey(value: String) = context.dataStore.edit { it[Keys.aiKey] = value.trim() }
    suspend fun setAiModel(value: String) = context.dataStore.edit { it[Keys.aiModel] = value.trim().ifBlank { "gemini-2.0-flash" } }
    suspend fun setAiStyle(value: AiStyle) = context.dataStore.edit { it[Keys.aiStyle] = value.name }
    suspend fun setAiShareAmounts(value: Boolean) = context.dataStore.edit { it[Keys.aiShare] = value }

    suspend fun updatePlan(salary: Long, day: Int, savings: Long, commitments: Long) {
        context.dataStore.edit {
            it[Keys.salary] = salary.coerceAtLeast(0)
            it[Keys.salaryDay] = day.coerceIn(1, 31)
            it[Keys.savings] = savings.coerceAtLeast(0)
            it[Keys.commitments] = commitments.coerceAtLeast(0)
        }
    }
}
