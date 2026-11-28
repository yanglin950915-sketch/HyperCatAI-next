#!/usr/bin/env python3
"""Mi Rear AI Pet — Lightweight Cross-Platform Agent Hook Client.

Invoked by Claude Code / Codex / Antigravity / Cursor / Gemini CLI lifecycle hooks.
Never reads, logs, or transmits prompt text, code, or model replies — only opaque
hashed session IDs and high-level lifecycle states (running / waiting_user /
completed_unread / failed / idle).

Always exits 0 within <= 0.4s even if the local pet server is offline.
"""

import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request

DEFAULT_PORT = int(os.environ.get("MI_REAR_PET_PORT", "8765"))
ENDPOINT = f"http://127.0.0.1:{DEFAULT_PORT}/api/event"
TIMEOUT_SEC = 0.35


def _short_hash(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8", errors="ignore")).hexdigest()[:24]


def _read_stdin_json() -> dict:
    try:
        if sys.stdin is None or sys.stdin.isatty():
            return {}
        data = sys.stdin.buffer.read(2 * 1024 * 1024)
        if not data:
            return {}
        parsed = json.loads(data.decode("utf-8", errors="ignore"))
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def _extract_session_id(provider: str, payload: dict) -> str:
    for key in (
        "session_id",
        "sessionId",
        "thread_id",
        "threadId",
        "conversation_id",
        "conversationId",
        "chat_id",
        "generation_id",
        "cwd",
    ):
        val = payload.get(key)
        if isinstance(val, str) and val.strip():
            return _short_hash(f"{provider}:{val.strip()}")
    return _short_hash(f"{provider}:default")


def _extract_turn_id(payload: dict) -> str:
    for key in ("turn_id", "turnId", "execution_id", "executionId", "invocation_id", "generation_id"):
        val = payload.get(key)
        if isinstance(val, str) and val.strip():
            return _short_hash(val.strip())
    return _short_hash(f"t:{int(time.time() * 10)}")


def map_event(provider: str, event: str, payload: dict):
    """Map (provider, event) -> (state, is_completion) or None to ignore."""
    p = provider.lower()

    if p == "claude":
        if event == "PreToolUse":
            if str(payload.get("tool_name") or "") == "AskUserQuestion":
                return ("waiting_user", False)
            return ("running", False)
        if event in ("UserPromptSubmit", "PostToolUse"):
            return ("running", False)
        if event == "PermissionRequest":
            return ("waiting_user", False)
        if event == "Notification":
            ntype = str(payload.get("notification_type", "")).lower()
            if ntype in ("permission_prompt", "elicitation_dialog"):
                return ("waiting_user", False)
            return None
        if event == "Stop":
            return ("completed_unread", True)
        if event == "StopFailure":
            return ("failed", False)
        if event in ("SessionEnd", "Interrupt"):
            return ("idle", False)
        return None

    if p == "codex":
        if event == "UserPromptSubmit":
            return ("running", False)
        if event in ("PreToolUse", "PostToolUse"):
            tool = str(payload.get("tool_name") or "").replace("__", ".").rsplit(".", 1)[-1]
            if tool in ("request_user_input", "request_user_input_async"):
                if event == "PreToolUse":
                    return ("waiting_user", False)
                if tool == "request_user_input_async":
                    resp = payload.get("tool_response")
                    if isinstance(resp, str):
                        try:
                            resp = json.loads(resp)
                        except ValueError:
                            resp = None
                    rejected = isinstance(resp, dict) and (resp.get("accepted") is False or resp.get("isError") is True)
                    return ("running" if rejected else "waiting_user", False)
            return ("running", False)
        if event == "PermissionRequest":
            return ("waiting_user", False)
        if event == "Stop":
            return ("completed_unread", True)
        if event in ("Interrupt", "SessionEnd"):
            return ("idle", False)
        return None

    if p == "antigravity":
        if event in ("PreInvocation", "PostToolUse"):
            return ("running", False)
        if event == "AskQuestion":
            return ("waiting_user", False)
        if event == "PreToolUse":
            tool_name = str(
                payload.get("tool_name")
                or payload.get("toolName")
                or payload.get("tool")
                or payload.get("name")
                or ""
            ).lower()
            if tool_name in ("ask_question", "ask_user_question") or "question" in tool_name:
                return ("waiting_user", False)
            return ("running", False)
        if event == "Stop":
            return ("idle", True)
        return None

    if p == "workbuddy":
        if event in ("UserPromptSubmit", "PreToolUse", "PostToolUse"):
            return ("running", False)
        if event == "PermissionRequest":
            return ("waiting_user", False)
        if event == "PostToolUseFailure":
            return ("failed", False)
        if event == "Stop":
            return ("completed_unread", True)
        if event in ("SessionEnd", "Interrupt"):
            return ("idle", False)
        return None

    if p == "cursor":
        if event in ("beforeSubmitPrompt", "preToolUse", "postToolUse"):
            return ("running", False)
        if event == "postToolUseFailure":
            return ("failed", False)
        if event == "stop":
            return ("completed_unread", True)
        return None

    if p == "gemini":
        if event in ("BeforeAgent", "BeforeTool", "AfterTool"):
            return ("running", False)
        if event == "AfterAgent":
            return ("idle", True)
        if event == "SessionEnd":
            return ("idle", False)
        return None

    # Generic fallback for custom integrations
    valid = {"idle", "running", "waiting_user", "completed_unread", "failed"}
    if event in valid:
        return (event, event == "completed_unread")
    return None


def _post_event(body: dict) -> None:
    raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        ENDPOINT,
        data=raw,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        # Bypass any HTTP(S) proxy for localhost!
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req, timeout=TIMEOUT_SEC):
            pass
    except Exception:
        pass


def _emit_required_stdout(provider: str, event: str) -> None:
    p = provider.lower()
    if p == "antigravity":
        if event in ("PreInvocation", "PostToolUse"):
            sys.stdout.write("{}\n")
        else:
            sys.stdout.write('{"decision":"allow"}\n')
        sys.stdout.flush()
    elif p == "gemini":
        sys.stdout.write('{"decision":"allow"}\n')
        sys.stdout.flush()
    elif p == "cursor":
        sys.stdout.write("{}\n")
        sys.stdout.flush()


def main() -> None:
    if len(sys.argv) < 3:
        sys.exit(0)
    provider = sys.argv[1].strip().lower()
    event = sys.argv[2].strip()
    payload = _read_stdin_json()

    try:
        mapped = map_event(provider, event, payload)
        if mapped is not None:
            state, is_completion = mapped
            _post_event(
                {
                    "provider": provider,
                    "state": state,
                    "event": event,
                    "session": _extract_session_id(provider, payload),
                    "turn": _extract_turn_id(payload),
                    "completion": is_completion,
                    "at": time.time(),
                }
            )
    except Exception:
        pass

    _emit_required_stdout(provider, event)


if __name__ == "__main__":
    main()
