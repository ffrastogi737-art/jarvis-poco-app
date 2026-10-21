package com.personal.workoutcoach.core.coach

import com.personal.workoutcoach.core.plan.PlannedWorkout

enum class ReplySourceKind { LOCAL_RULES, LOCAL_SAFETY, REMOTE_AI }

data class ReplySource(val kind: ReplySourceKind, val provider: String? = null, val model: String? = null) {
    fun label(): String = when (kind) {
        ReplySourceKind.LOCAL_RULES -> "Offline rule-based coach"
        ReplySourceKind.LOCAL_SAFETY -> "Safety rules (offline)"
        ReplySourceKind.REMOTE_AI -> "AI · ${provider ?: "remote"}" + (model?.let { " ($it)" } ?: "")
    }
}

data class CoachOutcome(
    val reply: CoachReply,
    val source: ReplySource,
    /** Validated, startable workout (if the reply contained one that survived validation). */
    val workout: PlannedWorkout?,
    /** Set when the remote AI failed and the local fallback answered instead. */
    val error: CoachError? = null,
    val warnings: List<String> = emptyList(),
    val usage: TokenUsage? = null,
)

/**
 * Order of operations: deterministic safety screen → remote AI (if configured) → validation.
 * Any remote failure falls back to the local rule-based coach and reports the error.
 */
class CoachOrchestrator(private val local: LocalRuleBasedCoach = LocalRuleBasedCoach()) {

    suspend fun respond(req: CoachRequest, remote: FitnessCoachProvider?, validator: RecommendationValidator): CoachOutcome {
        val safety = SafetyScreen.check(req.message)
        if (safety != SafetyLevel.NONE) {
            return CoachOutcome(local.safetyReply(safety, req.context.language), ReplySource(ReplySourceKind.LOCAL_SAFETY), null)
        }
        if (remote == null || !remote.isRemote) return localOutcome(req, validator, null)

        return when (val r = remote.reply(req)) {
            is CoachResult.Failure -> localOutcome(req, validator, r.error)
            is CoachResult.Success -> {
                val warnings = mutableListOf<String>()
                var reply = r.reply
                var workout: PlannedWorkout? = null
                reply.workout?.let { rec ->
                    val v = validator.validate(rec)
                    warnings += v.warnings
                    workout = v.workout
                    reply = reply.copy(workout = v.workout?.toRecommendation(rec.explanation))
                }
                // Memory notes are optional and bounded; anything odd is dropped.
                reply.memoryNote?.let { n ->
                    if (n.category !in MEMORY_CATEGORIES || n.content.isBlank() || n.content.length > 200) reply = reply.copy(memoryNote = null)
                }
                CoachOutcome(reply, ReplySource(ReplySourceKind.REMOTE_AI, r.provider, r.model), workout, null, warnings, r.usage)
            }
        }
    }

    private fun localOutcome(req: CoachRequest, validator: RecommendationValidator, error: CoachError?): CoachOutcome {
        val reply = local.respond(req)
        val workout = reply.workout?.let { validator.validate(it).workout }
        return CoachOutcome(reply, ReplySource(ReplySourceKind.LOCAL_RULES), workout, error)
    }

    companion object {
        val MEMORY_CATEGORIES = setOf("PROFILE", "TRAINING", "BEHAVIOR", "CONVERSATION")
    }
}

/** Keeps the last [keep] turns verbatim and condenses older user turns into a short summary. */
object ConversationWindow {
    fun build(turns: List<ChatTurn>, keep: Int = 12): Pair<List<ChatTurn>, String?> {
        if (turns.size <= keep) return turns to null
        val older = turns.dropLast(keep).filter { it.role == "user" }.takeLast(6)
        val summary = if (older.isEmpty()) null else "Earlier the user said: " + older.joinToString(" | ") { it.content.take(80) }
        return turns.takeLast(keep) to summary
    }
}
