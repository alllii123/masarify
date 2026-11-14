package com.masarify.app.ai

import com.masarify.app.util.normalizeDigits

/**
 * تطبيع النص العربي قبل أي مطابقة.
 *
 * السبب: المستخدم يكتب بلهجته: «اضف» بلا همزة، «صرفته»، «شريت»، «خذلك».
 * لو طابقنا النص كما هو، نفشل في كل مرة لأن «أضف» ≠ «اضف».
 * لذلك نوحّد: الهمزات (أ إ آ) ← ا، ى ← ي، ة ← ه، ونحذف التشكيل والتطويل.
 */
object ArText {

    private val harakat = Regex("[\\u064B-\\u0652\\u0670\\u0640]")

    fun normalize(raw: String): String {
        var s = normalizeDigits(raw)
        s = harakat.replace(s, "")
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        s = s.replace('ى', 'ي').replace('ئ', 'ي').replace('ؤ', 'و')
        s = s.replace('ة', 'ه')
        s = s.replace('ـ', ' ')
        s = s.replace(Regex("[\\u061F]"), "؟")
        return s.lowercase().trim()
    }

    /** يحتوي كلمة كاملة لا جزءًا من كلمة أخرى. */
    fun hasWord(text: String, word: String): Boolean =
        Regex("(^|[^\\p{L}])" + Regex.escape(word) + "($|[^\\p{L}])").containsMatchIn(text)

    fun hasAny(text: String, words: List<String>): Boolean = words.any { hasWord(text, it) }
}

/**
 * طبقة تنفيذ أوامر المساعد.
 *
 * ── القاعدة الحاكمة ─────────────────────────────────────────────────────────
 * لا يُكتب شيء في قاعدة البيانات إلا بأمر صريح من المستخدم:
 *   • [ParsedCommand.explicit] = true  → ينفّذ فورًا (فعل ماضٍ واضح: صرفت/دفعت/أضف).
 *   • [ParsedCommand.explicit] = false → يُعرض طلب تأكيد بضغطة واحدة،
 *     فلا يسجّل «على كيفه» أبدًا، لكنه أيضًا لا يرفض صياغة المستخدم.
 *
 * ردّ المساعد السحابي لا ينفّذ أي شيء مهما قال.
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
        val matchAmount: Long?,
        val matchMerchant: String?,
        val newAmount: Long,
        val newCategory: String?
    ) : FinCommand

    data class Delete(
        val matchAmount: Long?,
        val matchMerchant: String?
    ) : FinCommand
}

/** أمر مع درجة يقين: صريح يُنفّذ، مستنتج يُطلب تأكيده. */
data class ParsedCommand(
    val command: FinCommand,
    val explicit: Boolean,
    val summary: String
)

object CommandParser {

    // ── أفعال الأمر الصريحة (بعد التطبيع) ───────────────────────────────────
    private val addVerbs = listOf(
        "اضف", "اضيف", "ضيف", "ضيفي", "سجل", "سجللي", "سجلها", "سجله", "حط", "حطي", "حطلي",
        "صرفت", "صرف", "صرفته", "دفعت", "دفع", "دفعته", "دفعتهم", "شريت", "اشتريت", "شريتها",
        "اخذت", "اخذ", "خذ", "خذلك", "خذه", "طلع", "طلعت", "طلعوا",
        "كلفني", "كلفتني", "بليت", "بلعت", "اكلت", "تعشيت", "تغديت", "فطرت", "شربت",
        "استلمت", "قبضت", "حولت", "وصلني", "نزل", "نزلوا", "خصم", "خصموا", "نقص", "نقصوا"
    )

    private val editVerbs = listOf(
        "عدل", "عدله", "عدلها", "تعديل", "غير", "غيره", "غيرها", "صحح", "صححها", "صححها", "بدل", "بدلها"
    )

    private val deleteVerbs = listOf(
        "احذف", "حذف", "امسح", "مسح", "الغ", "الغي", "شيل", "شيلها", "ازل", "ازلها", "كنسل", "الغها"
    )

