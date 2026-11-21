package com.candyai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

class ApiException(val code: String, val status: Int) : Exception(code)

data class Conversation(val id: String, val title: String, val updatedAt: Long, val pinned: Boolean = false)

/** One message. [n] is the server id; a negative [n] means it was just sent and has no id yet. */
class AiMsg(
    val n: Long,
    val role: String,
    val text: String,
    val at: Long,
    val memAdded: List<String> = emptyList(),
    val memRemoved: List<String> = emptyList(),
    /** 1 = liked, -1 = disliked, 0 = nothing. */
    val react: Int = 0,
    val atts: List<AttRef> = emptyList(),
) {
    val mine: Boolean get() = role == "user"
    fun withReact(v: Int) = AiMsg(n, role, text, at, memAdded, memRemoved, v, atts)
    fun withText(t: String) = AiMsg(n, role, t, at, memAdded, memRemoved, react, atts)
}

class PollResult(
    val status: String,
    val error: String?,
    val full: Boolean,
    val text: String,
    val delta: String,
    val next: Int,
    val final: String?,
    val memAdded: List<String>,
    val memRemoved: List<String>,
    val messageId: Long,
    /** Pictures Candy drew for this reply (empty most of the time). */
    val atts: List<AttRef> = emptyList(),
)

class VoiceStatus(val ttsOn: Boolean, val ttsLeft: Int, val ttsLimit: Int, val sttOn: Boolean)
class MemItem(val id: Long, val text: String)
class MemState(val items: List<MemItem>, val on: Boolean, val max: Int)

private fun attRefs(arr: org.json.JSONArray?): List<AttRef> {
    val out = ArrayList<AttRef>()
    if (arr != null) for (k in 0 until arr.length()) arr.optJSONObject(k)?.let { out.add(AttRef(it.optLong("id"), it.optString("kind"), it.optString("name"))) }
    return out
}

private fun memLists(mem: JSONObject?): Pair<List<String>, List<String>> {
    val added = ArrayList<String>()
    mem?.optJSONArray("a")?.let { a ->
        for (i in 0 until a.length()) a.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }?.let { added.add(it) }
    }
    val removed = ArrayList<String>()
    mem?.optJSONArray("r")?.let { a ->
        for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { removed.add(it) }
    }
    return added to removed
}

object Api {
    private class Raw(val status: Int, val bytes: ByteArray)

