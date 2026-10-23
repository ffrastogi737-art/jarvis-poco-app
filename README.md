# J.A.R.V.I.S. for POCO X6

Personal JARVIS app (Flutter, Android). Arc-reactor UI, real voice conversation, phone calls,
JARVIS calling you, tools, memory and multi-device control — on free services.

## APK kaise milega

1. GitHub par repo kholo → **Actions** → "Build Android APK" → latest green run.
2. Neeche **Artifacts** mein `jarvis-apk` download karo, zip kholo, `app-release.apk` phone par install karo.
3. Pehli baar app khulne par permissions allow karo (mic, contacts, phone, notifications, alarms).

## Free setup (recommended)

* **Smart brain + Vision:** <https://aistudio.google.com/apikey> se free Gemini key banao →
  app mein **Profile → Advanced** → paste → Save → *Test brain*.
  (Bina key ke bhi JARVIS chalega — free key-less model se, lekin photo/video nahi dekh payega.)
* **Voice:** Microsoft ki free neural voice "Ryan" (British, JARVIS jaisi) — koi key nahi chahiye.
  Hindi ke liye "Madhur". Badalne ke liye **Profile → Voice & Language**.

## Kya kya bol sakte ho

| Bolo | JARVIS kya karega |
|---|---|
| "Call Mummy" / "9876543210 par call karo" | Asli phone call |
| "Rahul ko WhatsApp karo ki main late hoon" | WhatsApp message ready |
| "Kal subah 10 baje bank jaana hai, yaad dilana" | Task + us time JARVIS aapko **call** karega |
| "Mujhe shaam 6 baje call karke meeting yaad dilana" | Scheduled JARVIS call |
| "YouTube par lo-fi music chalao" | YouTube app mein play |
| "Is video ka summary do <link>" | Video summary |
| "Aaj ka weather / news" | Live weather / headlines |
| "Search karo latest iPhone price" | Live web search |
| "Ek futuristic car ki image banao" | Image generation |
| "Yaad rakhna mera business Shri Radha Krishna Jewellers hai" | Memory mein save |
| "Laptop par Chrome kholo" | Laptop JARVIS ko command |

**Call JARVIS:** input bar ka ✳️ (dotted) button, ya Tools → Calling → *Call JARVIS* — phone call jaisa
hands-free baat-cheet. "Bye" bolo to call khatam.

**JARVIS aapko call karega:** task reminders, *Automation* routines (roz subah briefing), scheduled calls,
aur laptop se aaye "kaam ho gaya" events — full-screen incoming call ke saath. Na uthao to message
conversation mein "Missed call" ban kar save ho jata hai.

## Laptop / doosre devices jodna

[DEVICE_PROTOCOL.md](DEVICE_PROTOCOL.md) dekho. Short mein: laptop par `/status`, `/command`, `/events`
wala server chalao (example: `laptop_agent/jarvis_agent.py`), ngrok se expose karo, app mein
**Connected Devices → Pair New Device** mein address daalo.

## Developer notes

* `lib/services/` — brain (Gemini / Pollinations), agent loop + tools (`jarvis.dart`), voice
  (Edge neural TTS port in `edge_tts.dart`, speech recognition), calls, devices, notifications.
* `lib/screens/`, `lib/ui/` — the screens from the design.
* `flutter analyze && flutter test`; `SCREENSHOT_DIR=/tmp/shots flutter test test/screens_test.dart --update-goldens`
  renders every screen to PNG.
