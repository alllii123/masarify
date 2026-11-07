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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masarify.app.MainViewModel
import com.masarify.app.UiState
import com.masarify.app.data.UserSettings
import com.masarify.app.util.formatMoney
import com.masarify.app.util.moneyInput
import com.masarify.app.util.monthStart
import com.masarify.app.util.parseMoney
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionsScreen(state: UiState, contentPadding: PaddingValues, onSettings: () -> Unit, onDelete: (com.masarify.app.data.TransactionEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = state.transactions.filter {
        query.isBlank() || listOf(it.merchant, it.category, it.note).any { field -> field.contains(query, ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, contentPadding.calculateBottomPadding() + 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader("العمليات", "كل دخلك ومصروفك في مكان واحد", onSettings) }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث باسم المتجر أو التصنيف أو الملاحظة") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyState("لا توجد عمليات", if (query.isBlank()) "أضف أول عملية باستخدام زر +" else "لا توجد نتائج مطابقة للبحث") }
        } else {
            items(filtered, key = { it.id }) { transaction ->
                SectionCard { TransactionRow(transaction, state.accounts, onDelete = { onDelete(transaction) }) }
            }
        }
    }
}

@Composable
fun PlanningScreen(state: UiState, contentPadding: PaddingValues, onSettings: () -> Unit, viewModel: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val settings = state.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, contentPadding.calculateBottomPadding() + 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader("التخطيط", "خطة دورة الراتب والالتزامات والادخار", onSettings) }
        item {
            SectionCard {
                Text("خطة دورة الراتب", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                PlanLine("الراتب", formatMoney(settings.salaryAmount))
                PlanLine("يوم الراتب", "${settings.salaryDay}")
                PlanLine("هدف الادخار", formatMoney(settings.savingsTarget))
                PlanLine("الالتزامات القادمة", formatMoney(settings.upcomingCommitments))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("تعديل الخطة") }
            }
        }
        item {
            SectionCard {
                Text("كيف يُحسب المتاح بأمان؟", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("مجموع أرصدة الحسابات − الالتزامات القادمة − هدف الادخار. النتيجة تقديرية وتعتمد على اكتمال البيانات المسجلة.", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                Text("اقتراح محلي", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val suggestion = if (settings.upcomingCommitments == 0L) "أدخل مجموع الأقساط والفواتير القادمة حتى لا تظن أن كامل رصيدك متاح للصرف." else "راجع الخطة عند نزول الراتب أو تغيّر أحد الأقساط."
                Text(suggestion, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
    }
    if (showDialog) PlanDialog(settings, { showDialog = false }) { salary, day, savings, commitments ->
        viewModel.updatePlan(salary, day, savings, commitments)
        showDialog = false
    }
}

@Composable
fun PlanLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PlanDialog(settings: UserSettings, onDismiss: () -> Unit, onSave: (Long, Int, Long, Long) -> Unit) {
    var salary by remember { mutableStateOf(moneyInput(settings.salaryAmount)) }
    var day by remember { mutableStateOf(settings.salaryDay.toString()) }
    var savings by remember { mutableStateOf(moneyInput(settings.savingsTarget)) }
    var commitments by remember { mutableStateOf(moneyInput(settings.upcomingCommitments)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل خطة الراتب") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MoneyField("الراتب", salary) { salary = it }
                OutlinedTextField(value = day, onValueChange = { day = it.filter(Char::isDigit).take(2) }, label = { Text("يوم الراتب 1–31") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                MoneyField("هدف الادخار", savings) { savings = it }
                MoneyField("الالتزامات القادمة", commitments) { commitments = it }
            }
        },
        confirmButton = { TextButton(onClick = {
            onSave(parseMoney(salary) ?: 0, day.toIntOrNull()?.coerceIn(1, 31) ?: 27, parseMoney(savings) ?: 0, parseMoney(commitments) ?: 0)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, suffix = { Text("ر.س") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
}

@Composable
fun ReportsScreen(state: UiState, contentPadding: PaddingValues, onSettings: () -> Unit) {
    val start = monthStart().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val monthTransactions = state.transactions.filter { it.timestamp >= start }
    val income = monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val categories = monthTransactions.filter { it.type == "EXPENSE" }.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    val max = categories.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, contentPadding.calculateBottomPadding() + 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeader("التقارير", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar", "SA"))), onSettings) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallMetric("الدخل", formatMoney(income), Modifier.weight(1f))
                SmallMetric("المصروف", formatMoney(expense), Modifier.weight(1f))
                SmallMetric("الصافي", formatMoney(income - expense), Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                Text("المصروف حسب التصنيف", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (categories.isEmpty()) EmptyState("لا توجد مصاريف هذا الشهر", "ستظهر التصنيفات هنا تلقائيًا")
                categories.forEach { (category, amount) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, fontSize = 13.sp)
                            Text(formatMoney(amount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
                            Box(modifier = Modifier.fillMaxWidth((amount.toFloat() / max).coerceIn(.02f, 1f)).height(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                        }
                    }
                }
            }
        }
    }
}