    // ── كلمات تكشف النية المستقبلية ⇒ لا تُنفّذ ─────────────────────────────
    private val futureWords = listOf(
        "بصرف", "بشتري", "بروح", "باخذ", "بدفع", "بعدين", "بكره", "باجر", "بكره",
        "ناوي", "ناويه", "نفسي", "ودي", "ابي", "ابغي", "افكر", "افكر", "راح اصرف",
        "لازم", "محتاج", "احتجت", "يمكن", "لو", "اذا", "ان", "سوف"
    )

    // ── كلمات سؤال ⇒ لا تُنفّذ ───────────────────────────────────────────────
    private val questionWords = listOf(
        "كم", "بكم", "جم", "قديش", "شكثر", "شحال", "وين", "ليش", "ليه", "متى", "هل",
        "شلون", "كيف", "اقدر", "باقي", "بقى", "شصار", "عندي", "معي", "رصيدي", "فلوسي",
        "المتاح", "مصروفي", "صرفي", "تقدر", "تعرف", "تتوقع", "توقع", "رايك", "تنصح",
        "نصيحه", "ليش", "وش", "ايش", "شنو", "شو"
    )

    private val incomeWords = listOf(
        "راتب", "معاش", "دخل", "استلمت", "قبضت", "وارد", "نزل لي", "وصلني", "مكافاه", "منحه", "حصه"
    )

    private val expenseWords = listOf(
        "مصروف", "مصاريف", "فاتوره", "قيمه", "دفعت", "صرفت", "شريت", "مشتريات", "كلفني"
    )

    /** تحويل كلمة إلى التصنيف المعتمد داخل التطبيق (بعد التطبيع). */
    private val categoryRules: List<Pair<List<String>, String>> = listOf(
        listOf(
            "بقاله", "جمعيه", "سوبر", "كارفور", "لولو", "مونوبري", "سبينس", "خضار", "فواكه",
            "لحم", "دجاج", "سمك", "بيض", "طحين", "رز", "بضاعه", "سوق", "مركز"
        ) to "بقالة",
        listOf(
            "مطعم", "مطاعم", "قهوه", "كوفي", "كافيه", "كافتيريا", "ستاربكس", "ماكدونالدز", "برجر",
            "بيتزا", "غدا", "عشا", "فطور", "اكل", "شاي", "كيك", "حلويات", "دونات", "شاورما",
            "رز بخاري", "مندي", "مشاوي", "فاست", "فرايد", "كنتاكي", "هرفي", "شيبس"
        ) to "مطاعم",
        listOf(
            "بنزين", "وقود", "بترول", "كيروسين", "وقود", "بترومين", "تاكسي", "اوبر", "كريم",
            "مواصلات", "باص", "مترو", "صيانه", "سياره", "تواير", "اطارات", "زيت", "غسيل"
        ) to "مواصلات",
        listOf(
            "صيدليه", "دكتور", "دكتوره", "مستشفى", "عياده", "تحليل", "دوا", "دواء", "اسنان",
            "علاج", "مرض", "كشف", "مستوصف", "صحه"
        ) to "صحة",
        listOf(
            "ملابس", "ثوب", "غتره", "حقيبه", "ساعه", "امازون", "نون", "تسوق", "جوال", "ايفون",
            "لابتوب", "سماعه", "الكترونيات", "جهاز", "عطر", "مكياج", "احذيه", "نظاره"
        ) to "تسوق",
        listOf(
            "كهرباء", "كهرماء", "ماي", "ماء", "ايجار", "بيت", "منزل", "اثاث", "كنب", "انترنت",
            "اتصالات", "فودافون", "اوريدو", "شهر", "اشتراك", "صيانه البيت", "سباك", "كهربجي", "تنظيف"
        ) to "منزل",
        listOf(
            "سينما", "لعبه", "العاب", "ترفيه", "نزهه", "رحله", "تذاكر", "اشتراك", "بلايستيشن",
            "قيم", "مهرجان", "حفله"
        ) to "ترفيه",
        listOf("راتب", "معاش", "شهر") to "راتب",
        listOf("مكافاه", "دخل اضافي", "بيع", "منحه", "هديه") to "دخل إضافي"
    )

    private const val DEFAULT_EXPENSE_CATEGORY = "أخرى"
    private const val DEFAULT_INCOME_CATEGORY = "أخرى"

