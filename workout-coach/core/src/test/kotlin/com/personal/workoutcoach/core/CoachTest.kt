package com.personal.workoutcoach.core

import com.personal.workoutcoach.core.coach.*
import com.personal.workoutcoach.core.equipment.Inventory
import com.personal.workoutcoach.core.equipment.defaultInventory
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.model.TrainingPhase
import com.personal.workoutcoach.core.plan.PlanSource
import com.personal.workoutcoach.core.plan.PlannerInput
import com.personal.workoutcoach.core.plan.WorkoutPlanner
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.*

private val inv = Inventory(defaultInventory())
private val validator = RecommendationValidator(inv, TrainingPhase.COMEBACK)

private fun ctx(lang: String = "ENGLISH", style: String = "FRIENDLY", postpones: Int = 0) = CoachContext(
    language = lang, style = style, phase = "COMEBACK", today = "2026-10-21", recentPostpones = postpones,
    memory = listOf("Prefers evening workouts"), lastFeedback = "energy 3/5",
)

private fun req(msg: String, c: CoachContext = ctx()) = CoachRequest(msg, listOf(ChatTurn("user", "hi"), ChatTurn("assistant", "hello")), c,
    { mode, minutes -> WorkoutPlanner.plan(PlannerInput(inv, TrainingPhase.COMEBACK, mode, "A", maxMinutes = minutes)) }, "req-1")

class ParserTest {
    @Test fun parsesFencedJsonAndIgnoresUnknownFields() {
        val raw = "Sure!\n```json\n{\"type\":\"WORKOUT\",\"message\":\"Do this\",\"extra\":1,\"workout\":{\"mode\":\"MINIMUM\",\"exercises\":[{\"exerciseId\":\"goblet_squat\",\"sets\":2,\"repsMin\":8,\"repsMax\":12}]}}\n```"
        val r = AiResponseParser.parse(raw).getOrThrow()
        assertEquals(ReplyType.WORKOUT, r.type)
        assertEquals(SessionMode.MINIMUM, r.workout!!.mode)
    }

    @Test fun unknownEnumCoercedToDefault() {
        assertEquals(ReplyType.CHAT, AiResponseParser.parse("{\"type\":\"PARTY\",\"message\":\"x\"}").getOrThrow().type)
    }

    @Test fun malformedRejected() {
        assertTrue(AiResponseParser.parse("not json").isFailure)
        assertTrue(AiResponseParser.parse("{\"message\":").isFailure)
        assertTrue(AiResponseParser.parse("{\"type\":\"CHAT\"}").isFailure, "missing message")
        assertTrue(AiResponseParser.parse("{\"message\":\"   \"}").isFailure)
        assertTrue(AiResponseParser.parse("{\"message\":\"x\",\"workout\":{\"exercises\":\"nope\"}}").isFailure)
    }

    @Test fun redactorHidesSecrets() {
        val s = Redactor.redact("Authorization: Bearer abcdefghijklmnop key=sk-or-v1-1234567890abcdef AIzaSyA1234567890123456789012 gsk_abcdefghijklmn api_key=supersecretvalue")
        assertFalse(s.contains("abcdefghijklmnop"))
        assertFalse(s.contains("1234567890abcdef"))
        assertFalse(s.contains("AIzaSy"))
        assertFalse(s.contains("gsk_abc"))
        assertFalse(s.contains("supersecretvalue"))
    }
}

class ValidatorTest {
    @Test fun dropsUnknownAndSubstitutesUnavailable() {
        val rec = WorkoutRecommendation(exercises = listOf(
            RecommendedExercise("made_up_exercise", 3),
            RecommendedExercise("pull_up", 3), // needs a bar; no easier alternative available → removed
            RecommendedExercise("db_lateral_raise", 2), // too heavy at 17.5 → no compatible alternative here
            RecommendedExercise("goblet_squat", 9, 1, 100, restSeconds = 2),
        ))
        val v = validator.validate(rec)
        val w = v.workout!!
        assertEquals(listOf("goblet_squat"), w.exercises.map { it.exercise.id })
        val rx = w.exercises[0].prescription
        assertEquals(3, rx.sets) // clamped to comeback max
        assertEquals(30, rx.repsMax)
        assertEquals(15, rx.restSeconds)
        assertEquals(17.5, w.exercises[0].loadKg)
        assertEquals(PlanSource.AI_VALIDATED, w.source)
        assertTrue(v.warnings.any { it.contains("Unknown exercise") })
    }

