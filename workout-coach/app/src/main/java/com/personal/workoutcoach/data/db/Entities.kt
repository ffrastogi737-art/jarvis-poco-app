package com.personal.workoutcoach.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

// Enums are stored by name; small id/day sets as comma-separated text.

@Serializable
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val goal: String = "Muscle building",
    val language: String = "HINGLISH",
    val coachStyle: String = "BALANCED",
    val preferredMinutes: Int = 25,
    /** ISO day numbers (1 = Monday). */
    val trainingDays: String = "1,3,5",
    val preferredTimeMinutes: Int = 18 * 60,
    val phase: String = "COMEBACK",
    val restrictions: String = "",
    val excludedExercises: String = "",
    val onboarded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val enabled: Boolean = true,
    val quantity: Int = 1,
    val weightKg: Double? = null,
    val minWeightKg: Double? = null,
    val maxWeightKg: Double? = null,
    val incrementKg: Double? = null,
    val bandType: String? = null,
    val resistanceLabel: String? = null,
    val resistanceKg: Double? = null,
    val anchor: String? = null,
    val benchPositions: String = "",
    val mountType: String? = null,
    val customCapabilities: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "workout_sessions", indices = [Index("status"), Index("plannedDate")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day the session counts for. */
    val plannedDate: Long,
    val templateKey: String?,
    val mode: String,
    val title: String,
    val source: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val pausedTotalMs: Long = 0,
    val currentIndex: Int = 0,
    val notes: String = "",
) {
    fun activeDurationMs(now: Long): Long = ((endedAt ?: pausedAt ?: now) - startedAt - pausedTotalMs).coerceAtLeast(0)
}

@Serializable
@Entity(
    tableName = "exercise_performances",
    foreignKeys = [ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId", "orderIndex"], unique = true), Index("exerciseId")],
)
data class ExercisePerformanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    /** Snapshot so history stays readable even if the catalogue changes. */
    val exerciseName: String,
    val orderIndex: Int,
    val plannedSets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val seconds: Int?,
    val restSeconds: Int,
    val plannedLoadKg: Double?,
    val status: String = "PENDING",
    val substitutedFrom: String? = null,
    val progressionNote: String? = null,
    val notes: String = "",
)

@Serializable
@Entity(
    tableName = "set_performances",
    foreignKeys = [ForeignKey(entity = ExercisePerformanceEntity::class, parentColumns = ["id"], childColumns = ["exercisePerformanceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["exercisePerformanceId", "setNumber"], unique = true)],
)
data class SetPerformanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercisePerformanceId: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val completedAt: Long,
)

@Serializable
@Entity(
    tableName = "schedule_entries",
    foreignKeys = [ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["date"], unique = true), Index("sessionId")],
)
data class ScheduleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val status: String,
    val missReason: String? = null,
    val movedToDate: Long? = null,
    val movedFromDate: Long? = null,
    val sessionId: Long? = null,
)

@Serializable
@Entity(tableName = "chat_messages", indices = [Index(value = ["conversationId", "timestamp"])])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String = "main",
    /** user | coach */
    val role: String,
    val content: String,
    val timestamp: Long,
    /** ReplySourceKind name for coach messages. */
    val source: String? = null,
    val sourceLabel: String? = null,
    val structuredJson: String? = null,
    /** Set when remote AI failed and the offline coach answered. */
    val errorNotice: String? = null,
    /** Original user text + request id, kept so the AI request can be retried. */
    val retryText: String? = null,
    val retryRequestId: String? = null,
    val warnings: String? = null,
)

@Serializable
@Entity(
    tableName = "user_feedback",
    foreignKeys = [ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("sessionId")],
)
data class UserFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sessionId: Long? = null,
    val energy: Int? = null,
    val motivation: Int? = null,
    val soreness: Int? = null,
    val notes: String = "",
)

@Serializable
@Entity(tableName = "progress_measurements", indices = [Index(value = ["type", "timestamp"])])
data class ProgressMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val value: Double,
    val unit: String,
)

@Serializable
@Entity(tableName = "coach_memory")
data class CoachMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** PROFILE | TRAINING | BEHAVIOR | CONVERSATION */
    val category: String,
    val content: String,
    /** USER | COACH */
    val source: String = "USER",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "reminder_configs")
data class ReminderConfigEntity(
    @PrimaryKey val type: String,
    val minuteOfDay: Int,
    val enabled: Boolean,
)

data class ExerciseWithSets(
    @Embedded val exercise: ExercisePerformanceEntity,
    @Relation(parentColumn = "id", entityColumn = "exercisePerformanceId") val sets: List<SetPerformanceEntity>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = ExercisePerformanceEntity::class, parentColumn = "id", entityColumn = "sessionId") val exercises: List<ExerciseWithSets>,
) {
    val orderedExercises get() = exercises.sortedBy { it.exercise.orderIndex }
}