    // ── الواجهة العامة ──────────────────────────────────────────────────────
    fun parse(raw: String): ParsedCommand? {
        val text = ArText.normalize(raw)
        if (text.isBlank()) return null

        val amount = findAmount(text)
        val asked = looksLikeQuestion(text) || looksLikeFuture(text)

        // ١) الحذف
        if (ArText.hasAny(text, deleteVerbs)) {
            return ParsedCommand(
                command = FinCommand.Delete(
                    matchAmount = amount,
                    matchMerchant = merchantOf(raw).takeIf { it.isNotBlank() }
                ),
                explicit = true,
                summary = "حذف عملية"
            )
        }

        // ٢) التعديل
        if (ArText.hasAny(text, editVerbs)) {
            val newAmount = amountAfterEditMarker(text) ?: amount ?: return null
            val oldAmount = findAmount(removeSegmentAfterMarker(text))
            val command = FinCommand.Edit(
                matchAmount = oldAmount.takeIf { it != null && it != newAmount },
                matchMerchant = merchantOf(raw).takeIf { it.isNotBlank() },
                newAmount = newAmount,
                newCategory = categoryOfOrNull(raw)
            )
            return ParsedCommand(command, explicit = true, summary = "تعديل عملية إلى ${newAmount / 100}")
        }

        if (amount == null || amount <= 0) return null
        if (asked) return null

        val isIncome = ArText.hasAny(text, incomeWords) && !ArText.hasAny(text, expenseWords)
        val command = FinCommand.Add(
            type = if (isIncome) "INCOME" else "EXPENSE",
            amount = amount,
            category = if (isIncome) incomeCategoryOf(raw) else categoryOf(raw),
            merchant = merchantOf(raw),
            accountHint = accountHintOf(raw)
        )

        // صريح: فيه فعل من أفعال الأمر ⇒ ينفّذ فورًا
        if (ArText.hasAny(text, addVerbs)) {
            return ParsedCommand(command, explicit = true, summary = "تسجيل ${if (isIncome) "دخل" else "مصروف"}")
        }

        // مستنتج: مبلغ + اسم مكان/تصنيف واضح، بلا فعل ⇒ نطلب تأكيدًا بضغطة
        val hasPlace = command.merchant.isNotBlank() || command.category != DEFAULT_EXPENSE_CATEGORY
        if (hasPlace) {
            return ParsedCommand(command, explicit = false, summary = "تسجيل ${if (isIncome) "دخل" else "مصروف"}")
        }

        return null
    }

    // ── استخراج الأرقام ─────────────────────────────────────────────────────
    private val numberRegex = Regex("""\d+(?:[.,]\d{1,2})?""")

    fun findAmount(text: String): Long? {
        val normalized = ArText.normalize(text)
        val match = numberRegex.find(normalized) ?: return null
        val value = match.value.replace(',', '.').toDoubleOrNull() ?: return null
        val after = normalized.substring(match.range.last + 1).trim().take(14)
        val multiplier = when {
            after.startsWith("الف") || after.startsWith("الاف") || after.startsWith("الفين") -> 1000.0
            after.startsWith("k") -> 1000.0
            after.startsWith("مليون") -> 1_000_000.0
            else -> 1.0
        }
        return Math.round(value * multiplier * 100.0)
    }

    private val editMarkers = listOf("الى", "الي", "خله", "خلها", "ليكون", "تكون", "يصير", "صار")

    private fun amountAfterEditMarker(text: String): Long? {
        val index = editMarkers.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
        return findAmount(text.substring(index))
    }

    private fun removeSegmentAfterMarker(text: String): String {
        val index = editMarkers.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return text
        return text.substring(0, index)
    }

    // ── التصنيف والتاجر ─────────────────────────────────────────────────────
    fun categoryOf(raw: String): String = categoryOfOrNull(raw) ?: DEFAULT_EXPENSE_CATEGORY

    fun categoryOfOrNull(raw: String): String? {
        val text = ArText.normalize(raw)
        categoryRules.forEach { (words, category) ->
            if (ArText.hasAny(text, words)) return category
        }
        return null
    }

    private fun incomeCategoryOf(raw: String): String {
        val category = categoryOfOrNull(raw)
        return when {
            category == null -> DEFAULT_INCOME_CATEGORY
            category == DEFAULT_EXPENSE_CATEGORY -> DEFAULT_INCOME_CATEGORY
            else -> category
        }
    }

