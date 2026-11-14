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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.masarify.app.notifications.Notifier
import com.masarify.app.ui.AppRoot
import com.masarify.app.ui.MasarifyTheme

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
