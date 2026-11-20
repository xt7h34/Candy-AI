package com.candyai

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.launch

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

    // every touch counts: Candy gets bored when nobody touches the screen
    override fun onStart() {
        super.onStart()
        CandyOverlay.activityStarted()
        CandyOverlay.ensure(this)   // she comes back after the permission screen, or after the phone restarted the app
    }

    override fun onStop() {
        CandyOverlay.activityStopped()
        super.onStop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        CandyIdle.touched()
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_conv")?.let { pendingConv = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandyApp(pendingConv: String? = null, onPendingHandled: () -> Unit = {}) {
    val c = LocalCandy.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // the chat always starts on a fresh conversation; picking one in the drawer re-creates the chat screen with it
    var startId by remember { mutableStateOf<String?>(null) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var chatKey by remember { mutableIntStateOf(0) }
    var listKey by remember { mutableIntStateOf(0) }
    var memoryOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var side by remember { mutableStateOf(Store.drawerSide) }

    fun openChat(id: String?) {
        startId = id
        activeId = id
        chatKey++
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(pendingConv) {
        if (pendingConv != null) {
            openChat(pendingConv)
            onPendingHandled()
        }
    }

    // back closes the drawer first; with nothing open it leaves the app
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // the drawer docks to the start edge of the layout direction around it, so that direction picks the side;
        // everything inside is put back to right-to-left
        CompositionLocalProvider(LocalLayoutDirection provides if (side == "left") LayoutDirection.Ltr else LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ConversationsDrawer(
                            side = side,
                            activeId = activeId,
                            isOpen = drawerState.targetValue == DrawerValue.Open,
                            reloadKey = listKey,
                            onOpen = { openChat(it) },
                            onNew = {
                                // already on an empty chat: just close the drawer
                                if (activeId == null) scope.launch { drawerState.close() } else openChat(null)
                            },
                            onSettings = {
                                settingsOpen = true
                                scope.launch { drawerState.close() }
                            },
                            onDeleted = { id -> if (id == activeId) openChat(null) },
                        )
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().background(c.bg)) {
                        key(chatKey) {
                            ChatScreen(
                                startId = startId,
                                menuOnLeft = side == "left",
                                onMenu = { scope.launch { drawerState.open() } },
                                onConversation = { activeId = it },
                                onOpenMemory = { memoryOpen = true },
                            )
                        }
                    }
                }
            }
        }

        // on top of everything, so the chat underneath (and a reply being written) is not lost
        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            SettingsScreen(
                side = side,
                onSide = {
                    side = it
                    Store.drawerSide = it
                },
                onClose = { settingsOpen = false },
                onOpenMemory = { memoryOpen = true },
                onServerChanged = {
                    // another server means another account: start clean
                    listKey++
                    openChat(null)
                },
            )
        }
        if (memoryOpen) {
            BackHandler { memoryOpen = false }
            Box(Modifier.fillMaxSize().background(c.bg)) {
                MemoryScreen(onBack = { memoryOpen = false })
            }
        }
    }
}
