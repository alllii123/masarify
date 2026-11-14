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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masarify.app.MainViewModel
import com.masarify.app.UiState
import com.masarify.app.ai.ChatMessage
import com.masarify.app.ai.Insight
import com.masarify.app.ai.MoneySnapshot
import com.masarify.app.ai.localInsights
import com.masarify.app.data.AiMode
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AssistantScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: MainViewModel,
    snapshot: MoneySnapshot
) {
    val messages = state.chatMessages
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val settings = state.settings
    val insights = remember(snapshot) { localInsights(snapshot) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        input = ""
        viewModel.sendChat(clean, snapshot)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // رأس الصفحة
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "رجوع")
            }
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text("مساعد مصاريفي", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        settings.aiCloudReady -> "الوضع المحلي + السحابي"
                        else -> "وضع محلي • يشتغل بدون إنترنت"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (settings.aiMode == AiMode.LOCAL) Icons.Rounded.CloudOff else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        when (settings.aiMode) {
                            AiMode.LOCAL -> "محلي"
                            AiMode.AUTO -> "تلقائي"
                            AiMode.CLOUD -> "سحابي"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // مبدّل الوضع: يظهر دائمًا حتى يعرف المستخدم مين يجاوب
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.aiMode == mode,
                    onClick = { viewModel.setAiMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                AiMode.LOCAL -> "محلي"
                                AiMode.AUTO -> "تلقائي"
                                AiMode.CLOUD -> "سحابي"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        if (settings.aiMode == mode) {
                            Icon(
                                if (mode == AiMode.LOCAL) Icons.Rounded.CloudOff else Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.page, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item { AssistantIntro(snapshot) }
                if (insights.isNotEmpty()) {
                    item {
                        Text(
                            "ملاحظات جاهزة من أرقامك",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(insights) { insight -> InsightCard(insight) }
                }
                item { QuickQuestions() { send(it) } }
                if (settings.aiCloudReady) {
                    item {
                        com.masarify.app.ui.SpeedHint(settings.aiModel)
                    }
                }
            } else {
                items(messages.filter { it.text.isNotBlank() || it.error != null }, key = { it.id }) { message ->
                    ChatBubble(message)
                }
                if (state.aiThinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(9.dp))
                            Text("أفكر في أرقامك…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                if (!settings.aiCloudReady) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when {
                                settings.aiMode == AiMode.LOCAL -> "الوضع المحلي: يشتغل بدون إنترنت ولا يرسل شيء."
                                settings.aiMode == AiMode.CLOUD && !settings.aiCloudReady -> "اخترت السحابي لكن المفتاح غير مضاف — راح أجاوبك محليًا."
                                settings.aiMode == AiMode.AUTO && !settings.aiCloudReady -> "تلقائي: محلي الآن. أضف مفتاح Gemini للرد السحابي."
                                else -> "سحابي مفعّل — الأرقام ${if (settings.aiShareAmounts) "تُرسل" else "لا تُرسل"}."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = 12.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("اسألني: كم أقدر أصرف؟", fontSize = 13.sp) },
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) })
                    )
                    Spacer(Modifier.size(8.dp))
                    Surface(
                        onClick = { send(input) },
                        shape = CircleShape,
                        color = if (input.isBlank()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = "إرسال",
                                tint = if (input.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantIntro(snapshot: MoneySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "هلا 👋 أنا مساعدك المالي",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "أحلل عملياتك وأرصدة حساباتك على جهازك مباشرة. ما أرسل شي لأي جهة إلا إذا فعّلت الوضع السحابي بمفتاحك.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("المتاح", com.masarify.app.util.formatMoney(snapshot.safe), Modifier.weight(1f))
                MiniStat("يوميًا", com.masarify.app.util.formatMoney(snapshot.dailyAllowance), Modifier.weight(1f))
                MiniStat("للراتب", "${snapshot.daysLeft} يوم", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val tint = when (insight.tone) {
        Insight.Tone.GOOD -> MaterialTheme.colorScheme.primary
        Insight.Tone.WARN -> MaterialTheme.colorScheme.tertiary
        Insight.Tone.NEUTRAL -> MaterialTheme.colorScheme.secondary
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).background(tint, CircleShape))
            Spacer(Modifier.size(11.dp))
            Column {
                Text(insight.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(insight.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun QuickQuestions(onPick: (String) -> Unit) {
    val questions = listOf(
        "كم باقي لي أقدر أصرف؟",
        "وين أصرف أكثر؟",
        "كم أقدر أوفر هذي الدورة؟",
        "توقع مصروفي نهاية الشهر"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("أسئلة سريعة", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        questions.forEach { question ->
            Surface(
                onClick = { onPick(question) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    question,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Surface(
                shape = if (isUser) {
                    RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                } else {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                },
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 21.sp
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(message.time.format(DateTimeFormatter.ofPattern("h:mm a", Locale("ar", "QA"))))
                    if (!isUser && message.source != null) {
                        append(" • ")
                        append(if (message.source == "cloud") "سحابي" else "محلي")
                    }
                    message.elapsedMs?.let { append(" • ${it / 1000}.${(it % 1000) / 100} ث") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isUser && message.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    message.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** تلميح صغير يوضح الموديل الحالي وسرعته المتوقعة. */
@Composable
fun SpeedHint(model: String) {
    val fast = model.contains("lite")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fast) "⚡" else "🧠",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (fast) "الموديل «$model» سريع جدًا — مناسب للمحادثة اليومية."
                else "الموديل «$model» أدق لكنه أبطأ. لو الرد يطول، بدّله إلى gemini-2.5-flash-lite من الإعدادات.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
