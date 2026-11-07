package com.masarify.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.masarify.app.MainViewModel
import com.masarify.app.UiState
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
    LaunchedEffect(state.settings.smsSenders) { senders = state.settings.smsSenders }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.setSmsEnabled(result[Manifest.permission.RECEIVE_SMS] == true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowForward, contentDescription = "رجوع") }
                Column {
                    Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("المظهر والحسابات والخصوصية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard {
                Text("لون التطبيق", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.settings.themeColor == ThemeColor.GREEN, onClick = { viewModel.setThemeColor(ThemeColor.GREEN) }, label = { Text("الأخضر") })
                    FilterChip(selected = state.settings.themeColor == ThemeColor.BLUE, onClick = { viewModel.setThemeColor(ThemeColor.BLUE) }, label = { Text("الأزرق") })
                }
                Spacer(Modifier.height(18.dp))
                Text("وضع العرض", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = state.settings.themeMode == mode, onClick = { viewModel.setThemeMode(mode) }, label = {
                            Text(when (mode) { ThemeMode.SYSTEM -> "حسب النظام"; ThemeMode.LIGHT -> "فاتح"; ThemeMode.DARK -> "ليلي" })
                        })
                    }
                }
            }
        }
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الحسابات", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddAccount = true }) { Text("+ حساب") }
                }
                state.accounts.forEach { account ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            Text(formatMoney(account.currentBalance), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { balanceAccount = account }) { Text("ضبط الرصيد") }
                    }
                }
            }
        }
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("خطة الراتب", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showPlan = true }) { Text("تعديل") }
                }
                PlanLine("الراتب", formatMoney(state.settings.salaryAmount))
                PlanLine("هدف الادخار", formatMoney(state.settings.savingsTarget))
                PlanLine("الالتزامات", formatMoney(state.settings.upcomingCommitments))
            }
        }
        item {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("قراءة رسائل البنك الجديدة", fontWeight = FontWeight.Bold)
                        Text("اختياري • التحليل محلي على الجهاز", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("لا يطلب التطبيق قراءة الرسائل القديمة. يستخرج العملية ولا يحفظ نص الرسالة، وتبقى المراجعة قبل الحفظ.", fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.settings.smsEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = senders, onValueChange = { senders = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المرسلون المعتمدون (اختياري)") }, placeholder = { Text("مثال: Bank1, Bank2") })
                    Text("اترك الحقل فارغًا لتحليل الرسائل التي تحتوي كلمات مالية فقط.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { viewModel.setSmsSenders(senders) }) { Text("حفظ المرسلين") }
                }
            }
        }
        if (state.smsDrafts.isNotEmpty()) {
            item {
                SectionCard {
                    Text("مسودات رسائل البنك", fontWeight = FontWeight.Bold)
                    Text("لم تُحفظ في حساباتك حتى تؤكدها", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.smsDrafts.forEach { draft ->
                        Column(modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(draft.merchant, fontWeight = FontWeight.SemiBold)
                            Text("${formatMoney(draft.amount)} • ${draft.category} • ${formatDate(draft.timestamp)}", fontSize = 11.sp)
                            Row {
                                TextButton(onClick = { state.accounts.firstOrNull()?.let { viewModel.confirmDraft(draft, it.id) } }, enabled = state.accounts.isNotEmpty()) { Text("تأكيد على الحساب الأول") }
                                TextButton(onClick = { viewModel.dismissDraft(draft) }) { Text("تجاهل") }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard {
                Text("Google Drive", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("لم يُرفع أي شيء إلى Drive في نسخة الاختبار هذه. الربط يحتاج إعداد OAuth في Google Cloud، وقاعدة البيانات حاليًا محلية فقط.", fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("إضافة حساب") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الحساب") }, singleLine = true)
            MoneyField("الرصيد الحالي", balance) { balance = it }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, parseMoney(balance) ?: 0) }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun BalanceDialog(name: String, current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var balance by remember { mutableStateOf(moneyInput(current)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("ضبط رصيد $name") },
        text = { MoneyField("الرصيد الحالي", balance) { balance = it } },
        confirmButton = { TextButton(onClick = { onSave(parseMoney(balance) ?: current) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
