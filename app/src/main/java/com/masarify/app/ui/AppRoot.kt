package com.masarify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarify.app.MainViewModel

enum class AppTab { HOME, TRANSACTIONS, ASSISTANT, PLANNING, REPORTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: MainViewModel, initialOpenAdd: Boolean = false, initialOpenSms: Boolean = false) {
    val state by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    var showAdd by rememberSaveable { mutableStateOf(initialOpenAdd) }
    var showSettings by rememberSaveable { mutableStateOf(initialOpenSms) }
    val currentTab = AppTab.valueOf(tab)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialOpenAdd) { if (initialOpenAdd) showAdd = true }
    LaunchedEffect(initialOpenSms) { if (initialOpenSms) showSettings = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showSettings) AppBottomBar(
                selected = currentTab,
                onSelect = { tab = it.name },
                onAdd = { showAdd = true }
            )
        }
    ) { padding ->
        when {
            showSettings -> SettingsScreen(state, padding, { showSettings = false }, viewModel)
            currentTab == AppTab.HOME -> HomeScreen(
                state = state,
                contentPadding = padding,
                onSettings = { showSettings = true },
                onTransactions = { tab = AppTab.TRANSACTIONS.name },
                onPlanning = { tab = AppTab.PLANNING.name },
                onAssistant = { tab = AppTab.ASSISTANT.name },
                onAdd = { showAdd = true }
            )
            currentTab == AppTab.TRANSACTIONS -> TransactionsScreen(state, padding, { showSettings = true }, viewModel::deleteTransaction)
            currentTab == AppTab.ASSISTANT -> AssistantScreen(
                state = state,
                contentPadding = padding,
                onBack = { tab = AppTab.HOME.name },
                viewModel = viewModel,
                snapshot = snapshotOf(state)
            )
            currentTab == AppTab.PLANNING -> PlanningScreen(state, padding, { showSettings = true }, viewModel)
            currentTab == AppTab.REPORTS -> ReportsScreen(state, padding, { showSettings = true })
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddTransactionSheet(state.accounts, { showAdd = false }) { type, amount, accountId, toAccountId, category, merchant, note ->
                viewModel.addTransaction(type, amount, accountId, toAccountId, category, merchant, note)
            }
        }
    }
}

@Composable
private fun AppBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit, onAdd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BarItem("الرئيسية", Icons.Rounded.Home, selected == AppTab.HOME) { onSelect(AppTab.HOME) }
                BarItem("العمليات", Icons.Rounded.ReceiptLong, selected == AppTab.TRANSACTIONS) { onSelect(AppTab.TRANSACTIONS) }
                AddButton(onAdd)
                BarItem("المساعد", Icons.Rounded.AutoAwesome, selected == AppTab.ASSISTANT) { onSelect(AppTab.ASSISTANT) }
                BarItem("التقارير", Icons.Rounded.BarChart, selected == AppTab.REPORTS) { onSelect(AppTab.REPORTS) }
            }
        }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = "إضافة", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
    }
}
