package com.masarify.app.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masarify.app.UiState
import com.masarify.app.ai.Insight
import com.masarify.app.ai.MoneySnapshot
import com.masarify.app.ai.localInsights
import com.masarify.app.ai.snapshotFrom
import com.masarify.app.util.cycleSpendRatio
import com.masarify.app.util.dailyAllowance
import com.masarify.app.util.daysUntilSalary
import com.masarify.app.util.formatMoney
import com.masarify.app.util.safeToSpend
import com.masarify.app.util.salaryCycleStart
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** يجمع أرقام الدورة الحالية في مكان واحد، ويستخدمه المساعد أيضًا. */
fun snapshotOf(state: UiState): MoneySnapshot {
    val balance = state.accounts.sumOf { it.currentBalance }
    val settings = state.settings
    val days = daysUntilSalary(settings.salaryDay)
    val cycleStartDate = salaryCycleStart(settings.salaryDay)
    val cycleStart = cycleStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val cycleTransactions = state.transactions.filter { it.timestamp >= cycleStart }
    val expense = cycleTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val income = cycleTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val records = cycleTransactions.map { Triple(it.merchant, it.category, if (it.type == "EXPENSE") -it.amount else it.amount) }
    return snapshotFrom(
        totalBalance = balance,
        commitments = settings.upcomingCommitments,
        savingsTarget = settings.savingsTarget,
        salaryDay = settings.salaryDay,
        daysLeft = days,
        cycleStart = cycleStart,
        cycleTransactions = records,
        cycleExpenses = expense,
        cycleIncome = income,
        today = LocalDate.now()
    )
}

@Composable
fun HomeScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onSettings: () -> Unit,
    onTransactions: () -> Unit,
    onPlanning: () -> Unit,
    onAssistant: () -> Unit,
    onAdd: () -> Unit
) {
    val snapshot = snapshotOf(state)
    val settings = state.settings
    val days = snapshot.daysLeft
    val daily = dailyAllowance(snapshot.safe, days)
    val insights = localInsights(snapshot)
    val ratio by animateFloatAsState(
        targetValue = cycleSpendRatio(snapshot.cycleExpense, snapshot.cycleIncome),
        label = "ratio"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.page,
            end = Space.page,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 26.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        item {
            PageHeader(
                title = "أهلًا فيك 👋",
                subtitle = "ملخص مالك لهذي الدورة",
                onSettings = onSettings
            )
        }

        item {
            SafeCard(
                safe = snapshot.safe,
                total = snapshot.totalBalance,
                daily = daily,
                days = days,
                ratio = ratio,
                expense = snapshot.cycleExpense,
                income = snapshot.cycleIncome
            )
        }

        item {
            // صف الأرقام الثلاثة التي يسأل عنها المستخدم دائمًا
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SumChip(
                    label = "مجموعك",
                    value = formatMoney(snapshot.totalBalance),
                    highlighted = true,
                    modifier = Modifier.weight(1f)
                )
                SumChip(
                    label = "المتاح بأمان",
                    value = formatMoney(snapshot.safe),
                    modifier = Modifier.weight(1f)
                )
                SumChip(
                    label = "يوميًا",
                    value = formatMoney(daily),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "مصروف الدورة",
                    value = formatMoney(snapshot.cycleExpense),
                    icon = Icons.Rounded.ArrowUpward,
                    tint = MaterialTheme.colorScheme.error,
                    caption = if (snapshot.cycleIncome > 0) "من ${formatMoney(snapshot.cycleIncome)}" else "دون دخل مسجل",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "الالتزامات القادمة",
                    value = formatMoney(settings.upcomingCommitments),
                    icon = Icons.Rounded.ArrowDownward,
                    tint = MaterialTheme.colorScheme.tertiary,
                    caption = "محجوزة من رصيدك",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            AssistantCard(insights = insights, onAssistant = onAssistant)
        }

        item {
            SectionTitle("خطتك", "تعديل", onPlanning)
        }
        item {
            SectionCard {
                PlanProgressRow(
                    label = "هدف الادخار",
                    value = formatMoney(settings.savingsTarget),
                    icon = Icons.Rounded.Savings
                )
                Spacer(Modifier.height(10.dp))
                PlanProgressRow(
                    label = "الالتزامات المحجوزة",
                    value = formatMoney(settings.upcomingCommitments),
                    icon = Icons.Rounded.ArrowDownward
                )
                if (settings.salaryAmount > 0) {
                    Spacer(Modifier.height(10.dp))
                    PlanProgressRow(
                        label = "الراتب المسجل",
                        value = formatMoney(settings.salaryAmount),
                        icon = Icons.Rounded.CheckCircle
                    )
                }
            }
        }

        item {
            SectionTitle("آخر العمليات", "عرض الكل", onTransactions)
        }
        item {
            SectionCard(padding = 12) {
                val recent = state.transactions.take(4)
                if (recent.isEmpty()) {
                    EmptyState(
                        title = "ما فيه عمليات بعد",
                        subtitle = "اضغط زر + تحت وسجّل أول مصروف أو راتب"
                    )
                } else {
                    recent.forEachIndexed { index, transaction ->
                        TransactionRow(transaction, state.accounts)
                        if (index < recent.lastIndex) {
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }

        if (state.transactions.isEmpty()) {
            item {
                Surface(
                    onClick = onAdd,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("سجّل أول عملية", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                "مصروف أو راتب — دقيقة وحدة وتكون جاهزة",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeCard(
    safe: Long,
    total: Long,
    daily: Long,
    days: Int,
    ratio: Float,
    expense: Long,
    income: Long
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary,
                        scheme.primary.copy(alpha = 0.92f),
                        scheme.primary.copy(alpha = 0.88f)
                    )
                )
            )
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "المتاح بأمان",
                    color = scheme.onPrimary.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        if (days == 0) "الراتب اليوم" else "باقي $days يوم",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                formatMoney(safe),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "بعد حجز الالتزامات وهدف الادخار",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "مجموعك في كل الحسابات",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    formatMoney(total),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("تقدر تصرف يوميًا", formatMoney(daily), Modifier.weight(1f))
                StatPill("مصروف الدورة", formatMoney(expense), Modifier.weight(1f))
            }
            if (income > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "صرفت ${(ratio * 100).toInt()}% من دخل الدورة",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** شريحة رقم صغيرة موحّدة الشكل لصف الأرقام الرئيسية. */
@Composable
private fun SumChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                if (highlighted) scheme.primaryContainer else scheme.surfaceContainerHigh
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlanProgressRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AssistantCard(insights: List<Insight>, onAssistant: () -> Unit) {
    Surface(
        onClick = onAssistant,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("مساعد مصاريفي", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "تحليل على جهازك • بدون إنترنت",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(12.dp))
            val first = insights.firstOrNull()
            if (first != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(7.dp)
                            .background(
                                when (first.tone) {
                                    Insight.Tone.GOOD -> MaterialTheme.colorScheme.primary
                                    Insight.Tone.WARN -> MaterialTheme.colorScheme.tertiary
                                    Insight.Tone.NEUTRAL -> MaterialTheme.colorScheme.secondary
                                },
                                CircleShape
                            )
                    )
                    Spacer(Modifier.size(9.dp))
                    Column {
                        Text(
                            first.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            first.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    "اسألني أي شي عن مالك، وأنا أحلّلك أرقامك على جهازك.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "افتح المحادثة",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.size(4.dp))
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                Spacer(Modifier.weight(1f))
                if (insights.size > 1) {
                    Text(
                        "+${insights.size - 1} ملاحظات",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
