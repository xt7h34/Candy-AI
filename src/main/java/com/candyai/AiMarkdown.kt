package com.candyai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Blocks Candy's replies can contain: paragraphs, headings (#), code fences, and horizontal rules (---). */
internal sealed class MdBlock {
    class Para(val text: String) : MdBlock()
    class Heading(val level: Int, val text: String) : MdBlock()
    class Code(val lang: String, val code: String) : MdBlock()
    object Rule : MdBlock()
}

private val headingRe = Regex("^(#{1,4})\\s+(.*)$")

/** Splits a reply into blocks. A code fence that is still open (the reply is streaming) counts as code until the end. */
internal fun mdBlocks(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val para = ArrayList<String>()
    fun flush() {
        if (para.isNotEmpty()) {
            out.add(MdBlock.Para(para.joinToString("\n")))
            para.clear()
        }
    }
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()
        if (t.startsWith("```")) {
            flush()
            val lang = t.removePrefix("```").trim()
            val code = ArrayList<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.add(lines[i])
                i++
            }
            out.add(MdBlock.Code(lang, code.joinToString("\n")))
            i++
            continue
        }
        if (t.isEmpty()) {
            flush()
            i++
            continue
        }
        val isRule = t.length >= 3 && (t.all { it == '-' } || t.all { it == '*' } || t.all { it == '_' })
        if (isRule) {
            flush()
            out.add(MdBlock.Rule)
            i++
            continue
        }
        val h = headingRe.find(t)
        if (h != null) {
            flush()
            out.add(MdBlock.Heading(h.groupValues[1].length, h.groupValues[2]))
            i++
            continue
        }
        para.add(raw)
        i++
    }
    flush()
    return out
}

/** Inline styling: **bold**, `code`, and "- " / "* " lists turned into bullets. */
internal fun aiRich(text: String, bold: SpanStyle, code: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace)): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    for ((li, raw) in lines.withIndex()) {
        val line = if (raw.startsWith("- ") || raw.startsWith("* ")) "• " + raw.substring(2) else raw
        var i = 0
        while (i < line.length) {
            val b = line.indexOf("**", i)
            val c = line.indexOf('`', i)
            val bOk = b >= 0 && line.indexOf("**", b + 2) >= 0
            val cOk = c >= 0 && line.indexOf('`', c + 1) >= 0
            if (!bOk && !cOk) {
                append(line.substring(i))
                break
            }
            if (bOk && (!cOk || b < c)) {
                val e = line.indexOf("**", b + 2)
                append(line.substring(i, b))
                withStyle(bold) { append(line.substring(b + 2, e)) }
                i = e + 2
            } else {
                val e = line.indexOf('`', c + 1)
                append(line.substring(i, c))
                withStyle(code) { append(line.substring(c + 1, e)) }
                i = e + 1
            }
        }
        if (li < lines.lastIndex) append("\n")
    }
}

/** Copies text exactly as is (code must keep its ** and other symbols). */
internal fun copyRawText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Candy", text))
    // Android 13 and newer show their own "copied" note
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.ai_copied, Toast.LENGTH_SHORT).show()
}

@Composable
internal fun AiMarkdown(text: String) {
    val parts = remember(text) { splitArtifacts(text) }
    if (parts.size == 1 && parts[0] is ArtPart.Text) { AiMarkdownText(text); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in parts) when (p) {
            is ArtPart.Text -> if (p.s.isNotBlank()) AiMarkdownText(p.s.trim())
            is ArtPart.Card -> ArtifactCard(p.id, p.type, p.title)
        }
    }
}

@Composable
private fun AiMarkdownText(text: String) {
    val cs = MaterialTheme.colorScheme
    val boldSpan = SpanStyle(fontWeight = FontWeight.Bold)
    val codeSpan = SpanStyle(fontFamily = FontFamily.Monospace, background = cs.onSurface.copy(alpha = 0.14f))
    val blocks = remember(text) { mdBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Para -> Text(
                    aiRich(b.text, boldSpan, codeSpan),
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MdBlock.Heading -> Text(
                    aiRich(b.text, boldSpan, codeSpan),
                    color = cs.onSurface,
                    style = if (b.level <= 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                is MdBlock.Rule -> Box(
                    Modifier.fillMaxWidth().height(1.dp).background(cs.onSurface.copy(alpha = 0.25f)),
                )
                is MdBlock.Code -> AiCodeBlock(b.lang, b.code)
            }
        }
    }
}

@Composable
private fun AiCodeBlock(lang: String, code: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerLowest),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            AiActionIcon(painterResource(R.drawable.ic_copy), R.string.ai_act_copy, false, size = 16.dp, onClick = { copyRawText(ctx, code) })
        }
        // Code always reads left to right and scrolls sideways instead of wrapping
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    code,
                    color = cs.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    softWrap = false,
                )
            }
        }
    }
}
