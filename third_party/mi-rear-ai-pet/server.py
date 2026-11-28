#!/usr/bin/env python3
"""Xiaomi 17 Pro Max Rear Screen AI Pet — Standalone Cross-Platform Local Server.

Works out-of-the-box on macOS, Windows, and Linux with ZERO external dependencies
(Python 3.9+ standard library only):
  - Built-in universal HTTP event hub (POST /api/event) for Claude Code, Codex,
    Antigravity, Cursor, Gemini CLI, DeepSeek Harness, and custom scripts.
  - One-click Hook install/uninstall API (/api/agents) powering the web console.
  - UDP LAN Auto-Discovery (port 8766) so the native Android app finds the PC
    automatically without typing IP addresses.
  - Built-in QR code generator (/api/qr.svg) and direct Wi-Fi APK download route
    (/download/MiRearPet.apk).
"""

import argparse
import errno
import json
import mimetypes
import os
import platform
import queue
import re
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
from urllib.parse import parse_qs, urlparse

import hook_manager
import qrcode_svg

ROOT_DIR = Path(__file__).resolve().parent
WEB_DIR = ROOT_DIR / "web"
APK_PATH = ROOT_DIR / "android" / "dist" / "MiRearPet.apk"
SHIZUKU_APK_PATH = ROOT_DIR / "android" / "dist" / "Shizuku.apk"
APK_MANIFEST_PATH = ROOT_DIR / "android" / "AndroidManifest.xml"
DISCOVERY_PORT = 8766


def _read_apk_manifest_version() -> Tuple[int, str]:
    try:
        text = APK_MANIFEST_PATH.read_text(encoding="utf-8")
        vc_m = re.search(r'android:versionCode="(\d+)"', text)
        vn_m = re.search(r'android:versionName="([^"]+)"', text)
        vc = int(vc_m.group(1)) if vc_m else 0
        vn = vn_m.group(1) if vn_m else "0.0.0"
        return vc, vn
    except OSError:
        return 0, "0.0.0"

PROVIDERS = ("codex", "dsh", "antigravity", "claude", "workbuddy", "cursor", "gemini", "custom")
PRIORITY = {
    "idle": 0,
    "completed_unread": 1,
    "failed": 2,
    "running": 3,
    "waiting_user": 4,
}


def _is_good_lan_ip(ip: str) -> bool:
    if not ip or ip.startswith("127.") or ip.startswith("169.254.") or ip.startswith("198.18."):
        return False
    return bool(re.match(r"^\d{1,3}(\.\d{1,3}){3}$", ip))


def get_lan_ips() -> List[str]:
    """Cross-platform real LAN IPv4 discovery (filters Clash/VPN 198.18.x.x & loopback)."""
    candidates: List[str] = []

    def add(ip: str) -> None:
        ip = ip.strip()
        if _is_good_lan_ip(ip) and ip not in candidates:
            candidates.append(ip)

    sys_name = platform.system().lower()
    if sys_name == "darwin":
        ipconfig_bin = "/usr/sbin/ipconfig" if Path("/usr/sbin/ipconfig").exists() else "ipconfig"
        for iface in ("en0", "en1", "en2", "en3"):
            try:
                out = subprocess.check_output(
                    [ipconfig_bin, "getifaddr", iface],
                    stderr=subprocess.DEVNULL,
                    text=True,
                    timeout=1,
                ).strip()
                add(out)
            except Exception:
                pass
        if not candidates:
            try:
                ifconfig_bin = "/sbin/ifconfig" if Path("/sbin/ifconfig").exists() else "ifconfig"
                out = subprocess.check_output(
                    [ifconfig_bin], stderr=subprocess.DEVNULL, text=True, timeout=1
                )
                for match in re.findall(r"\binet\s+(\d{1,3}(?:\.\d{1,3}){3})\b", out):
                    add(match)
            except Exception:
                pass
    elif sys_name == "windows":
        try:
            out = subprocess.check_output(
                ["ipconfig"], stderr=subprocess.DEVNULL, text=True, errors="ignore", timeout=2
            )
            for match in re.findall(r"(?:IPv4[^:]*:\s*)(\d{1,3}(?:\.\d{1,3}){3})", out):
                add(match)
        except Exception:
            pass
    else:
        try:
            out = subprocess.check_output(
                ["hostname", "-I"], stderr=subprocess.DEVNULL, text=True, timeout=1
            )
            for token in out.split():
                add(token)
        except Exception:
            pass

    # Fallback UDP routing trick (no packets actually sent)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 80))
        add(sock.getsockname()[0])
    except OSError:
        pass
    finally:
        sock.close()

    # Prefer RFC1918 private LAN ranges (192.168.x.x > 10.x.x.x > 172.16-31.x.x)
    def score(ip: str) -> int:
        if ip.startswith("192.168."):
            return 0
        if ip.startswith("10."):
            return 1
        if ip.startswith("172."):
            return 2
        return 3

    candidates.sort(key=score)
    return candidates or ["127.0.0.1"]


