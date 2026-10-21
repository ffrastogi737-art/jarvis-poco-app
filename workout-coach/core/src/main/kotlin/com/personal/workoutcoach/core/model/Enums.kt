package com.personal.workoutcoach.core.model

import kotlinx.serialization.Serializable

enum class Language { ENGLISH, HINGLISH }

/** FRIENDLY never escalates; BALANCED gets direct after repeated postponing; STRICT is direct by default. */
enum class CoachStyle { FRIENDLY, BALANCED, STRICT }

enum class TrainingPhase { COMEBACK, BUILD }

@Serializable
enum class SessionMode { MINIMUM, STANDARD, EXTENDED }

enum class SessionStatus { IN_PROGRESS, PAUSED, COMPLETED, PARTIAL, ABANDONED }

enum class ScheduleStatus { PLANNED, DONE, MISSED, RESCHEDULED, SKIPPED }

/** Why a planned session was missed. UNKNOWN unless the user tells us. */
enum class MissReason { UNKNOWN, NO_TIME, LOW_MOTIVATION, TIRED, SORE, POOR_SLEEP, ILL_OR_INJURED, SCHEDULE_CONFLICT }
