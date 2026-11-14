package com.masarify.app.ai

import com.masarify.app.util.CURRENCY_LABEL
import com.masarify.app.util.formatMoney
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * محرّك التحليل المحلي: يعمل بالكامل على الجهاز، بدون إنترنت وبدون أي مفتاح.
 * لا يقرأ إلا الأرقام التي أدخلها المستخدم داخل التطبيق.
 */

data class SpendRecord(
    val category: String,
    val merchant: String,
    val amount: Long,
    val isExpense: Boolean
)

data class MoneySnapshot(
    val totalBalance: Long = 0,
    val commitments: Long = 0,
    val savingsTarget: Long = 0,
    val salaryDay: Int = 27,
    val daysLeft: Int = 0,
    val cycleDays: Int = 30,
    val cycleElapsedDays: Int = 1,
    val cycleExpense: Long = 0,
    val cycleIncome: Long = 0,
    val records: List<SpendRecord> = emptyList()
) {
    val safe: Long = (totalBalance - commitments - savingsTarget).coerceAtLeast(0)
    val dailyAllowance: Long = if (daysLeft > 0) safe / daysLeft else safe
    val expenses: List<SpendRecord> = records.filter { it.isExpense }
    val income: List<SpendRecord> = records.filter { !it.isExpense }
    val spendByCategory: List<Pair<String, Long>> = expenses
        .groupBy { it.category.ifBlank { "أخرى" } }
        .map { (category, items) -> category to items.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val averageDailySpend: Long = if (cycleElapsedDays > 0) cycleExpense / cycleElapsedDays else cycleExpense
    /** كم بيبقى من المتاح لو استمر بنفس معدل الصرف. */
    val projectedLeftover: Long = (safe - averageDailySpend * daysLeft).coerceAtLeast(0)
    val burnRatio: Int = if (safe <= 0) 0 else ((averageDailySpend * daysLeft) * 100 / safe).toInt()
}

data class Insight(
    val title: String,
    val body: String,
    val tone: Tone = Tone.NEUTRAL
) {
    enum class Tone { GOOD, WARN, NEUTRAL }
}

private fun pct(part: Long, whole: Long): Int =
    if (whole <= 0L) 0 else ((part.toDouble() / whole.toDouble()) * 100).toInt()

private fun shorten(amount: Long): String = formatMoney(amount, withCurrency = false)

/** ملاحظات ذكية تظهر في الرئيسية وفي صفحة المساعد. لا ترمي استثناءات أبدًا. */
fun localInsights(snapshot: MoneySnapshot): List<Insight> = try {
    buildInsights(snapshot)
} catch (t: Throwable) {
    listOf(Insight("تعذّر تحليل الأرقام", "راجع عملياتك أو أضف بيانات أكثر ثم أعد المحاولة."))
}

private fun buildInsights(snapshot: MoneySnapshot): List<Insight> {
    val out = mutableListOf<Insight>()

    if (snapshot.expenses.isEmpty() && snapshot.cycleIncome <= 0L) {
        out += Insight(
            title = "خلّنا نبدأ بخطوة وحدة",
            body = "أضف رصيد حسابك من الإعدادات، وبعدها أول عملية. من أول عملية راح أقدر أحسب لك المتاح بأمان وتوقع نهاية الشهر."
        )
        return out
    }

    // 1) سرعة الصرف مقارنة بالدخل
    if (snapshot.cycleIncome > 0L) {
        val projected = when {
            snapshot.cycleElapsedDays <= 0 -> snapshot.cycleExpense
            else -> snapshot.cycleExpense * snapshot.cycleDays / snapshot.cycleElapsedDays
        }
        val share = pct(projected, snapshot.cycleIncome)
        out += when {
            share >= 100 -> Insight(
                title = "معدل الصرف يتجاوز دخلك",
                body = "على هذا المعدل بتصرف ${formatMoney(projected)} بنهاية الدورة، وهذا $share% من دخلك. " +
                    "أقرب بند تقدر تمسكه: ${snapshot.spendByCategory.firstOrNull()?.first ?: "المصاريف اليومية"}.",
                tone = Insight.Tone.WARN
            )

            share >= 80 -> Insight(
                title = "الصرف قريب من الدخل",
                body = "متوقع تصرف ${formatMoney(projected)} ($share% من الدخل). لو خفّضت ${formatMoney((projected - snapshot.cycleIncome).coerceAtLeast(snapshot.cycleIncome / 10))} تبقى في الأمان.",
                tone = Insight.Tone.WARN
            )

            else -> Insight(
                title = "وضعك مستقر",
                body = "متوقع تصرف ${formatMoney(projected)} ($share% من دخلك) وتوفّر ${formatMoney((snapshot.cycleIncome - projected).coerceAtLeast(0))}.",
                tone = Insight.Tone.GOOD
            )
        }
    }

    // 2) وتيرة الأيام المتبقية
    if (snapshot.daysLeft > 0 && snapshot.safe > 0) {
        val burn = snapshot.burnRatio
        out += when {
            burn >= 100 -> Insight(
                title = "المعدل الحالي أعلى من المتاح",
                body = "لو كملت بنفس الوتيرة (${formatMoney(snapshot.averageDailySpend)} يوميًا) تحتاج ${formatMoney(snapshot.averageDailySpend * snapshot.daysLeft)} " +
                    "لباقي ${snapshot.daysLeft} يوم، والمتاح ${formatMoney(snapshot.safe)}. حاول تنزل ليومي ${formatMoney(snapshot.dailyAllowance)}.",
                tone = Insight.Tone.WARN
            )

            burn >= 80 -> Insight(
                title = "قريب من الحد",
                body = "صرفت $burn% من متاحك على الأيام المتبقية. باقي لك ${formatMoney(snapshot.safe)} و${snapshot.daysLeft} يوم.",
                tone = Insight.Tone.WARN
            )

            else -> Insight(
                title = "عندك مساحة",
                body = "باقي ${formatMoney(snapshot.safe)} لـ ${snapshot.daysLeft} يوم — يعني ${formatMoney(snapshot.dailyAllowance)} باليوم بكل راحة.",
                tone = Insight.Tone.GOOD
            )
        }
    }

    // 3) أكبر بند
    snapshot.spendByCategory.firstOrNull()?.let { (category, amount) ->
        if (snapshot.cycleExpense > 0) {
            out += Insight(
                title = "أكبر بند: $category",
                body = "${formatMoney(amount)} — ${
                    pct(amount, snapshot.cycleExpense)
                }% من صرفك. لو خفّضته 10% توفر ${formatMoney(amount / 10)}.",
                tone = Insight.Tone.NEUTRAL
            )
        }
    }

    // 4) مقارنة بالشهر السابق تُحسب من السجلات لو توفرت
    return out.take(4)
}

