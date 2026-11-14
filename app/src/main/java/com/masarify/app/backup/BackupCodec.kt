package com.masarify.app.backup

import com.masarify.app.data.AccountEntity
import com.masarify.app.data.SmsDraftEntity
import com.masarify.app.data.TransactionEntity
import com.masarify.app.data.UserSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * صيغة النسخة الاحتياطية: JSON مقروء وقابل للمراجعة.
 * لا تحتوي أي سر: لا مفاتيح API، ولا رصيد محسوب مشتق، فقط البيانات الأصلية.
 */
typealias RestoreParsed = BackupCodec.Parsed

data class BackupPayload(
    val createdAt: Long,
    val schemaVersion: Int = 1,
    val appVersion: String = "0.3.0",
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val smsDrafts: List<SmsDraftEntity>,
    val settings: UserSettings
)

object BackupCodec {

    fun encode(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("format", "masarify-backup")
        root.put("schemaVersion", payload.schemaVersion)
        root.put("appVersion", payload.appVersion)
        root.put("currency", "QAR")
        root.put("createdAt", payload.createdAt)

        root.put("accounts", JSONArray().apply {
            payload.accounts.forEach { account ->
                put(JSONObject().apply {
                    put("id", account.id)
                    put("name", account.name)
                    put("currentBalance", account.currentBalance)
                    put("createdAt", account.createdAt)
                })
            }
        })

        root.put("transactions", JSONArray().apply {
            payload.transactions.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("type", t.type)
                    put("amount", t.amount)
                    put("accountId", t.accountId)
                    put("toAccountId", t.toAccountId ?: JSONObject.NULL)
                    put("category", t.category)
                    put("merchant", t.merchant)
                    put("note", t.note)
                    put("timestamp", t.timestamp)
                    put("source", t.source)
                    put("messageHash", t.messageHash ?: JSONObject.NULL)
                })
            }
        })

        root.put("smsDrafts", JSONArray().apply {
            payload.smsDrafts.forEach { d ->
                put(JSONObject().apply {
                    put("type", d.type)
                    put("amount", d.amount)
                    put("merchant", d.merchant)
                    put("sender", d.sender)
                    put("accountHint", d.accountHint)
                    put("category", d.category)
                    put("timestamp", d.timestamp)
                    put("messageHash", d.messageHash)
                })
            }
        })

        // إعدادات غير حساسة فقط. مفتاح API لا يُصدَّر أبدًا.
        root.put("settings", JSONObject().apply {
            put("themeColor", payload.settings.themeColor.name)
            put("themeMode", payload.settings.themeMode.name)
            put("salaryAmount", payload.settings.salaryAmount)
            put("salaryDay", payload.settings.salaryDay)
            put("savingsTarget", payload.settings.savingsTarget)
            put("upcomingCommitments", payload.settings.upcomingCommitments)
            put("smsEnabled", payload.settings.smsEnabled)
            put("smsSenders", payload.settings.smsSenders)
            put("aiLocalEnabled", payload.settings.aiLocalEnabled)
            put("aiCloudEnabled", false) // لا نُصدّر التفعيل السحابي
            put("aiModel", payload.settings.aiModel)
            put("aiStyle", payload.settings.aiStyle.name)
            put("aiMode", payload.settings.aiMode.name)
            put("aiShareAmounts", payload.settings.aiShareAmounts)
        })

        return root.toString(2)
    }

    data class Parsed(
        val accounts: List<AccountEntity>,
        val transactions: List<TransactionEntity>,
        val smsDrafts: List<SmsDraftEntity>,
        val settings: UserSettings?,
        val createdAt: Long
    )

    /** يقرأ نسخة احتياطية ويتحقق من شكلها قبل أي كتابة على قاعدة البيانات. */
    fun decode(raw: String): Result<Parsed> = runCatching {
        val root = JSONObject(raw)
        require(root.optString("format") == "masarify-backup") { "الملف ليس نسخة احتياطية من محفظتي" }
        require(root.optInt("schemaVersion", 1) <= 1) { "النسخة الاحتياطية أحدث من التطبيق. حدّث التطبيق أولًا." }

        val accounts = root.optJSONArray("accounts").orEmptyJson().mapIndexed { index, o ->
            AccountEntity(
                id = o.optLong("id", (index + 1).toLong()),
                name = o.optString("name").ifBlank { "حساب ${index + 1}" },
                currentBalance = o.optLong("currentBalance"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
        val transactions = root.optJSONArray("transactions").orEmptyJson().map { o ->
            TransactionEntity(
                type = o.optString("type", "EXPENSE"),
                amount = o.optLong("amount"),
                accountId = o.optLong("accountId"),
                toAccountId = if (o.isNull("toAccountId")) null else o.optLong("toAccountId"),
                category = o.optString("category", "أخرى"),
                merchant = o.optString("merchant"),
                note = o.optString("note"),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                source = o.optString("source", "MANUAL"),
                messageHash = if (o.isNull("messageHash")) null else o.optString("messageHash")
            )
        }
        val drafts = root.optJSONArray("smsDrafts").orEmptyJson().map { o ->
            SmsDraftEntity(
                type = o.optString("type", "EXPENSE"),
                amount = o.optLong("amount"),
                merchant = o.optString("merchant"),
                sender = o.optString("sender"),
                accountHint = o.optString("accountHint"),
                category = o.optString("category"),
                timestamp = o.optLong("timestamp"),
                messageHash = o.optString("messageHash")
            )
        }
        val settings = root.optJSONObject("settings")?.let { o ->
            UserSettings(
                themeColor = runCatching { com.masarify.app.data.ThemeColor.valueOf(o.optString("themeColor", "GREEN")) }.getOrDefault(com.masarify.app.data.ThemeColor.GREEN),
                themeMode = runCatching { com.masarify.app.data.ThemeMode.valueOf(o.optString("themeMode", "SYSTEM")) }.getOrDefault(com.masarify.app.data.ThemeMode.SYSTEM),
                salaryAmount = o.optLong("salaryAmount"),
                salaryDay = o.optInt("salaryDay", 27),
                savingsTarget = o.optLong("savingsTarget"),
                upcomingCommitments = o.optLong("upcomingCommitments"),
                smsEnabled = o.optBoolean("smsEnabled"),
                smsSenders = o.optString("smsSenders"),
                aiLocalEnabled = o.optBoolean("aiLocalEnabled", true),
                aiModel = o.optString("aiModel", "gemini-flash-latest"),
                aiStyle = runCatching { com.masarify.app.data.AiStyle.valueOf(o.optString("aiStyle", "SIMPLE")) }.getOrDefault(com.masarify.app.data.AiStyle.SIMPLE),
                aiMode = runCatching { com.masarify.app.data.AiMode.valueOf(o.optString("aiMode", "AUTO")) }.getOrDefault(com.masarify.app.data.AiMode.AUTO),
                aiShareAmounts = o.optBoolean("aiShareAmounts")
            )
        }
        Parsed(accounts, transactions, drafts, settings, root.optLong("createdAt", System.currentTimeMillis()))
    }
}

private fun JSONArray?.orEmptyJson(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}
