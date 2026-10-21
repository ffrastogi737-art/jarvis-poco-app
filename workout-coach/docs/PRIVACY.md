# Privacy policy — Personal AI Workout Coach

_Last updated: 2026-10-21_

The app is built for a single personal user. There is no account, no analytics SDK, no advertising, no crash-reporting service, and no access to contacts, location or other unrelated device data.

## What is stored, and where

Everything is stored **only on your phone**, in the app's private database and preferences:

- Your profile: goal, language, coaching style, training days, preferred time, restrictions you typed.
- Your equipment list.
- Workout sessions, sets, reps and weights, plus your schedule and any missed or rescheduled sessions.
- Feedback ratings (energy, motivation, soreness) and body measurements, only when you enter them.
- Coach chat history, and "coach memory" notes you can view, edit and delete.
- Reminder settings. If you enable remote AI, also the backend URL and app token.

Android cloud backup and device-to-device transfer are disabled for this data.

## When data leaves the phone

Data leaves the phone **only if you turn on "Use remote AI"** and configure a backend. Even then, it is sent only when you send a chat message. Each request goes to *your* backend URL, which forwards it to the AI provider you selected (Google Gemini, Groq or OpenRouter). That provider's own privacy terms apply.

Each request contains:

- Your message.
- Recent chat turns.
- A coaching context: language, style, phase, equipment, available exercises and planned workout options.
- If **Share training history & memory** is on, also: recent session summaries, this week's numbers, your last feedback, restrictions and memory notes.

Turn sharing off to send only the message, equipment and plan options.

Safety-related messages (e.g. chest pain, fainting, injury, illness) are answered by fixed on-device rules and are never sent to the AI.

The backend stores no conversation content. It logs only request metadata (time, request ID, provider, status, duration, token count) and keeps daily usage counters.

## Your controls

- **Export**: Profile → Your data → Export all data (JSON). The backend token is never included.
- **Delete**: Profile → Your data → Delete all data. This permanently removes everything from the phone and cancels reminders.
- **Edit or delete** coach memory, chat history, workout records and measurements at any time.
- **Pause** all reminders, or turn remote AI off.

## Health disclaimer

The app provides general fitness guidance, not medical advice, diagnosis or treatment. Stop exercising and seek medical help if you experience chest pain, fainting, severe breathlessness or unusual symptoms.
