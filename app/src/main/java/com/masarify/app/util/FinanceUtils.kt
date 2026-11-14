package com.masarify.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** عملة التطبيق الثابتة: الريال القطري. */
const val CURRENCY_CODE = "QAR"
const val CURRENCY_LABEL = "ر.ق"

fun normalizeDigits(value: String): String {
    var result = value
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val eastern = "۰۱۲۳۴۵۶۷۸۹"
    arabic.forEachIndexed { i, c -> result = result.replace(c, ('0' + i)) }
    eastern.forEachIndexed { i, c -> result = result.replace(c, ('0' + i)) }
    return result.replace('٫', '.').replace('،', ',')
}

fun parseMoney(value: String): Long? = runCatching {
    val clean = normalizeDigits(value).replace(",", "").replace(" ", "")
    if (clean.isBlank()) return null
    BigDecimal(clean).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()

fun moneyInput(value: Long): String = if (value % 100 == 0L) {
    (value / 100).toString()
} else String.format(Locale.US, "%.2f", value / 100.0)

/** يعرض المبلغ دائمًا بالريال القطري، بالخانات اللاتينية لسهولة القراءة. */
fun formatMoney(amount: Long, withCurrency: Boolean = true): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (amount % 100 == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    val formatted = formatter.format(amount / 100.0)
    return if (withCurrency) "$formatted $CURRENCY_LABEL" else formatted
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM • h:mm a", Locale("ar", "QA")).format(Date(timestamp))

fun formatMonth(today: LocalDate = LocalDate.now()): String =
    today.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar", "QA")))

fun daysUntilSalary(salaryDay: Int, today: LocalDate = LocalDate.now()): Int {
    val thisMonthDay = salaryDay.coerceIn(1, today.lengthOfMonth())
    var next = today.withDayOfMonth(thisMonthDay)
    if (next.isBefore(today)) {
        val nextMonth = today.plusMonths(1)
        next = nextMonth.withDayOfMonth(salaryDay.coerceIn(1, nextMonth.lengthOfMonth()))
    }
    return ChronoUnit.DAYS.between(today, next).toInt()
}

fun salaryCycleStart(salaryDay: Int, today: LocalDate = LocalDate.now()): LocalDate {
    val day = salaryDay.coerceIn(1, today.lengthOfMonth())
    val candidate = today.withDayOfMonth(day)
    if (!candidate.isAfter(today)) return candidate
    val previous = today.minusMonths(1)
    return previous.withDayOfMonth(salaryDay.coerceIn(1, previous.lengthOfMonth()))
}

fun monthStart(today: LocalDate = LocalDate.now()): LocalDate = YearMonth.from(today).atDay(1)

/**
 * «المتاح بأمان» = مجموع الأرصدة − الالتزامات القادمة − هدف الادخار.
 * لا ينزل تحت الصفر حتى لا تظهر أرقام سالبة مضللة.
 */
fun safeToSpend(totalBalance: Long, commitments: Long, savingsTarget: Long): Long =
    (totalBalance - commitments - savingsTarget).coerceAtLeast(0)

fun dailyAllowance(safe: Long, daysLeft: Int): Long = if (daysLeft > 0) safe / daysLeft else safe

/** نسبة ما تم صرفه من دورة الراتب الحالية (0.0 – 1.0). */
fun cycleSpendRatio(expenses: Long, income: Long): Float =
    if (income <= 0L) 0f else (expenses.toDouble() / income.toDouble()).toFloat().coerceIn(0f, 1f)
