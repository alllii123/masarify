package com.masarify.app.ai

import com.masarify.app.util.normalizeDigits

/**
 * طبقة تنفيذ أوامر المساعد.
 *
 * ── القاعدة الحاكمة ─────────────────────────────────────────────────────────
 * لا يُكتب شيء في قاعدة البيانات إلا إذا **أمر المستخدم صراحةً** في رسالته:
 * «أضف / سجّل / صرفت / دفعت» ← إضافة، «عدّل / غيّر» ← تعديل، «احذف / امسح» ← حذف.
 *
 * ردّ المساعد السحابي **لا ينفّذ أي شيء** مهما قال. هذا مقصود: المستخدم رفض
 * أن «يسجل على كيفه»، فجعلنا الكتابة مرتبطة بصيغة أمر يفهمها المحلل المحلي
 * الذي يعمل بدون إنترنت وبدون مفتاح — أي أنه يعمل دائمًا.
 */
sealed interface FinCommand {

    data class Add(
        val type: String,          // EXPENSE أو INCOME
        val amount: Long,          // بالهللة (×100)
        val category: String,
        val merchant: String,
        val accountHint: String
    ) : FinCommand

    data class Edit(
        val matchAmount: Long?,    // المبلغ القديم للبحث عن العملية
        val matchMerchant: String?,
        val newAmount: Long,
        val newCategory: String?     // null = لا تغيّر التصنيف
    ) : FinCommand

    data class Delete(
        val matchAmount: Long?,
        val matchMerchant: String?
    ) : FinCommand
}

object CommandParser {

    // ── مفردات الأوامر ──────────────────────────────────────────────────────
    private val addVerbs = listOf(
        "اضف", "أضف", "اضيف", "أضيف", "سجل", "سجّل", "سجللي", "سجلها",
        "صرفت", "صرف", "دفعت", "ادفعت", "اشتريت", "شريت",
        "استلمت", "قبضت", "حولت", "حول"
    )
    private val editVerbs = listOf("عدل", "عدّل", "غير", "غيّر", "صحح", "صحّح", "تعديل")
    private val deleteVerbs = listOf("احذف", "أحذف", "امسح", "امسحي", "الغ", "ألغ", "شيل", "حذف")

    private val expenseWords = listOf("مصروف", "صرفت", "دفعت", "اشتريت", "شريت", "فاتورة", "قيمة")
    private val incomeWords = listOf("راتب", "دخل", "استلمت", "قبضت", "وارد", "تحويل وارد")

    /** تحويل كلمة إلى التصنيف المعتمد داخل التطبيق. */
    private val categoryRules: List<Pair<List<String>, String>> = listOf(
        listOf("بقالة", "جمعية", "سوبر", "كارفور", "لولو", "مونوبري", "سبينس", "خضار", "لحم", "دجاج", "بضاعة") to "بقالة",
        listOf("مطعم", "مطاعم", "قهوة", "كوفي", "كافتيريا", "ستاربكس", "ماكدونالدز", "برجر", "بيتزا", "غداء", "عشاء", "فطور", "اكل", "أكل", "شاي") to "مطاعم",
        listOf("بنزين", "وقود", "بترول", "كيرلا", "وودين", "تاكسي", "اوبر", "أوبر", "كريم", "مواصلات", "باص", "مترو", "صيانة سيارة") to "مواصلات",
        listOf("صيدلية", "دكتور", "مستشفى", "عيادة", "تحليل", "دواء", "اسنان", "أسنان", "علاج") to "صحة",
        listOf("ملابس", "ثوب", "حقيبة", "ساعة", "امازون", "أمازون", "نون", "شراء", "تسوق", "جوال", "لكترونيات", "إلكترونيات", "لابتوب") to "تسوق",
        listOf("كهرباء", "كهرماء", "ماء", "ايجار", "إيجار", "ايجار البيت", "بيت", "منزل", "اثاث", "أثاث", "انترنت", "إنترنت", "اتصالات", "فودافون", "اوريدو", "أوريدو", "ooredoo", "فودافون قطر") to "منزل",
        listOf("سينما", "لعبة", "العاب", "ألعاب", "ترفيه", "نزهة", "رحلة", "تذاكر", "اشتراك") to "ترفيه",
        listOf("راتب", "معاش") to "راتب",
        listOf("مكافأة", "مكافاه", "دخل اضافي", "دخل إضافي", "بيع") to "دخل إضافي"
    )

    private const val DEFAULT_EXPENSE_CATEGORY = "أخرى"
    private const val DEFAULT_INCOME_CATEGORY = "أخرى"

    /** يقرأ الأمر من نص المستخدم. يعيد null إذا كانت الرسالة سؤالًا لا أمرًا. */
    fun parse(raw: String): FinCommand? {
        val text = normalizeDigits(raw).trim()
        if (text.isBlank()) return null

        val amount = findAmount(text)

        // ١) الحذف له الأولوية لأنه قد يحتوي كلمة «مصروف» فقط
        if (deleteVerbs.any { text.contains(it) }) {
            return FinCommand.Delete(
                matchAmount = amount,
                matchMerchant = merchantOf(raw).takeIf { it.isNotBlank() }
            )
        }

        // ٢) التعديل: يحتاج مبلغًا جديدًا صريحًا بعد «الى / إلى / خله / خلها»
        if (editVerbs.any { text.contains(it) }) {
            val newAmount = amountAfterEditMarker(text) ?: amount ?: return null
            val oldAmount = findAmount(removeSegmentAfterMarker(text, newAmount))
            return FinCommand.Edit(
                matchAmount = oldAmount.takeIf { it != null && it != newAmount },
                matchMerchant = merchantOf(raw).takeIf { it.isNotBlank() },
                newAmount = newAmount,
                newCategory = categoryOfOrNull(raw)
            )
        }

        // ٣) الإضافة: تتطلب فعلًا صريحًا + مبلغًا + ألا تكون الأسئلة
        if (amount == null || amount <= 0) return null
        if (looksLikeQuestion(text)) return null
        if (!addVerbs.any { text.contains(it) }) return null

        val isIncome = incomeWords.any { text.contains(it) } &&
            expenseWords.none { text.contains(it) }

        return FinCommand.Add(
            type = if (isIncome) "INCOME" else "EXPENSE",
            amount = amount,
            category = if (isIncome) incomeCategoryOf(raw) else categoryOf(raw),
            merchant = merchantOf(raw),
            accountHint = accountHintOf(raw)
        )
    }

