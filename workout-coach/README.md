# Personal AI Workout Coach (Android)

A local-first, chat-first home-workout coach for one person. It's built around restarting training after a break, with a pair of 17.5 kg dumbbells and whatever other equipment you add later.

* **Works fully offline.** The workout planner, tracking, rest timer, reminders, progress reports and a rule-based coach (English and Hinglish) all run on the phone.
* **Remote AI is optional.** When you enable it, the app talks to *your own* small backend (`backend/`). The backend holds the provider API key (Gemini, Groq or OpenRouter) and enforces rate limits and spending limits. The APK never contains a provider key.

```
workout-coach/
├── core/      Pure Kotlin/JVM domain logic (no Android, no network required)
│              equipment · exercise catalogue · planner/progression · schedule · rest timer
│              reminder policy · progress reports · coach (safety screen, intents, local coach,
│              AI response parser + validator, remote client, orchestrator)
├── app/       Android app: Jetpack Compose + Material 3, Room, DataStore, WorkManager
├── backend/   Zero-dependency Node 20 AI proxy (auth, rate limit, budgets, idempotency)
└── docs/      Privacy policy, testing checklist, implementation report
```

## Architecture

| Layer | Where | Notes |
|---|---|---|
| Presentation | `app/.../ui/*` | One ViewModel per screen (MVVM), `StateFlow`/`Flow`, Compose + Material 3, light/dark themes |
| Domain | `core/...` | Deterministic and unit-tested. The AI cannot change these rules. |
| Workout planning | `core/plan` | 3-day full-body comeback templates A/B/C, Minimum/Standard/Extended modes, double progression |
| Equipment | `core/equipment` | Inventory → capabilities → exercise compatibility, load options and safety limits |
| Persistence | `app/.../data/db` | Room (11 tables, FKs, indices, transactions), exported schema in `app/schemas` |
| Settings | `app/.../data/settings` | DataStore preferences (no provider keys, ever) |
| AI integration | `core/coach` + `backend/` | `FitnessCoachProvider` interface: `LocalRuleBasedCoach`, `RemoteBackendCoach`; `RecommendationValidator` |
| Notifications | `app/.../notifications` | WorkManager reminders + `ReminderPolicy`; rest-timer alarm |
| DI | `AppContainer` | Manual DI. The app is small enough that a framework isn't worth it. |

### How a coach message flows

1. **Safety screen** (deterministic): chest pain, fainting, sharp pain, illness and similar messages get a fixed safety reply. They are never sent to an AI.
2. If remote AI is enabled, `RemoteBackendCoach` POSTs to `<backend>/v1/coach` with the message, the recent conversation and a context built from local data. The context includes equipment, available exercise IDs and the three locally computed plan options. History and memory are included only if *Share history* is on.
3. The reply must be JSON matching `CoachReply`. Any workout in it goes through `RecommendationValidator`:
   - Unknown exercise IDs are dropped.
   - Exercises your equipment can't support are swapped for a compatible easier variant, or removed.
   - Sets, reps, seconds and rest are clamped to the phase limits.
   - Loads are always picked from your inventory, never taken from the AI.
   - Duration is recomputed locally.
4. **Any remote failure** (timeout, rate limit, auth, malformed output, provider down) falls back to the offline coach. Its answer is clearly labelled *"Offline rule-based coach"* with an *AI unavailable* notice and a **Retry AI** button. A retry reuses the same request ID, so the backend can return a cached result instead of billing twice.

## Comeback program defaults

* Training days: Mon/Wed/Fri. You can change them; the app warns if you pick back-to-back days.
* **Comeback phase**: 2 sets per exercise, difficulty ≤ 2, stop with 2–3 reps in reserve, no training to failure. It lasts until 9 sessions are completed. A 21+ day break or high reported soreness puts you back into it.
* **Build phase**: up to 4 sets, all difficulties.
* **Progression** changes one thing at a time. When every set hits the top of the rep range, the target goes up by 2 reps, up to 15 reps for compound lifts or 20 for isolation work. After that a set is added, and after that the app suggests a harder variation. The dumbbells are fixed, so the weight is never assumed to go up. Poor recovery or a 10+ day gap removes a set; two tough sessions in a row lower the rep target.
* **Modes**:

  | Mode | Typical duration | Content |
  |---|---|---|
  | Minimum | ~10–15 min | 3 exercises, ≤ 2 sets each |
  | Standard | ~20–30 min | Full template |
  | Extended | ~35–45 min | +1 set on compound lifts, +2 accessories |

  A session is *completed* when every non-skipped exercise has at least 1 set logged. Minimum Mode counts in full.
* **Missed sessions** are recorded as `MISSED` and never deleted. Home offers up to 2 realistic new dates, never the same day as another session and never next to a completed one. You can also skip and continue with the next planned session; workouts are never doubled up.

With the default 2 × 17.5 kg dumbbells, some exercises are deliberately excluded because the load is too heavy for controlled technique:

* Lateral raise and rear-delt fly (limit 10 kg)
* Floor pullover (limit 15 kg)
* Two-arm shoulder press (difficulty 3; the single-arm version is used in the comeback phase)