    @Test fun replacesHardExerciseWithEasierCompatibleOne() {
        val v = validator.validate(WorkoutRecommendation(exercises = listOf(RecommendedExercise("db_shoulder_press", 2, 6, 10))))
        assertEquals("db_shoulder_press_single", v.workout!!.exercises.single().exercise.id)
    }

    @Test fun rejectsWhenNothingValid() {
        val v = validator.validate(WorkoutRecommendation(exercises = listOf(RecommendedExercise("kb_swing", 3))))
        assertNull(v.workout)
    }

    @Test fun respectsExclusionsAndDuplicates() {
        val v = RecommendationValidator(inv, TrainingPhase.COMEBACK, setOf("dead_bug")).validate(WorkoutRecommendation(exercises = listOf(
            RecommendedExercise("dead_bug", 2), RecommendedExercise("plank", 2, seconds = 500), RecommendedExercise("plank", 2))))
        assertEquals(listOf("plank"), v.workout!!.exercises.map { it.exercise.id })
        assertEquals(120, v.workout!!.exercises[0].prescription.seconds)
    }

    @Test fun estimatedDurationIsRecomputedLocally() {
        val v = validator.validate(WorkoutRecommendation(estimatedMinutes = 999, exercises = listOf(RecommendedExercise("goblet_squat", 2, 8, 12))))
        assertTrue(v.workout!!.estimatedMinutes < 20)
    }
}

class LocalCoachTest {
    private val coach = LocalRuleBasedCoach()

    @Test fun limitedTimeGivesTrimmedWorkout() {
        val r = coach.respond(req("I have only 15 minutes"))
        assertEquals(ReplyType.WORKOUT, r.type)
        assertEquals(SessionMode.MINIMUM, r.workout!!.mode)
        assertTrue(r.workout!!.estimatedMinutes!! <= 15)
        assertTrue(QuickAction.START_MINIMUM in r.actions)
    }

    @Test fun hinglishAndEnglish() {
        val hi = coach.respond(req("aaj motivation nahi hai", ctx("HINGLISH")))
        assertTrue(hi.message.contains("Bhai, aaj motivation kam hai"))
        val en = coach.respond(req("I have no motivation today"))
        assertTrue(en.message.contains("Low motivation days happen"))
    }

    @Test fun missedWorkoutNoGuiltNoDoubling() {
        val r = coach.respond(req("I missed my last workout"))
        assertTrue(r.message.contains("progress isn't gone"))
        assertEquals(SessionMode.MINIMUM, r.workout!!.mode)
        assertTrue(QuickAction.RESCHEDULE in r.actions)
    }

    @Test fun strictnessEscalatesRespectfully() {
        val friendly = coach.respond(req("I'm busy, maybe later", ctx(style = "FRIENDLY", postpones = 3)))
        assertFalse(friendly.message.contains("Let's be honest"))
        val balanced = coach.respond(req("I'm busy, maybe later", ctx(style = "BALANCED", postpones = 2)))
        assertTrue(balanced.message.contains("Let's be honest"))
        assertFalse(balanced.message.contains("lazy", ignoreCase = true))
    }

    @Test fun distinguishesTiredSoreSleep() {
        assertEquals(Intent.TIRED, IntentClassifier.classify("I'm so tired").intent)
        assertEquals(Intent.SORE, IntentClassifier.classify("legs are sore").intent)
        assertEquals(Intent.POOR_SLEEP, IntentClassifier.classify("neend nahi aayi").intent)
        assertEquals(Intent.SCHEDULE_CONFLICT, IntentClassifier.classify("office mein kaam hai").intent)
        assertEquals(Intent.LIMITED_TIME, IntentClassifier.classify("sirf 10 min hai").intent)
        assertEquals(10, IntentClassifier.classify("sirf 10 min hai").minutes)
    }

    @Test fun explainsExercise() {
        val r = coach.respond(req("Explain goblet squat"))
        assertTrue(r.message.startsWith("Goblet Squat"))
    }

    @Test fun safetyScreen() {
        assertEquals(SafetyLevel.URGENT, SafetyScreen.check("I have chest pain"))
        assertEquals(SafetyLevel.URGENT, SafetyScreen.check("chakkar aa raha hai"))
        assertEquals(SafetyLevel.PAIN, SafetyScreen.check("sharp pain in my knee"))
        assertEquals(SafetyLevel.ILLNESS, SafetyScreen.check("mujhe bukhar hai"))
        assertEquals(SafetyLevel.NONE, SafetyScreen.check("I will train later"), "'will' must not match 'ill'")
        assertEquals(SafetyLevel.NONE, SafetyScreen.check("perform better"))
        val r = coach.respond(req("I have chest pain"))
        assertEquals(ReplyType.SAFETY, r.type)
        assertNull(r.workout)
    }
}

