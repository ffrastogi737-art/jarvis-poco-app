# Release testing checklist (manual, on a real device)

Automated coverage: core unit tests, backend tests, and app Room/repository/Robolectric UI tests (see the implementation report). Before each release, check these on a physical phone:

- [ ] Fresh install → onboarding → Home shows a Minimum, Standard or Extended plan using only 17.5 kg dumbbells and bodyweight.
- [ ] Start a Minimum workout, log sets with the +/- steppers, change exercise, swap an exercise, skip an exercise.
- [ ] Rest timer counts down. Pause, resume, ±15 s, reset and skip work. Lock the screen during rest: the "Rest over" notification arrives (it may be a few seconds late).
- [ ] Swipe the app away mid-workout and reopen it: the session and the timer are restored, and Home offers "Continue workout".
- [ ] Finish the workout → feedback dialog → Progress shows the weekly report and history. Delete a wrong record from the history dialog.
- [ ] Coach: try each quick prompt in English and in Hinglish. "Explain goblet squat" shows instructions; "chest pain" shows the safety message.
- [ ] Remote AI with a deployed backend: a reply is labelled "AI · provider". Stop the backend: an error notice appears, the offline answer appears, and Retry AI works once the backend is back.
- [ ] Equipment: add a resistance band with no anchor (anchored exercises are not suggested), add a door anchor (band row becomes possible), change the dumbbell weight to 10 kg (lateral raise appears), and disable or remove dumbbells (past sessions are unchanged).
- [ ] Reminders: grant the notification permission, set the training reminder 2 minutes ahead, and confirm it arrives. Check that quiet hours block it and that "Pause all" stops it.
- [ ] Missed session: change the device date forward past a training day → Home shows the missed card with reschedule or skip.
- [ ] Export JSON opens in a file manager; Delete all data returns the app to onboarding.
- [ ] Dark mode, a large font size (accessibility), and TalkBack reading the main buttons.
