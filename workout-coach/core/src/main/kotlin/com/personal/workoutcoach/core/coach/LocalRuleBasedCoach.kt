package com.personal.workoutcoach.core.coach

import com.personal.workoutcoach.core.catalog.ExerciseCatalog
import com.personal.workoutcoach.core.model.SessionMode
import com.personal.workoutcoach.core.plan.PlannedWorkout

/**
 * Deterministic offline coach. Its replies are labelled as rule-based in the UI, never as AI output.
 */
class LocalRuleBasedCoach : FitnessCoachProvider {
    override val id = "local"
    override val isRemote = false

    override suspend fun reply(request: CoachRequest): CoachResult = CoachResult.Success(respond(request), id, null, null)

    private class Ctx(val req: CoachRequest) {
        val hi = req.context.language == "HINGLISH"
        fun t(en: String, hinglish: String) = if (hi) hinglish else en
    }

    fun respond(req: CoachRequest): CoachReply {
        val c = Ctx(req)
        val safety = SafetyScreen.check(req.message)
        if (safety != SafetyLevel.NONE) return safetyReply(safety, req.context.language)
        val msg = IntentClassifier.classify(req.message)
        val ctx = req.context
        val strict = ctx.style == "STRICT" || (ctx.style == "BALANCED" && ctx.recentPostpones >= 2) || msg.intent == Intent.ACCOUNTABILITY

        fun plan(mode: SessionMode, minutes: Int? = null) = req.planFor(mode, minutes)
        fun workoutReply(p: PlannedWorkout?, text: String, type: ReplyType = ReplyType.WORKOUT, extra: List<QuickAction> = emptyList()): CoachReply {
            if (p == null || p.exercises.isEmpty()) return CoachReply(
                message = text + "\n\n" + c.t("I couldn't build a session with your current equipment settings. Check Profile → Equipment.",
                    "Current equipment se session nahi ban paya. Profile → Equipment check karo."),
            )
            val list = p.exercises.joinToString("\n") { "• ${it.exercise.name} — ${it.prescription.describe()}" + (it.loadKg?.let { kg -> " @ ${fmtKg(kg)} kg" } ?: "") }
            return CoachReply(
                type = type,
                message = "$text\n\n${p.title} (~${p.estimatedMinutes} min)\n$list",
                workout = p.toRecommendation(),
                actions = listOf(if (p.mode == SessionMode.MINIMUM) QuickAction.START_MINIMUM else QuickAction.START_WORKOUT) + extra,
                safetyNotes = p.notes,
            )
        }

        val strictLine = if (strict) "\n\n" + c.t(
            "Let's be honest: postponing again won't fit your schedule better. Pick this short session now, or reschedule it to a specific time.",
            "Seedhi baat: phir se postpone karne se schedule better nahi hoga. Ya toh yeh short session abhi karo, ya ek specific time pe reschedule karo.",
        ) else ""

        return when (msg.intent) {
            Intent.PLAN_TODAY -> {
                val mode = if ((ctx.daysSinceLastWorkout ?: 0) >= 10) SessionMode.MINIMUM else SessionMode.STANDARD
                workoutReply(plan(mode), c.t("Here's today's plan. Keep 2–3 reps in reserve on every set.", "Aaj ka plan ready hai. Har set mein 2–3 reps reserve mein rakhna.") + strictLine)
            }
            Intent.LIMITED_TIME -> {
                val m = msg.minutes ?: 15
                val p = plan(if (m <= 15) SessionMode.MINIMUM else SessionMode.STANDARD, m)
                val lead = if (m < 8) c.t("Even $m minutes is worth it — do the first exercise or two and call it a win.", "$m minute bhi kaafi hai — pehla ek-do exercise karo, woh bhi win hai.")
                else c.t("$m minutes is enough for a real session. I've trimmed the plan to fit.", "$m minute mein proper session ho jayega. Plan time ke hisaab se chhota kar diya hai.")
                workoutReply(p, lead + strictLine)
            }
            Intent.NO_MOTIVATION -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("Low motivation days happen. Let's keep the target small: finish this Minimum session and you've kept the habit alive.",
                    "Bhai, aaj motivation kam hai toh koi problem nahi. Aaj ka target simple rakhte hain. Ek short workout complete kar aur consistency ko wapas build kar.") + strictLine,
            )
            Intent.TIRED -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("If it's general tiredness, a short, easy session often helps — stop if you feel worse. If you're truly exhausted, rest today and log how you feel.",
                    "Agar normal thakaan hai, toh ek short easy session aksar help karta hai — worse lage toh ruk jao. Agar bahut zyada thake ho, toh aaj rest karo aur feedback log kar do."),
                extra = listOf(QuickAction.LOG_FEEDBACK),
            )
            Intent.SORE -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("Normal muscle soreness after a break is expected. Light movement usually helps; this lighter session keeps it easy. Sharp or joint pain is different — skip those exercises.",
                    "Break ke baad muscle soreness normal hai. Halka movement usually help karta hai; yeh lighter session easy rakhega. Sharp ya joint pain alag cheez hai — woh exercises skip karo."),
                extra = listOf(QuickAction.LOG_FEEDBACK),
            )
            Intent.POOR_SLEEP -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("After poor sleep, keep it light: a Minimum session at easy effort, or a rest day. Both are fine choices.",
                    "Neend kam hui hai toh light rakho: easy effort pe Minimum session, ya rest day. Dono theek hain."),
                type = ReplyType.REST_DAY,
                extra = listOf(QuickAction.LOG_FEEDBACK),
            )
            Intent.MISSED -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("A missed session is just a scheduling event — your progress isn't gone. No need to make it up with extra work. Do a short session today, or reschedule to a specific day.",
                    "Ek session miss hona bas schedule ki baat hai — progress khatam nahi hui. Extra workout karke compensate karne ki zarurat nahi. Aaj short session kar lo, ya kisi specific din reschedule karo.") + strictLine,
                type = ReplyType.RESCHEDULE,
                extra = listOf(QuickAction.RESCHEDULE),
            )
            Intent.BACK_ON_TRACK -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("Plan to restart: 3 non-consecutive days a week, 15–25 minutes, easy effort for the first couple of weeks. Step one is today's Minimum session.",
                    "Restart plan: hafte mein 3 din (gap ke saath), 15–25 minute, pehle do hafte easy effort. Pehla step: aaj ka Minimum session."),
            )
            Intent.EASIER -> workoutReply(
                plan(SessionMode.MINIMUM),
                c.t("Done — fewer sets today. Inside the workout you can also swap any exercise for an easier version.",
                    "Ho gaya — aaj sets kam hain. Workout ke andar kisi bhi exercise ko easier version se swap kar sakte ho."),
            )
            Intent.PROGRESS -> CoachReply(
                type = ReplyType.PROGRESS,
                message = ctx.weeklySummary ?: c.t("There isn't enough recorded data for a progress summary yet. Complete a session and I'll compare like-for-like.",
                    "Abhi progress summary ke liye data kaafi nahi hai. Ek session complete karo, phir same-exercise comparison dikhaunga."),
                actions = listOf(QuickAction.VIEW_PROGRESS),
            )
            Intent.EXPLAIN -> {
                val ex = ExerciseCatalog.findMentioned(req.message)
                if (ex == null) CoachReply(message = c.t("Which exercise? For example: \"explain goblet squat\".", "Kaunsi exercise? Jaise: \"explain goblet squat\"."))
                else CoachReply(
                    message = buildString {
                        append(ex.name).append("\n")
                        ex.instructions.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                        append(c.t("Cues: ", "Dhyan rakho: ")).append(ex.cues.joinToString("; ")).append("\n")
                        append(c.t("Avoid: ", "Galti mat karo: ")).append(ex.commonErrors.joinToString("; "))
                        if (ex.easier.isNotEmpty()) append("\n").append(c.t("Easier option: ", "Aasan option: ")).append(ex.easier.mapNotNull { ExerciseCatalog.get(it)?.name }.joinToString())
                    },
                    safetyNotes = ex.safetyNotes,
                )
            }
            Intent.SCHEDULE_CONFLICT -> CoachReply(
                type = ReplyType.RESCHEDULE,
                message = c.t("Work and home come first sometimes. Two options: a 10–15 minute Minimum session now, or move today's session to a specific day that keeps a rest day in between.",
                    "Kaam aur ghar ki zimmedari pehle aati hai, samajh sakta hoon. Do options: abhi 10–15 minute ka Minimum session, ya aaj ka session kisi specific din pe shift karo jisme beech mein rest day rahe.") + strictLine,
                actions = listOf(QuickAction.START_MINIMUM, QuickAction.RESCHEDULE),
            )
            Intent.ACCOUNTABILITY -> workoutReply(
                plan(SessionMode.STANDARD),
                c.t("You asked for accountability, so here it is: this is today's session. Start within the next 10 minutes and log every set.",
                    "Tumne accountability maangi hai, toh suno: yeh aaj ka session hai. Agle 10 minute mein start karo aur har set log karo."),
            )
            Intent.THANKS -> CoachReply(message = c.t("Anytime. One session at a time.", "Koi baat nahi. Ek session at a time."))
            Intent.GREETING -> CoachReply(
                message = c.t("Hi! ${ctx.completedThisWeek} of ${ctx.plannedThisWeek} sessions done this week. Want today's plan?",
                    "Hey! Is hafte ${ctx.completedThisWeek}/${ctx.plannedThisWeek} sessions ho gaye. Aaj ka plan chahiye?"),
                actions = listOf(QuickAction.START_WORKOUT),
            )
            Intent.UNKNOWN -> CoachReply(
                message = c.t("I'm the offline coach, so I understand a fixed set of requests: today's plan, short on time, low motivation, tired or sore, missed a workout, progress, or explaining an exercise. Try one of the quick prompts below.",
                    "Main offline coach hoon, toh kuch fixed requests samajhta hoon: aaj ka plan, time kam hai, motivation nahi, thakaan ya soreness, workout miss hua, progress, ya exercise explain karna. Neeche quick prompts try karo."),
            )
        }
    }

    fun safetyReply(level: SafetyLevel, language: String): CoachReply {
        val hi = language == "HINGLISH"
        fun t(en: String, h: String) = if (hi) h else en
        return when (level) {
            SafetyLevel.URGENT -> CoachReply(
                type = ReplyType.SAFETY,
                message = t("Please stop exercising now. Chest pain, fainting, severe breathlessness, dizziness or numbness can be serious — contact a doctor or local emergency services (112 in India) right away. I can't assess symptoms.",
                    "Abhi exercise band karo. Seene mein dard, behoshi, saans ki dikkat, chakkar ya numbness serious ho sakte hain — turant doctor ya emergency services (India mein 112) se contact karo. Main symptoms assess nahi kar sakta."),
                safetyNotes = listOf(t("Do not resume training until a medical professional says it's OK.", "Doctor se clearance ke bina training wapas shuru mat karo.")),
            )
            SafetyLevel.PAIN -> CoachReply(
                type = ReplyType.SAFETY,
                message = t("Don't train through sharp, joint or unusual pain. Skip any exercise that hurts, rest that area, and if it persists or is severe, get it checked by a doctor or physiotherapist. Normal muscle soreness is fine to train around lightly.",
                    "Sharp, joint ya unusual pain mein training mat karo. Jo exercise dard de, use skip karo aur us area ko rest do. Agar dard bana rahe ya zyada ho, toh doctor ya physiotherapist ko dikhao. Normal muscle soreness mein halka training theek hai."),
                actions = listOf(QuickAction.LOG_FEEDBACK),
            )
            SafetyLevel.ILLNESS -> CoachReply(
                type = ReplyType.REST_DAY,
                message = t("If you're unwell, rest is the right call — training can wait. Come back once you feel better; we'll restart with a Minimum session. See a doctor if symptoms are severe or don't improve.",
                    "Tabiyat theek nahi hai toh rest hi sahi decision hai — training wait kar sakti hai. Theek hone ke baad Minimum session se restart karenge. Symptoms zyada hon ya improve na hon toh doctor ko dikhao."),
                actions = listOf(QuickAction.RESCHEDULE),
            )
            SafetyLevel.NONE -> CoachReply(message = "")
        }
    }

    private fun fmtKg(v: Double) = com.personal.workoutcoach.core.equipment.fmt(v)
}