// ── الإجابة المحلية ─────────────────────────────────────────────────────────

/**
 * كل سؤال له مسار. لو ما فهمنا السؤال، **لا نعتذر** — نعطي الملخص المالي الكامل.
 * سبب التصميم: المستخدم قال «لا يكون سيئ»، والرد «ما فهمت» أسوأ من ملخص مفيد.
 */
fun localAnswer(question: String, snapshot: MoneySnapshot): String = try {
    buildAnswer(question, snapshot)
} catch (t: Throwable) {
    // حتى لو صار خطأ غير متوقع، نعطي أرقامًا بدل رسالة خطأ
    "المتاح بأمان: ${formatMoney(snapshot.safe)} • يوميًا: ${formatMoney(snapshot.dailyAllowance)} • مجموعك: ${formatMoney(snapshot.totalBalance)}."
}

/** نية السؤال بعد التطبيع — تُستخدم أيضًا لاختيار شريحة سريعة في الواجهة. */
enum class LocalTopic {
    SAFE,        // كم باقي / المتاح
    TOTAL,       // كم عندي / مجموعي
    SPEND,       // كم صرفت
    CATEGORY,    // وين أصرف أكثر
    SAVE,        // كيف أوفر
    FORECAST,    // توقع نهاية الشهر
    SALARY,      // الراتب / كم يوم باقي
    INCOME,      // الدخل
    COMPARE,     // مقارنة
    ADVICE,      // نصيحة عامة
    HELP         // ما فهمنا
}

