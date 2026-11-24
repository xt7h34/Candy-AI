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

data class Conversation(val id: String, val title: String, val updatedAt: Long, val pinned: Boolean = false, val projectId: String? = null, val notebookId: String? = null)

data class Note(val id: String, val text: String, val color: Int, val pinned: Boolean, val updatedAt: Long, val remindAt: Long = 0, val analyzed: Boolean = true)

/** Candy found a future event in a note: [day] is yyyy-MM-dd, [time] is HH:mm or null when the note does not say. */
data class NoteSuggest(val event: String, val whenText: String, val day: String, val time: String?)

data class Notebook(val id: String, val name: String, val color: Int, val sources: Int, val chats: Int)

data class Source(val id: String, val name: String, val chars: Int)

class AgentStep(
    val action: String, val id: Int?, val text: String, val direction: String, val x: Int?, val y: Int?,
    val app: String, val seconds: Int, val question: String, val summary: String, val note: String, val confirm: String,
)
data class ArtifactItem(val id: String, val title: String, val type: String, val updatedAt: Long)
data class JarItem(val id: String, val title: String, val files: Int, val updatedAt: Long)
data class JarFile(val path: String, val content: String)
data class JarFull(val id: String, val title: String, val current: Int, val files: List<JarFile>, val versions: List<Pair<Int, Long>>)
data class ArtifactFull(val id: String, val title: String, val type: String, val lang: String, val current: Int, val content: String, val versions: List<Pair<Int, Long>>)
data class Project(val id: String, val name: String, val color: Int, val instructions: String, val count: Int, val updatedAt: Long)

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
    /** Ids of SugarNote notes Candy wrote in this reply. */
    val notes: List<String> = emptyList(),
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
    /** Ids of SugarNote notes Candy wrote in this reply. */
    val notes: List<String> = emptyList(),
    /** Phone actions Candy prepared (alarm, call...). The app asks the person before doing any. */
    val actions: List<AgentAction> = emptyList(),
    /** Steps of a research that is running (shown as a card, not as text). */
    val progress: List<String> = emptyList(),
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
            Conversation(o.getString("id"), o.getString("title"), o.getLong("updated_at"), o.optInt("pinned", 0) == 1, if (o.isNull("project_id")) null else o.optString("project_id"), if (o.isNull("notebook_id")) null else o.optString("notebook_id"))
        }
    }

    suspend fun createConversation(projectId: String? = null, notebookId: String? = null): String {
        val body = JSONObject()
        if (projectId != null) body.put("project_id", projectId)
        if (notebookId != null) body.put("notebook_id", notebookId)
        return json("POST", "/conversations", body).getString("id")
    }

    // ───── SugarNote ─────
    suspend fun notes(): List<Note> {
        val arr = json("GET", "/notes").getJSONArray("notes")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Note(o.getString("id"), o.getString("text"), o.optInt("color", 0), o.optInt("pinned", 0) == 1, o.optLong("updated_at"), if (o.isNull("remind_at")) 0L else o.optLong("remind_at"), o.optInt("analyzed", 1) == 1)
        }
    }

    suspend fun createNote(text: String, color: Int, pinned: Boolean, remindAt: Long? = null): String =
        json("POST", "/notes", JSONObject().put("text", text).put("color", color).put("pinned", pinned).put("remind_at", remindAt ?: JSONObject.NULL)).getString("id")

    suspend fun updateNote(id: String, text: String, color: Int, pinned: Boolean, remindAt: Long?) {
        json("POST", "/notes/$id", JSONObject().put("text", text).put("color", color).put("pinned", pinned).put("remind_at", remindAt ?: JSONObject.NULL))
    }

    /** Asks the server (once per note) whether the note mentions a future event. [now] is the phone's local date and time as text. */
    suspend fun analyzeNote(id: String, now: String): NoteSuggest? {
        val s = json("POST", "/notes/$id/analyze", JSONObject().put("now", now))
        if (s.isNull("suggest")) return null
        val o = s.getJSONObject("suggest")
        return NoteSuggest(o.optString("event"), o.optString("when_text"), o.getString("day"), if (o.isNull("time")) null else o.optString("time"))
    }

    suspend fun deleteNote(id: String) { json("DELETE", "/notes/$id") }

    // ───── Notella ─────
    suspend fun notebooks(): List<Notebook> {
        val arr = json("GET", "/notebooks").getJSONArray("notebooks")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Notebook(o.getString("id"), o.getString("name"), o.optInt("color", 0), o.optInt("sources", 0), o.optInt("chats", 0))
        }
    }

    suspend fun createNotebook(name: String, color: Int): String =
        json("POST", "/notebooks", JSONObject().put("name", name).put("color", color)).getString("id")

    suspend fun updateNotebook(id: String, name: String, color: Int) {
        json("POST", "/notebooks/$id", JSONObject().put("name", name).put("color", color))
    }

    suspend fun deleteNotebook(id: String) { json("DELETE", "/notebooks/$id") }

    suspend fun sources(notebookId: String): List<Source> {
        val arr = json("GET", "/notebooks/$notebookId/sources").getJSONArray("sources")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Source(o.getString("id"), o.getString("name"), o.optInt("chars", 0))
        }
    }

    /** A file (base64) or pasted text becomes a source; the server extracts and indexes it, which can take a while for big files. */
    suspend fun addSource(notebookId: String, name: String, b64: String? = null, text: String? = null) {
        val body = JSONObject().put("name", name)
        if (b64 != null) body.put("data", b64) else body.put("text", text ?: "")
        json("POST", "/notebooks/$notebookId/sources", body, readTimeoutMs = 240000)
    }

    suspend fun deleteSource(id: String) { json("DELETE", "/sources/$id") }

    // ───── agent (Candy controls the screen, one step at a time) ─────
    suspend fun agentStart(goal: String): String = json("POST", "/agent/start", JSONObject().put("goal", goal)).getString("id")

    suspend fun agentStep(id: String, screen: String, shotB64: String?, last: String, app: String, size: String): AgentStep {
        val body = JSONObject().put("screen", screen).put("last", last).put("app", app).put("size", size)
        if (shotB64 != null) body.put("shot", JSONObject().put("mime", "image/jpeg").put("data", shotB64))
        val j = json("POST", "/agent/$id/step", body, 90000)
        fun intOrNull(k: String): Int? = if (j.has(k) && !j.isNull(k)) j.optInt(k) else null
        return AgentStep(
            j.optString("action", "wait"), intOrNull("id"), j.optString("text", ""), j.optString("direction", "down"), intOrNull("x"), intOrNull("y"),
            j.optString("app", ""), j.optInt("seconds", 2), j.optString("question", ""), j.optString("summary", ""), j.optString("note", ""), j.optString("confirm", ""),
        )
    }

    suspend fun agentEnd(id: String) { json("DELETE", "/agent/$id") }

    // ───── artifacts ─────
    suspend fun artifacts(): List<ArtifactItem> {
        val arr = json("GET", "/artifacts").getJSONArray("artifacts")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ArtifactItem(o.getString("id"), o.getString("title"), o.optString("type", "code"), o.optLong("updated_at"))
        }
    }

    private fun parseArtifact(o: JSONObject): ArtifactFull {
        val vs = o.optJSONArray("versions")
        val versions = if (vs == null) emptyList() else (0 until vs.length()).map { vs.getJSONObject(it).let { v -> v.getInt("v") to v.optLong("created_at") } }
        return ArtifactFull(o.getString("id"), o.getString("title"), o.optString("type", "code"), o.optString("lang", ""), o.optInt("current", 1), o.optString("content", ""), versions)
    }

    suspend fun artifact(id: String): ArtifactFull = parseArtifact(json("GET", "/artifacts/$id", null, 30000))

    suspend fun artifactVersion(id: String, v: Int): String = json("GET", "/artifacts/$id/versions/$v", null, 30000).optString("content", "")

    suspend fun renameArtifact(id: String, title: String) { json("POST", "/artifacts/$id", JSONObject().put("title", title)) }

    suspend fun restoreArtifact(id: String, v: Int) { json("POST", "/artifacts/$id", JSONObject().put("restore", v)) }

    suspend fun deleteArtifact(id: String) { json("DELETE", "/artifacts/$id") }

    // ───── code jars ─────
    suspend fun jars(): List<JarItem> {
        val arr = json("GET", "/jars").getJSONArray("jars")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            JarItem(o.getString("id"), o.getString("title"), o.optInt("files"), o.optLong("updated_at"))
        }
    }

    private fun parseJarFiles(a: org.json.JSONArray?): List<JarFile> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it).let { f -> JarFile(f.getString("path"), f.optString("content", "")) } }

    suspend fun jar(id: String): JarFull {
        val o = json("GET", "/jars/$id", null, 30000)
        val vs = o.optJSONArray("versions")
        val versions = if (vs == null) emptyList() else (0 until vs.length()).map { vs.getJSONObject(it).let { v -> v.getInt("v") to v.optLong("created_at") } }
        return JarFull(o.getString("id"), o.getString("title"), o.optInt("current", 1), parseJarFiles(o.optJSONArray("files")), versions)
    }

    suspend fun jarVersion(id: String, v: Int): List<JarFile> = parseJarFiles(json("GET", "/jars/$id/versions/$v", null, 30000).optJSONArray("files"))

    suspend fun renameJar(id: String, title: String) { json("POST", "/jars/$id", JSONObject().put("title", title)) }

    suspend fun restoreJar(id: String, v: Int) { json("POST", "/jars/$id", JSONObject().put("restore", v)) }

    suspend fun deleteJar(id: String) { json("DELETE", "/jars/$id") }

    // ───── projects ─────
    suspend fun projects(): List<Project> {
        val arr = json("GET", "/projects").getJSONArray("projects")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Project(o.getString("id"), o.getString("name"), o.optInt("color", 0), o.optString("instructions", ""), o.optInt("count", 0), o.optLong("updated_at"))
        }
    }

    suspend fun createProject(name: String, color: Int, instructions: String): String =
        json("POST", "/projects", JSONObject().put("name", name).put("color", color).put("instructions", instructions)).getString("id")

    suspend fun updateProject(id: String, name: String, color: Int, instructions: String) {
        json("POST", "/projects/$id", JSONObject().put("name", name).put("color", color).put("instructions", instructions))
    }

    suspend fun deleteProject(id: String) { json("DELETE", "/projects/$id") }

    suspend fun moveToProject(convId: String, projectId: String?) {
        json("POST", "/conversations/$convId/project", JSONObject().put("project_id", projectId ?: JSONObject.NULL))
    }

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
        Store.recordSend()
        val body = JSONObject().put("text", text)
        if (files.isNotEmpty()) {
            val arr = org.json.JSONArray()
            for (f in files) arr.put(JSONObject().put("kind", f.kind).put("mime", f.mime).put("name", f.name).put("data", f.b64))
            body.put("attachments", arr)
        }
        return json("POST", "/conversations/$conversationId/messages", body, readTimeoutMs = if (files.any { it.kind == "doc" }) 180000 else 45000).getString("job_id")
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
            notes = j.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            progress = j.optJSONArray("progress")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            actions = j.optJSONArray("actions")?.let { arr ->
                (0 until arr.length()).mapNotNull { k ->
                    val o = arr.optJSONObject(k) ?: return@mapNotNull null
                    val ar = o.optJSONArray("args")
                    AgentAction(o.optString("type"), if (ar == null) emptyList() else (0 until ar.length()).map { ar.optString(it) })
                }
            } ?: emptyList(),
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
    e is ApiException && e.code == "unsupported_file" -> ctx.getString(R.string.attach_unsupported)
    e is ApiException && e.code == "empty_doc" -> ctx.getString(R.string.attach_empty)
    e is ApiException && e.code == "too_many_notes" -> ctx.getString(R.string.sugar_too_many)
    e is ApiException && e.code == "too_many_sources" -> ctx.getString(R.string.notella_too_many)
    e is ApiException && e.code == "image_limit" -> ctx.getString(R.string.attach_image_limit)
    e is ApiException && (e.code == "bad_attachment" || e.code == "attachment_too_large" || e.code == "too_many_attachments" || e.status == 413) -> ctx.getString(R.string.attach_fail)
    e is ApiException && e.code == "not_found" -> ctx.getString(R.string.ai_voice_gone)
    e is ApiException && (e.code == "voice_unavailable" || e.code == "stt_unavailable") -> ctx.getString(R.string.ai_voice_fail)
    e is ApiException -> ctx.getString(R.string.err_generic)
    e is IOException -> ctx.getString(R.string.err_network)
    else -> ctx.getString(R.string.err_generic)
}
