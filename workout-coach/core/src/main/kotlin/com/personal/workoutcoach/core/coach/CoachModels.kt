package com.personal.workoutcoach.core.coach

import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.plan.PlannedWorkout
import kotlinx.serialization.Serializable

// ---- Structured reply contract (shared with the backend prompt in backend/src/prompt.js) ----

@Serializable
enum class ReplyType { CHAT, WORKOUT, RESCHEDULE, PROGRESS, SAFETY, REST_DAY }

@Serializable
enum class QuickAction { START_WORKOUT, START_MINIMUM, RESCHEDULE, LOG_FEEDBACK, VIEW_PROGRESS }

@Serializable
data class RecommendedExercise(
    val exerciseId: String,
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val seconds: Int? = null,
    val restSeconds: Int? = null,
)

@Serializable
data class WorkoutRecommendation(
    val mode: SessionMode = SessionMode.STANDARD,
    val title: String = "",
    val explanation: String = "",
    val exercises: List<RecommendedExercise> = emptyList(),
    val estimatedMinutes: Int? = null,
    val modifications: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList(),
)

@Serializable
data class MemoryNote(val category: String, val content: String)

@Serializable
data class CoachReply(
    val type: ReplyType = ReplyType.CHAT,
    val message: String,
    val workout: WorkoutRecommendation? = null,
    val actions: List<QuickAction> = emptyList(),
    val safetyNotes: List<String> = emptyList(),
    /** A fact the coach proposes to remember. Stored visibly; the user can edit or delete it. */
    val memoryNote: MemoryNote? = null,
)

// ---- Request side ----

@Serializable
data class ChatTurn(val role: String, val content: String)

@Serializable
data class PlanOption(val mode: SessionMode, val title: String, val estimatedMinutes: Int, val exercises: List<RecommendedExercise>)

/** What the coach knows. Serialized and sent to the backend only when remote AI is enabled. */
@Serializable
data class CoachContext(
    val language: String,
    val style: String,
    val phase: String,
    val today: String,
    val goal: String = "Muscle building",
    val equipment: List<String> = emptyList(),
    val availableExerciseIds: List<String> = emptyList(),
    val planOptions: List<PlanOption> = emptyList(),
    val completedThisWeek: Int = 0,
    val plannedThisWeek: Int = 0,
    val missedRecent: Int = 0,
    val daysSinceLastWorkout: Int? = null,
    val recentPostpones: Int = 0,
    val lastFeedback: String? = null,
    val restrictions: String? = null,
    val memory: List<String> = emptyList(),
    val recentSessions: List<String> = emptyList(),
    val weeklySummary: String? = null,
    val conversationSummary: String? = null,
)

class CoachRequest(
    val message: String,
    val history: List<ChatTurn>,
    val context: CoachContext,
    /** Local planner hook: plan for a mode and optional minute limit. Never sent anywhere. */
    val planFor: (SessionMode, Int?) -> PlannedWorkout?,
    /** Stable id reused on manual retry so the backend can deduplicate billable calls. */
    val requestId: String,
)

data class TokenUsage(val promptTokens: Int?, val completionTokens: Int?)

sealed interface CoachError {
    val userMessage: String
    data object NotConfigured : CoachError { override val userMessage = "Remote AI is not configured." }
    data class Network(val detail: String) : CoachError { override val userMessage = "Couldn't reach the AI backend (network)." }
    data object Timeout : CoachError { override val userMessage = "The AI backend took too long. The request may or may not have been processed." }
    data class RateLimited(val retryAfterSeconds: Int?) : CoachError {
        override val userMessage = "AI rate limit reached" + (retryAfterSeconds?.let { ". Try again in ${it}s." } ?: ". Try again later.")
    }
    data object Unauthorized : CoachError { override val userMessage = "The AI backend rejected the app token. Check Settings → AI." }
    data object BudgetExceeded : CoachError { override val userMessage = "The AI backend's daily usage limit is reached." }
    data class ProviderUnavailable(val provider: String) : CoachError { override val userMessage = "AI provider '$provider' is not available on the backend." }
    data class Server(val code: Int) : CoachError { override val userMessage = "The AI backend returned an error ($code)." }
    data class Malformed(val detail: String) : CoachError { override val userMessage = "The AI reply was not in the expected format." }
}

sealed interface CoachResult {
    data class Success(val reply: CoachReply, val provider: String, val model: String?, val usage: TokenUsage?) : CoachResult
    data class Failure(val error: CoachError) : CoachResult
}

/** Provider-independent coach interface. */
interface FitnessCoachProvider {
    val id: String
    val isRemote: Boolean
    suspend fun reply(request: CoachRequest): CoachResult
}