fun detectTopic(question: String): LocalTopic {
    val q = ArText.normalize(question)
    return when {
        ArText.hasAny(q, listOf("كم عندي", "عندي كم", "مجموع", "مجموعي", "الرصيد", "رصيدي", "فلوسي", "كم معي", "معي كم", "كم فلوس", "كم مجموع")) -> LocalTopic.TOTAL
        ArText.hasAny(q, listOf("اقدر اصرف", "باقي لي", "باقي", "متاح", "المتاح", "امان", "بأمان")) -> LocalTopic.SAFE
        ArText.hasAny(q, listOf("صرفت", "صرفي", "مصروفي", "مصاريفي", "كم صرف", "كم طلع", "كم كلفني")) -> LocalTopic.SPEND
        ArText.hasAny(q, listOf("وين", "اكثر", "اكثر شي", "فين", "على شنو", "على ايش", "تصنيف", "بند", "بنود")) -> LocalTopic.CATEGORY
        ArText.hasAny(q, listOf("اوفر", "ادخر", "توفير", "اوفر كم", "كيف اوفر", "توفيري")) -> LocalTopic.SAVE
        ArText.hasAny(q, listOf("توقع", "نهايه", "اخر الشهر", "بعد كم", "بتوصل", "معدل")) -> LocalTopic.FORECAST
        ArText.hasAny(q, listOf("راتب", "معاش", "الدوره", "يوم للراتب", "متى ينزل")) -> LocalTopic.SALARY
        ArText.hasAny(q, listOf("دخل", "وارد", "استلمت", "كسبت")) -> LocalTopic.INCOME
        ArText.hasAny(q, listOf("مقارنه", "قارن", "الشهر الماضي", "السابق", "افضل من")) -> LocalTopic.COMPARE
        ArText.hasAny(q, listOf("نصيحه", "تنصح", "رايك", "شنو اسوي", "شسوي", "كيف احسن", "احسن")) -> LocalTopic.ADVICE
        else -> LocalTopic.HELP
    }
}

