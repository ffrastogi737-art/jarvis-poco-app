package com.personal.workoutcoach.core.coach

enum class SafetyLevel { NONE, ILLNESS, PAIN, URGENT }

/** Keyword match anchored at a word start ("thak" matches "thaka", "ill" does not match "will"); short words must match whole. */
internal fun hasWord(text: String, w: String): Boolean =
    Regex("(?<![a-z])" + Regex.escape(w) + if (w.length <= 3) "(?![a-z])" else "").containsMatchIn(text)

/** Deterministic safety screen. Runs before any AI call; the AI never overrides it. */
object SafetyScreen {
    private val urgent = listOf(
        "chest pain", "chest pressure", "chest tight", "seene mein dard", "seene me dard", "chhati mein dard", "chhati me dard",
        "faint", "passed out", "behosh", "can't breathe", "cant breathe", "short of breath", "shortness of breath", "saans nahi", "saans phool",
        "dizzy", "chakkar", "numb", "palpitation", "heart racing", "dhadkan",
    )
    private val pain = listOf(
        "sharp pain", "joint pain", "injur", "chot", "sprain", "moch", "swelling", "sujan", "pain", "dard", "hurts", "hurt my", "back pain", "knee pain", "shoulder pain",
    )
    private val illness = listOf("fever", "bukhar", "sick", "ill", "flu", "cold and", "khansi", "cough", "infection", "beemar", "bimar", "covid", "vomit", "ulti")

    fun check(text: String): SafetyLevel {
        val t = " ${text.lowercase()} "
        return when {
            urgent.any { hasWord(t, it) } -> SafetyLevel.URGENT
            illness.any { hasWord(t, it) } -> SafetyLevel.ILLNESS
            // "muscle soreness" / "body dard" after training is common; still treated conservatively as pain
            pain.any { hasWord(t, it) } && !t.contains("no pain") && !t.contains("dard nahi") -> SafetyLevel.PAIN
            else -> SafetyLevel.NONE
        }
    }
}

enum class Intent {
    PLAN_TODAY, LIMITED_TIME, NO_MOTIVATION, TIRED, SORE, POOR_SLEEP, MISSED, PROGRESS, EASIER,
    EXPLAIN, BACK_ON_TRACK, SCHEDULE_CONFLICT, ACCOUNTABILITY, GREETING, THANKS, UNKNOWN
}

data class ClassifiedMessage(val intent: Intent, val minutes: Int? = null)

object IntentClassifier {
    private val minutesRegex = Regex("(\\d{1,3})\\s*(?:-|\\s)?(?:min|mins|minute|minutes|minat|mnt)\\b")

    private val rules: List<Pair<Intent, List<String>>> = listOf(
        Intent.ACCOUNTABILITY to listOf("be strict", "strict", "push me", "hold me accountable", "accountab", "sakhti", "daant", "kick my"),
        Intent.MISSED to listOf("missed", "miss ho", "miss kar", "skip kiya", "skipped", "chhoot", "chhut gaya", "nahi kar paya", "nahi kar paaya", "didn't work out", "didnt work out"),
        Intent.BACK_ON_TRACK to listOf("back on track", "get back", "restart", "wapas", "phir se shuru", "dobara", "track pe"),
        Intent.NO_MOTIVATION to listOf("motivation", "not motivated", "mann nahi", "man nahi", "lazy", "aalas", "aalsi", "don't feel like", "dont feel like", "feel like skipping"),
        Intent.POOR_SLEEP to listOf("sleep", "slept", "neend", "soya nahi", "so nahi"),
        Intent.SORE to listOf("sore", "soreness", "doms", "akdan", "akad", "stiff"),
        Intent.TIRED to listOf("tired", "exhausted", "thak", "fatigue", "no energy", "energy nahi", "drained"),
        Intent.EASIER to listOf("easier", "easy", "aasan", "asaan", "halka", "lighter", "too hard", "bahut mushkil", "kam kar"),
        Intent.PROGRESS to listOf("progress", "report", "how am i doing", "kitna improve", "stats", "history"),
        Intent.EXPLAIN to listOf("explain", "how to do", "how do i do", "kaise karte", "kaise kare", "kaise karu", "form", "technique", "samjha"),
        Intent.SCHEDULE_CONFLICT to listOf("busy", "meeting", "office", "work late", "kaam hai", "guest", "mehmaan", "travel", "schedule", "kal karunga", "later", "baad mein", "tomorrow", "postpone"),
        Intent.LIMITED_TIME to listOf("only have", "no time", "time nahi", "sirf", "jaldi", "quick", "short workout", "kam time", "less time"),
        Intent.PLAN_TODAY to listOf("plan", "workout today", "aaj ka workout", "aaj kya", "what should i do", "kya karu", "start", "today's workout", "todays workout"),
        Intent.THANKS to listOf("thanks", "thank you", "shukriya", "dhanyavad"),
        Intent.GREETING to listOf("hi", "hello", "hey", "namaste", "good morning"),
    )

    fun classify(text: String): ClassifiedMessage {
        val t = " ${text.lowercase()} "
        val minutes = minutesRegex.find(t)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..180 }
        if (minutes != null && minutes <= 30) return ClassifiedMessage(Intent.LIMITED_TIME, minutes)
        for ((intent, words) in rules) {
            if (words.any { hasWord(t, it) }) return ClassifiedMessage(intent, minutes)
        }
        return ClassifiedMessage(Intent.UNKNOWN, minutes)
    }
}
