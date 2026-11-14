package com.masarify.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يضمن أن التنفيذ مرتبط بفهم كلام المستخدم — لا بأمر صيغته حرفية.
 *
 * ثلاثة مبادئ مختبرة هنا:
 * 1) اللهجة العامية مفهومة: «صرفت»، «شريت»، «دفعت»، «خذ».
 * 2) الأسئلة والنوايا المستقبلية لا تُنفّذ إطلاقًا.
 * 3) الصياغة غير الصريحة تُقترح للتأكيد بضغطة، لا تُسجَّل تلقائيًا.
 */
class CommandParserTest {

    private fun add(raw: String): FinCommand.Add {
        val parsed = CommandParser.parse(raw)
        assertNotNull("لم يُفهم: $raw", parsed)
        return parsed!!.command as FinCommand.Add
    }

    @Test
    fun `add expense with amount and category`() {
        val command = add("أضف مصروف ٥٠ بقالة")
        assertEquals("EXPENSE", command.type)
        assertEquals(5000L, command.amount)
        assertEquals("بقالة", command.category)
    }

    @Test
    fun `colloquial past tense without hamza is understood`() {
        // «صرفت» بلا همزة، «ستاربكس» بلا تصنيف صريح
        val command = add("صرفت 120 ريال ستاربكس")
        assertEquals(12000L, command.amount)
        assertEquals("مطاعم", command.category)
    }

    @Test
    fun `bought phrasing is understood`() {
        val command = add("شريت ثوب ب 250")
        assertEquals(25000L, command.amount)
        assertEquals("تسوق", command.category)
    }

    @Test
    fun `take phrasing is understood`() {
        val command = add("خذ 40 بنزين")
        assertEquals(4000L, command.amount)
        assertEquals("مواصلات", command.category)
    }

    @Test
    fun `dialect with ta marbuta and alef variants`() {
        // «عشاء» تُكتب «عشا»، «قهوة» تُكتب «قهوه»
        val command = add("دفعت 15 قهوه")
        assertEquals(1500L, command.amount)
        assertEquals("مطاعم", command.category)
    }

    @Test
    fun `thousands suffix works`() {
        assertNull(CommandParser.parse("دفعت بنزين")) // بلا مبلغ ⇒ لا تنفيذ
        val command = add("صرفت 2 ألف بنزين")
        assertEquals(200000L, command.amount)
    }

    @Test
    fun `income is detected`() {
        val command = add("أضف دخل راتب 9000")
        assertEquals("INCOME", command.type)
        assertEquals(900000L, command.amount)
    }

    @Test
    fun `edit command targets new amount`() {
        val parsed = CommandParser.parse("عدّل آخر عملية إلى 70")
        assertNotNull(parsed)
        val command = parsed!!.command as FinCommand.Edit
        assertEquals(7000L, command.newAmount)
    }

    @Test
    fun `delete command parses`() {
        val parsed = CommandParser.parse("احذف آخر عملية")
        assertNotNull(parsed)
        assertTrue(parsed!!.command is FinCommand.Delete)
    }

    @Test
    fun `questions are never executed`() {
        val questions = listOf(
            "كم صرفت هذا الشهر؟",
            "وين أصرف أكثر؟",
            "ليش رصيدي نازل؟",
            "كم باقي لي أقدر أصرف؟",
            "توقع مصروفي نهاية الشهر",
            "جم عندي في الحساب؟",
            "شكثر صرفت على المطاعم؟"
        )
        questions.forEach { question ->
            assertNull("يجب ألا تُنفّذ: $question", CommandParser.parse(question))
        }
    }

    @Test
    fun `future intentions are never executed`() {
        val intentions = listOf(
            "بصرف 200 بكرة على ملابس",
            "ناوي أشتري جوال ب 3000",
            "ودي أصرف 100 على العشاء",
            "محتاج أدفع 500 إيجار"
        )
        intentions.forEach { text ->
            assertNull("يجب ألا تُنفّذ: $text", CommandParser.parse(text))
        }
    }

    @Test
    fun `statement without verb asks confirmation instead of executing`() {
        // مبلغ + تصنيف واضح بلا فعل ⇒ مقترح للتأكيد بضغطة، ولا يُسجَّل تلقائيًا
        val parsed = CommandParser.parse("المصروف اليومي 40 مطاعم")
        assertNotNull(parsed)
        assertFalse("الصياغة غير الصريحة لا تُنفّذ تلقائيًا", parsed!!.explicit)
    }

    @Test
    fun `plain statement without place is ignored`() {
        assertNull(CommandParser.parse("المصروف اليومي تقريبًا 40 ريال"))
    }

    @Test
    fun `merchant extracted`() {
        val command = add("أضف مصروف 80 ستاربكس")
        assertTrue(command.merchant.contains("ستاربكس"))
        assertEquals("مطاعم", command.category)
    }

    @Test
    fun `normalization unifies hamza forms`() {
        assertEquals(ArText.normalize("أضف"), ArText.normalize("اضف"))
        assertEquals(ArText.normalize("قهوة"), ArText.normalize("قهوه"))
        assertEquals(ArText.normalize("إلى"), ArText.normalize("الى"))
    }
}