    // ── استخراج الأرقام ─────────────────────────────────────────────────────
    private val numberRegex = Regex("""\d+(?:[.,]\d{1,2})?""")

    /** يستخرج المبلغ بالهللة. يفهم «٥٠٠»، «500»، «1.5»، «٣ آلاف»، «2k». */
    private fun findAmount(text: String): Long? {
        val match = numberRegex.find(text) ?: return null
        val value = match.value.replace(',', '.').toDoubleOrNull() ?: return null
        val after = text.substring(match.range.last + 1).trim().take(12)
        val multiplier = when {
            after.startsWith("الف") || after.startsWith("ألف") || after.startsWith("الاف") ||
                after.startsWith("آلاف") -> 1000.0
            after.startsWith("k") || after.startsWith("K") -> 1000.0
            else -> 1.0
        }
        return Math.round(value * multiplier * 100.0)
    }

    private val editMarkers = listOf("الى", "إلى", "خله", "خلها", "ليكون", "تكون", "خلها")

    private fun amountAfterEditMarker(text: String): Long? {
        val index = editMarkers.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
        return findAmount(text.substring(index))
    }

    private fun removeSegmentAfterMarker(text: String, cut: Long): String {
        val index = editMarkers.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return text
        return text.substring(0, index).replace(cut.toString(), "")
    }

    // ── التصنيف ─────────────────────────────────────────────────────────────
    fun categoryOf(raw: String): String = categoryOfOrNull(raw) ?: DEFAULT_EXPENSE_CATEGORY

    fun categoryOfOrNull(raw: String): String? {
        val text = normalizeDigits(raw).lowercase()
        categoryRules.forEach { (words, category) ->
            if (words.any { text.contains(it.lowercase()) }) return category
        }
        return null
    }

    private fun incomeCategoryOf(raw: String): String {
        val category = categoryOf(raw)
        return if (category == DEFAULT_EXPENSE_CATEGORY) DEFAULT_INCOME_CATEGORY else category
    }

    /** التاجر/الوصف: أول اسم علم أو كلمة دالة بعد إزالة الأرقام وأفعال الأمر. */
    fun merchantOf(raw: String): String {
        var text = normalizeDigits(raw)
        numberRegex.replace(text, " ")
        (addVerbs + editVerbs + deleteVerbs + editMarkers).forEach { text = text.replace(it, " ") }
        val stop = listOf(
            "ريال", "ر.ق", "ر ق", "مصروف", "دخل", "راتب", "قيمة", "مبلغ", "عملية", "الحين",
            "من", "في", "على", "الى", "إلى", "على كيفك", "على", "تراني", "ماله", "دخل",
            "اضف", "أضف", "سجل", "سجللي", "خله", "خلها", "اخر", "آخر", "اخير", "أخير", "عملية", "عمليات"
        )
        stop.forEach { text = text.replace(it, " ") }
        text = text.replace(Regex("""[^\p{L}\s]"""), " ")
        val words = text.split(" ").map { it.trim() }.filter { it.length > 1 }
        return words.take(3).joinToString(" ").trim()
    }

    private fun accountHintOf(raw: String): String {
        val text = normalizeDigits(raw)
        val match = Regex("""من\s+(حساب\s+)?([\p{L}\s]{3,20})""").find(text)?.groupValues?.getOrNull(2)
        return match?.trim().orEmpty()
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val questionWords = listOf("كم", "وين", "ليش", "متى", "هل", "شكثر", "قديش", "كيف", "أقدر", "اقدر", "?")
        return questionWords.any { text.contains(it) }
    }

    /** صياغة رد واضح يعتمد على ما حدث فعلًا في قاعدة البيانات. */
    fun describe(command: FinCommand, balanceAfter: Long, money: (Long) -> String): String = when (command) {
        is FinCommand.Add -> buildString {
            append(if (command.type == "INCOME") "تم ✅ سجلت دخل " else "تم ✅ سجلت مصروف ")
            append(money(command.amount))
            append(" — ")
            append(command.category)
            if (command.merchant.isNotBlank()) {
                append("، ")
                append(command.merchant)
            }
            append(".\n")
            append("رصيدك الحين: ")
            append(money(balanceAfter))
            append(".")
        }
        is FinCommand.Edit -> buildString {
            append("تم ✅ عدّلت العملية إلى ")
            append(money(command.newAmount))
            append(".\nرصيدك الحين: ")
            append(money(balanceAfter))
            append(".")
        }
        is FinCommand.Delete -> buildString {
            append("تم ✅ حذفت العملية ورجعت تأثيرها على الرصيد.\nرصيدك الحين: ")
            append(money(balanceAfter))
            append(".")
        }
    }
}
