package com.masarify.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    @Test fun `parses Arabic salary deposit`() {
        val result = SmsParser.parse("تم إيداع راتب 10,500 ر.س في حسابك", "BANK")!!
        assertEquals("INCOME", result.type)
        assertEquals(1_050_000L, result.amount)
        assertEquals("راتب", result.category)
    }

    @Test fun `parses purchase`() {
        val result = SmsParser.parse("Purchase SAR 85.00 at TAMIMI", "BANK")!!
        assertEquals("EXPENSE", result.type)
        assertEquals(8_500L, result.amount)
    }

    @Test fun `supports Arabic digits`() {
        val result = SmsParser.parse("شراء ٨٥٫٥٠ ر.س لدى متجر", "BANK")!!
        assertEquals(8_550L, result.amount)
    }

    @Test fun `ignores OTP`() {
        assertNull(SmsParser.parse("رمز التحقق 123456 لا تشاركه", "BANK"))
    }
}
