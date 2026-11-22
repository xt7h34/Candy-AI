package com.candyai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** One thing the person is about to send: a (shrunk) picture or the text of a file. */
class Pending(val kind: String, val mime: String, val name: String, val b64: String, val thumb: ImageBitmap?)

/** An attachment of a message already in the chat. [thumb] is set for what was just sent from this phone; otherwise it is loaded from the server by [id]. */
class AttRef(val id: Long, val kind: String, val name: String, val thumb: ImageBitmap? = null)

object AttachKit {
    const val MAX_FILES = 3
    private const val MAX_SIDE = 1280
    private const val MAX_TEXT_BYTES = 80_000

    private fun displayName(ctx: Context, uri: Uri, fallback: String): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: fallback
    } catch (_: Exception) { fallback }

    /** Reads a picture, turns it upright, shrinks it to about 1280px and packs it as JPEG. */
    suspend fun fromImage(ctx: Context, uri: Uri): Pending? = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val decoded = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            val degrees = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) { 0f }

            val m = Matrix()
            if (degrees != 0f) m.postRotate(degrees)
            val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
            if (scale < 1f) m.postScale(scale, scale)
            val bmp = if (m.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)

            pack(bmp, "photo.jpg")
        } catch (_: Exception) {
            null
        }
    }

    /** Shrinks a bitmap (e.g. a circled part of the screen) to about 1280px and packs it as JPEG, ready to send. */
    suspend fun fromBitmap(bmp: Bitmap, name: String = "region.jpg"): Pending? = withContext(Dispatchers.Default) {
        try { pack(bmp, name) } catch (_: Exception) { null }
    }

    private fun pack(src: Bitmap, name: String): Pending? {
        val scale = MAX_SIDE.toFloat() / max(src.width, src.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, max(1, (src.width * scale).toInt()), max(1, (src.height * scale).toInt()), true)
        } else src
        var q = 82
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            bytes = out.toByteArray()
            q -= 15
        } while (bytes.size > 1_400_000 && q > 30)
        if (bytes.size > 1_450_000) return null
        val tw = 256
        val th = max(1, bmp.height * tw / max(1, bmp.width))
        val thumb = Bitmap.createScaledBitmap(bmp, tw, th, true).asImageBitmap()
        return Pending("image", "image/jpeg", name, Base64.encodeToString(bytes, Base64.NO_WRAP), thumb)
    }

    /** Reads a plain-text file (txt, md, csv, json, code...). Anything binary (PDF, Word...) is refused. */
    private const val MAX_DOC_BYTES = 25_000_000

    /** Any file (PDF, Word, Excel, PowerPoint, text...): it goes as it is and the server pulls the text out of it. */
    suspend fun fromDocument(ctx: Context, uri: Uri): Pending? = withContext(Dispatchers.IO) {
        try {
            val out = java.io.ByteArrayOutputStream()
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = s.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    if (out.size() > MAX_DOC_BYTES) return@withContext null
                }
            } ?: return@withContext null
            if (out.size() == 0) return@withContext null
            Pending("doc", ctx.contentResolver.getType(uri) ?: "application/octet-stream", displayName(ctx, uri, "file"), Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), null)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fromText(ctx: Context, uri: Uri): Pending? = withContext(Dispatchers.IO) {
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { s ->
                // read by hand: InputStream.readNBytes is missing on older Android versions
                val buf = ByteArray(MAX_TEXT_BYTES)
                var n = 0
                while (n < buf.size) {
                    val r = s.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                buf.copyOf(n)
            } ?: return@withContext null
            if (bytes.isEmpty() || bytes.any { it == 0.toByte() }) return@withContext null
            Pending("text", "text/plain", displayName(ctx, uri, "file.txt"), Base64.encodeToString(bytes, Base64.NO_WRAP), null)
        } catch (_: Exception) {
            null
        }
    }
}

/** Pictures of messages already sent, kept in memory so scrolling does not download them again. */
object AttCache {
    private val cache = LruCache<Long, ImageBitmap>(24)

    suspend fun load(id: Long): ImageBitmap? {
        if (id <= 0) return null
        cache.get(id)?.let { return it }
        val bytes = try { Api.attachmentBytes(id) } catch (_: Exception) { return null }
        val bmp = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } ?: return null
        cache.put(id, bmp)
        return bmp
    }
}

@Composable
fun AttachMenu(expanded: Boolean, onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit, onFile: () -> Unit, onScreen: (() -> Unit)? = null, onCircle: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = cs.surfaceContainerHigh) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.attach_camera), color = cs.onSurface) },
            leadingIcon = { Icon(Icons.Rounded.CameraAlt, null, tint = cs.onSurfaceVariant) },
            onClick = { onDismiss(); onCamera() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.attach_gallery), color = cs.onSurface) },
            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, tint = cs.onSurfaceVariant) },
            onClick = { onDismiss(); onGallery() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.attach_file), color = cs.onSurface) },
            leadingIcon = { Icon(Icons.Rounded.AttachFile, null, tint = cs.onSurfaceVariant) },
            onClick = { onDismiss(); onFile() },
        )
        if (onCircle != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.see_circle), color = cs.onSurface) },
                leadingIcon = { Icon(Icons.Rounded.Gesture, null, tint = cs.onSurfaceVariant) },
                onClick = { onDismiss(); onCircle() },
            )
        }
        if (onScreen != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.see_screenshot), color = cs.onSurface) },
                leadingIcon = { Icon(Icons.Rounded.Fullscreen, null, tint = cs.onSurfaceVariant) },
                onClick = { onDismiss(); onScreen() },
            )
        }
    }
}

/** What is waiting to be sent, above the message box. The x takes one away. */
@Composable
fun PendingStrip(list: List<Pending>, onRemove: (Pending) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (p in list) {
            Box(Modifier.size(64.dp)) {
                if (p.thumb != null) {
                    Image(p.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.AttachFile, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(p.name, color = cs.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.attach_remove), tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** An attachment inside a message bubble: the picture, or the name of the file. */
@Composable
fun AttachView(a: AttRef, large: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    if (a.kind != "image") {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(16.dp))
            Text(a.name.ifBlank { "file" }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        }
    } else {
        val bmp by produceState<ImageBitmap?>(a.thumb, a.id) {
            if (value == null) value = AttCache.load(a.id)
        }
        Box(Modifier.size(if (large) 250.dp else 150.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest)) {
            bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
}
