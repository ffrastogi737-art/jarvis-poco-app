package com.personal.workoutcoach.data.repo

import androidx.room.withTransaction
import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.coach.RecommendationValidator
import com.personal.workoutcoach.core.equipment.Compatibility
import com.personal.workoutcoach.core.equipment.EquipmentItem
import com.personal.workoutcoach.core.equipment.Inventory
import com.personal.workoutcoach.core.equipment.defaultInventory
import com.personal.workoutcoach.core.model.MissReason
import com.personal.workoutcoach.core.model.ScheduleStatus
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.model.SessionStatus
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.*
import com.personal.workoutcoach.core.reminders.ReminderType
import com.personal.workoutcoach.data.db.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Profile, equipment, schedule and workout sessions. All multi-row changes run in transactions.
 * [now] is injectable so tests can control the date.
 */
class TrainingRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    // ---------------- Profile

    val profile: Flow<UserProfileEntity?> = db.profile().observe()
    suspend fun profileOrDefault() = db.profile().get() ?: UserProfileEntity()

    suspend fun completeOnboarding(p: UserProfileEntity) {
        db.withTransaction {
            db.profile().upsert(p.copy(onboarded = true, createdAt = now()))
            if (db.equipment().all().isEmpty()) defaultInventory().forEach { db.equipment().insert(it.toEntity()) }
            if (db.reminders().all().isEmpty()) {
                db.reminders().upsert(ReminderConfigEntity(ReminderType.TRAINING_DAY.name, p.preferredTimeMinutes, true))
                db.reminders().upsert(ReminderConfigEntity(ReminderType.DAILY_CHECKIN.name, 9 * 60, false))
                db.reminders().upsert(ReminderConfigEntity(ReminderType.MISSED_FOLLOWUP.name, 10 * 60, true))
            }
        }
        ensureSchedule()
    }

    suspend fun updateProfile(p: UserProfileEntity) {
        val old = db.profile().get()
        db.profile().upsert(p)
        if (old != null && old.trainingDays != p.trainingDays) {
            db.withTransaction { db.schedule().deleteFuturePlanned(today().toEpochDay()) }
            ensureSchedule()
        }
    }

    // ---------------- Equipment (history keeps snapshots, so removal never rewrites past sessions)

    val equipment: Flow<List<EquipmentEntity>> = db.equipment().observeAll()
    suspend fun inventory() = Inventory(db.equipment().all().map { it.toItem() })

    /** Returns validation errors; empty means saved. */
    suspend fun saveEquipment(item: EquipmentItem): List<String> {
        val errors = item.validate()
        if (errors.isNotEmpty()) return errors
        if (item.id == 0L) db.equipment().insert(item.toEntity())
        else {
            val existing = db.equipment().all().firstOrNull { it.id == item.id }
            db.equipment().update(item.toEntity(existing?.createdAt ?: now()))
        }
        return emptyList()
    }

    suspend fun deleteEquipment(id: Long) = db.equipment().delete(id)

    // ---------------- Schedule

    val schedule: Flow<List<ScheduleEntryEntity>> = db.schedule().observeAll()

    /** Creates planned entries for training days and marks past unfinished ones as missed. Idempotent. */
    suspend fun ensureSchedule() {
        val p = db.profile().get()?.takeIf { it.onboarded } ?: return
        val today = today()
        val start = maxOf(dateOf(p.createdAt), today.minusDays(14))
        db.withTransaction {
            for (d in ScheduleEngine.plannedDates(p.days, start, today.plusDays(14))) {
                db.schedule().insertIgnore(ScheduleEntryEntity(date = d.toEpochDay(), status = ScheduleStatus.PLANNED.name))
            }
            for (e in db.schedule().all()) {
                if (e.status == ScheduleStatus.PLANNED.name && e.date < today.toEpochDay()) db.schedule().update(e.copy(status = ScheduleStatus.MISSED.name))
            }
        }
    }

    suspend fun todayEntry() = db.schedule().byDate(today().toEpochDay())

    suspend fun unresolvedMissed(): List<ScheduleEntryEntity> {
        val cutoff = today().minusDays(7).toEpochDay()
        return db.schedule().all().filter { it.status == ScheduleStatus.MISSED.name && it.date >= cutoff }
    }

    suspend fun rescheduleOptions(): List<LocalDate> {
        val occupied = db.schedule().all()
            .filter { it.status == ScheduleStatus.PLANNED.name || it.status == ScheduleStatus.DONE.name }
            .map { LocalDate.ofEpochDay(it.date) }.toSet()
        return ScheduleEngine.rescheduleOptions(today(), occupied)
    }

    suspend fun reschedule(entryId: Long, newDate: LocalDate, reason: MissReason) = db.withTransaction {
        val e = db.schedule().byId(entryId) ?: return@withTransaction false
        if (db.schedule().byDate(newDate.toEpochDay()) != null) return@withTransaction false
        db.schedule().update(e.copy(status = ScheduleStatus.RESCHEDULED.name, missReason = reason.name, movedToDate = newDate.toEpochDay()))
        db.schedule().insertIgnore(ScheduleEntryEntity(date = newDate.toEpochDay(), status = ScheduleStatus.PLANNED.name, movedFromDate = e.date)) > 0
    }

    /** "Skip it and continue with the next planned session" — never doubles up. */
    suspend fun skipMissed(entryId: Long, reason: MissReason) {
        val e = db.schedule().byId(entryId) ?: return
        db.schedule().update(e.copy(status = ScheduleStatus.SKIPPED.name, missReason = reason.name))
    }

    suspend fun postponeCount(): Int {
        val cutoff = today().minusDays(14).toEpochDay()
        return db.schedule().all().count { it.date >= cutoff && (it.status == ScheduleStatus.MISSED.name || it.status == ScheduleStatus.RESCHEDULED.name) }
    }

    // ---------------- Planning

    suspend fun daysSinceLastWorkout(): Int? = db.sessions().lastCounted()?.let { ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it.plannedDate), today()).toInt() }

    suspend fun currentPhase(): TrainingPhase {
        val soreness = db.feedback().recent(3).mapNotNull { it.soreness }.takeIf { it.isNotEmpty() }?.average()
        return PhasePolicy.phase(db.sessions().countedCount(), daysSinceLastWorkout(), soreness)
    }

    suspend fun recoverySignal(): RecoverySignal {
        val recent = db.feedback().recent(1).firstOrNull()?.takeIf { now() - it.timestamp < 3 * 86_400_000L }
        return RecoverySignal(soreness = recent?.soreness, energy = recent?.energy, daysSinceLastWorkout = daysSinceLastWorkout())
    }

    suspend fun historyMap(): Map<String, List<ExerciseRecord>> =
        db.sessions().countedHistory().flatMap { s -> s.orderedExercises.filter { it.sets.isNotEmpty() }.map { it.toRecord(LocalDate.ofEpochDay(s.session.plannedDate)) } }
            .groupBy { it.exerciseId }

    suspend fun plannerInput(mode: SessionMode, maxMinutes: Int? = null): PlannerInput {
        val p = profileOrDefault()
        val lastTemplate = db.sessions().countedHistory().lastOrNull { it.session.templateKey != null }?.session?.templateKey
        return PlannerInput(
            inventory = inventory(), phase = currentPhase(), mode = mode, templateKey = WorkoutPlanner.nextTemplate(lastTemplate),
            excludedExerciseIds = p.excluded, history = historyMap(), recovery = recoverySignal(), maxMinutes = maxMinutes,
        )
    }

    suspend fun plan(mode: SessionMode, maxMinutes: Int? = null): PlannedWorkout = WorkoutPlanner.plan(plannerInput(mode, maxMinutes))

    suspend fun validator() = RecommendationValidator(inventory(), currentPhase(), profileOrDefault().excluded, historyMap())

    // ---------------- Sessions

    val activeSession: Flow<SessionWithExercises?> = db.sessions().observeActive()
    val history: Flow<List<SessionWithExercises>> = db.sessions().observeHistory()

    /** Starts a session, or returns the already active one (prevents duplicates). */
    suspend fun startSession(plan: PlannedWorkout): Long = db.withTransaction {
        db.sessions().active()?.let { return@withTransaction it.id }
        val id = db.sessions().insertSession(
            WorkoutSessionEntity(
                plannedDate = today().toEpochDay(), templateKey = plan.templateKey, mode = plan.mode.name, title = plan.title,
                source = plan.source.name, status = SessionStatus.IN_PROGRESS.name, startedAt = now(),
            ),
        )
        plan.exercises.forEachIndexed { i, pe -> db.sessions().insertExercise(pe.toEntity(id, i)) }
        id
    }

    private fun PlannedExercise.toEntity(sessionId: Long, index: Int) = ExercisePerformanceEntity(
        sessionId = sessionId, exerciseId = exercise.id, exerciseName = exercise.name, orderIndex = index,
        plannedSets = prescription.sets, repsMin = prescription.repsMin, repsMax = prescription.repsMax, seconds = prescription.seconds,
        restSeconds = prescription.restSeconds, plannedLoadKg = loadKg, progressionNote = note,
    )

    /** Records the next set. Actual values only — nothing is assumed from timers. */
    suspend fun logSet(exercisePerformanceId: Long, weightKg: Double?, reps: Int?, seconds: Int?): Long = db.withTransaction {
        val ex = db.sessions().exercise(exercisePerformanceId) ?: error("exercise not found")
        val sets = db.sessions().sets(exercisePerformanceId)
        val id = db.sessions().upsertSet(SetPerformanceEntity(exercisePerformanceId = exercisePerformanceId, setNumber = sets.size + 1, weightKg = weightKg, reps = reps, seconds = seconds, completedAt = now()))
        if (sets.size + 1 >= ex.plannedSets && ex.status == "PENDING") db.sessions().updateExercise(ex.copy(status = "DONE"))
        id
    }

    suspend fun updateSet(set: SetPerformanceEntity) = db.sessions().updateSet(set)

    suspend fun deleteSet(set: SetPerformanceEntity) = db.withTransaction {
        db.sessions().deleteSet(set)
        db.sessions().exercise(set.exercisePerformanceId)?.let { ex ->
            if (ex.status == "DONE" && db.sessions().sets(ex.id).size < ex.plannedSets) db.sessions().updateExercise(ex.copy(status = "PENDING"))
        }
    }

    suspend fun skipExercise(exercisePerformanceId: Long) {
        val ex = db.sessions().exercise(exercisePerformanceId) ?: return
        db.sessions().updateExercise(ex.copy(status = "SKIPPED"))
    }

    /** Alternatives the current equipment supports (easier/harder variants and same movement pattern). */
    suspend fun substitutes(exerciseId: String, excludeIds: Set<String>): List<String> {
        val ex = ExerciseCatalog.get(exerciseId) ?: return emptyList()
        val inv = inventory()
        val excluded = profileOrDefault().excluded + excludeIds
        val candidates = (ex.easier + ex.harder).mapNotNull(ExerciseCatalog::get) + ExerciseCatalog.all.filter { it.pattern == ex.pattern }
        return candidates.distinctBy { it.id }.filter { it.id != ex.id && it.id !in excluded && inv.isCompatible(it) }.map { it.id }
    }

    /** Swaps an exercise that has no sets logged yet. */
    suspend fun substitute(exercisePerformanceId: Long, newExerciseId: String): Boolean = db.withTransaction {
        val old = db.sessions().exercise(exercisePerformanceId) ?: return@withTransaction false
        if (db.sessions().sets(old.id).isNotEmpty()) return@withTransaction false
        val ex = ExerciseCatalog.get(newExerciseId) ?: return@withTransaction false
        val compat = inventory().check(ex) as? Compatibility.Ok ?: return@withTransaction false
        val rx = Progression.base(ex, currentPhase()).let { it.copy(sets = minOf(it.sets, old.plannedSets.coerceAtLeast(1))) }
        db.sessions().updateExercise(
            old.copy(
                exerciseId = ex.id, exerciseName = ex.name, plannedSets = rx.sets, repsMin = rx.repsMin, repsMax = rx.repsMax,
                seconds = rx.seconds, restSeconds = rx.restSeconds, plannedLoadKg = WorkoutPlanner.chooseLoad(compat.loadOptionsKg, historyMap()[ex.id].orEmpty()),
                substitutedFrom = old.substitutedFrom ?: old.exerciseId, status = "PENDING", progressionNote = null,
            ),
        )
        true
    }

    suspend fun setCurrentIndex(sessionId: Long, index: Int) {
        val s = db.sessions().session(sessionId) ?: return
        db.sessions().updateSession(s.copy(currentIndex = index))
    }

    suspend fun pause(sessionId: Long) {
        val s = db.sessions().session(sessionId) ?: return
        if (s.status == SessionStatus.IN_PROGRESS.name) db.sessions().updateSession(s.copy(status = SessionStatus.PAUSED.name, pausedAt = now()))
    }

    suspend fun resume(sessionId: Long) {
        val s = db.sessions().session(sessionId) ?: return
        if (s.status == SessionStatus.PAUSED.name) db.sessions().updateSession(
            s.copy(status = SessionStatus.IN_PROGRESS.name, pausedAt = null, pausedTotalMs = s.pausedTotalMs + (now() - (s.pausedAt ?: now()))),
        )
    }

    /**
     * Ends the session. COMPLETED when every non-skipped exercise has at least one set (this is the
     * minimum criterion, so Minimum Mode counts fully); PARTIAL when some sets were logged; otherwise ABANDONED.
     */
    suspend fun finish(sessionId: Long): SessionStatus {
        val status = db.withTransaction {
            val full = db.sessions().withExercises(sessionId) ?: return@withTransaction SessionStatus.ABANDONED
            val s = full.session
            if (s.status != SessionStatus.IN_PROGRESS.name && s.status != SessionStatus.PAUSED.name) return@withTransaction SessionStatus.valueOf(s.status)
            val active = full.exercises.filter { it.exercise.status != "SKIPPED" }
            val status = when {
                active.isNotEmpty() && active.all { it.sets.isNotEmpty() } -> SessionStatus.COMPLETED
                full.exercises.any { it.sets.isNotEmpty() } -> SessionStatus.PARTIAL
                else -> SessionStatus.ABANDONED
            }
            val end = now()
            val pausedExtra = s.pausedAt?.let { end - it } ?: 0
            db.sessions().updateSession(s.copy(status = status.name, endedAt = end, pausedAt = null, pausedTotalMs = s.pausedTotalMs + pausedExtra))
            if (status != SessionStatus.ABANDONED) {
                val entry = db.schedule().byDate(s.plannedDate)
                if (entry == null) db.schedule().insertIgnore(ScheduleEntryEntity(date = s.plannedDate, status = ScheduleStatus.DONE.name, sessionId = s.id))
                else db.schedule().update(entry.copy(status = ScheduleStatus.DONE.name, sessionId = s.id))
            }
            status
        }
        db.profile().get()?.let { db.profile().upsert(it.copy(phase = currentPhase().name)) }
        return status
    }

    /** Removes a wrongly recorded session (user correction). Its schedule day becomes planned/missed again. */
    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        val today = today().toEpochDay()
        for (e in db.schedule().bySession(sessionId)) {
            db.schedule().update(e.copy(sessionId = null, status = if (e.date < today) ScheduleStatus.MISSED.name else ScheduleStatus.PLANNED.name))
        }
        db.sessions().deleteSession(sessionId)
    }

    suspend fun sessionDetail(sessionId: Long) = db.sessions().withExercises(sessionId)

    /** Most recent counted performance of [exerciseId] outside [excludeSessionId]. */
    suspend fun previousPerformance(exerciseId: String, excludeSessionId: Long): ExerciseWithSets? =
        db.sessions().countedHistory().asReversed().filter { it.session.id != excludeSessionId }
            .firstNotNullOfOrNull { s -> s.exercises.firstOrNull { it.exercise.exerciseId == exerciseId && it.sets.isNotEmpty() } }

    // ---------------- Feedback & measurements

    suspend fun addFeedback(energy: Int?, motivation: Int?, soreness: Int?, notes: String, sessionId: Long? = null) =
        db.feedback().insert(UserFeedbackEntity(timestamp = now(), sessionId = sessionId, energy = energy, motivation = motivation, soreness = soreness, notes = notes.take(500)))

    val feedback = db.feedback().observe()
    val measurements = db.measurements().observe()
    suspend fun addMeasurement(type: String, value: Double, unit: String) = db.measurements().insert(ProgressMeasurementEntity(timestamp = now(), type = type, value = value, unit = unit))
    suspend fun deleteMeasurement(id: Long) = db.measurements().delete(id)
}
