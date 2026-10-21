package com.personal.workoutcoach.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1") fun observe(): Flow<UserProfileEntity?>
    @Query("SELECT * FROM user_profile WHERE id = 1") suspend fun get(): UserProfileEntity?
    @Upsert suspend fun upsert(p: UserProfileEntity)
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY type, name") fun observeAll(): Flow<List<EquipmentEntity>>
    @Query("SELECT * FROM equipment") suspend fun all(): List<EquipmentEntity>
    @Insert suspend fun insert(e: EquipmentEntity): Long
    @Update suspend fun update(e: EquipmentEntity)
    @Query("DELETE FROM equipment WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Insert suspend fun insertSession(s: WorkoutSessionEntity): Long
    @Update suspend fun updateSession(s: WorkoutSessionEntity)
    @Insert suspend fun insertExercise(e: ExercisePerformanceEntity): Long
    @Update suspend fun updateExercise(e: ExercisePerformanceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSet(s: SetPerformanceEntity): Long
    @Update suspend fun updateSet(s: SetPerformanceEntity)
    @Delete suspend fun deleteSet(s: SetPerformanceEntity)
    @Query("DELETE FROM workout_sessions WHERE id = :id") suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM workout_sessions WHERE status IN ('IN_PROGRESS','PAUSED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status IN ('IN_PROGRESS','PAUSED') ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<SessionWithExercises?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id") suspend fun session(id: Long): WorkoutSessionEntity?
    @Query("SELECT * FROM exercise_performances WHERE id = :id") suspend fun exercise(id: Long): ExercisePerformanceEntity?
    @Query("SELECT * FROM set_performances WHERE exercisePerformanceId = :exId ORDER BY setNumber") suspend fun sets(exId: Long): List<SetPerformanceEntity>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id") suspend fun withExercises(id: Long): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status NOT IN ('IN_PROGRESS','PAUSED') ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<SessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status IN ('COMPLETED','PARTIAL') ORDER BY startedAt ASC")
    suspend fun countedHistory(): List<SessionWithExercises>

    @Query("SELECT * FROM workout_sessions WHERE status IN ('COMPLETED','PARTIAL') ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastCounted(): WorkoutSessionEntity?

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status IN ('COMPLETED','PARTIAL')")
    suspend fun countedCount(): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status IN ('IN_PROGRESS','PAUSED')")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM workout_sessions") suspend fun allSessions(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM exercise_performances") suspend fun allExercises(): List<ExercisePerformanceEntity>
    @Query("SELECT * FROM set_performances") suspend fun allSets(): List<SetPerformanceEntity>
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_entries ORDER BY date") fun observeAll(): Flow<List<ScheduleEntryEntity>>
    @Query("SELECT * FROM schedule_entries ORDER BY date") suspend fun all(): List<ScheduleEntryEntity>
    @Query("SELECT * FROM schedule_entries WHERE date = :date") suspend fun byDate(date: Long): ScheduleEntryEntity?
    @Query("SELECT * FROM schedule_entries WHERE id = :id") suspend fun byId(id: Long): ScheduleEntryEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(e: ScheduleEntryEntity): Long
    @Update suspend fun update(e: ScheduleEntryEntity)
    @Query("DELETE FROM schedule_entries WHERE date >= :fromDate AND status = 'PLANNED' AND movedFromDate IS NULL")
    suspend fun deleteFuturePlanned(fromDate: Long)
    @Query("SELECT * FROM schedule_entries WHERE sessionId = :sessionId") suspend fun bySession(sessionId: Long): List<ScheduleEntryEntity>
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = 'main' ORDER BY timestamp, id") fun observe(): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM chat_messages WHERE conversationId = 'main' ORDER BY timestamp DESC, id DESC LIMIT :limit") suspend fun recent(limit: Int): List<ChatMessageEntity>
    @Insert suspend fun insert(m: ChatMessageEntity): Long
    @Update suspend fun update(m: ChatMessageEntity)
    @Query("DELETE FROM chat_messages") suspend fun clear()
    @Query("SELECT * FROM chat_messages ORDER BY timestamp") suspend fun all(): List<ChatMessageEntity>
    @Query("SELECT * FROM chat_messages WHERE id = :id") suspend fun byId(id: Long): ChatMessageEntity?
}

@Dao
interface FeedbackDao {
    @Insert suspend fun insert(f: UserFeedbackEntity): Long
    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC") fun observe(): Flow<List<UserFeedbackEntity>>
    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC LIMIT :n") suspend fun recent(n: Int): List<UserFeedbackEntity>
    @Query("SELECT * FROM user_feedback") suspend fun all(): List<UserFeedbackEntity>
}

@Dao
interface MeasurementDao {
    @Insert suspend fun insert(m: ProgressMeasurementEntity): Long
    @Query("DELETE FROM progress_measurements WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM progress_measurements ORDER BY timestamp") fun observe(): Flow<List<ProgressMeasurementEntity>>
    @Query("SELECT * FROM progress_measurements") suspend fun all(): List<ProgressMeasurementEntity>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM coach_memory ORDER BY category, updatedAt DESC") fun observe(): Flow<List<CoachMemoryEntity>>
    @Query("SELECT * FROM coach_memory ORDER BY updatedAt DESC") suspend fun all(): List<CoachMemoryEntity>
    @Insert suspend fun insert(m: CoachMemoryEntity): Long
    @Update suspend fun update(m: CoachMemoryEntity)
    @Query("DELETE FROM coach_memory WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_configs") fun observe(): Flow<List<ReminderConfigEntity>>
    @Query("SELECT * FROM reminder_configs") suspend fun all(): List<ReminderConfigEntity>
    @Query("SELECT * FROM reminder_configs WHERE type = :type") suspend fun get(type: String): ReminderConfigEntity?
    @Upsert suspend fun upsert(r: ReminderConfigEntity)
}