private fun buildAnswer(question: String, snapshot: MoneySnapshot): String {
    val q = question.trim()
    if (q.isBlank()) return quickSummary(snapshot)

    val topic = detectTopic(q)
    val ratioText = if (snapshot.cycleIncome > 0) " (${pct(snapshot.cycleExpense, snapshot.cycleIncome)}% من دخلك)" else ""

    return when (topic) {
        LocalTopic.TOTAL ->
            buildString {
                append("مجموعك الحالي: ${formatMoney(snapshot.totalBalance)} في كل الحسابات.\n")
                append("بعد حجز الالتزامات (${formatMoney(snapshot.commitments)}) والادخار (${formatMoney(snapshot.savingsTarget)}), ")
                append("المتاح بأمان: ${formatMoney(snapshot.safe)}.")
            }

        LocalTopic.SAFE ->
            buildString {
                append("المتاح بأمان: ${formatMoney(snapshot.safe)}.\n")
                if (snapshot.daysLeft > 0) {
                    append("يعني ${formatMoney(snapshot.dailyAllowance)} باليوم لين الراتب (باقي ${snapshot.daysLeft} يوم).\n")
                }
                append("الحساب: الرصيد ${formatMoney(snapshot.totalBalance)} − الالتزامات ${formatMoney(snapshot.commitments)} − الادخار ${formatMoney(snapshot.savingsTarget)}.")
            }

        LocalTopic.SPEND ->
            buildString {
                append("صرفت هذي الدورة ${formatMoney(snapshot.cycleExpense)}$ratioText.\n")
                if (snapshot.cycleElapsedDays > 0) {
                    append("متوسط اليوم: ${formatMoney(snapshot.averageDailySpend)} على مدى ${snapshot.cycleElapsedDays} يوم.")
                }
                snapshot.spendByCategory.firstOrNull()?.let { (c, a) ->
                    append("\nأكبر بند: $c (${formatMoney(a)}).")
                }
            }

        LocalTopic.CATEGORY -> {
            val top = snapshot.spendByCategory.take(3)
            if (top.isEmpty()) "ما فيه مصاريف مسجلة هذي الدورة بعد. سجّل أول عملية وأنا أحللها لك."
            else buildString {
                append("أكبر بنود الصرف هذي الدورة:\n")
                top.forEachIndexed { i, (category, amount) ->
                    append("${i + 1}. $category — ${formatMoney(amount)} (${pct(amount, snapshot.cycleExpense)}%)\n")
                }
                append("\nلو خفّضت البند الأول 10% توفر ${formatMoney(top.first().second / 10)}.")
            }
        }

        LocalTopic.SAVE -> {
            val leftover = snapshot.cycleIncome - snapshot.cycleExpense
            when {
                snapshot.cycleIncome <= 0L ->
                    "ما عندي دخل مسجل لهذي الدورة. سجّل الراتب أو حدده في الخطة وأحسب لك قدرة الادخار بدقة."

                leftover <= 0L ->
                    "مصروفك ${formatMoney(snapshot.cycleExpense)}$ratioText يوازي أو يتعدى الدخل. " +
                        "أول خطوة: قلل أكبر بند (${snapshot.spendByCategory.firstOrNull()?.first ?: "المصاريف اليومية"}) بمبلغ بسيط كل أسبوع."

                else ->
                    "تقدر تدخر حتى ${formatMoney(leftover)} هذي الدورة (${pct(leftover, snapshot.cycleIncome)}% من دخلك).\n" +
                        "لو حوّلت ${formatMoney(leftover / 2)} لهدف واضح من بداية الشهر، الباقي يكفيك بسهولة."
            }
        }

        LocalTopic.FORECAST -> {
            val projected = if (snapshot.cycleElapsedDays > 0)
                snapshot.cycleExpense * snapshot.cycleDays / snapshot.cycleElapsedDays
            else snapshot.cycleExpense
            buildString {
                append("لو كملت بنفس المعدل (${formatMoney(snapshot.averageDailySpend)} يوميًا)، متوقع تصرف ${formatMoney(projected)} بنهاية الدورة.\n")
                append("الفرق عن دخلك: ${if (snapshot.cycleIncome > 0) formatMoney(snapshot.cycleIncome - projected) else "أضف دخلك للمقارنة"}.")
            }
        }

        LocalTopic.SALARY ->
            "باقي ${snapshot.daysLeft} يوم على الراتب (يوم ${snapshot.salaryDay}). " +
                "مصروف الدورة ${formatMoney(snapshot.cycleExpense)}$ratioText."

        LocalTopic.INCOME ->
            if (snapshot.cycleIncome <= 0L) "ما فيه دخل مسجل هذي الدورة."
            else "دخلك المسجل هذي الدورة ${formatMoney(snapshot.cycleIncome)}، وصرفت ${formatMoney(snapshot.cycleExpense)}، " +
                "الباقي ${formatMoney(snapshot.cycleIncome - snapshot.cycleExpense)}."

        LocalTopic.COMPARE -> {
            val top = snapshot.spendByCategory.firstOrNull()
            if (top == null) "ما فيه بيانات كافية للمقارنة. سجّل عمليات أكثر وأقدر أقارن لك."
            else "${top.first} هو أعلى بند عندك (${formatMoney(top.second)}، ${pct(top.second, snapshot.cycleExpense)}% من صرفك). " +
                "هنا أول مكان تقدر توفّر منه."
        }

        LocalTopic.ADVICE -> buildString {
            append("أفضل ثلاث خطوات من أرقامك:\n")
            snapshot.spendByCategory.firstOrNull()?.let {
                append("1. خفّض ${it.first} 10% ⇒ توفير ${formatMoney(it.second / 10)}.\n")
            }
            if (snapshot.safe > 0 && snapshot.daysLeft > 0) {
                append("2. التزم بـ ${formatMoney(snapshot.dailyAllowance)} يوميًا لين الراتب.\n")
            }
            if (snapshot.commitments > 0) {
                append("3. خلّ ${formatMoney(snapshot.commitments)} محجوزة للالتزامات، لا تلمسها.")
            }
        }

        LocalTopic.HELP -> quickSummary(snapshot)
    }
}

