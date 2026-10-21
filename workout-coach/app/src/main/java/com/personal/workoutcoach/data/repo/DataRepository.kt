package com.personal.workoutcoach.data.repo

import com.personal.workoutcoach.data.db.*
import com.personal.workoutcoach.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExportBundle(
    val format: String = "workout-coach-export",
    val version: Int = 1,
    val exportedAt: Long,
    val profile: UserProfileEntity?,
    val equipment: List<EquipmentEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val exercisePerformances: List<ExercisePerformanceEntity>,
    val setPerformances: List<SetPerformanceEntity>,
    val schedule: List<ScheduleEntryEntity>,
    val chatMessages: List<ChatMessageEntity>,
    val feedback: List<UserFeedbackEntity>,
    val measurements: List<ProgressMeasurementEntity>,
    val coachMemory: List<CoachMemoryEntity>,
    val reminders: List<ReminderConfigEntity>,
)

/** Export and deletion of all personal data. Settings (incl. the backend token) are never exported. */
class DataRepository(private val db: AppDatabase, private val settings: SettingsStore) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportJson(): String = json.encodeToString(
        ExportBundle.serializer(),
        ExportBundle(
            exportedAt = System.currentTimeMillis(),
            profile = db.profile().get(),
            equipment = db.equipment().all(),
            sessions = db.sessions().allSessions(),
            exercisePerformances = db.sessions().allExercises(),
            setPerformances = db.sessions().allSets(),
            schedule = db.schedule().all(),
            chatMessages = db.chat().all(),
            feedback = db.feedback().all(),
            measurements = db.measurements().all(),
            coachMemory = db.memory().all(),
            reminders = db.reminders().all(),
        ),
    )

    suspend fun deleteEverything() {
        withContext(Dispatchers.IO) { db.clearAllTables() } // blocking API; must be off the main thread
        settings.clearAll()
    }
}
