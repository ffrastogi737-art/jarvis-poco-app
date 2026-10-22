# JARVIS Device Protocol

How any device (laptop, PC, Raspberry Pi, TV box…) joins the JARVIS system so the phone app can
see it, command it, and get called back by it. Plain HTTP + JSON, so it works from any language.

```
📱 Phone app  ──GET /status──────▶  💻 Device agent
              ──POST /command────▶   (your laptop JARVIS)
              ◀─GET /events──────   (device → phone: "call Krishna")
```

## Endpoints the device exposes

All requests carry `ngrok-skip-browser-warning: true`, and `Authorization: Bearer <token>` when a
token is set for the device in the app.

| Method & path | Request body | Response | Used for |
|---|---|---|---|
| `GET /status` | – | `{"online": true, "name": "MY-PC", "os": "Windows 11", "battery": 87}` | Online dot, battery %, device detail |
| `POST /command` | `{"command": "open chrome", "from": "phone"}` | `{"reply": "Chrome is open, sir."}` | JARVIS's `device` tool and the command box on the device page |
| `GET /events` | – | `[{"type": "call", "message": "Your report is ready"}]` | Device → phone messages. `type: "call"` rings the phone with an incoming JARVIS call, anything else shows a notice. Return each event once. |

* `/status` is optional: a device that only has `/command` (like the existing laptop server) still
  shows as online when it answers.
* `/events` is optional and is polled every 30 s while the app is open (Settings → Advanced →
  "Listen to devices").
* `/command` may take up to 90 s; reply with what happened, in one or two sentences — JARVIS reads
  it and answers you out loud.

## Connecting your laptop JARVIS

1. Make your laptop server answer the three endpoints above (or start from
   `laptop_agent/jarvis_agent.py` and put your logic in `handle()`).
2. Expose it: `ngrok http 5000` (or use the LAN address like `http://192.168.1.5:5000` on the same Wi-Fi).
3. In the app: **Connected Devices → Pair New Device** (or tap Laptop) → paste the address, optional
   token, choose *Full Access* (runs commands directly) or *Limited* (JARVIS asks you first) → **Test**.
4. Now say: *"Laptop par Chrome kholo"* or *"Ask my laptop to back up the Documents folder"*.

To have the laptop call you when a long job finishes, push an event (the reference agent has
`POST /notify` for this):

```bash
curl -X POST http://localhost:5000/notify -H "Content-Type: application/json" \
     -d '{"type":"call","message":"Krishna, the market research report is ready."}'
```

## Trust levels

| Setting | Effect |
|---|---|
| Untrusted | All commands blocked |
| Trusted + Limited Access | JARVIS asks for your approval before every command |
| Trusted + Full Access | JARVIS sends commands directly |
