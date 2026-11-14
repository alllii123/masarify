package com.masarify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.masarify.app.notifications.Notifier
import com.masarify.app.ui.AppRoot
import com.masarify.app.ui.MasarifyTheme
import com.masarify.app.ui.SplashScreen
import com.masarify.app.ui.greetingForHour
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannels(this)
        setContent {
            val state by viewModel.uiState.collectAsState()
            MasarifyTheme(state.settings) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface {
                        // شاشة الترحيب تظهر عند الفتح العادي فقط.
                        // تُتخطّى تمامًا لو فُتح التطبيق من الودجت أو الإشعار (open_add / open_sms)
                        // لأن المستخدم يبي الفعل فورًا في تلك الحالة.
                        val openedFromShortcut = intent.getBooleanExtra("open_add", false) ||
                            intent.getBooleanExtra("open_sms", false)
                        var showSplash by rememberSaveable { mutableStateOf(!openedFromShortcut) }

                        if (showSplash) {
                            val (greeting, subtitle) = remember { greetingForHour(LocalTime.now().hour) }
                            SplashScreen(
                                greeting = greeting,
                                subtitle = subtitle,
                                onFinished = { showSplash = false }
                            )
                        } else {
                            AppRoot(
                                viewModel = viewModel,
                                initialOpenAdd = intent.getBooleanExtra("open_add", false),
                                initialOpenSms = intent.getBooleanExtra("open_sms", false)
                            )
                        }
                    }
                }
            }
        }
    }
}
