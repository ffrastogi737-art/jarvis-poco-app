package com.personal.workoutcoach.data.repo

import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.coach.*
import com.personal.workoutcoach.core.equipment.fmt
import com.personal.workoutcoach.core.model.Language
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.plan.WorkoutPlanner
import com.personal.workoutcoach.core.progress.FeedbackPoint
import com.personal.workoutcoach.core.progress.ProgressCalculator
import com.personal.workoutcoach.core.progress.ScheduleEntryPoint
import com.personal.workoutcoach.core.progress.WeeklyReport
import com.personal.workoutcoach.data.db.AppDatabase
import com.personal.workoutcoach.data.db.ChatMessageEntity
import com.personal.workoutcoach.data.db.CoachMemoryEntity
import com.personal.workoutcoach.data.settings.AppSettings
import com.personal.workoutcoach.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Builds coach context from local data, runs the orchestrator, persists the conversation. */
class CoachRepository(
    private val db: AppDatabase,
    private val training: TrainingRepository,
    private val settings: SettingsStore,
    private val orchestrator: CoachOrchestrator = CoachOrchestrator(),
    private val remoteFactory: (AppSettings) -> FitnessCoachProvider? = ::defaultRemote,
) {
    val messages: Flow<List<ChatMessageEntity>> = db.chat().observe()
    val memory: Flow<List<CoachMemoryEntity>> = db.memory().observe()

    suspend fun send(text: String): CoachOutcome {
        val clean = text.trim().take(2000)
        val history = turns()
        db.chat().insert(ChatMessageEntity(role = "user", content = clean, timestamp = System.currentTimeMillis()))
        return answer(clean, history, UUID.randomUUID().toString(), retryOf = null)
    }

    /** Retries the remote AI for a message that fell back to the offline coach, reusing its request id. */
    suspend fun retry(messageId: Long): CoachOutcome? {
        val m = db.chat().byId(messageId) ?: return null
        val text = m.retryText ?: return null
        return answer(text, turns(), m.retryRequestId ?: UUID.randomUUID().toString(), retryOf = m)
    }

    private suspend fun answer(text: String, history: List<ChatTurn>, requestId: String, retryOf: ChatMessageEntity?): CoachOutcome {
        val s = settings.current()
        val (_, summary) = ConversationWindow.build(history)
        val ctx = buildContext().copy(conversationSummary = summary)
        val planCache = mutableMapOf<Pair<SessionMode, Int?>, com.personal.workoutcoach.core.plan.PlannedWorkout>()
        val input = training.plannerInput(SessionMode.STANDARD)
        val request = CoachRequest(text, history, ctx, { mode, minutes ->
            planCache.getOrPut(mode to minutes) { WorkoutPlanner.plan(input.copy(mode = mode, maxMinutes = minutes)) }
        }, requestId)
        val remote = remoteFactory(s)
        val outcome = orchestrator.respond(request, remote, training.validator())

        if (outcome.source.kind == ReplySourceKind.REMOTE_AI) {
            settings.recordAiUsage(training.today().toEpochDay(), (outcome.usage?.promptTokens ?: 0) + (outcome.usage?.completionTokens ?: 0))
        }
        if (retryOf != null) {
            if (outcome.error != null) {
                db.chat().update(retryOf.copy(errorNotice = "Retry failed: ${outcome.error!!.userMessage}"))
                return outcome
            }
            db.chat().update(retryOf.copy(retryText = null, retryRequestId = null, errorNotice = retryOf.errorNotice?.let { "$it (retried successfully below)" }))
        }
        val failedRemote = outcome.error != null && remote != null
        db.chat().insert(
            ChatMessageEntity(
                role = "coach",
                content = outcome.reply.message,
                timestamp = System.currentTimeMillis(),
                source = outcome.source.kind.name,
                sourceLabel = outcome.source.label(),
                structuredJson = AiResponseParser.encode(outcome.reply),
                errorNotice = if (failedRemote) "AI unavailable: ${outcome.error!!.userMessage} Answered by the offline coach." else null,
                retryText = if (failedRemote) text else null,
                retryRequestId = if (failedRemote) requestId else null,
                warnings = outcome.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            ),
        )
        outcome.reply.memoryNote?.let { db.memory().insert(CoachMemoryEntity(category = it.category, content = it.content, source = "COACH")) }
        return outcome
    }

    private suspend fun turns(): List<ChatTurn> = db.chat().recent(30).asReversed()
        .map { ChatTurn(if (it.role == "user") "user" else "assistant", it.content.take(1000)) }

    suspend fun buildContext(): CoachContext {
        val p = training.profileOrDefault()
        val inv = training.inventory()
        val phase = training.currentPhase()
        val today = training.today()
        val weekStart = ProgressCalculator.weekStart(today)
        val schedule = db.schedule().all()
        val sessions = db.sessions().countedHistory()
        val summaries = sessions.map { it.toSummary() }
        val fb = db.feedback().recent(1).firstOrNull()
        val lang = p.languageEnum
        return CoachContext(
            language = lang.name,
            style = p.styleEnum.name,
            phase = phase.name,
            today = today.toString(),
            goal = p.goal,
            equipment = inv.summary(),
            availableExerciseIds = ExerciseCatalog.all.filter { it.id !in p.excluded && it.difficulty <= WorkoutPlanner.maxDifficulty(phase) && inv.isCompatible(it) }.map { it.id },
            planOptions = SessionMode.entries.map { training.plan(it).toPlanOption() },
            completedThisWeek = summaries.count { it.counts && !it.date.isBefore(weekStart) },
            plannedThisWeek = schedule.count { it.date >= weekStart.toEpochDay() && it.date < weekStart.plusDays(7).toEpochDay() && it.status != ScheduleStatus.RESCHEDULED.name },
            missedRecent = schedule.count { it.status == ScheduleStatus.MISSED.name && it.date >= today.minusDays(14).toEpochDay() },
            daysSinceLastWorkout = training.daysSinceLastWorkout(),
            recentPostpones = training.postponeCount(),
            lastFeedback = fb?.let {
                val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), today)
                listOfNotNull(it.energy?.let { v -> "energy $v/5" }, it.soreness?.let { v -> "soreness $v/5" }, it.motivation?.let { v -> "motivation $v/5" }).joinToString(", ") + " ($daysAgo days ago)"
            },
            restrictions = p.restrictions.ifBlank { null },
            memory = db.memory().all().take(20).map { "${it.category}: ${it.content}" },
            recentSessions = sessions.takeLast(5).map { s ->
                "${LocalDate.ofEpochDay(s.session.plannedDate)} ${s.session.title}: ${s.session.status}, ${s.session.activeDurationMs(System.currentTimeMillis()) / 60000} min; " +
                    s.orderedExercises.filter { it.sets.isNotEmpty() }.joinToString("; ") { e ->
                        e.exercise.exerciseName + " " + e.sets.sortedBy { it.setNumber }.joinToString(",") { st -> (st.reps ?: st.seconds ?: 0).toString() } +
                            (e.sets.firstOrNull()?.weightKg?.let { "@${fmt(it)}kg" } ?: "")
                    }
            },
            weeklySummary = ProgressCalculator.weeklyReport(
                weekStart, summaries, schedule.map { ScheduleEntryPoint(LocalDate.ofEpochDay(it.date), ScheduleStatus.valueOf(it.status)) },
                db.feedback().recent(20).map { FeedbackPoint(Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), it.energy, it.soreness, it.motivation) },
                lang,
            ).toText(lang),
        )
    }

    // ---- Memory editing (user-controlled)
    suspend fun addMemory(category: String, content: String) = db.memory().insert(CoachMemoryEntity(category = category, content = content.trim().take(300)))
    suspend fun updateMemory(m: CoachMemoryEntity) = db.memory().update(m.copy(content = m.content.trim().take(300), updatedAt = System.currentTimeMillis()))
    suspend fun deleteMemory(id: Long) = db.memory().delete(id)
    suspend fun clearChat() = db.chat().clear()

    companion object {
        fun defaultRemote(s: AppSettings): FitnessCoachProvider? =
            if (s.aiEnabled && s.backendUrl.isNotBlank() && s.appToken.isNotBlank()) RemoteBackendCoach(s.backendUrl, s.appToken, s.provider, s.shareHistoryWithAi) else null
    }
}

fun WeeklyReport.toText(l: Language): String {
    val hi = l == Language.HINGLISH
    if (insufficientData) return recommendation
    return buildString {
        append(if (hi) "Is hafte: " else "This week: ")
        append(if (hi) "$completedSessions sessions complete, $totalMinutes min, $missed missed." else "$completedSessions sessions completed, $totalMinutes min recorded, $missed missed.")
        if (changes.isNotEmpty()) {
            append(if (hi) "\nSame-weight comparison: " else "\nLike-for-like changes: ")
            append(changes.joinToString("; ") { "${it.name}${it.loadKg?.let { kg -> " @${fmt(kg)}kg" } ?: ""}: ${it.previousBest}→${it.currentBest} ${it.unit}" })
        }
        append("\n").append(recommendation)
    }
}

