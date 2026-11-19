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

data class Conversation(val id: String, val title: String, val updatedAt: Long)
data class Msg(val role: String, val text: String)
data class PollResult(
    val status: String,
    val error: String?,
    val full: Boolean,
    val text: String,
    val delta: String,
    val next: Int,
)

object Api {
    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        readTimeoutMs: Int = 15000,
    ): JSONObject = withContext(Dispatchers.IO) {
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
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (status !in 200..299) throw ApiException(json.optString("error", "http_$status"), status)
            json
        } finally {
            conn.disconnect()
        }
    }

    private fun newSecret(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private suspend fun login() {
        val secret = Store.guestSecret ?: newSecret().also { Store.guestSecret = it }
        val r = call("POST", "/auth/guest", JSONObject().put("secret", secret), auth = false)
        Store.token = r.getString("token")
    }

    /** Calls with the saved token; logs in first if there is none, and once more if the server says 401. */
    private suspend fun authed(
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = 15000,
    ): JSONObject {
        if (Store.token == null) login()
        return try {
            call(method, path, body, readTimeoutMs = readTimeoutMs)
        } catch (e: ApiException) {
            if (e.status == 401) {
                login()
                call(method, path, body, readTimeoutMs = readTimeoutMs)
            } else throw e
        }
    }

    suspend fun conversations(): List<Conversation> {
        val arr = authed("GET", "/conversations").getJSONArray("conversations")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Conversation(o.getString("id"), o.getString("title"), o.getLong("updated_at"))
        }
    }

    suspend fun createConversation(): String = authed("POST", "/conversations", JSONObject()).getString("id")

    suspend fun deleteConversation(id: String) {
        authed("DELETE", "/conversations/$id")
    }

    suspend fun messages(id: String): List<Msg> {
        val arr = authed("GET", "/conversations/$id/messages").getJSONArray("messages")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Msg(o.getString("role"), o.getString("content"))
        }
    }

    /** Starts a reply and returns the job id right away. */
    suspend fun sendMessage(conversationId: String, text: String): String =
        authed("POST", "/conversations/$conversationId/messages", JSONObject().put("text", text)).getString("job_id")

    /** Long-poll: the server holds the request up to ~20s until there is something new. */
    suspend fun poll(jobId: String, since: Int): PollResult {
        val j = authed("GET", "/jobs/$jobId?since=$since", readTimeoutMs = 35000)
        return PollResult(
            status = j.getString("status"),
            error = if (j.isNull("error")) null else j.getString("error"),
            full = j.optBoolean("full", false),
            text = j.optString("text", ""),
            delta = j.optString("delta", ""),
            next = j.optInt("next", since),
        )
    }

    suspend fun cancel(jobId: String) {
        authed("POST", "/jobs/$jobId/cancel", JSONObject())
    }
}

fun errorMessage(ctx: Context, e: Throwable): String = when {
    e is ApiException && e.code == "daily_limit" -> ctx.getString(R.string.err_daily_limit)
    e is ApiException && e.code == "reply_in_progress" -> ctx.getString(R.string.err_busy)
    e is ApiException -> ctx.getString(R.string.err_generic)
    e is IOException -> ctx.getString(R.string.err_network)
    else -> ctx.getString(R.string.err_generic)
}
