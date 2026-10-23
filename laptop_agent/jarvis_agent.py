"""Minimal JARVIS device agent (Python 3 standard library only).

Run it on any laptop/PC/Raspberry Pi so the phone app can see and command it:
    set JARVIS_TOKEN=some-secret        (optional, Windows)   export JARVIS_TOKEN=...  (Linux/mac)
    python jarvis_agent.py              (listens on port 5000)
Expose it with ngrok (`ngrok http 5000`) or use the LAN address, then add it in the app:
Connected Devices -> Pair New Device.

Replace `handle()` with a call into your own laptop JARVIS. See DEVICE_PROTOCOL.md.
"""
import json
import os
import platform
import shutil
import subprocess
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("JARVIS_TOKEN", "")
EVENTS = []  # messages waiting for the phone, e.g. {"type": "call", "message": "Report is ready"}


def battery():
    try:
        if platform.system() == "Windows":
            out = subprocess.run(["WMIC", "PATH", "Win32_Battery", "Get", "EstimatedChargeRemaining"],
                                 capture_output=True, text=True, timeout=5).stdout
            return int([l for l in out.split() if l.isdigit()][0])
        path = "/sys/class/power_supply/BAT0/capacity"
        return int(open(path).read()) if os.path.exists(path) else None
    except Exception:
        return None


def handle(command: str) -> str:
    """Turn a natural-language command into an action. Plug your laptop JARVIS in here."""
    c = command.lower().strip()
    if c.startswith("open "):
        target = command[5:].strip()
        if "." in target and " " not in target:
            webbrowser.open(target if target.startswith("http") else "https://" + target)
            return f"Opened {target}"
        app = shutil.which(target) or target
        if platform.system() == "Windows":
            os.startfile(app) if os.path.exists(app) else subprocess.Popen(["cmd", "/c", "start", "", target])
        else:
            subprocess.Popen([app])
        return f"Launching {target}"
    if c in ("status", "ping"):
        return f"{platform.node()} online, battery {battery()}%"
    return f"Received: {command} (no handler yet - connect your laptop JARVIS in handle())"


class Agent(BaseHTTPRequestHandler):
    def _reply(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _authorised(self):
        if TOKEN and self.headers.get("Authorization") != f"Bearer {TOKEN}":
            self._reply(401, {"error": "bad token"})
            return False
        return True

    def do_GET(self):
        if not self._authorised():
            return
        if self.path.startswith("/status"):
            self._reply(200, {"online": True, "name": platform.node(), "os": f"{platform.system()} {platform.release()}",
                              "battery": battery()})
        elif self.path.startswith("/events"):
            pending = EVENTS[:]
            EVENTS.clear()
            self._reply(200, pending)
        else:
            self._reply(404, {"error": "not found"})

    def do_POST(self):
        if not self._authorised():
            return
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path.startswith("/command"):
            try:
                self._reply(200, {"reply": handle(body.get("command", ""))})
            except Exception as e:
                self._reply(500, {"reply": f"Failed: {e}"})
        elif self.path.startswith("/notify"):
            # Your laptop JARVIS posts here when a long task finishes; the phone rings with it.
            EVENTS.append({"type": body.get("type", "call"), "message": body.get("message", "")})
            self._reply(200, {"ok": True})
        else:
            self._reply(404, {"error": "not found"})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    print(f"JARVIS agent on port {port}")
    ThreadingHTTPServer(("0.0.0.0", port), Agent).serve_forever()
