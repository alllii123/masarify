package com.masarify.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.masarify.app.data.AccountEntity
import com.masarify.app.util.parseMoney

@Composable
fun AddTransactionSheet(accounts: List<AccountEntity>, onDismiss: () -> Unit, onSave: (String, Long, Long, Long?, String, String, String) -> Unit) {
    var type by remember { mutableStateOf("EXPENSE") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("بقالة") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAccountId by remember(accounts) { mutableStateOf(accounts.drop(1).firstOrNull()?.id) }
    var error by remember { mutableStateOf("") }
    val expenseCategories = listOf("بقالة", "مطاعم", "مواصلات", "تسوق", "منزل", "صحة", "ترفيه", "أخرى")
    val incomeCategories = listOf("راتب", "دخل إضافي", "تحويل وارد", "أخرى")

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.page, vertical = 8.dp)) {
        Text("إضافة عملية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("تسجيل سريع، وتُحفظ البيانات على جهازك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EXPENSE" to "مصروف", "INCOME" to "دخل", "TRANSFER" to "تحويل").forEach { (value, label) ->
                FilterChip(selected = type == value, onClick = {
                    type = value
                    category = if (value == "INCOME") "راتب" else if (value == "EXPENSE") "بقالة" else "تحويل"
                }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("المبلغ") }, suffix = { Text(com.masarify.app.util.CURRENCY_LABEL) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineMedium
        )
        if (type != "TRANSFER") {
            Spacer(Modifier.height(16.dp))
            Text("التصنيف", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val categories = if (type == "INCOME") incomeCategories else expenseCategories
            categories.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item, fontSize = 11.sp) }) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        AccountSelector("من حساب", accounts, accountId) { accountId = it }
        if (type == "TRANSFER") {
            Spacer(Modifier.height(8.dp))
            AccountSelector("إلى حساب", accounts.filter { it.id != accountId }, toAccountId) { toAccountId = it }
        }
        if (type != "TRANSFER") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المتجر أو الجهة (اختياري)") }, singleLine = true)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظة (اختيارية)") }, singleLine = true)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val value = parseMoney(amount)
            when {
                value == null || value <= 0 -> error = "أدخل مبلغًا صحيحًا"
                accountId == null -> error = "أضف حسابًا أولًا من الإعدادات"
                type == "TRANSFER" && toAccountId == null -> error = "التحويل يحتاج حسابًا آخر"
                else -> {
                    onSave(type, value, accountId!!, if (type == "TRANSFER") toAccountId else null, category, merchant, note)
                    onDismiss()
                }
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("حفظ العملية") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccountSelector(label: String, accounts: List<AccountEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.name ?: "اختر الحساب"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text(account.name) }, onClick = {
                    onSelect(account.id)
                    expanded = false
                })
            }
        }
    }
}
