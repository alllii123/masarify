package com.masarify.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.masarify.app.MainViewModel

private enum class AppTab { HOME, TRANSACTIONS, PLANNING, REPORTS }

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
            currentTab == AppTab.HOME -> HomeScreen(state, padding, { showSettings = true }, { tab = AppTab.TRANSACTIONS.name }, { tab = AppTab.PLANNING.name })
            currentTab == AppTab.TRANSACTIONS -> TransactionsScreen(state, padding, { showSettings = true }, viewModel::deleteTransaction)
            currentTab == AppTab.PLANNING -> PlanningScreen(state, padding, { showSettings = true }, viewModel)
            currentTab == AppTab.REPORTS -> ReportsScreen(state, padding, { showSettings = true })
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, sheetState = sheetState) {
            AddTransactionSheet(state.accounts, { showAdd = false }) { type, amount, accountId, toAccountId, category, merchant, note ->
                viewModel.addTransaction(type, amount, accountId, toAccountId, category, merchant, note)
            }
        }
    }
}

@Composable
private fun AppBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit, onAdd: () -> Unit) {
    NavigationBar {
        NavigationBarItem(selected = selected == AppTab.HOME, onClick = { onSelect(AppTab.HOME) }, icon = { Icon(Icons.Rounded.Home, "الرئيسية") }, label = { Text("الرئيسية") })
        NavigationBarItem(selected = selected == AppTab.TRANSACTIONS, onClick = { onSelect(AppTab.TRANSACTIONS) }, icon = { Icon(Icons.Rounded.ReceiptLong, "العمليات") }, label = { Text("العمليات") })
        NavigationBarItem(selected = false, onClick = onAdd, icon = { Icon(Icons.Rounded.AddCircleOutline, "إضافة") }, label = { Text("إضافة") })
        NavigationBarItem(selected = selected == AppTab.PLANNING, onClick = { onSelect(AppTab.PLANNING) }, icon = { Icon(Icons.Rounded.TrackChanges, "التخطيط") }, label = { Text("التخطيط") })
        NavigationBarItem(selected = selected == AppTab.REPORTS, onClick = { onSelect(AppTab.REPORTS) }, icon = { Icon(Icons.Rounded.BarChart, "التقارير") }, label = { Text("التقارير") })
    }
}