## Build and run

Requirements: JDK 17+, Android SDK (platform 35, build-tools), Node 20+ (backend only).

```bash
cd workout-coach
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or set ANDROID_HOME
./gradlew :core:test :app:testDebugUnitTest       # unit + Room + Robolectric UI tests
./gradlew :app:assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Without an Android SDK, `settings.gradle.kts` builds `:core` only (`./gradlew :core:test`).

CI (`.github/workflows/workout-coach.yml`) runs:

* core tests
* app unit tests (Room and Robolectric Compose UI tests)
* debug and release builds
* Android lint
* backend tests

The APKs are uploaded as artifacts.

## Optional: remote AI backend

```bash
cd workout-coach/backend
cp .env.example .env    # fill in APP_TOKEN, one provider key + model ID (never commit .env)
set -a; . ./.env; set +a
npm start               # listens on $PORT (default 8787)
npm test                # 11 tests, no network needed
```

Deploy it anywhere that runs Node 20 behind HTTPS: a small VPS, Render, Fly.io, or a home server behind a tunnel. Then, in the app, open **Profile → AI coach**:

1. Enter `https://your-host` and the `APP_TOKEN`.
2. Pick a provider.
3. Switch **Use remote AI** on.

What the backend enforces:

* Bearer-token auth with a timing-safe comparison.
* Request validation: 64 KB body, at most 30 messages, at most 4000 characters each.
* A per-minute rate limit.
* Daily request and token budgets, persisted to `USAGE_FILE`.
* A request timeout and an output token cap (`MAX_OUTPUT_TOKENS`).
* No automatic retries.
* Idempotent replay by request ID (10 minutes).
* Model-output validation.
* Logs with metadata only: no message content, tokens or keys.

`GET /health` shows the configured providers and today's usage.

### Provider evaluation (and why the default is configurable)

All three providers offer an OpenAI-compatible chat-completions endpoint with JSON mode, so one adapter covers them:

| Provider | Endpoint used | Notes |
|---|---|---|
| Google Gemini | `generativelanguage.googleapis.com/v1beta/openai/chat/completions` | Has a free tier for Flash-class models (limits vary by model and change over time); good multilingual/Hinglish quality |
| Groq | `api.groq.com/openai/v1/chat/completions` | Very fast inference of open models; free tier is rate limited per model |
| OpenRouter | `openrouter.ai/api/v1/chat/completions` | One key for many models; some `:free` variants with low limits; `response_format` support depends on the model |

**Important — these details are not verified.** The build environment's network policy blocked the official docs (ai.google.dev, console.groq.com, openrouter.ai), so current model lists, prices and limits could not be checked live. Because of that:

* **No model ID is hard-coded.** You must set `GEMINI_MODEL`, `GROQ_MODEL` or `OPENROUTER_MODEL` from the provider's current model list.
* Base URLs can be overridden (`*_BASE_URL`).
* No free tier is assumed to be permanent.

`DEFAULT_PROVIDER=gemini` is the suggested default (OpenAI-compatible, free tier for small models, good Hinglish). Check the current pricing and terms before relying on it, and set the daily budgets.

## Release build

1. Create a keystore once and keep it out of Git (`*.jks` and `keystore.properties` are ignored):
   ```bash
   keytool -genkeypair -v -keystore workout-coach-release.jks -alias coach -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Create `workout-coach/keystore.properties`:
   ```
   storeFile=workout-coach-release.jks
   storePassword=…
   keyAlias=coach
   keyPassword=…
   ```
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
4. Build:

   ```bash
   ./gradlew :app:assembleRelease    # or :app:bundleRelease for Play
   ```

   R8 minification and resource shrinking are enabled. Without `keystore.properties` the release APK is unsigned.
5. Run through the checklist in `docs/TESTING_CHECKLIST.md` on a real device.

The app has **not** been published to Google Play.

### Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Only used by the optional remote AI |
| `POST_NOTIFICATIONS` | Reminders and rest-over alerts; requested at runtime on Android 13+ |
| `VIBRATE` | Rest-timer alert |

The app uses no location, contacts, analytics or ads, and `allowBackup=false`.

## Known limitations

* **Reminders are approximate.** They use WorkManager, so Doze or battery optimisation can delay them by minutes.
* **The background rest alert may be a few seconds late.** It uses an inexact idle-safe alarm, which needs no exact-alarm permission. The in-app countdown is drift-free because it is derived from `elapsedRealtime`.
* **The offline coach only understands keyword-based intents** (English and Hinglish). Free-form questions need the remote AI.
* **Older conversation history is summarised deterministically** (recent user messages), not by an LLM.
* **No database migrations exist yet** because schema v1 is the first release. Destructive fallback is deliberately disabled.
* **UI tests run under Robolectric, not on an emulator.** They cover logic and state, not pixel rendering.
* **The backend token is stored in app preferences** (excluded from backups). It is a token for your own backend, not a provider key. Rotate it by changing `APP_TOKEN`.
