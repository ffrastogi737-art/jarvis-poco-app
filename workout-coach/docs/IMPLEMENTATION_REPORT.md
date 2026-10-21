# Implementation report — Personal AI Workout Coach

Date: 2026-10-21 · Branch: `ccr-cd90486f-70izuq`

## 1. Environment findings (Phase 0)

- **Existing repo.** It held a small Flutter "JARVIS" stub (`lib/main.dart`, `pubspec.yaml`, `.github/workflows/main.yml`, `MASTER_CONTEXT.md`) that calls an ngrok endpoint. It has nothing to do with workout coaching. It was **left untouched**, and the new app lives in `workout-coach/`. The existing workflow only runs on `main` and doesn't affect this directory.
- **Container network policy.** It blocked `dl.google.com`, and `maven.google.com` redirects there. So the Android SDK, the Android Gradle Plugin and AndroidX could **not** be downloaded in the development container. The same policy blocked the provider documentation sites (ai.google.dev, console.groq.com, openrouter.ai).
- **Consequence.** The Android app was compiled, tested, linted and packaged on **GitHub Actions** (`.github/workflows/workout-coach.yml`). The pure-Kotlin `core` module and the Node `backend` were also built and tested locally.

## 2. Verified results

| Check | Where | Result |
|---|---|---|
| `:core:test` | local (JDK 21) and CI (JDK 17) | **85 tests, 0 failed** |
| `:app:testDebugUnitTest` (Room, repositories, WorkManager, Robolectric Compose UI) | CI run #6 | **26 tests, 0 failed** |
| `:app:assembleDebug` | CI | success (APK uploaded as the `workout-coach-apks` artifact) |
| `:app:lintDebug` | CI | success (no lint errors) |
| `:app:assembleRelease` (R8 + resource shrinking, unsigned) | CI | success |
| `backend: node --test` | local and CI | **11 tests, 0 failed** |
| Backend end-to-end (real server + fake OpenAI-compatible provider + curl) | local | auth, reply contract, usage accounting and metadata-only logs all confirmed |

CI history on this branch, showing how the failures were resolved:

| Run | Outcome | Cause and fix |
|---|---|---|
| #1 | Failed | The Google Maven content filter was too narrow, so AGP couldn't resolve `com.google.testing.platform`. Fixed. |
| #2 | Passed | Build, lint and release all green. App tests did not exist yet. |
| #3 | 4 of 26 app tests failed | (a) A real bug: the postpone count ignored skipped sessions. Fixed in code. (b) Database `waitUntil` checks didn't idle the main looper. Fixed the test helper. |
| #4 | 1 failed | A test tapped an off-screen button. Fixed the test. |
| #5, #6 | All green | — |

**Not executed:**

- Instrumented tests on an emulator or real device. The UI tests run under Robolectric on the JVM.
- A manual on-device pass. See `docs/TESTING_CHECKLIST.md`.
- Calls to any real AI provider. No API keys were available, and the provider hosts are blocked here.

## 3. Features implemented

- **Onboarding and profile**: language (Hinglish/English), coach style (friendly/balanced/strict), training days with a warning for consecutive days, preferred time and length, restrictions, excluded exercises.
- **Dynamic equipment**: 14 types with type-specific fields, plus custom items that declare capabilities. You can add, edit, enable/disable and remove items.
  - Bands only unlock anchored exercises when they have a purpose-made door anchor or a secure post.
  - Per-exercise maximum loads keep lateral raises, rear-delt flies and pullovers out of plans with 17.5 kg dumbbells.
- **Exercise catalogue**: 48 exercises with stable IDs. Each has muscles, required capabilities, load type, difficulty, instructions, cues, common errors, a starting prescription, easier and harder alternatives, and safety notes. No video URLs.
- **Deterministic planner**:
  - 3-day full-body comeback templates (A/B/C), rotated on completed sessions.
  - Minimum, Standard and Extended modes with duration estimates and a time-limited trim.
  - Comeback and Build phases.
  - Double progression with one variable at a time; reductions for poor recovery, long gaps or repeated misses.
- **Workout tracking**:
  - Start, with duplicate prevention. Pause and resume (paused time excluded from duration). Log actual reps, seconds and weight per set; delete a set.
  - Skip or swap exercises (swap only before any set is logged). Previous performance is shown. End early as PARTIAL.
  - The active session survives process death; it is stored in Room.
- **Rest timer**:
  - Monotonic clock with no drift. Start, pause, resume, reset, skip and ±15 s.
  - In-app sound and vibration (toggleable). An inexact idle-safe background alarm with a "Rest over" notification. The timer state is persisted.
- **Coach chat**:
  - Chat is persistent, with source labels, quick prompts, a loading indicator, error notices and an idempotent **Retry AI** button.
  - Workout cards can be started from chat after re-validation. Action chips cover reschedule, feedback and progress.
  - **Offline coach**: rule-based intents in English and Hinglish, distinguishing motivation, time, soreness, tiredness, sleep, schedule conflicts, missed sessions and illness or pain. It becomes respectfully direct after repeated postponing in Balanced style, always in Strict style, never in Friendly style.
  - A deterministic **safety screen** runs before any AI call.
- **Remote AI (optional)**:
  - A provider-independent `FitnessCoachProvider` interface, with `RemoteBackendCoach` calling the user's backend. Providers: Gemini, Groq or OpenRouter, switchable in Settings.
  - Responses are validated with `RecommendationValidator`. Every failure falls back to the clearly labelled offline coach.
  - A privacy switch limits what is sent. Daily request and token usage are shown.