    private suspend fun raw(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        readTimeoutMs: Int = 15000,
    ): Raw = withContext(Dispatchers.IO) {
        val conn = URL(Store.serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("ngrok-skip-browser-warning", "1")
            if (auth) Store.token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            Raw(status, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(r: Raw): JSONObject {
        val json = try { JSONObject(String(r.bytes, Charsets.UTF_8)) } catch (e: Exception) { JSONObject() }
        if (r.status !in 200..299) throw ApiException(json.optString("error", "http_${r.status}"), r.status)
        return json
    }

    private fun newSecret(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private suspend fun login() {
        val secret = Store.guestSecret ?: newSecret().also { Store.guestSecret = it }
        val r = parse(raw("POST", "/auth/guest", JSONObject().put("secret", secret), auth = false))
        Store.token = r.getString("token")
    }

    /** Sign in with a Google ID token. The current (guest) token is sent too, so the server can attach the account to it and keep the chats. */
    suspend fun googleLogin(idToken: String) {
        val r = parse(raw("POST", "/auth/google", JSONObject().put("id_token", idToken)))
        Store.token = r.getString("token")
    }

    /** Runs a request with the saved token; logs in first if there is none, and once more if the server says 401. */
    private suspend fun <T> authed(block: suspend () -> T): T {
        if (Store.token == null) login()
        return try {
            block()
        } catch (e: ApiException) {
            if (e.status == 401) { login(); block() } else throw e
        }
    }

    private suspend fun json(method: String, path: String, body: JSONObject? = null, readTimeoutMs: Int = 15000): JSONObject =
        authed { parse(raw(method, path, body, readTimeoutMs = readTimeoutMs)) }

    // ───── conversations ─────
    suspend fun conversations(): List<Conversation> {
        val arr = json("GET", "/conversations").getJSONArray("conversations")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Conversation(o.getString("id"), o.getString("title"), o.getLong("updated_at"), o.optInt("pinned", 0) == 1)
        }
    }

    suspend fun createConversation(): String = json("POST", "/conversations", JSONObject()).getString("id")

    suspend fun deleteConversation(id: String) { json("DELETE", "/conversations/$id") }

    suspend fun renameConversation(id: String, title: String) {
        json("POST", "/conversations/$id/rename", JSONObject().put("title", title))
    }

    suspend fun pinConversation(id: String, pinned: Boolean) {
        json("POST", "/conversations/$id/pin", JSONObject().put("pinned", pinned))
    }

    suspend fun messages(id: String): List<AiMsg> {
        val arr = json("GET", "/conversations/$id/messages").getJSONArray("messages")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val (a, r) = memLists(o.optJSONObject("mem"))
            val atts = ArrayList<AttRef>()
            o.optJSONArray("attachments")?.let { arr ->
                for (k in 0 until arr.length()) arr.optJSONObject(k)?.let { atts.add(AttRef(it.optLong("id"), it.optString("kind"), it.optString("name"))) }
            }
            AiMsg(o.getLong("id"), o.getString("role"), o.getString("content"), o.getLong("created_at"), a, r, o.optInt("react", 0), atts)
        }
    }

    // ───── replies (streaming) ─────
    suspend fun sendMessage(conversationId: String, text: String, files: List<Pending> = emptyList()): String {
        val body = JSONObject().put("text", text)
        if (files.isNotEmpty()) {
            val arr = org.json.JSONArray()
            for (f in files) arr.put(JSONObject().put("kind", f.kind).put("mime", f.mime).put("name", f.name).put("data", f.b64))
            body.put("attachments", arr)
        }
        return json("POST", "/conversations/$conversationId/messages", body, readTimeoutMs = 45000).getString("job_id")
    }

    /** A picture or file attached to a message, as raw bytes. */
    suspend fun attachmentBytes(id: Long): ByteArray = authed {
        val r = raw("GET", "/attachments/$id", readTimeoutMs = 30000)
        if (r.status !in 200..299) parse(r)
        r.bytes
    }

    suspend fun regenerate(conversationId: String): String =
        json("POST", "/conversations/$conversationId/regenerate", JSONObject()).getString("job_id")

    /** Long-poll: the server holds the request up to ~20s until there is something new. */
    suspend fun poll(jobId: String, since: Int): PollResult {
        val j = json("GET", "/jobs/$jobId?since=$since", readTimeoutMs = 35000)
        val (a, r) = memLists(j.optJSONObject("mem"))
        return PollResult(
            status = j.getString("status"),
            error = if (j.isNull("error")) null else j.getString("error"),
            full = j.optBoolean("full", false),
            text = j.optString("text", ""),
            delta = j.optString("delta", ""),
            next = j.optInt("next", since),
            final = if (j.has("final") && !j.isNull("final")) j.getString("final") else null,
            memAdded = a,
            memRemoved = r,
            messageId = j.optLong("message_id", 0L),
            atts = attRefs(j.optJSONArray("attachments")),
        )
    }

    suspend fun cancel(jobId: String) { json("POST", "/jobs/$jobId/cancel", JSONObject()) }

    suspend fun react(messageId: Long, v: Int) { json("POST", "/messages/$messageId/react", JSONObject().put("v", v)) }

    // ───── voice ─────
    suspend fun voiceStatus(): VoiceStatus {
        val j = json("GET", "/voice/status")
        val t = j.optJSONObject("tts")
        val s = j.optJSONObject("stt")
        return VoiceStatus(
            ttsOn = t?.optBoolean("enabled") == true,
            ttsLeft = t?.optInt("left") ?: 0,
            ttsLimit = t?.optInt("limit") ?: 0,
            sttOn = s?.optBoolean("enabled") == true,
        )
    }

    /** Candy's reply read out loud, as mp3 bytes. The server keeps what it already voiced. */
    suspend fun voiceBytes(messageId: Long): ByteArray = authed {
        val r = raw("GET", "/messages/$messageId/voice", readTimeoutMs = 45000)
        if (r.status !in 200..299) parse(r)
        r.bytes
    }

    suspend fun transcribe(audioBase64: String): String =
        json("POST", "/voice/transcribe", JSONObject().put("audio", audioBase64), readTimeoutMs = 45000).optString("text", "")

    // ───── memory ─────
    suspend fun memory(): MemState {
        val j = json("GET", "/memory")
        val a = j.optJSONArray("items")
        val items = ArrayList<MemItem>()
        if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { items.add(MemItem(it.optLong("id"), it.optString("text"))) }
        return MemState(items, j.optBoolean("on", true), j.optInt("max", 50))
    }

    suspend fun memoryAdd(text: String) { json("POST", "/memory/add", JSONObject().put("text", text)) }
    suspend fun memoryDelete(id: Long) { json("POST", "/memory/delete", JSONObject().put("id", id)) }
    suspend fun memoryClear() { json("POST", "/memory/delete", JSONObject().put("all", true)) }
    suspend fun memoryToggle(on: Boolean) { json("POST", "/memory/toggle", JSONObject().put("on", on)) }
}

fun errorMessage(ctx: Context, e: Throwable): String = when {
    e is ApiException && e.code == "daily_limit" -> ctx.getString(R.string.err_daily_limit)
    e is ApiException && e.code == "reply_in_progress" -> ctx.getString(R.string.err_busy)
    e is ApiException && e.code == "voice_limit" -> ctx.getString(R.string.ai_voice_limit)
    e is ApiException && e.code == "voice_empty" -> ctx.getString(R.string.ai_voice_empty)
    e is ApiException && e.code == "stt_empty" -> ctx.getString(R.string.ai_stt_empty)
    e is ApiException && e.code == "image_limit" -> ctx.getString(R.string.attach_image_limit)
    e is ApiException && (e.code == "bad_attachment" || e.code == "attachment_too_large" || e.code == "too_many_attachments" || e.status == 413) -> ctx.getString(R.string.attach_fail)
    e is ApiException && e.code == "not_found" -> ctx.getString(R.string.ai_voice_gone)
    e is ApiException && (e.code == "voice_unavailable" || e.code == "stt_unavailable") -> ctx.getString(R.string.ai_voice_fail)
    e is ApiException -> ctx.getString(R.string.err_generic)
    e is IOException -> ctx.getString(R.string.err_network)
    else -> ctx.getString(R.string.err_generic)
}
