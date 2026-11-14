package com.masarify.app.ai

import com.masarify.app.util.CURRENCY_LABEL
import com.masarify.app.util.formatMoney
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
    val spendByCategory: List<Pair<String, Long>> = expenses
        .groupBy { it.category.ifBlank { "أخرى" } }
        .map { (category, items) -> category to items.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val averageDailySpend: Long = if (cycleElapsedDays > 0) cycleExpense / cycleElapsedDays else cycleExpense
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
        val ratio = pct(projected, snapshot.cycleIncome)
        if (ratio >= 100) {
            out += Insight(
                "على هذا المعدل بتتعدى دخلك",
                "لو كملت بنفس الوتيرة، مصروف آخر الدورة ≈ ${formatMoney(projected)} وهذا أكثر من الدخل المسجل بـ ${formatMoney(projected - snapshot.cycleIncome)}. أسهل تعديل: خفّض أكبر تصنيف شوي.",
                Insight.Tone.WARN
            )
        } else if (ratio >= 80) {
            out += Insight(
                "صرفك قريب من حد الدخل",
                "المتوقع نهاية الدورة ≈ ${formatMoney(projected)} أي ${ratio}% من دخلك. باقي لك هامش بسيط، الأفضل تحجز مبلغ الادخار من بداية الشهر.",
                Insight.Tone.WARN
            )
        } else if (ratio in 1..60) {
            out += Insight(
                "وتيرة صرفك مريحة",
                "المتوقع لنهاية الدورة ≈ ${formatMoney(projected)} أي ${ratio}% من دخلك. تقدر تحوّل الفرق لهدف ادخار.",
                Insight.Tone.GOOD
            )
        }
    }

    // 2) أكبر تصنيف
    snapshot.spendByCategory.firstOrNull()?.let { (category, amount) ->
        val share = pct(amount, snapshot.cycleExpense)
        if (share >= 25) {
            val savingTen = amount / 10
            out += Insight(
                "أكبر بند عندك: $category",
                "صرفت ${formatMoney(amount)} عليه ($share% من مصروف الدورة). لو خفّضته 10% فقط، بتوفر ${formatMoney(savingTen)} كل دورة.",
                Insight.Tone.WARN
            )
        }
    }

    // 3) مصروف استثنائي
    val biggest = snapshot.expenses.maxByOrNull { it.amount }
    if (biggest != null && snapshot.expenses.size >= 4) {
        val avgOther = (snapshot.cycleExpense - biggest.amount) / (snapshot.expenses.size - 1).coerceAtLeast(1)
        if (avgOther > 0 && biggest.amount > avgOther * 3) {
            out += Insight(
                "فيه عملية كبيرة عن معتادك",
                "«${biggest.merchant.ifBlank { biggest.category }}» بمبلغ ${formatMoney(biggest.amount)}، وهو تقريبًا ${if (avgOther > 0) (biggest.amount / avgOther).toString() else "أضعاف"} أضعاف متوسط عملياتك. إذا كانت استثنائية، استثنِها من توقع نهاية الشهر.",
                Insight.Tone.NEUTRAL
            )
        }
    }

    // 4) معدل الصرف اليومي مقابل المسموح
    if (snapshot.dailyAllowance > 0 && snapshot.averageDailySpend > 0) {
        if (snapshot.averageDailySpend > snapshot.dailyAllowance) {
            val over = snapshot.averageDailySpend - snapshot.dailyAllowance
            out += Insight(
                "تصرف أسرع من خطتك",
                "معدل صرفك اليومي ${formatMoney(snapshot.averageDailySpend)}، والمسموح ${formatMoney(snapshot.dailyAllowance)} — فرق ${formatMoney(over)} باليوم. لو رجعت للمسموح، المتاح بأمان يكمل لين الراتب.",
                Insight.Tone.WARN
            )
        } else if (snapshot.daysLeft > 0) {
            out += Insight(
                "أنت داخل حدود خطتك",
                "تصرف حوالي ${formatMoney(snapshot.averageDailySpend)} باليوم، وخطتك تسمح بـ ${formatMoney(snapshot.dailyAllowance)}. استمر بهذا الإيقاع.",
                Insight.Tone.GOOD
            )
        }
    }

    // 5) تكرار محل معين
    val repeated = snapshot.expenses
        .filter { it.merchant.isNotBlank() }
        .groupBy { it.merchant }
        .filter { it.value.size >= 3 }
        .mapValues { it.value.size to it.value.sumOf { r -> r.amount } }
        .maxByOrNull { it.value.second }
    if (repeated != null) {
        val (merchant, info) = repeated
        out += Insight(
            "زيارات متكررة لـ $merchant",
            "${info.first} عمليات هذا الشهر بإجمالي ${formatMoney(info.second)}. إذا كان شيء يومي، خصص له سقف أسبوعي عشان ما يطلع عن السيطرة.",
            Insight.Tone.NEUTRAL
        )
    }

    // 6) فرصة ادخار
    if (snapshot.cycleIncome > 0L) {
        val leftover = snapshot.cycleIncome - snapshot.cycleExpense
        if (leftover > 0 && snapshot.savingsTarget <= 0L) {
            out += Insight(
                "فرصة ادخار واضحة",
                "باقي من دخلك ${formatMoney(leftover)}. حدد هدف ادخار من صفحة التخطيط حتى يخصم تلقائيًا من المتاح بأمان.",
                Insight.Tone.GOOD
            )
        }
    }

    return out.take(4)
}

