package com.masarify.app.sms

import com.masarify.app.util.normalizeDigits
import com.masarify.app.util.parseMoney
import java.security.MessageDigest

data class ParsedSms(
    val type: String,
    val amount: Long,
    val merchant: String,
    val category: String,
    val accountHint: String
)

object SmsParser {
    private val salaryWords = listOf("راتب", "رواتب", "salary", "payroll")
    private val incomeWords = listOf("إيداع", "ايداع", "deposit", "credited", "حوالة واردة", "تحويل وارد", "credit")
    private val expenseWords = listOf("شراء", "purchase", "سحب", "withdraw", "pos", "دفع", "payment", "خصم")
    private val amountPatterns = listOf(
        Regex("(?i)(?:SAR|SR)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"),
        Regex("(?i)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:SAR|SR)"),
        Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:ر\\.س|ريال|رس)")
    )

    fun parse(body: String, sender: String = ""): ParsedSms? {
        val normalized = normalizeDigits(body)
        val lower = normalized.lowercase()
        val isSalary = salaryWords.any { lower.contains(it) }
        val isIncome = isSalary || incomeWords.any { lower.contains(it) }
        val isExpense = expenseWords.any { lower.contains(it) }
        if (!isIncome && !isExpense) return null

        val amountText = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)
        } ?: return null
        val amount = parseMoney(amountText)?.takeIf { it > 0 } ?: return null
        val merchant = Regex("(?:at|من|لدى)\\s+([A-Za-z0-9\\u0600-\\u06FF .&_-]{2,40})")
            .find(normalized)?.groupValues?.getOrNull(1)?.trim()?.take(40) ?: sender.ifBlank { "عملية بنكية" }
        val accountHint = Regex("(?<!\\d)(\\d{4})(?!\\d)").findAll(normalized).lastOrNull()?.value ?: ""
        return ParsedSms(
            type = if (isIncome) "INCOME" else "EXPENSE",
            amount = amount,
            merchant = merchant,
            category = when {
                isSalary -> "راتب"
                isIncome -> "دخل إضافي"
                else -> "مصروف"
            },
            accountHint = accountHint
        )
    }

    fun hash(sender: String, timestamp: Long, body: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$sender|$timestamp|$body".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
