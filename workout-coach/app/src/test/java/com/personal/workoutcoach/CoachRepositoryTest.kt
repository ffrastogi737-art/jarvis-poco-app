package com.personal.workoutcoach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.personal.workoutcoach.core.coach.*
import com.personal.workoutcoach.core.reminders.ReminderType
import com.personal.workoutcoach.data.db.AppDatabase
import com.personal.workoutcoach.data.db.ReminderConfigEntity
import com.personal.workoutcoach.data.db.UserProfileEntity
import com.personal.workoutcoach.data.repo.CoachRepository
import com.personal.workoutcoach.data.repo.DataRepository
import com.personal.workoutcoach.data.repo.TrainingRepository
import com.personal.workoutcoach.data.settings.SettingsStore
import com.personal.workoutcoach.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

private class ScriptedRemote(var result: CoachResult) : FitnessCoachProvider {
    override val id = "scripted"
    override val isRemote = true
    val requestIds = mutableListOf<String>()
    override suspend fun reply(request: CoachRequest): CoachResult { requestIds += request.requestId; return result }
}

@RunWith(AndroidJUnit4::class)
class CoachRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var training: TrainingRepository
    private lateinit var settings: SettingsStore
    private val remote = ScriptedRemote(CoachResult.Failure(CoachError.Timeout))
    private var useRemote = false

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        training = TrainingRepository(db)
        settings = SettingsStore(context)
        settings.clearAll()
        training.completeOnboarding(UserProfileEntity(language = "ENGLISH"))
    }

    @After fun tearDown() = db.close()

    private fun repo() = CoachRepository(db, training, settings, remoteFactory = { if (useRemote) remote else null })

    @Test fun offlineCoachAnswersAndPersists() = runBlocking {
        val out = repo().send("I have only 15 minutes")
        assertEquals(ReplySourceKind.LOCAL_RULES, out.source.kind)
        assertNotNull(out.workout)
        val msgs = db.chat().all()
        assertEquals(listOf("user", "coach"), msgs.map { it.role })
        assertEquals("Offline rule-based coach", msgs[1].sourceLabel)
        assertNull(msgs[1].errorNotice)
        assertNotNull(AiResponseParser.decodeStored(msgs[1].structuredJson!!)!!.workout)
    }

    @Test fun hinglishPreferenceIsUsed() = runBlocking {
        db.profile().upsert(db.profile().get()!!.copy(language = "HINGLISH"))
        val out = repo().send("aaj motivation nahi hai")
        assertTrue(out.reply.message, out.reply.message.contains("Bhai"))
    }

    @Test fun remoteFailureFallsBackKeepsDataAndRetryReusesRequestId() = runBlocking {
        useRemote = true
        val r = repo()
        r.send("Plan my workout today")
        val coachMsg = db.chat().all().last()
        assertTrue(coachMsg.errorNotice!!.startsWith("AI unavailable"))
        assertEquals("Offline rule-based coach", coachMsg.sourceLabel)
        assertEquals("Plan my workout today", coachMsg.retryText)

        remote.result = CoachResult.Success(CoachReply(message = "AI says hi", memoryNote = MemoryNote("BEHAVIOR", "Prefers evening workouts")), "gemini", "m", TokenUsage(10, 5))
        val out = r.retry(coachMsg.id)!!
        assertEquals(ReplySourceKind.REMOTE_AI, out.source.kind)
        assertEquals(remote.requestIds[0], remote.requestIds[1]) // same id → backend can deduplicate
        val msgs = db.chat().all()
        assertEquals(3, msgs.size) // user, local fallback, AI reply (nothing deleted)
        assertEquals("AI says hi", msgs.last().content)
        assertNull(db.chat().byId(coachMsg.id)!!.retryText)
        assertEquals("Prefers evening workouts", db.memory().all().single().content)
        assertEquals(1, settings.settings.first().aiRequestsToday)
        assertEquals(15, settings.settings.first().aiTokensToday)
    }

    @Test fun malformedAiWorkoutIsNotShown() = runBlocking {
        useRemote = true
        remote.result = CoachResult.Success(CoachReply(ReplyType.WORKOUT, "Do this", WorkoutRecommendation(exercises = listOf(RecommendedExercise("teleport_squat", 3)))), "groq", null, null)
        val out = repo().send("plan")
        assertNull(out.workout)
        assertNull(out.reply.workout)
        assertTrue(db.chat().all().last().warnings!!.contains("Unknown exercise"))
    }

    @Test fun safetyMessagesNeverReachRemote() = runBlocking {
        useRemote = true
        repo().send("I feel chest pain")
        assertTrue(remote.requestIds.isEmpty())
        assertEquals(ReplySourceKind.LOCAL_SAFETY.name, db.chat().all().last().source)
    }

    @Test fun contextUsesRealDataOnly() = runBlocking {
        val ctx = repo().buildContext()
        assertEquals(0, ctx.completedThisWeek)
        assertTrue(ctx.recentSessions.isEmpty())
        assertTrue("goblet_squat" in ctx.availableExerciseIds)
        assertFalse("pull_up" in ctx.availableExerciseIds)
        assertEquals(3, ctx.planOptions.size)
        assertTrue(ctx.equipment.any { it.contains("17.5") })
    }

    @Test fun exportAndDeleteAllData() = runBlocking {
        repo().send("hello")
        settings.update { it.copy(appToken = "secret-app-token-value", backendUrl = "https://example.org") }
        val data = DataRepository(db, settings)
        val json = data.exportJson()
        assertTrue(json.contains("\"chatMessages\""))
        assertTrue(json.contains("Dumbbells"))
        assertFalse("backend token must never be exported", json.contains("secret-app-token-value"))
        data.deleteEverything()
        assertNull(db.profile().get())
        assertTrue(db.chat().all().isEmpty())
        assertEquals("", settings.current().appToken)
    }
}

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun state(type: ReminderType) = WorkManager.getInstance(context).getWorkInfosForUniqueWork("reminder_${type.name}").get().firstOrNull()?.state

    @Test fun enabledReminderIsScheduledDisabledIsCancelled() {
        val s = ReminderScheduler(context)
        s.schedule(ReminderConfigEntity(ReminderType.TRAINING_DAY.name, 18 * 60, true), LocalDateTime.of(2026, 10, 19, 9, 0))
        assertEquals(WorkInfo.State.ENQUEUED, state(ReminderType.TRAINING_DAY))
        s.schedule(ReminderConfigEntity(ReminderType.TRAINING_DAY.name, 18 * 60, false))
        assertEquals(WorkInfo.State.CANCELLED, state(ReminderType.TRAINING_DAY))
    }

    @Test fun cancelAllStopsEverything() {
        val s = ReminderScheduler(context)
        ReminderType.entries.forEach { s.schedule(ReminderConfigEntity(it.name, 9 * 60, true)) }
        s.cancelAll()
        ReminderType.entries.forEach { assertEquals(WorkInfo.State.CANCELLED, state(it)) }
    }
}
