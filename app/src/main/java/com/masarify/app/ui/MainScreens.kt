package com.masarify.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.BarChart
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masarify.app.MainViewModel
import com.masarify.app.UiState
import com.masarify.app.data.UserSettings
import com.masarify.app.util.formatMoney
import com.masarify.app.util.moneyInput
import com.masarify.app.util.formatMonth
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
        contentPadding = PaddingValues(start = Space.page, end = Space.page, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 26.dp),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
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
        contentPadding = PaddingValues(start = Space.page, end = Space.page, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 26.dp),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
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
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, suffix = { Text(com.masarify.app.util.CURRENCY_LABEL) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
}

@Composable
fun ReportsScreen(state: UiState, contentPadding: PaddingValues, onSettings: () -> Unit) {
    val start = monthStart().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val monthTransactions = state.transactions.filter { it.timestamp >= start }
    val income = monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val categories = monthTransactions
        .filter { it.type == "EXPENSE" }
        .groupBy { it.category.ifBlank { "أخرى" } }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
    val max = categories.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val net = income - expense

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.page, end = Space.page, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 26.dp),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        item {
            PageHeader("التقارير", formatMonth(), onSettings)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("الدخل", formatMoney(income), Icons.Rounded.ArrowDownward, MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                MetricCard("المصروف", formatMoney(expense), Icons.Rounded.ArrowUpward, MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            }
        }

        item {
            MetricCard(
                title = "الصافي هذا الشهر",
                value = formatMoney(net),
                icon = if (net >= 0) Icons.Rounded.Savings else Icons.Rounded.ErrorOutline,
                tint = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                caption = if (income > 0) "من دخل ${formatMoney(income)}" else "لا يوجد دخل مسجل",
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            SectionCard {
                Text("توزيع المصروف", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("نسبة كل تصنيف من مصروف الشهر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (categories.isEmpty()) {
                    EmptyState("لا توجد مصاريف هذا الشهر", "أضف عمليات وسيظهر التوزيع هنا", Icons.Rounded.BarChart)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDonut(
                            slices = categories.take(6).map { it.second },
                            modifier = Modifier.size(150.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            categories.take(6).forEachIndexed { index, (category, amount) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(9.dp)
                                            .background(chartColor(index), CircleShape)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        category,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${if (expense > 0) (amount * 100 / expense) else 0}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (categories.isNotEmpty()) {
            item { SectionTitle("التفصيل حسب التصنيف") }
            items(categories.size) { index ->
                val (category, amount) = categories[index]
                SectionCard(padding = 16) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(chartColor(index).copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(categoryIcon(category), contentDescription = null, tint = chartColor(index), modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${if (expense > 0) (amount * 100 / expense) else 0}% من مصروف الشهر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatMoney(amount), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((amount.toFloat() / max).coerceIn(0.02f, 1f))
                                .height(7.dp)
                                .background(chartColor(index), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("ملاحظة على الأرقام", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "التقارير تحسب من العمليات المسجلة داخل التطبيق فقط، وتشمل الشهر الحالي ميلاديًا. لو اعتمدت على قراءة رسائل البنك، تأكد من تأكيد المسودات حتى تدخل في الحساب.",
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** ألوان ثابتة للرسم البياني، تعمل مع الثيم الأخضر والأزرق والوضع الليلي. */
private fun chartColor(index: Int): androidx.compose.ui.graphics.Color = listOf(
    androidx.compose.ui.graphics.Color(0xFF2E8B6F),
    androidx.compose.ui.graphics.Color(0xFF4A7FB5),
    androidx.compose.ui.graphics.Color(0xFFC8963E),
    androidx.compose.ui.graphics.Color(0xFF8E6BAF),
    androidx.compose.ui.graphics.Color(0xFFB5654A),
    androidx.compose.ui.graphics.Color(0xFF4E9A9A)
)[index % 6]

/** حلقة توزيع مرسومة يدويًا بدون مكتبات خارجية. */
@Composable
private fun CategoryDonut(slices: List<Long>, modifier: Modifier = Modifier) {
    val total = slices.sum().coerceAtLeast(1)
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.17f
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        var startAngle = -90f
        slices.forEachIndexed { index, value ->
            val sweep = (value.toFloat() / total.toFloat()) * 360f
            drawArc(
                color = chartColor(index),
                startAngle = startAngle,
                sweepAngle = (sweep - 2.2f).coerceAtLeast(0.8f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
            startAngle += sweep
        }
    }
}
