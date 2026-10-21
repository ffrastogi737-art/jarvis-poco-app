package com.personal.workoutcoach.core.coach

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object AiResponseParser {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = true }

    const val MAX_MESSAGE_CHARS = 4000

    /** Parses model output into a [CoachReply]. Tolerates code fences and leading prose; nothing else. */
    fun parse(raw: String): Result<CoachReply> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return Result.failure(IllegalArgumentException("No JSON object in reply"))
        val reply = try {
            json.decodeFromString(CoachReply.serializer(), raw.substring(start, end + 1))
        } catch (e: SerializationException) {
            return Result.failure(IllegalArgumentException("Invalid reply JSON: ${e.message?.take(120)}"))
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("Invalid reply JSON: ${e.message?.take(120)}"))
        }
        if (reply.message.isBlank()) return Result.failure(IllegalArgumentException("Reply message is empty"))
        if (reply.message.length > MAX_MESSAGE_CHARS) return Result.failure(IllegalArgumentException("Reply message too long"))
        return Result.success(reply)
    }

    fun encode(reply: CoachReply): String = json.encodeToString(CoachReply.serializer(), reply)
    fun decodeStored(s: String): CoachReply? = runCatching { json.decodeFromString(CoachReply.serializer(), s) }.getOrNull()
}

/** Removes things that look like credentials before anything is logged or shown in an error. */
object Redactor {
    private val patterns = listOf(
        Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
        Regex("sk-[A-Za-z0-9_-]{10,}"),
        Regex("gsk_[A-Za-z0-9]{10,}"),
        Regex("AIza[0-9A-Za-z_-]{20,}"),
        Regex("(?i)(api[_-]?key|token|secret)([\"'=:\\s]+)[A-Za-z0-9._-]{8,}"),
    )

    fun redact(s: String): String = patterns.fold(s) { acc, r ->
        r.replace(acc) { m -> if (m.groupValues.size > 2 && m.groupValues[1].isNotEmpty()) m.groupValues[1] + m.groupValues[2] + "[REDACTED]" else "[REDACTED]" }
    }
}
