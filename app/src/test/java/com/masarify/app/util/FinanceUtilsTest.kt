package com.masarify.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FinanceUtilsTest {
    @Test fun `money is stored as minor units`() {
        assertEquals(8500L, parseMoney("85.00"))
        assertEquals(8550L, parseMoney("٨٥٫٥٠"))
        assertEquals(1050000L, parseMoney("10,500"))
        assertNull(parseMoney("abc"))
    }

    @Test fun `salary day moves to next month`() {
        val today = LocalDate.of(2026, 11, 7)
        assertEquals(20, daysUntilSalary(27, today))
        assertEquals(24, daysUntilSalary(1, today))
    }
}