private class FakeRemote(val result: CoachResult) : FitnessCoachProvider {
    var calls = 0
    override val id = "fake"; override val isRemote = true
    override suspend fun reply(request: CoachRequest): CoachResult { calls++; return result }
}

class OrchestratorTest {
    private val orch = CoachOrchestrator()

    @Test fun noRemoteUsesLocalRules() = runTest {
        val o = orch.respond(req("Plan my workout today"), null, validator)
        assertEquals(ReplySourceKind.LOCAL_RULES, o.source.kind)
        assertNotNull(o.workout)
        assertNull(o.error)
    }

    @Test fun safetyNeverCallsRemote() = runTest {
        val remote = FakeRemote(CoachResult.Success(CoachReply(message = "push through it!"), "fake", null, null))
        val o = orch.respond(req("chest pain while training"), remote, validator)
        assertEquals(0, remote.calls)
        assertEquals(ReplySourceKind.LOCAL_SAFETY, o.source.kind)
    }

    @Test fun remoteFailureFallsBackToLocalAndReportsError() = runTest {
        for (err in listOf(CoachError.Timeout, CoachError.RateLimited(30), CoachError.Malformed("x"), CoachError.ProviderUnavailable("groq"), CoachError.Unauthorized)) {
            val o = orch.respond(req("I have only 15 minutes"), FakeRemote(CoachResult.Failure(err)), validator)
            assertEquals(ReplySourceKind.LOCAL_RULES, o.source.kind)
            assertEquals(err, o.error)
            assertNotNull(o.workout)
        }
    }

    @Test fun remoteWorkoutIsValidated() = runTest {
        val reply = CoachReply(ReplyType.WORKOUT, "Try this", WorkoutRecommendation(exercises = listOf(
            RecommendedExercise("barbell_rdl", 5), RecommendedExercise("goblet_squat", 2, 8, 12))),
            memoryNote = MemoryNote("HACK", "x"))
        val o = orch.respond(req("plan"), FakeRemote(CoachResult.Success(reply, "gemini", "m", null)), validator)
        assertEquals(ReplySourceKind.REMOTE_AI, o.source.kind)
        assertEquals(listOf("db_rdl", "goblet_squat"), o.workout!!.exercises.map { it.exercise.id }) // barbell → easier dumbbell RDL
        assertNull(o.reply.memoryNote, "invalid memory category dropped")
        assertTrue(o.source.label().startsWith("AI"))
    }

    @Test fun switchingProvidersOnlyChangesTheRemote() = runTest {
        val a = orch.respond(req("plan"), FakeRemote(CoachResult.Success(CoachReply(message = "a"), "groq", null, null)), validator)
        val b = orch.respond(req("plan"), FakeRemote(CoachResult.Success(CoachReply(message = "b"), "openrouter", null, null)), validator)
        assertEquals("groq", a.source.provider); assertEquals("openrouter", b.source.provider)
    }

    @Test fun conversationWindowSummarisesOlderTurns() {
        val turns = (1..20).map { ChatTurn(if (it % 2 == 0) "assistant" else "user", "message $it") }
        val (window, summary) = ConversationWindow.build(turns, keep = 6)
        assertEquals(6, window.size)
        assertTrue(summary!!.contains("message 13"))
        assertFalse(summary.contains("message 14"), "assistant turns not included")
    }
}

