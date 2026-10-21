package com.personal.workoutcoach.core.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class RemoteProvider(val wireName: String, val displayName: String) {
    GEMINI("gemini", "Google Gemini"), GROQ("groq", "Groq"), OPENROUTER("openrouter", "OpenRouter");
    companion object { fun fromWire(s: String?) = entries.firstOrNull { it.wireName == s } }
}

@Serializable
private data class BackendRequest(
    val provider: String,
    val requestId: String,
    val language: String,
    val context: CoachContext?,
    val messages: List<ChatTurn>,
)

/**
 * Talks to the user's own backend (see /backend), which holds the provider API keys.
 * The app only ever holds an app token for that backend — never a provider key.
 * No automatic retries: a timed-out POST may already have been billed. Manual retries reuse
 * [CoachRequest.requestId] so the backend can return the cached result instead of paying twice.
 */
class RemoteBackendCoach(
    private val baseUrl: String,
    private val appToken: String,
    val provider: RemoteProvider,
    /** When false, only language/equipment/plan options are sent, not history or memory. */
    private val shareHistory: Boolean,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) : FitnessCoachProvider {
    override val id = provider.wireName
    override val isRemote = true

    override suspend fun reply(request: CoachRequest): CoachResult = withContext(Dispatchers.IO) { call(request) }

    internal fun buildBody(request: CoachRequest): String {
        val ctx = if (shareHistory) request.context else request.context.copy(
            completedThisWeek = 0, plannedThisWeek = 0, missedRecent = 0, daysSinceLastWorkout = null, recentPostpones = 0,
            lastFeedback = null, memory = emptyList(), recentSessions = emptyList(), weeklySummary = null, conversationSummary = null,
        )
        val (window, _) = ConversationWindow.build(request.history)
        val turns = (if (shareHistory) window else emptyList()) + ChatTurn("user", request.message.take(2000))
        return AiResponseParser.json.encodeToString(
            BackendRequest.serializer(),
            BackendRequest(provider.wireName, request.requestId, request.context.language, ctx, turns),
        )
    }

    private fun call(request: CoachRequest): CoachResult {
        if (baseUrl.isBlank() || appToken.isBlank()) return CoachResult.Failure(CoachError.NotConfigured)
        val url = try { URL(baseUrl.trimEnd('/') + "/v1/coach") } catch (e: Exception) { return CoachResult.Failure(CoachError.NotConfigured) }
        if (url.protocol != "https" && url.host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) {
            return CoachResult.Failure(CoachError.Network("Backend URL must use https"))
        }
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $appToken")
            conn.setRequestProperty("Idempotency-Key", request.requestId)
            conn.outputStream.use { it.write(buildBody(request).toByteArray()) }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return CoachResult.Failure(mapError(code, body, conn.getHeaderField("Retry-After")))
            return parseSuccess(body)
        } catch (e: SocketTimeoutException) {
            return CoachResult.Failure(CoachError.Timeout)
        } catch (e: IOException) {
            return CoachResult.Failure(CoachError.Network(Redactor.redact(e.javaClass.simpleName)))
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseSuccess(body: String): CoachResult {
        val root = runCatching { AiResponseParser.json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return CoachResult.Failure(CoachError.Malformed("Backend response is not JSON"))
        val replyEl = root["reply"] as? JsonObject ?: return CoachResult.Failure(CoachError.Malformed("Missing reply"))
        val reply = AiResponseParser.parse(replyEl.toString()).getOrElse { return CoachResult.Failure(CoachError.Malformed(it.message ?: "invalid")) }
        val usage = (root["usage"] as? JsonObject)?.let {
            TokenUsage(it["promptTokens"]?.jsonPrimitive?.intOrNull, it["completionTokens"]?.jsonPrimitive?.intOrNull)
        }
        val model = runCatching { root["model"]?.jsonPrimitive?.content }.getOrNull()
        return CoachResult.Success(reply, provider.wireName, model, usage)
    }

    internal fun mapError(code: Int, body: String, retryAfter: String?): CoachError {
        val err = runCatching { AiResponseParser.json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        return when {
            code == 401 || code == 403 -> CoachError.Unauthorized
            err == "budget_exceeded" -> CoachError.BudgetExceeded
            code == 429 -> CoachError.RateLimited(retryAfter?.toIntOrNull())
            err == "provider_unavailable" -> CoachError.ProviderUnavailable(provider.wireName)
            code == 504 -> CoachError.Timeout
            err == "malformed_model_output" -> CoachError.Malformed("Model output rejected by backend")
            else -> CoachError.Server(code)
        }
    }
}

