package com.masarify.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.masarify.app.MainActivity
import com.masarify.app.MasarifyApp
import com.masarify.app.data.ThemeColor
import com.masarify.app.util.daysUntilSalary
import com.masarify.app.util.formatMoney
import kotlinx.coroutines.flow.first

class MasarifyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MasarifyWidget()
}

class MasarifyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MasarifyApp
        val accounts = app.repository.accounts.first()
        val settings = app.settings.settings.first()
        val balance = accounts.sumOf { it.currentBalance }
        val safe = (balance - settings.upcomingCommitments - settings.savingsTarget).coerceAtLeast(0)
        val days = daysUntilSalary(settings.salaryDay)
        val green = settings.themeColor == ThemeColor.GREEN
        val background = if (green) Color(0xFFE5F2EB) else Color(0xFFE5EDF8)
        val primary = if (green) Color(0xFF135F4C) else Color(0xFF183F70)
        provideContent {
            WidgetContent(
                safe = safe,
                days = days,
                background = background,
                primary = primary,
                onClick = actionStartActivity<MainActivity>()
            )
        }
    }
}

@Composable
private fun WidgetContent(safe: Long, days: Int, background: Color, primary: Color, onClick: androidx.glance.action.Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(background).padding(16.dp).clickable(onClick),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text("مصاريفي • المتاح بأمان", style = TextStyle(color = ColorProvider(primary), fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        Text(formatMoney(safe), style = TextStyle(color = ColorProvider(Color(0xFF17231F)), fontSize = 25.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Text("باقي $days يومًا للراتب", style = TextStyle(color = ColorProvider(Color(0xFF61726A)), fontSize = 12.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text("+ إضافة", style = TextStyle(color = ColorProvider(primary), fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}
