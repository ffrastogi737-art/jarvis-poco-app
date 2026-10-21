package com.personal.workoutcoach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.personal.workoutcoach.ui.components.UiClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical user journeys, run on the JVM with Robolectric (no emulator needed).
 * Each test starts from a fresh app install (first launch → onboarding).
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<WorkoutCoachApp>()

    @Before fun setUp() {
        UiClock.tickingEnabled = false
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build()) }
    }

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for a database condition while letting main-thread coroutines (viewModelScope) run. */
    private fun waitForDb(timeoutMs: Long = 10_000, condition: suspend () -> Boolean) =
        rule.waitUntil(timeoutMs) { rule.waitForIdle(); runBlocking { condition() } }

    private fun onboard() {
        waitFor(hasTestTag("get_started"))
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.onNodeWithTag("get_started").performScrollTo().performClick()
        waitFor(hasTestTag("start_workout"))
    }

    private fun tab(label: String) {
        rule.onAllNodesWithText(label).filterToOne(hasClickAction()).performClick()
        rule.waitForIdle()
    }

    @Test fun firstLaunchProfileSetupAndHome() {
        waitFor(hasText("Welcome back to training"))
        onboard()
        rule.onNodeWithText("Recommended workout").assertExists()
        rule.onNodeWithText("This week").performScrollTo().assertExists()
        val p = runBlocking { app.container.db.profile().get()!! }
        assertEquals("ENGLISH", p.language)
        assertTrue(p.onboarded)
    }

    @Test fun workoutFlowSetTimerPauseResumeFinishAndProgress() {
        onboard()
        rule.onNodeWithText("Minimum").performClick()
        rule.onNodeWithTag("start_workout").performScrollTo().performClick()
        waitFor(hasTestTag("exercise_name"))

        rule.onNodeWithTag("log_set").performScrollTo().performClick()
        waitFor(hasTestTag("timer_text"))
        rule.onNodeWithTag("timer_pause").performScrollTo().performClick()
        waitFor(hasTestTag("timer_resume"))
        rule.onNodeWithTag("timer_resume").performClick()
        waitFor(hasTestTag("timer_pause"))
        rule.onNodeWithTag("timer_skip").performClick()
        waitFor(hasText("Rest over"))

        rule.onNodeWithTag("pause").performClick()
        waitFor(hasTestTag("resume"))
        rule.onNodeWithTag("resume").performClick()
        waitFor(hasTestTag("pause"))

        val sets = runBlocking { app.container.db.sessions().allSets() }
        assertEquals(1, sets.size)

        rule.onNodeWithTag("finish_workout").performScrollTo().performClick()
        rule.onNodeWithTag("confirm_finish").performClick()
        waitFor(hasTestTag("save_feedback"))
        rule.onNodeWithTag("save_feedback").performClick()
        waitFor(hasTestTag("weekly_report"))
        val s = runBlocking { app.container.db.sessions().allSessions().single() }
        assertEquals("PARTIAL", s.status) // one set on the first exercise only
    }

    @Test fun coachChatOfflineAndLanguageChange() {
        onboard()
        tab("Coach")
        waitFor(hasTestTag("chat_input"))
        rule.onNodeWithTag("chat_input").performTextInput("I have only 15 minutes")
        rule.onNodeWithTag("send").performClick()
        waitFor(hasText("Offline rule-based coach"))
        waitFor(hasTestTag("start_from_chat"))

        tab("Profile")
        rule.onNodeWithText("Hinglish").performScrollTo().performClick()
        rule.onNodeWithTag("save_profile").performScrollTo().performClick()
        waitForDb { app.container.db.profile().get()!!.language == "HINGLISH" }

        tab("Coach")
        waitFor(hasTestTag("chat_input"))
        rule.onNodeWithTag("chat_input").performTextInput("I have no motivation today")
        rule.onNodeWithTag("send").performClick()
        waitFor(hasText("Bhai", substring = true))
    }

    @Test fun aiServiceFailureShowsErrorAndKeepsWorking() {
        onboard()
        runBlocking {
            // Nothing listens on this port: the remote call fails fast.
            app.container.settings.update { it.copy(aiEnabled = true, backendUrl = "http://127.0.0.1:9", appToken = "test-token") }
        }
        tab("Coach")
        waitFor(hasText("Remote AI on", substring = true))
        rule.onNodeWithTag("chat_input").performTextInput("Plan my workout today")
        rule.onNodeWithTag("send").performClick()
        waitFor(hasText("AI unavailable", substring = true), 20_000)
        rule.onNodeWithTag("retry").assertExists()
        rule.onNodeWithText("Offline rule-based coach").assertExists()
        val msgs = runBlocking { app.container.db.chat().all() }
        assertEquals(2, msgs.size)
    }

    @Test fun configureReminders() {
        onboard()
        tab("Profile")
        rule.onNodeWithTag("reminder_DAILY_CHECKIN").performScrollTo().performClick()
        waitForDb { app.container.db.reminders().get("DAILY_CHECKIN")!!.enabled }
        rule.onNodeWithTag("pause_reminders").performScrollTo().performClick()
        waitForDb { app.container.settings.current().remindersPaused }
    }
}
