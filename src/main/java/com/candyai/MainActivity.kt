package com.candyai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

class MainActivity : ComponentActivity() {
    // a conversation the ask panel handed over ("open the full chat")
    private var pendingConv by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        pendingConv = intent?.getStringExtra("open_conv")
        setContent {
            CandyTheme {
                // The app is Arabic-first, so the whole interface is right-to-left.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CandyApp(pendingConv) { pendingConv = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_conv")?.let { pendingConv = it }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Chat(val id: String?) : Screen
}

@Composable
fun CandyApp(pendingConv: String? = null, onPendingHandled: () -> Unit = {}) {
    val c = LocalCandy.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var memoryOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pendingConv) {
        if (pendingConv != null) {
            screen = Screen.Chat(pendingConv)
            onPendingHandled()
        }
    }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        when (val s = screen) {
            is Screen.Home -> ConversationsScreen(
                onOpen = { screen = Screen.Chat(it) },
                onNew = { screen = Screen.Chat(null) },
                onOpenMemory = { memoryOpen = true },
            )
            is Screen.Chat -> {
                BackHandler { screen = Screen.Home }
                ChatScreen(s.id, onBack = { screen = Screen.Home }, onOpenMemory = { memoryOpen = true })
            }
        }
        // on top of everything, so the chat underneath (and a reply being written) is not lost
        if (memoryOpen) {
            BackHandler { memoryOpen = false }
            Box(Modifier.fillMaxSize().background(c.bg)) {
                MemoryScreen(onBack = { memoryOpen = false })
            }
        }
    }
}
