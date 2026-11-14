package com.masarify.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInsightsTest {

    private fun snapshot(
        balance: Long = 5_000_00,
        commitments: Long = 1_000_00,
        savings: Long = 500_00,
        daysLeft: Int = 10,
        cycleDays: Int = 30,
        elapsed: Int = 20,
        expense: Long = 2_000_00,
        income: Long = 6_000_00,
        records: List<SpendRecord> = emptyList()
    ) = MoneySnapshot(
        totalBalance = balance,
        commitments = commitments,
        savingsTarget = savings,
        salaryDay = 27,
        daysLeft = daysLeft,
        cycleDays = cycleDays,
        cycleElapsedDays = elapsed,
        cycleExpense = expense,
        cycleIncome = income,
        records = records
    )

    @Test
    fun `safe to spend subtracts commitments and savings`() {
        val s = snapshot()
        assertEquals(3_500_00L, s.safe)
        assertEquals(350_00L, s.dailyAllowance)
    }

    @Test
    fun `empty data returns a starter note`() {
        val notes = localInsights(snapshot(balance = 0, commitments = 0, savings = 0, expense = 0, income = 0))
        assertEquals(1, notes.size)
        assertTrue(notes.first().title.contains("نبدأ"))
    }

    @Test
    fun `largest category note mentions saving ten percent`() {
        val records = listOf(
            SpendRecord("مطاعم", "مطعم", 900_00, true),
            SpendRecord("بقالة", "سوق", 300_00, true),
            SpendRecord("مواصلات", "بنزين", 200_00, true)
        )
        val notes = localInsights(snapshot(expense = 1_400_00, records = records))
        assertTrue(notes.any { it.title.contains("أكبر بند") })
    }

    @Test
    fun `local answer handles the remaining budget question`() {
        val answer = localAnswer("كم باقي لي أقدر أصرف؟", snapshot())
        assertTrue(answer.contains("3,500"))
        assertTrue(answer.contains("350"))
    }

    @Test
    fun `cloud context hides amounts unless the user opts in`() {
        val hidden = cloudContext(snapshot(), shareAmounts = false)
        assertTrue(hidden.contains("amountsWithheld=true"))
        assertTrue(!hidden.contains("safe="))

        val shared = cloudContext(snapshot(), shareAmounts = true)
        assertTrue(shared.contains("safe=3500.0"))
    }
}
