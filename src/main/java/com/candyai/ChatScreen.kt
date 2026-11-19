package com.candyai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun ChatScreen(startId: String?, onBack: () -> Unit) {
    val c = LocalCandy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // A new chat only becomes a real conversation on the first message, so empty chats never pile up.
    var convId by remember { mutableStateOf(startId) }
    val msgs = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val id = startId ?: return@LaunchedEffect
        try {
            msgs.clear()
            msgs.addAll(Api.messages(id))
        } catch (e: Exception) {
            error = errorMessage(ctx, e)
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        error = null
        busy = true
        scope.launch {
            var sent = false
            try {
                val cid = convId ?: Api.createConversation().also { convId = it }
                val job = Api.sendMessage(cid, text)
                sent = true
                jobId = job
                msgs.add(Msg("user", text))
                msgs.add(Msg("assistant", ""))
                val idx = msgs.lastIndex
                var since = 0
                var fails = 0
                while (true) {
                    val p = try {
                        Api.poll(job, since)
                    } catch (e: IOException) {
                        // a short network hiccup: try again before giving up
                        fails++
                        if (fails > 5) throw e
                        delay(1500)
                        continue
                    }
                    fails = 0
                    if (p.full) msgs[idx] = Msg("assistant", p.text)
                    else if (p.delta.isNotEmpty()) msgs[idx] = Msg("assistant", msgs[idx].text + p.delta)
                    since = p.next
                    if (p.status != "running") {
                        if (p.status == "error") error = ctx.getString(R.string.err_reply)
                        break
                    }
                }
                if (msgs.getOrNull(idx)?.text.isNullOrEmpty()) msgs.removeAt(idx)
            } catch (e: Exception) {
                error = errorMessage(ctx, e)
                if (msgs.lastOrNull()?.let { it.role == "assistant" && it.text.isEmpty() } == true) {
                    msgs.removeAt(msgs.lastIndex)
                }
                // the message never reached the server: give the text back so nothing is lost
                if (!sent) input = text
            } finally {
                busy = false
                jobId = null
            }
        }
    }

    fun stop() {
        val j = jobId ?: return
        scope.launch { try { Api.cancel(j) } catch (_: Exception) { } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text)
            }
            Text("Candy", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // reverseLayout keeps the newest message at the bottom and pinned there while the reply grows
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(count = msgs.size) { i ->
                val m = msgs[msgs.lastIndex - i]
                Bubble(m, streaming = busy && i == 0 && m.role == "assistant")
            }
        }

        error?.let {
            Text(
                it, color = c.text, fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.type_message), color = c.subtext) },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                textStyle = TextStyle(textDirection = TextDirection.Content, fontSize = 16.sp, color = c.text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.border,
                    cursorColor = c.accent,
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface,
                ),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .clickable { if (busy) stop() else send() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.stop), tint = c.onAccent)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.send), tint = c.onAccent)
                }
            }
        }
    }
}

@Composable
fun Bubble(msg: Msg, streaming: Boolean) {
    val c = LocalCandy.current
    val isUser = msg.role == "user"
    // In right-to-left, "start" is the right side: my messages on the right, Candy on the left.
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (isUser) c.userBubble else c.surface)
                .then(if (isUser) Modifier else Modifier.border(1.dp, c.border, shape))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val color = if (isUser) c.onUserBubble else c.text
            if (msg.text.isEmpty() && streaming) {
                Text(stringResource(R.string.thinking), color = c.subtext, fontSize = 15.sp)
            } else {
                SelectionContainer {
                    Text(
                        msg.text, color = color, fontSize = 16.sp, lineHeight = 24.sp,
                        style = TextStyle(textDirection = TextDirection.Content),
                    )
                }
            }
        }
    }
}
