package com.candyai

import android.content.Context
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // a conversation the ask panel handed over ("open the full chat")
    private var pendingConv by mutableStateOf<String?>(null)
    // the home-screen widget asks for SugarNote: "list" or "new"
    private var pendingSugar by mutableStateOf<String?>(null)
    // an artifact card was tapped (in the edge panel, say): open it
    private var pendingArtifact by mutableStateOf<String?>(null)
    private var pendingJar by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        ThemeMode.load()
        // not after a restart (language change): the intent still carries the old request
        if (savedInstanceState == null) pendingConv = intent?.getStringExtra("open_conv")
        if (savedInstanceState == null) pendingSugar = intent?.getStringExtra("open_sugar")
        if (savedInstanceState == null) pendingArtifact = intent?.getStringExtra("open_artifact")
        if (savedInstanceState == null) pendingJar = intent?.getStringExtra("open_jar")
        setContent {
            val dark = rememberDark()
            // the bars' icons follow the app's theme, which can differ from the phone's
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            CandyTheme(dark) {
                // right-to-left for Arabic, left-to-right for English
                CompositionLocalProvider(LocalLayoutDirection provides Lang.direction(this@MainActivity)) {
                    // asks GitHub for a newer release (about once a day) and offers to install it
                    UpdatePrompt()
                    var loggedIn by remember { mutableStateOf(Store.loginDone) }
                    if (loggedIn) {
                        CandyApp(pendingConv, { pendingConv = null }, pendingSugar, { pendingSugar = null }, pendingArtifact, { pendingArtifact = null }, pendingJar) { pendingJar = null }
                    } else {
                        val scope = rememberCoroutineScope()
                        var googleBusy by remember { mutableStateOf(false) }
                        var googleError by remember { mutableStateOf<String?>(null) }
                        LoginScreen(
                            onGuest = {
                                Store.loginDone = true
                                loggedIn = true
                            },
                            onGoogle = {
                                googleBusy = true
                                googleError = null
                                scope.launch {
                                    try {
                                        Api.googleLogin(GoogleAuth.idToken(this@MainActivity))
                                        Store.loginDone = true
                                        loggedIn = true
                                    } catch (e: GoogleCancelled) {
                                        // closed the sheet: nothing to say
                                    } catch (e: Exception) {
                                        googleError = if (e is ApiException || e is java.io.IOException) errorMessage(this@MainActivity, e)
                                        else getString(R.string.login_google_fail)
                                    } finally {
                                        googleBusy = false
                                    }
                                }
                            },
                            googleBusy = googleBusy,
                            googleError = googleError,
                        )
                    }
                }
            }
        }
    }

    // every touch counts: Candy gets bored when nobody touches the screen
    override fun onStart() {
        super.onStart()
        WakeService.ensure(this)
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
        intent.getStringExtra("open_sugar")?.let { pendingSugar = it }
        intent.getStringExtra("open_artifact")?.let { pendingArtifact = it }
        intent.getStringExtra("open_jar")?.let { pendingJar = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandyApp(pendingConv: String? = null, onPendingHandled: () -> Unit = {}, pendingSugar: String? = null, onSugarHandled: () -> Unit = {}, pendingArtifact: String? = null, onArtifactHandled: () -> Unit = {}, pendingJar: String? = null, onJarHandled: () -> Unit = {}) {
    val c = LocalCandy.current
    val scope = rememberCoroutineScope()
    val appDir = LocalLayoutDirection.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // the chat always starts on a fresh conversation; picking one in the drawer re-creates the chat screen with it
    // saved across a restart, so changing the language in the settings brings you back to the same chat and the same page
    var startId by rememberSaveable { mutableStateOf<String?>(null) }
    var startProject by rememberSaveable { mutableStateOf<String?>(null) }
    var projectsOpen by rememberSaveable { mutableStateOf(false) }
    var startNotebook by rememberSaveable { mutableStateOf<String?>(null) }
    var notellaOpen by rememberSaveable { mutableStateOf(false) }
    var sugarOpen by rememberSaveable { mutableStateOf(false) }
    var sugarNew by remember { mutableStateOf(false) }
    var artifactsOpen by rememberSaveable { mutableStateOf(false) }
    var viewArtifact by rememberSaveable { mutableStateOf<String?>(null) }
    var artifactsKey by remember { mutableIntStateOf(0) }
    var jarsOpen by rememberSaveable { mutableStateOf(false) }
    var viewJar by rememberSaveable { mutableStateOf<String?>(null) }
    var jarsKey by remember { mutableIntStateOf(0) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatKey by remember { mutableIntStateOf(0) }
    var listKey by remember { mutableIntStateOf(0) }
    var memoryOpen by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var side by remember { mutableStateOf(Store.drawerSide) }
    var sendSide by remember { mutableStateOf(Store.sendSide) }

    fun openChat(id: String?, project: String? = null, notebook: String? = null) {
        startProject = project
        startNotebook = notebook
        projectsOpen = false
        notellaOpen = false
        sugarOpen = false
        artifactsOpen = false
        viewArtifact = null
        jarsOpen = false
        viewJar = null
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

    LaunchedEffect(pendingSugar) {
        if (pendingSugar != null) {
            sugarNew = pendingSugar == "new"
            sugarOpen = true
            onSugarHandled()
        }
    }

    LaunchedEffect(pendingArtifact) {
        if (pendingArtifact != null) {
            viewArtifact = pendingArtifact
            onArtifactHandled()
        }
    }

    LaunchedEffect(pendingJar) {
        if (pendingJar != null) {
            viewJar = pendingJar
            onJarHandled()
        }
    }

    // back closes the drawer first; with nothing open it leaves the app
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    CompositionLocalProvider(LocalOpenArtifact provides { aid: String -> viewArtifact = aid }, LocalOpenJar provides { jid: String -> viewJar = jid }) {
    Box(Modifier.fillMaxSize().background(c.bg)) {
        // the drawer docks to the start edge of the layout direction around it, so that direction picks the side;
        // everything inside is put back to the app's own direction
        CompositionLocalProvider(LocalLayoutDirection provides if (side == "left") LayoutDirection.Ltr else LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides appDir) {
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
                            onSugar = {
                                sugarNew = false
                                sugarOpen = true
                                scope.launch { drawerState.close() }
                            },
                            onArtifacts = {
                                artifactsOpen = true
                                scope.launch { drawerState.close() }
                            },
                            onJars = {
                                jarsOpen = true
                                scope.launch { drawerState.close() }
                            },
                            onNotella = {
                                notellaOpen = true
                                scope.launch { drawerState.close() }
                            },
                            onProjects = {
                                projectsOpen = true
                                scope.launch { drawerState.close() }
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
                CompositionLocalProvider(LocalLayoutDirection provides appDir) {
                    Box(Modifier.fillMaxSize().background(c.bg)) {
                        key(chatKey) {
                            ChatScreen(
                                startId = startId,
                                projectId = startProject,
                                notebookId = startNotebook,
                                menuOnLeft = side == "left",
                                sendOnLeft = sendSide == "left",
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
                sendSide = sendSide,
                onSendSide = {
                    sendSide = it
                    Store.sendSide = it
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
        if (projectsOpen) {
            ProjectsScreen(
                onBack = { projectsOpen = false },
                onOpenChat = { openChat(it) },
                onNewChat = { openChat(null, it) },
            )
        }
        if (sugarOpen) {
            SugarNoteScreen(onBack = { sugarOpen = false }, startNew = sugarNew)
        }
        if (notellaOpen) {
            NotellaScreen(
                onBack = { notellaOpen = false },
                onOpenChat = { openChat(it) },
                onNewChat = { openChat(null, null, it) },
            )
        }
        if (artifactsOpen) {
            ArtifactsScreen(onBack = { artifactsOpen = false }, onOpen = { viewArtifact = it }, reloadKey = artifactsKey)
        }
        if (jarsOpen) {
            JarsScreen(onBack = { jarsOpen = false }, onOpen = { viewJar = it }, reloadKey = jarsKey)
        }
        viewJar?.let { jid ->
            Box(Modifier.fillMaxSize().background(c.bg)) {
                JarViewer(jid, onBack = { viewJar = null }, onChanged = { jarsKey++ })
            }
        }
        viewArtifact?.let { aid ->
            Box(Modifier.fillMaxSize().background(c.bg)) {
                ArtifactViewer(aid, onBack = { viewArtifact = null }, onChanged = { artifactsKey++ })
            }
        }
        if (memoryOpen) {
            BackHandler { memoryOpen = false }
            Box(Modifier.fillMaxSize().background(c.bg)) {
                MemoryScreen(onBack = { memoryOpen = false })
            }
        }
    }
    }
}
