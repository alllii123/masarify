package com.masarify.app.backup

import com.masarify.app.data.AccountEntity
import com.masarify.app.data.TransactionEntity
import com.masarify.app.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun payload() = BackupPayload(
        createdAt = 1_700_000_000_000,
        accounts = listOf(AccountEntity(id = 1, name = "الحساب الرئيسي", currentBalance = 500_000)),
        transactions = listOf(
            TransactionEntity(
                id = 7, type = "EXPENSE", amount = 8_500, accountId = 1,
                category = "بقالة", merchant = "سوق", timestamp = 1_700_000_000_000
            )
        ),
        smsDrafts = emptyList(),
        settings = UserSettings(salaryAmount = 1_000_000, salaryDay = 27, savingsTarget = 100_000)
    )

    @Test
    fun `round trip keeps every financial field`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(payload())).getOrThrow()
        assertEquals(1, decoded.accounts.size)
        assertEquals(500_000L, decoded.accounts.first().currentBalance)
        assertEquals(1, decoded.transactions.size)
        assertEquals(8_500L, decoded.transactions.first().amount)
        assertEquals("بقالة", decoded.transactions.first().category)
        assertEquals(1_000_000L, decoded.settings?.salaryAmount)
    }

    @Test
    fun `api key is never exported`() {
        val encoded = BackupCodec.encode(payload())
        assertFalse(encoded.contains("AIza"))
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("aiApiKey"))
    }

    @Test
    fun `rejects unrelated json files`() {
        val result = BackupCodec.decode("""{"hello":"world"}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects a newer schema`() {
        val encoded = BackupCodec.encode(payload()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 9")
        assertTrue(BackupCodec.decode(encoded).isFailure)
    }
}