def get_lan_ip() -> str:
    return get_lan_ips()[0]


class UniversalStatusHub:
    """Standalone multi-agent state reducer + SSE broadcaster."""

    def __init__(self, poll_interval: float = 1.0):
        self.poll_interval = poll_interval
        self._lock = threading.Lock()
        self._subscribers: Dict[queue.Queue, str] = {}  # queue -> role ("phone" | "preview")
        self._sessions: Dict[Tuple[str, str], Dict[str, Any]] = {}
        self._recent_events: List[Dict[str, Any]] = []
        self._completion_epoch = uuid.uuid4().hex
        self._completion_seq = 0
        self._completions: List[Dict[str, Any]] = []
        self._agy_ax_waiting = False
        self._agy_ax_next_check = 0.0
        self._agy_ax_backoff_until = 0.0
        self._last_payload_str = ""
        self._current = self._build_snapshot()
        self._stop_event = threading.Event()
        self._thread = threading.Thread(target=self._watch_loop, daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()

    def accept_event(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """Accept a lifecycle event from POST /api/event or POST /api/test."""
        now = time.time()
        provider = str(body.get("provider", "custom")).strip().lower()
        if provider not in PROVIDERS:
            provider = "custom"
        state = str(body.get("state", "idle")).strip().lower()
        if state not in PRIORITY:
            return {"ok": False, "error": f"Invalid state: {state}"}

        session = str(body.get("session") or "default")[:48]
        turn = str(body.get("turn") or "")[:48]
        event_name = str(body.get("event") or state)[:48]
        is_completion = bool(body.get("completion", False)) or state == "completed_unread"

        with self._lock:
            key = (provider, session)
            old = self._sessions.get(key)
            self._sessions[key] = {
                "provider": provider,
                "state": state,
                "at": now,
                "turn": turn,
                "event": event_name,
            }
            if is_completion:
                old_turn = old.get("completed_turn") if old else None
                if not turn or turn != old_turn:
                    self._completion_seq += 1
                    self._completions.append(
                        {
                            "provider": provider,
                            "sequence": self._completion_seq,
                            "at": now,
                        }
                    )
                    self._completions = self._completions[-64:]
                    self._sessions[key]["completed_turn"] = turn or str(self._completion_seq)

            self._recent_events.insert(
                0,
                {
                    "provider": provider,
                    "state": state,
                    "event": event_name,
                    "at": round(now, 3),
                },
            )
            self._recent_events = self._recent_events[:12]

        self._refresh_and_broadcast()
        return {"ok": True, "provider": provider, "state": state}

    def acknowledge_all(self) -> None:
        with self._lock:
            for item in self._sessions.values():
                if item.get("state") in ("completed_unread", "failed"):
                    item["state"] = "idle"
        self._refresh_and_broadcast()

    def clear_test_sessions(self) -> None:
        with self._lock:
            self._sessions = {k: v for k, v in self._sessions.items() if k[1] != "console-test"}
        self._refresh_and_broadcast()

    def _build_snapshot(self) -> Dict[str, Any]:
        now = time.time()

        with self._lock:
            # Auto-decay stale sessions so crashes never leave rear screen stuck
            alive_sessions: Dict[Tuple[str, str], Dict[str, Any]] = {}
            for key, item in self._sessions.items():
                age = now - float(item.get("at", 0))
                st = item.get("state", "idle")
                ttl = 600.0 if st in ("running", "waiting_user") else 300.0
                if age < ttl and st != "idle":
                    alive_sessions[key] = item
            self._sessions = alive_sessions

            # Prune completions older than 30s
            self._completions = [
                c for c in self._completions if now >= float(c.get("at", 0)) and float(c.get("at", 0)) + 30.0 >= now
            ]

            providers: Dict[str, str] = {p: "idle" for p in PROVIDERS}
            for item in self._sessions.values():
                p = item["provider"]
                st = item["state"]
                if PRIORITY.get(st, 0) > PRIORITY.get(providers[p], 0):
                    providers[p] = st

            if self._agy_ax_waiting and providers.get("antigravity") == "running":
                providers["antigravity"] = "waiting_user"

            combined = max(providers.values(), key=PRIORITY.get)
            phone_count = sum(1 for role in self._subscribers.values() if role == "phone")
            total_clients = len(self._subscribers)

            return {
                "hub_online": True,
                "stale": False,
                "providers": providers,
                "combined": combined,
                "completion_epoch": self._completion_epoch,
                "completions": list(self._completions[-16:]),
                "connected_phones": phone_count,
                "connected_clients": total_clients,
                "server_time": now,
            }

    def _snapshot_key(self, snap: Dict[str, Any]) -> str:
        seqs = [(c.get("provider"), c.get("sequence")) for c in snap.get("completions", [])]
        key_obj = {
            "providers": snap["providers"],
            "combined": snap["combined"],
            "completion_epoch": snap.get("completion_epoch", ""),
            "completion_seqs": seqs,
            "connected_phones": snap.get("connected_phones", 0),
        }
        return json.dumps(key_obj, sort_keys=True)

    def _refresh_and_broadcast(self) -> None:
        snap = self._build_snapshot()
        key = self._snapshot_key(snap)
        changed = False
        with self._lock:
            self._current = snap
            if key != self._last_payload_str:
                self._last_payload_str = key
                changed = True
        if changed:
            self._broadcast(snap)

    def _poll_antigravity_permission_ui(self) -> None:
        """macOS-only lightweight Accessibility check while Antigravity is running.
        Detects native permission/question cards (e.g. Allow...? / Yes, allow this time /
        Skip + Submit / Waiting for user input.) without reading text inputs or storing text.
        Gracefully no-ops if Accessibility permission is not granted or on non-macOS."""
        if platform.system() != "Darwin":
            return
        now = time.time()
        if now < self._agy_ax_backoff_until or now < self._agy_ax_next_check:
            return
        with self._lock:
            agy_running = any(
                item.get("provider") == "antigravity" and item.get("state") == "running"
                for item in self._sessions.values()
            )
        if not agy_running:
            self._agy_ax_waiting = False
            return

        self._agy_ax_next_check = now + 0.65
        jxa = (
            'var se = Application("System Events");'
            'var procs = se.applicationProcesses.whose({name: {_contains: "Antigravity"}});'
            'if (procs.length === 0) { "NO_PROC"; } else {'
            '  var p = procs[0];'
            '  var found = false, hasSkip = false, hasSubmit = false;'
            '  try {'
            '    var wins = p.windows();'
            '    for (var w = 0; w < Math.min(wins.length, 2) && !found; w++) {'
            '      var elems = wins[w].entireContents();'
            '      var limit = Math.min(elems.length, 320);'
            '      for (var i = 0; i < limit && !found; i++) {'
            '        try {'
            '          var el = elems[i];'
            '          var role = el.role();'
            '          if (role === "AXTextField" || role === "AXTextArea") continue;'
            '          if (role !== "AXButton" && role !== "AXStaticText") continue;'
            '          var txt = "";'
            '          try { txt = String(el.title() || el.description() || el.value() || ""); } catch (e) {}'
            '          if (!txt || txt.length > 120) continue;'
            '          if (txt === "Skip") hasSkip = true;'
            '          if (txt === "Submit") hasSubmit = true;'
            '          if (txt.indexOf("Yes, allow this time") !== -1 ||'
            '              txt.indexOf("Waiting for user input") !== -1 ||'
            '              /^Allow .+\\?$/.test(txt) ||'
            '              (hasSkip && hasSubmit)) { found = true; }'
            '        } catch (e) {}'
            '      }'
            '    }'
            '    found ? "WAITING" : "NONE";'
            '  } catch (e) { "AX_ERR"; }'
            '}'
        )
        try:
            proc = subprocess.run(
                ["osascript", "-l", "JavaScript", "-e", jxa],
                capture_output=True,
                text=True,
                timeout=0.55,
                check=False,
            )
            out = (proc.stdout or "").strip()
            if "AX_ERR" in out or proc.returncode != 0:
                self._agy_ax_waiting = False
                self._agy_ax_backoff_until = now + 60.0
                return
            self._agy_ax_waiting = (out == "WAITING")
        except Exception:
            self._agy_ax_waiting = False
            self._agy_ax_backoff_until = now + 30.0

    def _watch_loop(self) -> None:
        while not self._stop_event.is_set():
            try:
                self._poll_antigravity_permission_ui()
                self._refresh_and_broadcast()
            except Exception:
                pass
            self._stop_event.wait(self.poll_interval)

    def get_current(self) -> Dict[str, Any]:
        with self._lock:
            return dict(self._current)

    def get_recent_events(self) -> List[Dict[str, Any]]:
        with self._lock:
            return list(self._recent_events)

    def get_connected_counts(self) -> Tuple[int, int]:
        with self._lock:
            phones = sum(1 for role in self._subscribers.values() if role == "phone")
            return phones, len(self._subscribers)

    def subscribe(self, role: str = "phone") -> queue.Queue:
        q: queue.Queue = queue.Queue(maxsize=16)
        with self._lock:
            self._subscribers[q] = "preview" if role == "preview" else "phone"
        self._refresh_and_broadcast()
        return q

    def unsubscribe(self, q: queue.Queue) -> None:
        with self._lock:
            self._subscribers.pop(q, None)
        self._refresh_and_broadcast()

    def _broadcast(self, payload: Dict[str, Any]) -> None:
        data = json.dumps(payload, ensure_ascii=False)
        with self._lock:
            subs = list(self._subscribers.keys())
        for q in subs:
            try:
                q.put_nowait(data)
            except queue.Full:
                try:
                    q.get_nowait()
                    q.put_nowait(data)
                except queue.Empty:
                    pass


class LanDiscoveryResponder:
    """UDP broadcaster responder on port 8766 so the Android app discovers the PC in < 0.5s."""

    def __init__(self, http_port: int, discovery_port: int = DISCOVERY_PORT):
        self.http_port = http_port
        self.discovery_port = discovery_port
        self._stop = threading.Event()
        self._sock: Optional[socket.socket] = None
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass

    def _run(self) -> None:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(("0.0.0.0", self.discovery_port))
            sock.settimeout(1.0)
            self._sock = sock
        except OSError:
            return

        hostname = socket.gethostname().split(".")[0] or "My-PC"
        while not self._stop.is_set():
            try:
                data, addr = sock.recvfrom(512)
                if b"MI_REAR_PET_DISCOVER" in data:
                    lan_ip = get_lan_ip()
                    reply = json.dumps(
                        {
                            "service": "mi-rear-pet",
                            "version": "2.0",
                            "host": hostname,
                            "os": platform.system(),
                            "ip": lan_ip,
                            "port": self.http_port,
                            "url": f"http://{lan_ip}:{self.http_port}/",
                        },
                        ensure_ascii=False,
                    ).encode("utf-8")
                    sock.sendto(reply, addr)
            except socket.timeout:
                continue
            except OSError:
                break


def make_handler(hub: UniversalStatusHub, port: int):
    class PetRequestHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, format: str, *args) -> None:
            client_ip = self.client_address[0] if self.client_address else ""
            if client_ip and client_ip not in ("127.0.0.1", "::1"):
                print(f"[局域网设备 {client_ip}] {format % args}", flush=True)

        def _send_bytes(
            self,
            body: bytes,
            content_type: str,
            status: int = HTTPStatus.OK,
            extra_headers: Optional[Dict[str, str]] = None,
            head_only: bool = False,
        ) -> None:
            self.close_connection = True
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Private-Network", "true")
            if extra_headers:
                for k, v in extra_headers.items():
                    self.send_header(k, v)
            self.end_headers()
            if not head_only and body:
                self.wfile.write(body)

        def do_HEAD(self) -> None:
            self._handle_get_or_head(head_only=True)

        def do_GET(self) -> None:
            self._handle_get_or_head(head_only=False)

        def _send_json(self, obj: Any, status: int = HTTPStatus.OK) -> None:
            body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self._send_bytes(body, "application/json; charset=utf-8", status)

        def _read_json_body(self) -> Dict[str, Any]:
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            if length <= 0 or length > 256 * 1024:
                return {}
            raw = self.rfile.read(length)
            try:
                parsed = json.loads(raw.decode("utf-8"))
                return parsed if isinstance(parsed, dict) else {}
            except ValueError:
                return {}

        def do_OPTIONS(self) -> None:
            self.close_connection = True
            self.send_response(HTTPStatus.NO_CONTENT)
            self.send_header("Connection", "close")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Private-Network", "true")
            self.send_header("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.end_headers()

        def do_POST(self) -> None:
            parsed = urlparse(self.path)
            path = parsed.path

            if path == "/api/event":
                body = self._read_json_body()
                res = hub.accept_event(body)
                self._send_json(res, HTTPStatus.OK if res.get("ok") else HTTPStatus.BAD_REQUEST)
                return

            if path == "/api/test":
                body = self._read_json_body()
                provider = str(body.get("provider", "codex"))
                state = str(body.get("state", "running"))
                if state == "idle":
                    hub.clear_test_sessions()
                    self._send_json({"ok": True, "provider": provider, "state": "idle"})
                    return
                res = hub.accept_event(
                    {
                        "provider": provider,
                        "state": state,
                        "session": "console-test",
                        "turn": uuid.uuid4().hex[:12],
                        "event": "ConsoleTest",
                        "completion": state == "completed_unread",
                    }
                )
                self._send_json(res, HTTPStatus.OK if res.get("ok") else HTTPStatus.BAD_REQUEST)
                return

            if path == "/api/ack":
                hub.acknowledge_all()
                self._send_json({"ok": True})
                return

            if path == "/api/agents/install":
                body = self._read_json_body()
                agent = str(body.get("agent", "")).strip().lower()
                res = hook_manager.install_agent(agent)
                self._send_json(res, HTTPStatus.OK if res.get("ok") else HTTPStatus.BAD_REQUEST)
                return

            if path == "/api/agents/remove":
                body = self._read_json_body()
                agent = str(body.get("agent", "")).strip().lower()
                res = hook_manager.remove_agent(agent)
                self._send_json(res, HTTPStatus.OK if res.get("ok") else HTTPStatus.BAD_REQUEST)
                return

            if path == "/api/client-log":
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                except ValueError:
                    length = 0
                raw = self.rfile.read(min(max(length, 0), 256 * 1024)) if length > 0 else b""
                text = raw.decode("utf-8", errors="replace").strip()
                if text:
                    log_path = Path.home() / ".local" / "state" / "mi-rear-pet" / "android_client.log"
                    try:
                        log_path.parent.mkdir(parents=True, exist_ok=True)
                        with log_path.open("a", encoding="utf-8") as fh:
                            fh.write(text + "\n")
                    except OSError:
                        pass
                self._send_json({"ok": True})
                return

            self._send_json({"ok": False, "error": "Not Found"}, HTTPStatus.NOT_FOUND)

        def _handle_get_or_head(self, head_only: bool = False) -> None:
            parsed = urlparse(self.path)
            path = parsed.path
            qs = parse_qs(parsed.query)

            if path == "/api/status":
                body = json.dumps(hub.get_current(), ensure_ascii=False).encode("utf-8")
                self._send_bytes(body, "application/json; charset=utf-8", head_only=head_only)
                return

            if path == "/api/info":
                lan_ips = get_lan_ips()
                lan_ip = lan_ips[0]
                lan_url = f"http://{lan_ip}:{port}/"
                apk_ok = APK_PATH.is_file()
                shizuku_ok = SHIZUKU_APK_PATH.is_file()
                apk_vc, apk_vn = _read_apk_manifest_version()
                phones, total = hub.get_connected_counts()
                body = json.dumps(
                    {
                        "version": "2.1",
                        "hostname": socket.gethostname().split(".")[0],
                        "os": platform.system(),
                        "lan_ip": lan_ip,
                        "lan_ips": lan_ips,
                        "port": port,
                        "lan_url": lan_url,
                        "apk_available": apk_ok,
                        "apk_version_code": apk_vc,
                        "apk_version_name": apk_vn,
                        "apk_url": f"http://{lan_ip}:{port}/download/MiRearPet.apk",
                        "apk_size_kb": round(APK_PATH.stat().st_size / 1024, 1) if apk_ok else 0,
                        "shizuku_apk_available": shizuku_ok,
                        "shizuku_apk_url": f"http://{lan_ip}:{port}/download/Shizuku.apk",
                        "shizuku_apk_size_mb": round(SHIZUKU_APK_PATH.stat().st_size / (1024 * 1024), 2) if shizuku_ok else 0,
                        "connected_phones": phones,
                        "connected_clients": total,
                        "recent_events": hub.get_recent_events(),
                    },
                    ensure_ascii=False,
                ).encode("utf-8")
                self._send_bytes(body, "application/json; charset=utf-8", head_only=head_only)
                return

            if path == "/api/client-log":
                log_path = Path.home() / ".local" / "state" / "mi-rear-pet" / "android_client.log"
                content = log_path.read_text(encoding="utf-8", errors="replace") if log_path.is_file() else ""
                self._send_bytes(content.encode("utf-8"), "text/plain; charset=utf-8", head_only=head_only)
                return

            if path == "/api/agents":
                body = json.dumps({"agents": hook_manager.get_agents_status()}, ensure_ascii=False).encode("utf-8")
                self._send_bytes(body, "application/json; charset=utf-8", head_only=head_only)
                return

            if path == "/api/qr.svg":
                data = (qs.get("data") or [""])[0].strip()
                if not data:
                    data = f"http://{get_lan_ip()}:{port}/"
                try:
                    svg = qrcode_svg.render_svg(data)
                    self._send_bytes(svg.encode("utf-8"), "image/svg+xml; charset=utf-8", head_only=head_only)
                except Exception as exc:
                    self._send_bytes(
                        str(exc).encode("utf-8"), "text/plain; charset=utf-8", HTTPStatus.BAD_REQUEST, head_only=head_only
                    )
                return

            if path == "/download/Shizuku.apk":
                if not SHIZUKU_APK_PATH.is_file():
                    self._send_bytes(
                        b"Shizuku APK not bundled", "text/plain; charset=utf-8", HTTPStatus.NOT_FOUND, head_only=head_only
                    )
                    return
                try:
                    body = SHIZUKU_APK_PATH.read_bytes()
                    self._send_bytes(
                        body,
                        "application/vnd.android.package-archive",
                        HTTPStatus.OK,
                        {"Content-Disposition": 'attachment; filename="Shizuku-v13.6.0.apk"'},
                        head_only=head_only,
                    )
                except OSError:
                    self._send_bytes(
                        b"Internal Error",
                        "text/plain; charset=utf-8",
                        HTTPStatus.INTERNAL_SERVER_ERROR,
                        head_only=head_only,
                    )
                return

            if path == "/download/MiRearPet.apk":
                if not APK_PATH.is_file():
                    self._send_bytes(
                        b"APK not built yet", "text/plain; charset=utf-8", HTTPStatus.NOT_FOUND, head_only=head_only
                    )
                    return
                try:
                    body = APK_PATH.read_bytes()
                    self._send_bytes(
                        body,
                        "application/vnd.android.package-archive",
                        HTTPStatus.OK,
                        {"Content-Disposition": 'attachment; filename="MiRearPet.apk"'},
                        head_only=head_only,
                    )
                except OSError:
                    self._send_bytes(
                        b"Internal Error",
                        "text/plain; charset=utf-8",
                        HTTPStatus.INTERNAL_SERVER_ERROR,
                        head_only=head_only,
                    )
                return

            if path == "/api/events":
                if head_only:
                    self._send_bytes(b"", "text/event-stream; charset=utf-8", head_only=True)
                    return
                role = (qs.get("role") or ["phone"])[0]
                self._handle_sse(role)
                return

            rel = "index.html" if path in ("/", "") else path.lstrip("/")
            target = (WEB_DIR / rel).resolve()
            if not str(target).startswith(str(WEB_DIR.resolve())) or not target.is_file():
                self._send_bytes(b"Not Found", "text/plain; charset=utf-8", HTTPStatus.NOT_FOUND, head_only=head_only)
                return

            ctype, _ = mimetypes.guess_type(str(target))
            if not ctype:
                ctype = "application/octet-stream"
            if ctype.startswith("text/") or ctype in ("application/json", "application/javascript", "image/svg+xml"):
                ctype += "; charset=utf-8"

            try:
                body = target.read_bytes()
                self._send_bytes(body, ctype, head_only=head_only)
            except OSError:
                self._send_bytes(
                    b"Internal Error",
                    "text/plain; charset=utf-8",
                    HTTPStatus.INTERNAL_SERVER_ERROR,
                    head_only=head_only,
                )

        def _handle_sse(self, role: str) -> None:
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("X-Accel-Buffering", "no")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()

            q = hub.subscribe(role=role)
            try:
                initial = json.dumps(hub.get_current(), ensure_ascii=False)
                self.wfile.write(f"data: {initial}\n\n".encode("utf-8"))
                self.wfile.flush()

                while True:
                    try:
                        data = q.get(timeout=15.0)
                        self.wfile.write(f"data: {data}\n\n".encode("utf-8"))
                        self.wfile.flush()
                    except queue.Empty:
                        self.wfile.write(b": ping\n\n")
                        self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError, OSError):
                pass
            finally:
                hub.unsubscribe(q)

    return PetRequestHandler


class QuietThreadingHTTPServer(ThreadingHTTPServer):
    """Suppress noisy stack traces when mobile browsers sleep or reset keep-alive sockets."""

    daemon_threads = True
    allow_reuse_address = os.name != "nt"

    def handle_error(self, request, client_address) -> None:
        exc = sys.exc_info()[1]
        if isinstance(exc, (ConnectionResetError, BrokenPipeError, ConnectionAbortedError, TimeoutError)):
            return
        super().handle_error(request, client_address)


def _configure_stdio_utf8() -> None:
    """Prevent UnicodeEncodeError on non-UTF-8 Windows consoles (e.g. English CP1252)."""
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass


def _check_existing_server(port: int) -> Optional[Dict[str, Any]]:
    """Return /api/status payload if Mi Rear Pet is already running on localhost:port."""
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(f"http://127.0.0.1:{port}/api/status", timeout=1.0) as resp:
            if resp.status != 200:
                return None
            data = json.loads(resp.read().decode("utf-8", errors="replace"))
            if isinstance(data, dict) and data.get("hub_online") is True and "providers" in data:
                return data
    except Exception:
        pass
    return None


def _open_browser(url: str) -> None:
    """Open desktop console URL in the default browser with Windows fallbacks."""
    try:
        import webbrowser

        if webbrowser.open(url):
            return
    except Exception:
        pass
    if os.name == "nt":
        try:
            os.startfile(url)  # type: ignore[attr-defined]
            return
        except Exception:
            pass
        try:
            subprocess.Popen(["cmd", "/c", "start", "", url], shell=False)
        except Exception:
            pass


def main() -> None:
    _configure_stdio_utf8()

    parser = argparse.ArgumentParser(description="Xiaomi Rear Screen AI Pet Standalone Server")
    parser.add_argument("--host", default="0.0.0.0", help="Bind host (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8765, help="Bind port (default: 8765)")
    parser.add_argument("--open", action="store_true", help="Auto-open desktop preview & setup console in browser")
    args = parser.parse_args()

    console_url = f"http://127.0.0.1:{args.port}/?preview=1"

    existing = _check_existing_server(args.port)
    if existing is not None:
        lan_ip = get_lan_ip()
        print("=" * 64)
        print(f"  检测到「小米妙享背屏 AI 桌宠」服务已在端口 {args.port} 运行中！")
        print(f"  电脑控制台/预览 : {console_url}")
        print(f"  手机局域网直连  : http://{lan_ip}:{args.port}/")
        print("=" * 64)
        if args.open:
            print("  正在为你重新打开浏览器控制台...")
            _open_browser(console_url)
            time.sleep(2.5)
        return

    hub = UniversalStatusHub()
    try:
        server = QuietThreadingHTTPServer((args.host, args.port), make_handler(hub, args.port))
    except OSError as exc:
        winerr = getattr(exc, "winerror", None)
        print("=" * 64)
        print(f"  [错误] 无法监听端口 {args.port}：{exc}")
        if winerr == 10013:
            print("  可能原因：Windows 的 Hyper-V / WSL / Docker 保留了该端口段，或被安全软件拦截。")
            print("  解决建议：可尝试在命令行运行 python server.py --port 18765 --open，")
            print("           或右键以管理员运行「Windows放行防火墙8765端口.bat」后重试。")
        elif exc.errno == errno.EADDRINUSE or winerr == 10048:
            print(f"  可能原因：端口 {args.port} 已被本机其他程序占用。")
            print(f"  解决建议：关闭占用 {args.port} 端口的程序，或改用其他端口启动。")
        print("=" * 64)
        sys.exit(1)

    hub.start()
    discovery = LanDiscoveryResponder(http_port=args.port)
    discovery.start()

    lan_ip = get_lan_ip()
    phone_url = f"http://{lan_ip}:{args.port}/"

    print("=" * 64)
    print("  小米妙享背屏 AI 桌宠 · 通用独立版服务已启动 (v2.0)")
    print("=" * 64)
    print(f"  电脑控制台/预览 : {console_url}")
    print(f"  手机局域网直连  : {phone_url}")
    print(f"  APK 扫码直装    : http://{lan_ip}:{args.port}/download/MiRearPet.apk")
    print(f"  局域网自动发现  : UDP :{DISCOVERY_PORT} 已开启（App 打开自动搜到本机）")
    print(f"  USB 数据线直连  : adb reverse tcp:{args.port} tcp:{args.port}")
    print("=" * 64)

    if args.open:
        threading.Timer(0.6, lambda: _open_browser(console_url)).start()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n正在停止服务...")
    finally:
        discovery.stop()
        hub.stop()
        server.server_close()


if __name__ == "__main__":
    main()
