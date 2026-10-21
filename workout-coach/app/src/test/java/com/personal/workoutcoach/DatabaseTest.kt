package com.personal.workoutcoach

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.workoutcoach.core.equipment.EquipmentItem
import com.personal.workoutcoach.core.equipment.EquipmentType
import com.personal.workoutcoach.core.model.MissReason
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.model.SessionStatus
import com.personal.workoutcoach.data.db.*
import com.personal.workoutcoach.data.repo.TrainingRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: TrainingRepository
    // Monday 2026-10-19 09:00 UTC
    private var nowMs = LocalDate.of(2026, 10, 19).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 86_400_000L

    private fun open(name: String? = null) =
        (if (name == null) Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java) else Room.databaseBuilder(context, AppDatabase::class.java, name))
            .allowMainThreadQueries().build()

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = open()
        repo = TrainingRepository(db, { nowMs }, ZoneOffset.UTC)
        repo.completeOnboarding(UserProfileEntity())
    }

    @After fun tearDown() = db.close()

    @Test fun onboardingCreatesDefaultsAndSchedule() = runBlocking {
        val eq = db.equipment().all()
        assertEquals(2, eq.size) // 2×17.5 kg dumbbells item + bodyweight
        assertEquals(17.5, eq.first { it.type == "FIXED_DUMBBELL" }.weightKg!!, 0.0)
        assertEquals(3, db.reminders().all().size)
        val planned = db.schedule().all().map { LocalDate.ofEpochDay(it.date) }
        assertTrue(LocalDate.of(2026, 10, 19) in planned) // Monday
        assertFalse(LocalDate.of(2026, 10, 20) in planned) // Tuesday rest
    }

    @Test fun sessionLifecyclePersistsActualPerformance() = runBlocking {
        val plan = repo.plan(SessionMode.MINIMUM)
        val id = repo.startSession(plan)
        assertEquals("duplicate start returns the active session", id, repo.startSession(plan))
        assertEquals(1, db.sessions().activeCount())

        val full = db.sessions().withExercises(id)!!
        assertEquals(plan.exercises.size, full.exercises.size)
        for (ex in full.orderedExercises) repo.logSet(ex.exercise.id, ex.exercise.plannedLoadKg, 9, null)
        repo.logSet(full.orderedExercises[0].exercise.id, 17.5, 7, null)

        nowMs += 2 * 60_000; repo.pause(id)
        nowMs += 5 * 60_000; repo.resume(id)
        nowMs += 10 * 60_000
        assertEquals(SessionStatus.COMPLETED, repo.finish(id))

        val done = db.sessions().withExercises(id)!!
        assertEquals("COMPLETED", done.session.status)
        assertEquals(12 * 60_000L, done.session.activeDurationMs(nowMs)) // paused time excluded
        val first = done.orderedExercises[0]
        assertEquals(listOf(9, 7), first.sets.sortedBy { it.setNumber }.map { it.reps })
        assertEquals(first.exercise.plannedSets, 2) // planned stored separately from actual
        assertEquals("DONE", db.schedule().byDate(LocalDate.of(2026, 10, 19).toEpochDay())!!.status)
        assertNull(db.sessions().active())
    }

    @Test fun partialAndAbandonedSessions() = runBlocking {
        val id = repo.startSession(repo.plan(SessionMode.STANDARD))
        val ex = db.sessions().withExercises(id)!!.orderedExercises.first()
        repo.logSet(ex.exercise.id, 17.5, 8, null)
        assertEquals(SessionStatus.PARTIAL, repo.finish(id))
        val id2 = repo.startSession(repo.plan(SessionMode.MINIMUM))
        assertEquals(SessionStatus.ABANDONED, repo.finish(id2))
    }

    @Test fun skippedExercisesDontBlockCompletion() = runBlocking {
        val id = repo.startSession(repo.plan(SessionMode.MINIMUM))
        val exs = db.sessions().withExercises(id)!!.orderedExercises
        repo.skipExercise(exs[0].exercise.id)
        exs.drop(1).forEach { repo.logSet(it.exercise.id, it.exercise.plannedLoadKg, 8, null) }
        assertEquals(SessionStatus.COMPLETED, repo.finish(id))
    }

    @Test fun uniqueConstraintsPreventDuplicates() = runBlocking {
        val id = repo.startSession(repo.plan(SessionMode.MINIMUM))
        val ex = db.sessions().withExercises(id)!!.orderedExercises.first().exercise
        try {
            db.sessions().insertExercise(ex.copy(id = 0))
            fail("same orderIndex twice must fail")
        } catch (_: SQLiteConstraintException) { }
        assertEquals(0L, db.schedule().insertIgnore(ScheduleEntryEntity(date = LocalDate.of(2026, 10, 19).toEpochDay(), status = "PLANNED")).coerceAtLeast(0))
    }

    @Test fun foreignKeysCascadeAndSetNull() = runBlocking {
        val id = repo.startSession(repo.plan(SessionMode.MINIMUM))
        val ex = db.sessions().withExercises(id)!!.orderedExercises.first()
        repo.logSet(ex.exercise.id, 17.5, 10, null)
        repo.finish(id)
        repo.addFeedback(3, 3, 2, "ok", id)
        try {
            db.sessions().upsertSet(SetPerformanceEntity(exercisePerformanceId = 999_999, setNumber = 1, weightKg = null, reps = 1, seconds = null, completedAt = 0))
            fail("orphan set must violate the foreign key")
        } catch (_: SQLiteConstraintException) { }

        repo.deleteSession(id)
        assertTrue(db.sessions().allExercises().isEmpty())
        assertTrue(db.sessions().allSets().isEmpty())
        assertNull(db.feedback().all().single().sessionId) // feedback kept, link cleared
        val entry = db.schedule().byDate(LocalDate.of(2026, 10, 19).toEpochDay())!!
        assertEquals("PLANNED", entry.status) // today becomes plannable again
        assertNull(entry.sessionId)
    }

    @Test fun missedSessionsRecordedAndRescheduledWithoutDoubling() = runBlocking {
        nowMs += 3 * day // Thursday; Monday + Wednesday missed
        repo.ensureSchedule()
        val missed = repo.unresolvedMissed()
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21)), missed.map { LocalDate.ofEpochDay(it.date) })
        val options = repo.rescheduleOptions()
        assertTrue(options.isNotEmpty())
        assertFalse(LocalDate.of(2026, 10, 23) in options) // Friday already planned: never doubled up
        val target = options.first()
        assertTrue(repo.reschedule(missed[1].id, target, MissReason.NO_TIME))
        repo.skipMissed(missed[0].id, MissReason.UNKNOWN)
        val all = db.schedule().all()
        assertEquals("RESCHEDULED", all.first { it.id == missed[1].id }.status)
        assertEquals("SKIPPED", all.first { it.id == missed[0].id }.status) // history kept, not deleted
        assertEquals("PLANNED", all.first { it.date == target.toEpochDay() }.status)
        assertTrue(repo.unresolvedMissed().isEmpty())
        assertEquals(2, repo.postponeCount())
    }

    @Test fun removingEquipmentPreservesHistoryAndChangesPlans() = runBlocking {
        val id = repo.startSession(repo.plan(SessionMode.STANDARD))
        db.sessions().withExercises(id)!!.orderedExercises.forEach { repo.logSet(it.exercise.id, it.exercise.plannedLoadKg, 10, null) }
        repo.finish(id)
        val before = db.sessions().withExercises(id)!!

        db.equipment().all().filter { it.type == "FIXED_DUMBBELL" }.forEach { repo.deleteEquipment(it.id) }
        assertEquals(before, db.sessions().withExercises(id)) // history untouched
        val plan = repo.plan(SessionMode.STANDARD)
        assertTrue(plan.exercises.all { it.exercise.requires.isEmpty() })

        val errors = repo.saveEquipment(EquipmentItem(type = EquipmentType.RESISTANCE_BAND, name = "Band"))
        assertTrue(errors.isNotEmpty()) // resistance required
        assertTrue(repo.saveEquipment(EquipmentItem(type = EquipmentType.RESISTANCE_BAND, name = "Red", resistanceLabel = "Medium")).isEmpty())
        assertTrue(repo.inventory().capabilities.isNotEmpty())
    }

    @Test fun substitutionOnlyBeforeSetsAndOnlyCompatible() = runBlocking {
        val id = repo.startSession(repo.plan(SessionMode.STANDARD))
        val ex = db.sessions().withExercises(id)!!.orderedExercises.first()
        val subs = repo.substitutes(ex.exercise.exerciseId, emptySet())
        assertTrue(subs.isNotEmpty())
        assertFalse("pull_up" in subs)
        assertTrue(repo.substitute(ex.exercise.id, subs.first()))
        val swapped = db.sessions().exercise(ex.exercise.id)!!
        assertEquals(subs.first(), swapped.exerciseId)
        assertEquals(ex.exercise.exerciseId, swapped.substitutedFrom)
        repo.logSet(swapped.id, swapped.plannedLoadKg, 8, null)
        assertFalse(repo.substitute(swapped.id, ex.exercise.exerciseId))
    }

    @Test fun activeSessionSurvivesReopeningDatabase() = runBlocking {
        val name = "reopen-test.db"
        context.deleteDatabase(name)
        var fileDb = open(name)
        var r = TrainingRepository(fileDb, { nowMs }, ZoneOffset.UTC)
        r.completeOnboarding(UserProfileEntity())
        val id = r.startSession(r.plan(SessionMode.MINIMUM))
        val ex = fileDb.sessions().withExercises(id)!!.orderedExercises.first()
        r.logSet(ex.exercise.id, 17.5, 11, null)
        r.setCurrentIndex(id, 1)
        fileDb.close() // simulates process death

        fileDb = open(name)
        val restored = fileDb.sessions().active()!!
        assertEquals(id, restored.id)
        assertEquals(1, restored.currentIndex)
        assertEquals(11, fileDb.sessions().withExercises(id)!!.orderedExercises.first().sets.single().reps)
        fileDb.close()
    }

    @Test fun progressionUsesRecordedHistory() = runBlocking {
        // Hit the top of the range on every set → next plan raises the rep target.
        val id = repo.startSession(repo.plan(SessionMode.STANDARD))
        val exs = db.sessions().withExercises(id)!!.orderedExercises
        exs.forEach { e -> repeat(e.exercise.plannedSets) { repo.logSet(e.exercise.id, e.exercise.plannedLoadKg, e.exercise.repsMax, e.exercise.seconds) } }
        repo.finish(id)
        nowMs += 2 * day
        repo.addFeedback(4, 4, 2, "")
        val history = repo.historyMap()
        val goblet = history["goblet_squat"]!!.single()
        assertEquals(2, goblet.sets.size)
        val input = repo.plannerInput(SessionMode.STANDARD).copy(templateKey = "A")
        val next = com.personal.workoutcoach.core.plan.WorkoutPlanner.plan(input).exercises.first { it.exercise.id == "goblet_squat" }
        assertEquals(10, next.prescription.repsMin)
        assertEquals(14, next.prescription.repsMax)
    }

    @Test fun schemaIsExportedForFutureMigrations() {
        val schema = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/schemas/com.personal.workoutcoach.data.db.AppDatabase/1.json").takeIf(File::exists) ?: File(it, "schemas/com.personal.workoutcoach.data.db.AppDatabase/1.json") }
            .firstOrNull { it.exists() }
        assertNotNull("exported Room schema v1 should exist", schema)
        val text = schema!!.readText()
        listOf("user_profile", "equipment", "workout_sessions", "exercise_performances", "set_performances", "schedule_entries",
            "chat_messages", "user_feedback", "progress_measurements", "coach_memory", "reminder_configs").forEach { assertTrue(it, text.contains("\"$it\"")) }
        assertEquals(0, AppDatabase.MIGRATIONS.size)
    }
}
