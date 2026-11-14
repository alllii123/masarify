package com.masarify.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * شاشة الترحيب.
 *
 * سبب وجودها: المستخدم طلب أن يفتح التطبيق مثل التطبيقات الاحترافية — ترحيب
 * ثم تحميل ثم الدخول، لا قفزة مباشرة إلى الشاشة الأولى.
 *
 * التصميم: الشعار ينبض بنعومة، الاسم يظهر تدريجيًا، ثم شريط تقدّم قصير.
 * المدة الكلية ~1.6 ثانية — طويلة بما يكفي لتُرى، وقصيرة بما لا يُملّ.
 */
@Composable
fun SplashScreen(
    greeting: String,
    subtitle: String,
    onFinished: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    // دخول الشعار: نمو + ظهور
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }

    // نبضة مستمرة خفيفة تحت الشعار
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(320))
        logoScale.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, tween(380))
        progress.animateTo(1f, tween(900, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.primary.copy(alpha = 0.10f),
                        scheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
        ) {
            // ── الشعار ──────────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // هالة خلفية نابضة
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(pulse)
                        .alpha(logoAlpha.value * 0.25f)
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.primary,
                    modifier = Modifier
                        .size(88.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // ── الاسم والترحيب ─────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    "محفظتي",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── شريط التحميل أسفل الشاشة ──────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 64.dp, start = 72.dp, end = 72.dp)
                .alpha(textAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = scheme.primary,
                trackColor = scheme.primary.copy(alpha = 0.16f)
            )
            Text(
                "نجهّز أرقامك…",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/** بناء ترحيب يناسب وقت فتح التطبيق، فيحس المستخدم أنه مخصص له. */
fun greetingForHour(hour: Int): Pair<String, String> = when (hour) {
    in 5..11 -> "صباح الخير ☀️" to "خلّنا نبدأ يومك بفلوس واضحة"
    in 12..16 -> "مساء النور 🌤️" to "شوف وين وصلت اليوم"
    in 17..21 -> "مساء الخير 🌙" to "ملخصك المالي جاهز"
    else -> "سهرة موفقة 🌙" to "أرقامك محفوظة وآمنة"
}