    private val stopWords = listOf(
        "ريال", "ر.ق", "مصروف", "مصاريف", "دخل", "قيمه", "مبلغ", "عمليه", "عمليات", "الحين",
        "من", "في", "على", "الي", "الى", "تراني", "ماله", "اخر", "اخير", "تقريبا", "حوالي",
        "بس", "بعد", "قبل", "اليوم", "امس", "هذا", "هذي", "كان", "صار", "صارت", "الله", "يخليك",
        "شكرا", "جزاك", "خير", "طيب", "اوكي", "زين"
    )

    /** كلمات لا تصلح أن تكون اسم مكان: أفعال، حشو، أو وصف للمبلغ. */
    private fun isFiller(word: String): Boolean {
        val bare = word.removePrefix("ال").removePrefix("و")
        if (bare.length <= 2) return true
        val fillers = setOf(
            "مصروف", "مصاريف", "دخل", "راتب", "قيمه", "مبلغ", "عمليه", "عمليات", "ريال",
            "يومي", "يوميه", "شهري", "شهريه", "اسبوعي", "تقريبا", "حوالي", "يمكن", "بس",
            "الحين", "اليوم", "امس", "هذا", "هذي", "كان", "صار", "صارت", "بعد", "قبل",
            "على", "من", "في", "الى", "مع", "عن", "كل", "شي", "شوي", "كثير", "قليل",
            "والله", "الله", "طيب", "زين", "اوكي", "شكرا", "جزاك", "خير", "ياخي", "تكفى"
        )
        return bare in fillers || word in fillers
    }

    fun merchantOf(raw: String): String {
        var text = ArText.normalize(raw)
        numberRegex.replace(text, " ")
        (addVerbs + editVerbs + deleteVerbs + editMarkers).forEach { text = text.replace(it, " ") }
        text = text.replace(Regex("""[^\p{L}\s]"""), " ")
        val words = text.split(" ").map { it.trim() }.filter { it.length > 1 && !isFiller(it) }
        return words.take(3).joinToString(" ").trim()
    }

    private fun accountHintOf(raw: String): String {
        val text = ArText.normalize(raw)
        val match = Regex("""من\s+(حساب\s+)?([\p{L}\s]{3,20})""").find(text)?.groupValues?.getOrNull(2)
        return match?.trim().orEmpty()
    }

    // ── كواشف النية ─────────────────────────────────────────────────────────
    private fun looksLikeQuestion(text: String): Boolean {
        if (text.contains("؟") || text.contains("?")) return true
        return ArText.hasAny(text, questionWords)
    }

    private fun looksLikeFuture(text: String): Boolean = ArText.hasAny(text, futureWords)

    /** صياغة رد واضح يعتمد على ما حدث فعلًا في قاعدة البيانات. */
    fun describe(command: FinCommand, balanceAfter: Long, totalBalance: Long, money: (Long) -> String): String =
        when (command) {
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
                append("رصيد هذا الحساب: ")
                append(money(balanceAfter))
                append(" • مجموعك: ")
                append(money(totalBalance))
            }

            is FinCommand.Edit -> buildString {
                append("تم ✅ عدّلت العملية إلى ")
                append(money(command.newAmount))
                append(".\nمجموعك الحين: ")
                append(money(totalBalance))
            }

            is FinCommand.Delete -> buildString {
                append("تم ✅ حذفت العملية ورجعت أثرها على الرصيد.\nمجموعك الحين: ")
                append(money(totalBalance))
            }
        }

    /** نص مختصر يُعرض في بطاقة التأكيد قبل التنفيذ. */
    fun proposalText(command: FinCommand, money: (Long) -> String): String = when (command) {
        is FinCommand.Add -> buildString {
            append(if (command.type == "INCOME") "أسجل دخل " else "أسجل مصروف ")
            append(money(command.amount))
            append(" — ")
            append(command.category)
            if (command.merchant.isNotBlank()) append(" (${command.merchant})")
            append("؟")
        }

        is FinCommand.Edit -> "أعدّل العملية إلى ${money(command.newAmount)}؟"
        is FinCommand.Delete -> "أحذف العملية؟"
    }
}