/** ملخص دائم — بدل الاعتذار. */
private fun quickSummary(snapshot: MoneySnapshot): String = buildString {
    append("ملخصك الآن 👇\n")
    append("• مجموعك: ${formatMoney(snapshot.totalBalance)}\n")
    append("• المتاح بأمان: ${formatMoney(snapshot.safe)}\n")
    if (snapshot.daysLeft > 0) append("• يوميًا: ${formatMoney(snapshot.dailyAllowance)} (باقي ${snapshot.daysLeft} يوم)\n")
    append("• صرف الدورة: ${formatMoney(snapshot.cycleExpense)}")
    snapshot.spendByCategory.firstOrNull()?.let { append("\n• أكبر بند: ${it.first} (${formatMoney(it.second)})") }
    append("\n\nاسألني عن أي رقم، أو قل «أضف مصروف 50 بقالة» ونفّذها لك.")
}

/** سياق مختصر يُرسل للسحابة فقط عند موافقة المستخدم على مشاركة الأرقام. */
fun cloudContext(snapshot: MoneySnapshot, shareAmounts: Boolean): String = buildString {
    append("snapshot: currency=QAR")
    if (shareAmounts) {
        append(", safe=${snapshot.safe / 100.0}, balance=${snapshot.totalBalance / 100.0}")
        append(", commitments=${snapshot.commitments / 100.0}, savingsTarget=${snapshot.savingsTarget / 100.0}")
        append(", cycleExpense=${snapshot.cycleExpense / 100.0}, cycleIncome=${snapshot.cycleIncome / 100.0}")
    } else {
        append(", amountsWithheld=true")
        append(", expenseCount=${snapshot.expenses.size}")
    }
    append(", daysLeft=${snapshot.daysLeft}, salaryDay=${snapshot.salaryDay}")
    val top = snapshot.spendByCategory.take(3).joinToString("; ") { "${it.first}:${pct(it.second, snapshot.cycleExpense)}%" }
    if (top.isNotBlank()) append(", topCategories=[$top]")
}

fun snapshotFrom(
    totalBalance: Long,
    commitments: Long,
    savingsTarget: Long,
    salaryDay: Int,
    daysLeft: Int,
    cycleStart: Long,
    cycleTransactions: List<Triple<String, String, Long>>,
    cycleExpenses: Long,
    cycleIncome: Long,
    today: LocalDate = LocalDate.now()
): MoneySnapshot {
    val elapsed = ChronoUnit.DAYS.between(
        java.time.Instant.ofEpochMilli(cycleStart).atZone(ZoneId.systemDefault()).toLocalDate(),
        today
    ).toInt().coerceAtLeast(1)
    val cycleDays = (elapsed + daysLeft).coerceAtLeast(1)
    val records = cycleTransactions.map { (merchant, category, signedAmount) ->
        SpendRecord(category = category, merchant = merchant, amount = abs(signedAmount), isExpense = signedAmount < 0)
    }
    return MoneySnapshot(
        totalBalance = totalBalance,
        commitments = commitments,
        savingsTarget = savingsTarget,
        salaryDay = salaryDay,
        daysLeft = daysLeft,
        cycleDays = cycleDays,
        cycleElapsedDays = elapsed,
        cycleExpense = cycleExpenses,
        cycleIncome = cycleIncome,
        records = records
    )
}

/** للعرض السريع داخل البطاقة. */
fun currencyLabel() = CURRENCY_LABEL