class RemoteBackendCoachTest {
    private fun withServer(status: Int, body: String, headers: Map<String, String> = emptyMap(), delayMs: Long = 0, block: suspend (String, MutableList<Pair<Map<String, List<String>>, String>>) -> Unit) = runTest {
        val seen = mutableListOf<Pair<Map<String, List<String>>, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/coach") { ex ->
            seen += ex.requestHeaders.toMap() to ex.requestBody.bufferedReader().readText()
            if (delayMs > 0) Thread.sleep(delayMs)
            headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}", seen) } finally { server.stop(0) }
    }

    @Test fun success() = withServer(200, """{"reply":{"type":"CHAT","message":"Namaste"},"provider":"gemini","model":"m-1","usage":{"promptTokens":10,"completionTokens":5}}""") { url, seen ->
        val r = RemoteBackendCoach(url, "app-token", RemoteProvider.GEMINI, shareHistory = true).reply(req("hello"))
        assertIs<CoachResult.Success>(r)
        assertEquals("Namaste", r.reply.message)
        assertEquals(5, r.usage!!.completionTokens)
        val (h, body) = seen.single()
        assertEquals("req-1", h.entries.first { it.key.equals("Idempotency-Key", true) }.value.single())
        assertTrue(body.contains("Prefers evening workouts"))
        assertTrue(body.contains("\"provider\":\"gemini\""))
    }

    @Test fun privacyModeOmitsHistoryAndMemory() = withServer(200, """{"reply":{"message":"ok"}}""") { url, seen ->
        RemoteBackendCoach(url, "t", RemoteProvider.GROQ, shareHistory = false).reply(req("hello"))
        val body = seen.single().second
        assertFalse(body.contains("Prefers evening workouts"))
        assertFalse(body.contains("energy 3/5"))
        assertTrue(body.contains("hello"))
    }

    @Test fun unauthorized() = withServer(401, """{"error":"unauthorized"}""") { url, _ ->
        assertEquals(CoachError.Unauthorized, (RemoteBackendCoach(url, "bad", RemoteProvider.GEMINI, true).reply(req("x")) as CoachResult.Failure).error)
    }

    @Test fun rateLimited() = withServer(429, """{"error":"rate_limited"}""", mapOf("Retry-After" to "42")) { url, _ ->
        assertEquals(CoachError.RateLimited(42), (RemoteBackendCoach(url, "t", RemoteProvider.GEMINI, true).reply(req("x")) as CoachResult.Failure).error)
    }

    @Test fun budget() = withServer(429, """{"error":"budget_exceeded"}""") { url, _ ->
        assertEquals(CoachError.BudgetExceeded, (RemoteBackendCoach(url, "t", RemoteProvider.GEMINI, true).reply(req("x")) as CoachResult.Failure).error)
    }

    @Test fun providerUnavailable() = withServer(400, """{"error":"provider_unavailable"}""") { url, _ ->
        assertIs<CoachError.ProviderUnavailable>((RemoteBackendCoach(url, "t", RemoteProvider.OPENROUTER, true).reply(req("x")) as CoachResult.Failure).error)
    }

    @Test fun malformed() = withServer(200, """{"reply":{"type":"CHAT"}}""") { url, _ ->
        assertIs<CoachError.Malformed>((RemoteBackendCoach(url, "t", RemoteProvider.GEMINI, true).reply(req("x")) as CoachResult.Failure).error)
    }

    @Test fun timeout() = withServer(200, """{"reply":{"message":"late"}}""", delayMs = 1500) { url, seen ->
        val r = RemoteBackendCoach(url, "t", RemoteProvider.GEMINI, true, readTimeoutMs = 300).reply(req("x"))
        assertEquals(CoachError.Timeout, (r as CoachResult.Failure).error)
        assertEquals(1, seen.size, "no automatic retry")
    }

    @Test fun notConfiguredAndInsecureUrl() = runTest {
        assertEquals(CoachError.NotConfigured, (RemoteBackendCoach("", "t", RemoteProvider.GEMINI, true).reply(req("x")) as CoachResult.Failure).error)
        assertIs<CoachError.Network>((RemoteBackendCoach("http://example.com", "t", RemoteProvider.GEMINI, true).reply(req("x")) as CoachResult.Failure).error)
    }
}

/** Fails if anything that looks like a provider API key is committed in the project. */
class NoCommittedSecretsTest {
    @Test fun repositoryHasNoProviderKeys() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }.parentFile
        val patterns = listOf(Regex("AIza[0-9A-Za-z_-]{35}"), Regex("gsk_[A-Za-z0-9]{40,}"), Regex("sk-or-v1-[a-f0-9]{40,}"), Regex("sk-[A-Za-z0-9]{40,}"))
        val skipDirs = setOf("build", ".gradle", ".git", "node_modules", ".kotlin")
        val offenders = root.walkTopDown().onEnter { it.name !in skipDirs }.filter { it.isFile && it.length() < 2_000_000 && it.extension in setOf("kt", "kts", "js", "json", "md", "xml", "properties", "yml", "yaml", "toml", "env", "txt", "dart") }
            .filter { f -> f.readText().let { t -> patterns.any { it.containsMatchIn(t) } } }.toList()
        assertTrue(offenders.isEmpty(), "Possible secrets in: $offenders")
    }
}