- **Backend**: zero-dependency Node 20.
  - Bearer auth, request validation, per-minute rate limit, daily request and token budgets persisted to disk.
  - Timeouts, an output token cap, no automatic retries, idempotent replay, model-output validation, redacted logs and `/health`.
- **Memory**: a user-editable `CoachMemory` (the AI can only *propose* notes in 4 fixed categories, at most 200 characters). Older conversation is summarised deterministically, and context is rebuilt from records on each message.
- **Missed-session recovery**: past planned days become `MISSED`, never deleted. Home shows a no-guilt card with optional reason chips, up to 2 realistic reschedule dates (never doubling up, never next to a completed session) or skip.
- **Reminders**: WorkManager jobs for training-day, daily check-in and missed follow-up reminders.
  - Quiet hours (including across midnight), a max-per-day limit and a global pause.
  - An ignored-streak back-off: after 3 ignored reminders it sends at most every other day and suggests changing the time.
  - Runtime notification permission is requested, and every notification can be dismissed.
- **Progress**:
  - Weekly report: sessions, days, minutes, exercises, like-for-like changes (same exercise and same load only), missed sessions, average energy and soreness, one recommendation, and an explicit "not enough records" state.
  - An 8-week consistency bar chart, exercise trend lines per load, and body measurements (only when entered).
  - History with detail view and deletion of wrong records.
- **Data control**: JSON export (excludes the backend token), delete all data, `allowBackup=false` with data-extraction rules, and privacy text in Settings and `docs/PRIVACY.md`.

## 4. Not implemented / deviations

- **No on-device LLM.** Offline coaching is rule-based and is labelled as such.
- **The `WorkoutPlan` entity was folded into `UserProfile`** (training days, phase) plus `schedule_entries`. The **exercise catalogue lives in code**, not in a database table: it is versioned with the app, and history stores name snapshots.
- **Charts are simple Compose Canvas charts**, not a chart library.
- **Older conversation is summarised deterministically** (recent user messages), not by an LLM.
- **Dependency versions are stable releases from late 2024** (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1). They were chosen for known compatibility because the toolchain could only be exercised through CI.

## 5. Architecture and files

Read `workout-coach/README.md` for the full layout.

| Module | Contents |
|---|---|
| `core/` | `equipment/`, `catalog/`, `plan/` (planner, progression, schedule), `timer/`, `reminders/`, `progress/`, `coach/` (models, parser and redactor, validator, intents and safety, local coach, remote client, orchestrator) |
| `app/` | `data/db` (entities, DAOs, `AppDatabase`), `data/repo` (Training, Coach, Data repositories and mappers), `data/settings` (DataStore), `notifications/`, `ui/` (home, coach, workout, progress, settings, equipment, memory, onboarding, theme, components), `AppContainer`, `MainActivity` |
| `backend/` | `src/app.js`, `src/providers.js`, `src/prompt.js`, `src/server.js`, `test/app.test.js`, `.env.example` |

## 6. Database schema (Room v1)

The exported schema is `app/schemas/com.personal.workoutcoach.data.db.AppDatabase/1.json` (identity hash `3b35f939…`).

| Table | Notes |
|---|---|
| `user_profile` | Single row |
| `equipment` | — |
| `workout_sessions` | Indexed on status and date |
| `exercise_performances` | FK → sessions, CASCADE; unique (sessionId, orderIndex) |
| `set_performances` | FK → exercise_performances, CASCADE; unique (exercisePerformanceId, setNumber) |
| `schedule_entries` | Unique date; FK → sessions, SET NULL |
| `chat_messages` | — |
| `user_feedback` | FK → sessions, SET NULL |
| `progress_measurements` | — |
| `coach_memory` | — |
| `reminder_configs` | — |

Multi-row operations use `withTransaction`. There are no migrations yet because this is the first version. Destructive fallback is intentionally **not** enabled.

## 7. AI provider configuration

The phone needs only a backend URL and an app token. The backend needs:

- `APP_TOKEN` (24+ random characters)
- At least one provider pair: `GEMINI_API_KEY` + `GEMINI_MODEL`, `GROQ_API_KEY` + `GROQ_MODEL`, or `OPENROUTER_API_KEY` + `OPENROUTER_MODEL`
- Optionally `DEFAULT_PROVIDER`, rate and budget limits, a timeout, and `MAX_OUTPUT_TOKENS`

Model IDs are intentionally not defaulted: the provider docs couldn't be verified from this environment, so copy them from each provider's current model list. Suggested default: Gemini, via its OpenAI-compatible endpoint. Check current pricing and free-tier terms first.

## 8. Security

- No provider key exists in the app or in Git. A core test scans the repository for key patterns, and the backend tests assert that keys and tokens never appear in logs or `/health`.
- `.env`, keystores and `keystore.properties` are git-ignored.
- The app refuses non-HTTPS backend URLs, except localhost and the emulator host.
- Error text is redacted with `Redactor`.
- Exports exclude the backend token.

## 9. Build, install, release

```bash
cd workout-coach
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug   # needs Android SDK (platform 35)
adb install app/build/outputs/apk/debug/app-debug.apk
```

Without a local SDK, download the `workout-coach-apks` artifact from the latest successful CI run.

For a release: create a keystore and `keystore.properties` (see the README), bump the version, then run `./gradlew :app:assembleRelease` or `bundleRelease`, and complete `docs/TESTING_CHECKLIST.md` on a device. The app has **not** been published to Google Play.

## 10. Known limitations

- **Timing**: WorkManager reminders and the inexact rest alarm can be delayed by Doze.
- **Offline coach**: understands keyword intents only.
- **Robolectric UI tests**: verify behaviour and state, not pixel rendering.
- **Exercise safety limits** (maximum loads, difficulty levels) are conservative engineering defaults, not individual assessments.
