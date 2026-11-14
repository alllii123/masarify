package com.masarify.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يضمن أن التنفيذ مرتبط بأمر صريح من المستخدم، لا بتخمين المساعد.
 *
 * أهم اختبار هنا: [questions are not commands] — أي سؤال لا يجب أن يُنشئ عملية،
 * لأن هذا بالضبط ما رفضه المستخدم: «مو يسجل على كيفه».
 */
class CommandParserTest {

    @Test
    fun `add expense with amount and category`() {
        val command = CommandParser.parse("أضف مصروف ٥٠ بقالة")
        assertTrue(command is FinCommand.Add)
        command as FinCommand.Add
        assertEquals("EXPENSE", command.type)
        assertEquals(5000L, command.amount)
        assertEquals("بقالة", command.category)
    }

    @Test
    fun `spent phrasing is an add expense`() {
        val command = CommandParser.parse("صرفت 120 ريال على مطعم")
        assertTrue(command is FinCommand.Add)
        command as FinCommand.Add
        assertEquals(12000L, command.amount)
        assertEquals("مطاعم", command.category)
    }

    @Test
    fun `thousands suffix works`() {
        val command = CommandParser.parse("دفعت ألفين بنزين") // بلا رقم صريح
        assertNull(command) // لا رقم ⇒ لا تنفيذ، نطلب تحديد المبلغ

        val withNumber = CommandParser.parse("صرفت 2 ألف بنزين")
        assertTrue(withNumber is FinCommand.Add)
        assertEquals(200000L, (withNumber as FinCommand.Add).amount)
    }

    @Test
    fun `income is detected`() {
        val command = CommandParser.parse("أضف دخل راتب 9000")
        assertTrue(command is FinCommand.Add)
        command as FinCommand.Add
        assertEquals("INCOME", command.type)
        assertEquals(900000L, command.amount)
    }

    @Test
    fun `edit command targets new amount`() {
        val command = CommandParser.parse("عدّل آخر عملية إلى 70")
        assertTrue(command is FinCommand.Edit)
        command as FinCommand.Edit
        assertEquals(7000L, command.newAmount)
    }

    @Test
    fun `delete command parses`() {
        val command = CommandParser.parse("احذف آخر عملية")
        assertTrue(command is FinCommand.Delete)
    }

    @Test
    fun `questions are not commands`() {
        val questions = listOf(
            "كم صرفت هذا الشهر؟",
            "وين أصرف أكثر؟",
            "ليش رصيدي نازل؟",
            "كم باقي لي أقدر أصرف؟",
            "توقع مصروفي نهاية الشهر"
        )
        questions.forEach { question ->
            assertNull("يجب ألا تُنفّذ: $question", CommandParser.parse(question))
        }
    }

    @Test
    fun `statement without verb is not executed`() {
        // جملة وصفية بلا فعل أمر: لا يجب أن تتحول لعملية
        assertNull(CommandParser.parse("المصروف اليومي تقريبًا 40 ريال"))
    }

    @Test
    fun `merchant extracted`() {
        val command = CommandParser.parse("أضف مصروف 80 ستاربكس")
        assertTrue(command is FinCommand.Add)
        command as FinCommand.Add
        assertTrue(command.merchant.contains("ستاربكس"))
        assertEquals("مطاعم", command.category)
    }
}