/** أجوبة جاهزة لأسئلة شائعة، بدون إنترنت. لا تُرجع فراغًا أبدًا. */
fun localAnswer(question: String, snapshot: MoneySnapshot): String = try {
    buildAnswer(question, snapshot)
} catch (t: Throwable) {
    "صار خطأ بسيط في التحليل المحلي: ${t.javaClass.simpleName}. جرّب سؤال أبسط، أو أرسل التقرير من الإعدادات."
}

private fun buildAnswer(question: String, snapshot: MoneySnapshot): String {
    val q = question.trim()
    if (q.isBlank()) return "اكتب سؤالك، مثل: كم باقي لي؟ وين أصرف أكثر؟ أقدر أوفر كم؟"

    fun has(vararg words: String) = words.any { q.contains(it, ignoreCase = true) }

    val ratioText = if (snapshot.cycleIncome > 0) " (${pct(snapshot.cycleExpense, snapshot.cycleIncome)}% من دخلك)" else ""

    return when {
        has("باقي", "متبقي", "متاح", "أقدر أصرف", "اقدر اصرف", "كم معي") ->
            buildString {
                append("المتاح بأمان: ${formatMoney(snapshot.safe)}.")
                if (snapshot.daysLeft > 0) append("\nيعني ${formatMoney(snapshot.dailyAllowance)} باليوم لين الراتب (باقي ${snapshot.daysLeft} يوم).")
                append("\nالحساب: الرصيد ${formatMoney(snapshot.totalBalance)} − الالتزامات ${formatMoney(snapshot.commitments)} − الادخار ${formatMoney(snapshot.savingsTarget)}.")
            }

        has("وين", "أكثر", "اكثر", "أصرف", "اصرف", "تصنيف") -> {
            val top = snapshot.spendByCategory.take(3)
            if (top.isEmpty()) "ما فيه مصاريف مسجلة هذي الدورة بعد."
            else buildString {
                append("أكبر بنود الصرف هذي الدورة:\n")
                top.forEachIndexed { i, (category, amount) ->
                    append("${i + 1}. $category — ${formatMoney(amount)} (${pct(amount, snapshot.cycleExpense)}%)\n")
                }
                append("\nلو خفّضت البند الأول 10% توفر ${formatMoney(top.first().second / 10)}.")
            }
        }

        has("أوفر", "اوفر", "ادخر", "أدخر", "توفير") -> {
            val leftover = snapshot.cycleIncome - snapshot.cycleExpense
            when {
                snapshot.cycleIncome <= 0L -> "ما عندي دخل مسجل لهذي الدورة. سجّل الراتب أو حدده في الخطة وأقدر أحسب لك قدرة الادخار."
                leftover <= 0L -> "مصروفك ${formatMoney(snapshot.cycleExpense)}$ratioText يتعدى أو يساوي الدخل. أول خطوة: قلل أكبر بند (${
                    snapshot.spendByCategory.firstOrNull()?.first ?: "المصاريف المتغيرة"
                }) بمبلغ بسيط كل أسبوع."
                else -> "تقدر تدخر حتى ${formatMoney(leftover)} هذي الدورة (${
                    pct(leftover, snapshot.cycleIncome)
                }% من دخلك). لو حوّلت ${formatMoney(leftover / 2)} منها لهدف واضح، تبقى مرتاح للباقي."
            }
        }

        has("راتب", "الدورة", "الشهر") ->
            "مصروف الدورة ${formatMoney(snapshot.cycleExpense)}$ratioText، والدخل المسجل ${formatMoney(snapshot.cycleIncome)}. باقي ${snapshot.daysLeft} يوم للراتب القادم."

        has("توقع", "نهاية", "آخر الشهر") -> {
            val projected = if (snapshot.cycleElapsedDays > 0) snapshot.cycleExpense * snapshot.cycleDays / snapshot.cycleElapsedDays else snapshot.cycleExpense
            "لو كملت بنفس المعدل (${formatMoney(snapshot.averageDailySpend)} يوميًا)، متوقع تصرف ${formatMoney(projected)} بنهاية الدورة."
        }

        has("رسالة", "بنك", "sms") ->
            "قراءة رسائل البنك اختيارية ومحلية. فعّلها من الإعدادات، وأي رسالة تتحول إلى مسودة تراجعها قبل الحفظ — ما يُحفظ نص الرسالة نفسه."

        has("خصوصية", "بيانات", "آمن") ->
            "كل بياناتك في قاعدة بيانات على جوالك. التحليل المحلي يشتغل بدون إنترنت، والمساعد السحابي ما يشتغل إلا إذا فعّلته بمفتاحك ووافقت على مشاركة الأرقام."

        else -> buildString {
            append("أقدر أساعدك في: المتاح بأمان، أكبر بنود الصرف، فرص الادخار، توقع نهاية الشهر، ودورة الراتب.")
            append("\n\nملخص سريع: المتاح ${formatMoney(snapshot.safe)}")
            if (snapshot.daysLeft > 0) append(" • ${formatMoney(snapshot.dailyAllowance)} يوميًا لين الراتب")
            append(".")
        }
    }
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
