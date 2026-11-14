package com.masarify.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.masarify.app.MainViewModel
import com.masarify.app.UiState
import com.masarify.app.data.AiStyle
import com.masarify.app.data.ThemeColor
import com.masarify.app.data.ThemeMode
import com.masarify.app.util.formatDate
import com.masarify.app.util.formatMoney
import com.masarify.app.util.moneyInput
import com.masarify.app.util.parseMoney

@Composable
fun SettingsScreen(state: UiState, contentPadding: PaddingValues, onBack: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    var showPlan by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var balanceAccount by remember { mutableStateOf<com.masarify.app.data.AccountEntity?>(null) }
    var senders by remember { mutableStateOf(state.settings.smsSenders) }
    var apiKey by remember { mutableStateOf(state.settings.aiApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    LaunchedEffect(state.settings.smsSenders) { senders = state.settings.smsSenders }
    LaunchedEffect(state.settings.aiApiKey) { if (apiKey.isEmpty()) apiKey = state.settings.aiApiKey }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.setSmsEnabled(result[Manifest.permission.RECEIVE_SMS] == true)
    }

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "رجوع") }
                Column {
                    Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
                    Text("المظهر والذكاء الاصطناعي والحسابات والخصوصية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── المظهر ─────────────────────────────────────────────────────────
        item {
            SectionCard {
                Text("المظهر", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeColorSwatch("الأخضر", ThemeColor.GREEN, state.settings.themeColor == ThemeColor.GREEN) {
                        viewModel.setThemeColor(ThemeColor.GREEN)
                    }
                    ThemeColorSwatch("الأزرق", ThemeColor.BLUE, state.settings.themeColor == ThemeColor.BLUE) {
                        viewModel.setThemeColor(ThemeColor.BLUE)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("وضع العرض", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = state.settings.themeMode == mode, onClick = { viewModel.setThemeMode(mode) }, label = {
                            Text(when (mode) { ThemeMode.SYSTEM -> "حسب النظام"; ThemeMode.LIGHT -> "فاتح"; ThemeMode.DARK -> "ليلي" })
                        })
                    }
                }
            }
        }

        // ── الذكاء الاصطناعي ───────────────────────────────────────────────
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("مساعد مصاريفي", style = MaterialTheme.typography.titleMedium)
                        Text("ذكاء محلي + مساعد سحابي اختياري", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = "التحليل المحلي",
                    subtitle = "يعمل على جهازك بدون إنترنت وبدون أي مفتاح",
                    checked = state.settings.aiLocalEnabled
                ) { viewModel.setAiLocalEnabled(it) }
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "المساعد السحابي (Gemini)",
                    subtitle = "اختياري • يحتاج مفتاحك الخاص وتفعيلك",
                    checked = state.settings.aiCloudEnabled
                ) { enabled ->
                    viewModel.setAiCloudEnabled(enabled)
                    showAiSettings = enabled
                }

                AnimatedVisibility(visible = showAiSettings || state.settings.aiCloudEnabled) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("مفتاح Gemini API") },
                            placeholder = { Text("AIza…") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "إظهار")
                                }
                            },
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "المفتاح يُخزَّن على جهازك فقط ولا يُكتب في أي سجل. لا تشارك المفتاح مع أحد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.settings.aiModel,
                            onValueChange = { viewModel.setAiModel(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("الموديل") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("أسلوب الرد", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AiStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = state.settings.aiStyle == style,
                                    onClick = { viewModel.setAiStyle(style) },
                                    label = {
                                        Text(when (style) {
                                            AiStyle.SIMPLE -> "بسيط"
                                            AiStyle.FRIENDLY -> "ودّي"
                                            AiStyle.ANALYST -> "تحليلي"
                                        })
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        ToggleRow(
                            title = "أشارك الأرقام مع المساعد السحابي",
                            subtitle = "لو مقفول، يُرسل عدد العمليات فقط بدون مبالغ",
                            checked = state.settings.aiShareAmounts
                        ) { viewModel.setAiShareAmounts(it) }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.setAiApiKey(apiKey) }) { Text("حفظ المفتاح") }
                            TextButton(onClick = { viewModel.testAiKey() }) { Text("اختبار الاتصال") }
                            TextButton(onClick = { viewModel.clearChat() }) { Text("تفريغ المحادثة") }
                        }
                        if (state.keyStatus.isNotBlank()) {
                            Text(
                                state.keyStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.keyStatus.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "بياناتك المالية تبقى على جهازك. الوضع المحلي لا يرسل شي، والسحابي يشتغل فقط بتفعيلك.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── الحسابات ───────────────────────────────────────────────────────
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الحسابات", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAddAccount = true }) { Text("+ حساب") }
                }
                state.accounts.forEach { account ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(account.name.take(1), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.titleSmall)
                            Text(formatMoney(account.currentBalance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { balanceAccount = account }) { Text("ضبط") }
                    }
                }
            }
        }

        // ── الخطة ──────────────────────────────────────────────────────────
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("خطة الراتب", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showPlan = true }) { Text("تعديل") }
                }
                PlanLine("الراتب", formatMoney(state.settings.salaryAmount))
                PlanLine("يوم الراتب", "${state.settings.salaryDay}")
                PlanLine("هدف الادخار", formatMoney(state.settings.savingsTarget))
                PlanLine("الالتزامات", formatMoney(state.settings.upcomingCommitments))
            }
        }

        // ── رسائل البنك ────────────────────────────────────────────────────
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("قراءة رسائل البنك الجديدة", style = MaterialTheme.typography.titleMedium)
                        Text("اختياري • التحليل محلي على الجهاز", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.settings.smsEnabled, onCheckedChange = { enabled ->
                        if (!enabled) viewModel.setSmsEnabled(false)
                        else {
                            val permissions = buildList {
                                add(Manifest.permission.RECEIVE_SMS)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                            if (granted) viewModel.setSmsEnabled(true) else permissionLauncher.launch(permissions.toTypedArray())
                        }
                    })
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "لا يطلب التطبيق قراءة الرسائل القديمة. يستخرج العملية ولا يحفظ نص الرسالة، وتبقى المراجعة قبل الحفظ.",
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.settings.smsEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = senders,
                        onValueChange = { senders = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("المرسلون المعتمدون (اختياري)") },
                        placeholder = { Text("مثال: QNB, CBQ, DohaBank") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        "اتركه فارغًا لتحليل الرسائل التي تحتوي كلمات مالية فقط.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.setSmsSenders(senders) }) { Text("حفظ المرسلين") }
                }
            }
        }

        if (state.smsDrafts.isNotEmpty()) {
            item {
                SectionCard {
                    Text("مسودات رسائل البنك", style = MaterialTheme.typography.titleMedium)
                    Text("لم تُحفظ في حساباتك حتى تؤكدها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.smsDrafts.forEach { draft ->
                        Column(modifier = Modifier.padding(vertical = 9.dp)) {
                            Text(draft.merchant, style = MaterialTheme.typography.titleSmall)
                            Text("${formatMoney(draft.amount)} • ${draft.category} • ${formatDate(draft.timestamp)}", style = MaterialTheme.typography.labelSmall)
                            Row {
                                TextButton(
                                    onClick = { state.accounts.firstOrNull()?.let { viewModel.confirmDraft(draft, it.id) } },
                                    enabled = state.accounts.isNotEmpty()
                                ) { Text("تأكيد") }
                                TextButton(onClick = { viewModel.dismissDraft(draft) }) { Text("تجاهل") }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "لم يُرفع أي شيء إلى Google Drive في هذه النسخة. الربط يحتاج إعداد OAuth في Google Cloud، وقاعدة البيانات حاليًا محلية فقط.",
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                "مصاريفي • الإصدار 0.2.0 • العملة: الريال القطري (ر.ق)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showPlan) PlanDialog(state.settings, { showPlan = false }) { salary, day, savings, commitments ->
        viewModel.updatePlan(salary, day, savings, commitments)
        showPlan = false
    }
    if (showAddAccount) AddAccountDialog({ showAddAccount = false }) { name, balance ->
        viewModel.addAccount(name, balance)
        showAddAccount = false
    }
    balanceAccount?.let { account ->
        BalanceDialog(account.name, account.currentBalance, { balanceAccount = null }) { balance ->
            viewModel.setBalance(account.id, balance)
            balanceAccount = null
        }
    }
}

@Composable
private fun ThemeColorSwatch(label: String, value: ThemeColor, selected: Boolean, onClick: () -> Unit) {
    val color = if (value == ThemeColor.GREEN) androidx.compose.ui.graphics.Color(0xFF0E6B52) else androidx.compose.ui.graphics.Color(0xFF124A86)
    Row(
        modifier = Modifier
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = {
            Box(Modifier.size(14.dp).background(color, CircleShape))
        })
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الحساب") }, singleLine = true)
                MoneyField("الرصيد الحالي", balance) { balance = it }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, parseMoney(balance) ?: 0) }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun BalanceDialog(name: String, current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var balance by remember { mutableStateOf(moneyInput(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضبط رصيد $name") },
        text = { MoneyField("الرصيد الحالي", balance) { balance = it } },
        confirmButton = { TextButton(onClick = { onSave(parseMoney(balance) ?: current) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
