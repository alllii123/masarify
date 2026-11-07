package com.masarify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masarify.app.UiState
import com.masarify.app.util.daysUntilSalary
import com.masarify.app.util.formatMoney
import com.masarify.app.util.salaryCycleStart
import java.time.ZoneId

@Composable
fun HomeScreen(state: UiState, contentPadding: PaddingValues, onSettings: () -> Unit, onTransactions: () -> Unit, onPlanning: () -> Unit) {
    val balance = state.accounts.sumOf { it.currentBalance }
    val settings = state.settings
    val safe = (balance - settings.upcomingCommitments - settings.savingsTarget).coerceAtLeast(0)
    val days = daysUntilSalary(settings.salaryDay)
    val daily = if (days > 0) safe / days else safe
    val cycleStart = salaryCycleStart(settings.salaryDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val cycleTransactions = state.transactions.filter { it.timestamp >= cycleStart }
    val expense = cycleTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val income = cycleTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = contentPadding.calculateBottomPadding() + 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader("مصاريفي", "ملخصك المالي", onSettings) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("دورة الراتب الحالية", fontWeight = FontWeight.SemiBold)
                Text(if (days == 0) "الراتب اليوم" else "باقي $days يومًا", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Column {
                    Text("المتاح بأمان", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(formatMoney(safe), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    Text("بعد حماية الالتزامات والادخار", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f), fontSize = 11.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("معدل مريح لليوم\n${formatMoney(daily)}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("الرصيد الكلي\n${formatMoney(balance)}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallMetric("مصروف الفترة", formatMoney(expense), Modifier.weight(1f))
                SmallMetric("التزامات قادمة", formatMoney(settings.upcomingCommitments), Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                Text("ملاحظة محلية", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                val note = when {
                    state.transactions.isEmpty() -> "ابدأ بضبط الرصيد من الإعدادات ثم أضف أول عملية."
                    expense > income && income > 0 -> "مصروف دورة الراتب أعلى من الدخل المسجل فيها. راجع التفاصيل في التقارير."
                    settings.upcomingCommitments == 0L -> "أضف التزاماتك من صفحة التخطيط ليصبح رقم المتاح بأمان أدق."
                    else -> "بياناتك محدثة، ورقم المتاح بأمان محسوب من المعلومات المسجلة."
                }
                Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
            }
        }
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("التخطيط للفترة", fontWeight = FontWeight.Bold)
                    TextButton(onClick = onPlanning) { Text("تعديل الخطة") }
                }
                Text("هدف الادخار: ${formatMoney(settings.savingsTarget)}", fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Text("الالتزامات: ${formatMoney(settings.upcomingCommitments)}", fontSize = 12.sp)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("آخر العمليات", fontWeight = FontWeight.Bold)
                TextButton(onClick = onTransactions) { Text("عرض الكل") }
            }
        }
        item {
            SectionCard {
                val recent = state.transactions.take(3)
                if (recent.isEmpty()) EmptyState("لا توجد عمليات بعد", "استخدم زر + في الشريط السفلي")
                recent.forEachIndexed { index, transaction ->
                    TransactionRow(transaction, state.accounts)
                    if (index < recent.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
