package com.personal.workoutcoach.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        UserProfileEntity::class, EquipmentEntity::class, WorkoutSessionEntity::class, ExercisePerformanceEntity::class,
        SetPerformanceEntity::class, ScheduleEntryEntity::class, ChatMessageEntity::class, UserFeedbackEntity::class,
        ProgressMeasurementEntity::class, CoachMemoryEntity::class, ReminderConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun equipment(): EquipmentDao
    abstract fun sessions(): SessionDao
    abstract fun schedule(): ScheduleDao
    abstract fun chat(): ChatDao
    abstract fun feedback(): FeedbackDao
    abstract fun measurements(): MeasurementDao
    abstract fun memory(): MemoryDao
    abstract fun reminders(): ReminderDao

    companion object {
        /**
         * Schema history lives in app/schemas. Version 1 is the first release, so there are no
         * migrations yet. Destructive fallback is deliberately NOT enabled: a future schema change
         * without a migration fails loudly instead of wiping the user's training history.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "workout-coach.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
