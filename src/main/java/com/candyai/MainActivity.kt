package com.candyai

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        setContent {
            CandyTheme {
                // The app is Arabic-first, so the whole interface is right-to-left.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CandyApp()
                }
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Chat(val id: String?) : Screen
}

@Composable
fun CandyApp() {
    val c = LocalCandy.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        when (val s = screen) {
            is Screen.Home -> ConversationsScreen(
                onOpen = { screen = Screen.Chat(it) },
                onNew = { screen = Screen.Chat(null) },
            )
            is Screen.Chat -> {
                BackHandler { screen = Screen.Home }
                ChatScreen(s.id, onBack = { screen = Screen.Home })
            }
        }
    }
}
