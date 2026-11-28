#!/usr/bin/env python3
"""Mi Rear AI Pet — Safe One-Click Agent Hook Manager (macOS / Windows / Linux).

Detects installed AI coding agents and safely installs/removes Mi Rear AI Pet
lifecycle hooks without touching any existing user hooks or settings.
Always backs up the original config file before the first modification.
"""

import json
import os
import queue
import re
import shlex
import shutil
import stat
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

ROOT_DIR = Path(__file__).resolve().parent
HOOK_CLIENT = ROOT_DIR / "hook_client.py"
BACKUP_DIR = Path.home() / ".local" / "state" / "mi-rear-pet" / "backups"

HOOK_MARKER = "Mi Rear AI Pet status"
AGY_ENTRY_NAME = "mi-rear-pet-status"


def _format_command(provider: str, event: str) -> str:
    py = sys.executable or ("python" if os.name == "nt" else "/usr/bin/python3")
    argv = [py, str(HOOK_CLIENT), provider, event]
    if os.name == "nt":
        return subprocess.list2cmdline(argv)
    return " ".join(shlex.quote(part) for part in argv)


def _is_our_command(cmd: Any, provider: str) -> bool:
    if not isinstance(cmd, str):
        return False
    return "hook_client.py" in cmd and f" {provider} " in f"{cmd} "


def _atomic_write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    tmp = path.with_suffix(path.suffix + ".mi_rear_pet.tmp")
    tmp.write_text(content, encoding="utf-8")

    # 1. Try atomic replace (with brief retries on Windows for AV / file-watcher transient locks)
    last_exc: Optional[OSError] = None
    attempts = 4 if os.name == "nt" else 1
    for attempt in range(attempts):
        try:
            os.replace(tmp, path)
            return
        except PermissionError as exc:
            last_exc = exc
            if os.name != "nt":
                break
            # Clear Read-Only flag on target if set (Win32 MoveFileExW rejects Read-Only with WinError 5)
            if attempt == 0 and path.exists():
                try:
                    os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
                except OSError:
                    pass
            time.sleep(0.08 * (attempt + 1))
        except OSError as exc:
            last_exc = exc
            if os.name != "nt" or getattr(exc, "winerror", None) not in (5, 32):
                break
            time.sleep(0.08 * (attempt + 1))

    # 2. Windows fallback: when target is held open without FILE_SHARE_DELETE or marked Hidden,
    #    os.replace fails with WinError 5/32 even though in-place overwrite ("r+") succeeds.
    if os.name == "nt":
        try:
            if path.exists():
                try:
                    os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
                except OSError:
                    pass
                with path.open("r+", encoding="utf-8") as fh:
                    fh.seek(0)
                    fh.write(content)
                    fh.truncate()
            else:
                path.write_text(content, encoding="utf-8")
            try:
                tmp.unlink()
            except OSError:
                pass
            return
        except OSError as exc:
            last_exc = exc

    try:
        tmp.unlink()
    except OSError:
        pass

    if os.name == "nt" and isinstance(last_exc, PermissionError):
        raise PermissionError(
            f"无法写入配置文件 {path}（[WinError 5] 拒绝访问）。"
            "请先完全退出对应 AI 软件（如 WorkBuddy）后再点「一键接入」，"
            "或检查该文件是否被设为只读 / 被杀毒软件拦截后重试。"
        ) from last_exc
    if last_exc is not None:
        raise last_exc


def _backup_once(path: Path, tag: str) -> None:
    if not path.exists():
        return
    try:
        BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        stamp = time.strftime("%Y%m%d")
        dst = BACKUP_DIR / f"{tag}.before-{stamp}.json"
        if not dst.exists():
            dst.write_bytes(path.read_bytes())
    except OSError:
        pass


def _read_json_obj(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {}
    text = path.read_text(encoding="utf-8-sig").strip()
    if not text:
        return {}
    obj = json.loads(text)
    if not isinstance(obj, dict):
        raise ValueError(f"{path} is not a JSON object")
    return obj


# ================= CLAUDE CODE (~/.claude/settings.json) =================

CLAUDE_EVENTS = (
    "UserPromptSubmit",
    "PreToolUse",
    "PostToolUse",
    "PermissionRequest",
    "Notification",
    "Stop",
    "SessionEnd",
)


def _claude_path() -> Path:
    return Path.home() / ".claude" / "settings.json"


def _is_claude_installed() -> bool:
    try:
        cfg = _read_json_obj(_claude_path())
        hooks = cfg.get("hooks", {})
        if not isinstance(hooks, dict):
            return False
        for groups in hooks.values():
            if not isinstance(groups, list):
                continue
            for group in groups:
                for h in group.get("hooks", []) if isinstance(group, dict) else []:
                    if isinstance(h, dict) and (
                        h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "claude")
                    ):
                        return True
    except Exception:
        pass
    return False


def _configure_claude(remove: bool = False) -> Dict[str, Any]:
    path = _claude_path()
    cfg = _read_json_obj(path)
    if not remove:
        _backup_once(path, "claude-settings")
    hooks = cfg.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise ValueError("Invalid hooks object in ~/.claude/settings.json")

    for event in CLAUDE_EVENTS:
        groups = hooks.get(event, [])
        if not isinstance(groups, list):
            groups = []
        kept = []
        for group in groups:
            if not isinstance(group, dict):
                kept.append(group)
                continue
            sub = group.get("hooks")
            if not isinstance(sub, list):
                kept.append(group)
                continue
            remaining = [
                h
                for h in sub
                if not (
                    isinstance(h, dict)
                    and (h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "claude"))
                )
            ]
            if remaining:
                kept.append(dict(group, hooks=remaining))
        if not remove:
            kept.append(
                {
                    "hooks": [
                        {
                            "type": "command",
                            "command": _format_command("claude", event),
                            "timeout": 2,
                            "statusMessage": HOOK_MARKER,
                        }
                    ]
                }
            )
        if kept:
            hooks[event] = kept
        else:
            hooks.pop(event, None)

    _atomic_write_json(path, cfg)
    return {"ok": True, "installed": not remove, "path": str(path)}


# ================= CODEX (~/.codex/hooks.json) =================

CODEX_EVENTS = (
    "UserPromptSubmit",
    "PreToolUse",
    "PostToolUse",
    "PermissionRequest",
    "Stop",
    "Interrupt",
    "SessionEnd",
)


def _codex_path() -> Path:
    root = Path(os.environ.get("CODEX_HOME", str(Path.home() / ".codex")))
    return root / "hooks.json"


_CODEX_TRUST_CACHE: Dict[str, Any] = {"sig": None, "result": None}


def _find_codex_binary() -> Optional[str]:
    which_bin = shutil.which("codex")
    if which_bin:
        return which_bin
    candidates: List[str] = []
    if os.name == "nt":
        local_app = os.environ.get("LOCALAPPDATA", "")
        prog_files = os.environ.get("ProgramFiles", "")
        for base in (local_app, prog_files):
            if not base:
                continue
            candidates.extend(
                [
                    os.path.join(base, "Programs", "Codex", "resources", "codex.exe"),
                    os.path.join(base, "Programs", "ChatGPT", "resources", "codex-cli", "codex.exe"),
                    os.path.join(base, "OpenAI", "Codex", "codex.exe"),
                ]
            )
    else:
        candidates.extend(
            [
                "/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex",
                "/Applications/Codex.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex",
                "/Applications/Codex.app/Contents/Resources/codex",
            ]
        )
    for cand in candidates:
        if os.path.isfile(cand) and os.access(cand, os.X_OK):
            return cand
    return None


def _camel_to_snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def _codex_trust_fallback_from_config(hooks_path: Path) -> Dict[str, Any]:
    """Read-only fallback when codex CLI is unavailable: check [hooks.state] keys in config.toml."""
    config_path = hooks_path.parent / "config.toml"
    try:
        cfg = _read_json_obj(hooks_path)
        hooks = cfg.get("hooks", {})
        if not isinstance(hooks, dict):
            return {"checked": False, "trusted": 0, "untrusted": 0, "total": 0}
        our_keys: List[str] = []
        for event_name, groups in hooks.items():
            if not isinstance(groups, list):
                continue
            snake = _camel_to_snake(str(event_name))
            for g_idx, group in enumerate(groups):
                sub = group.get("hooks", []) if isinstance(group, dict) else []
                if not isinstance(sub, list):
                    continue
                for h_idx, h in enumerate(sub):
                    if isinstance(h, dict) and (
                        h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "codex")
                    ):
                        our_keys.append(f"{hooks_path}:{snake}:{g_idx}:{h_idx}")
        if not our_keys:
            return {"checked": True, "trusted": 0, "untrusted": 0, "total": 0}
        toml_text = config_path.read_text(encoding="utf-8", errors="ignore") if config_path.exists() else ""
        # Only conclude missing keys are untrusted; present keys might still be modified without CLI check.
        def _has_key(k: str) -> bool:
            escaped = k.replace("\\", "\\\\")
            return f'[hooks.state."{k}"]' in toml_text or f'[hooks.state."{escaped}"]' in toml_text

        missing = sum(1 for k in our_keys if not _has_key(k))
        return {
            "checked": True,
            "trusted": len(our_keys) - missing,
            "untrusted": missing,
            "total": len(our_keys),
        }
    except Exception:
        return {"checked": False, "trusted": 0, "untrusted": 0, "total": 0}


def _get_codex_trust_status() -> Dict[str, Any]:
    """Query Codex's official read-only hooks/list RPC across macOS/Windows/Linux (cached by mtimes)."""
    hooks_path = _codex_path()
    config_path = hooks_path.parent / "config.toml"
    try:
        h_stat = hooks_path.stat() if hooks_path.exists() else None
        c_stat = config_path.stat() if config_path.exists() else None
        sig: Tuple[Any, ...] = (
            str(hooks_path),
            h_stat.st_mtime_ns if h_stat else 0,
            h_stat.st_size if h_stat else 0,
            c_stat.st_mtime_ns if c_stat else 0,
            c_stat.st_size if c_stat else 0,
        )
    except OSError:
        sig = (str(hooks_path), 0, 0, 0, 0)

    if _CODEX_TRUST_CACHE.get("sig") == sig and _CODEX_TRUST_CACHE.get("result") is not None:
        return dict(_CODEX_TRUST_CACHE["result"])

    codex_bin = _find_codex_binary()
    result: Optional[Dict[str, Any]] = None

    if codex_bin:
        proc = None
        try:
            popen_kwargs: Dict[str, Any] = {
                "stdin": subprocess.PIPE,
                "stdout": subprocess.PIPE,
                "stderr": subprocess.DEVNULL,
                "text": True,
                "encoding": "utf-8",
                "errors": "replace",
            }
            if os.name == "nt":
                popen_kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0)
            proc = subprocess.Popen([codex_bin, "app-server"], **popen_kwargs)
            assert proc.stdin is not None and proc.stdout is not None

            lines_q: "queue.Queue[Optional[str]]" = queue.Queue()

            def _reader() -> None:
                try:
                    assert proc is not None and proc.stdout is not None
                    for raw_line in proc.stdout:
                        lines_q.put(raw_line)
                except Exception:
                    pass
                finally:
                    lines_q.put(None)

            threading.Thread(target=_reader, daemon=True).start()

            proc.stdin.write(
                json.dumps(
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "initialize",
                        "params": {"clientInfo": {"name": "mi-rear-pet", "version": "2.1"}},
                    }
                )
                + "\n"
            )
            proc.stdin.write(json.dumps({"jsonrpc": "2.0", "id": 2, "method": "hooks/list", "params": {}}) + "\n")
            proc.stdin.flush()

            deadline = time.time() + 3.5
            while time.time() < deadline:
                remaining = max(0.05, deadline - time.time())
                try:
                    line = lines_q.get(timeout=min(0.3, remaining))
                except queue.Empty:
                    if proc.poll() is not None:
                        break
                    continue
                if line is None:
                    break
                try:
                    msg = json.loads(line)
                except ValueError:
                    continue
                if msg.get("id") == 2 and isinstance(msg.get("result"), dict):
                    trusted = 0
                    untrusted = 0
                    for entry in msg["result"].get("data", []):
                        for h in entry.get("hooks", []):
                            if not isinstance(h, dict):
                                continue
                            if h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "codex"):
                                if h.get("trustStatus") == "trusted":
                                    trusted += 1
                                else:
                                    untrusted += 1
                    result = {
                        "checked": True,
                        "trusted": trusted,
                        "untrusted": untrusted,
                        "total": trusted + untrusted,
                    }
                    break
        except Exception:
            result = None
        finally:
            if proc is not None:
                try:
                    proc.terminate()
                    proc.wait(timeout=0.5)
                except Exception:
                    try:
                        proc.kill()
                    except Exception:
                        pass

    if result is None:
        result = _codex_trust_fallback_from_config(hooks_path)

    _CODEX_TRUST_CACHE["sig"] = sig
    _CODEX_TRUST_CACHE["result"] = dict(result)
    return result


def _is_codex_installed() -> bool:
    try:
        cfg = _read_json_obj(_codex_path())
        hooks = cfg.get("hooks", {})
        if not isinstance(hooks, dict):
            return False
        for groups in hooks.values():
            if not isinstance(groups, list):
                continue
            for group in groups:
                for h in group.get("hooks", []) if isinstance(group, dict) else []:
                    if isinstance(h, dict) and (
                        h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "codex")
                    ):
                        return True
    except Exception:
        pass
    return False


def _configure_codex(remove: bool = False) -> Dict[str, Any]:
    path = _codex_path()
    cfg = _read_json_obj(path)
    if not remove:
        _backup_once(path, "codex-hooks")
    hooks = cfg.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise ValueError("Invalid hooks object in ~/.codex/hooks.json")

    for event in CODEX_EVENTS:
        groups = hooks.get(event, [])
        if not isinstance(groups, list):
            groups = []
        kept = []
        for group in groups:
            if not isinstance(group, dict):
                kept.append(group)
                continue
            sub = group.get("hooks")
            if not isinstance(sub, list):
                kept.append(group)
                continue
            remaining = [
                h
                for h in sub
                if not (
                    isinstance(h, dict)
                    and (h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "codex"))
                )
            ]
            if remaining:
                kept.append(dict(group, hooks=remaining))
        if not remove:
            kept.append(
                {
                    "hooks": [
                        {
                            "type": "command",
                            "command": _format_command("codex", event),
                            "timeout": 2,
                            "statusMessage": HOOK_MARKER,
                        }
                    ]
                }
            )
        if kept:
            hooks[event] = kept
        else:
            hooks.pop(event, None)

    _atomic_write_json(path, cfg)
    _CODEX_TRUST_CACHE["sig"] = None
    _CODEX_TRUST_CACHE["result"] = None
    res: Dict[str, Any] = {"ok": True, "installed": not remove, "path": str(path)}
    if not remove:
        res["next_step"] = (
            "已写入 ~/.codex/hooks.json！请先完全退出并重启 Codex，再进入「Codex 设置 → Hooks」"
            "（或在终端 Codex 输入 /hooks）信任所有「Mi Rear AI Pet status」钩子，状态才会开始同步。"
        )
    return res


# ================= ANTIGRAVITY (~/.gemini/config/hooks.json) =================

def _antigravity_path() -> Path:
    return Path.home() / ".gemini" / "config" / "hooks.json"


def _is_antigravity_installed() -> bool:
    try:
        cfg = _read_json_obj(_antigravity_path())
        return AGY_ENTRY_NAME in cfg
    except Exception:
        return False


def _configure_antigravity(remove: bool = False) -> Dict[str, Any]:
    path = _antigravity_path()
    if path.is_symlink():
        raise ValueError("Refusing to modify symlinked Antigravity hooks.json")
    cfg = _read_json_obj(path)
    if not remove:
        _backup_once(path, "antigravity-hooks")
        cfg[AGY_ENTRY_NAME] = {
            "PreInvocation": [
                {"type": "command", "command": _format_command("antigravity", "PreInvocation"), "timeout": 2}
            ],
            "PreToolUse": [
                {
                    "matcher": "^ask_question$",
                    "hooks": [
                        {"type": "command", "command": _format_command("antigravity", "AskQuestion"), "timeout": 2}
                    ],
                }
            ],
            "PostToolUse": [
                {
                    "matcher": "^ask_question$",
                    "hooks": [
                        {"type": "command", "command": _format_command("antigravity", "PostToolUse"), "timeout": 2}
                    ],
                }
            ],
            "Stop": [
                {"type": "command", "command": _format_command("antigravity", "Stop"), "timeout": 2}
            ],
        }
    else:
        cfg.pop(AGY_ENTRY_NAME, None)

    _atomic_write_json(path, cfg)
    return {"ok": True, "installed": not remove, "path": str(path)}


# ================= CURSOR (~/.cursor/hooks.json) =================

CURSOR_EVENTS = (
    "beforeSubmitPrompt",
    "preToolUse",
    "postToolUse",
    "postToolUseFailure",
    "stop",
)


def _cursor_path() -> Path:
    return Path.home() / ".cursor" / "hooks.json"


def _is_cursor_installed() -> bool:
    try:
        cfg = _read_json_obj(_cursor_path())
        hooks = cfg.get("hooks", {})
        if not isinstance(hooks, dict):
            return False
        for items in hooks.values():
            if not isinstance(items, list):
                continue
            for item in items:
                if isinstance(item, dict) and _is_our_command(item.get("command"), "cursor"):
                    return True
    except Exception:
        pass
    return False


def _configure_cursor(remove: bool = False) -> Dict[str, Any]:
    path = _cursor_path()
    cfg = _read_json_obj(path)
    if not remove:
        _backup_once(path, "cursor-hooks")
    cfg.setdefault("version", 1)
    hooks = cfg.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise ValueError("Invalid hooks object in ~/.cursor/hooks.json")

    for event in CURSOR_EVENTS:
        items = hooks.get(event, [])
        if not isinstance(items, list):
            items = []
        kept = [
            it
            for it in items
            if not (isinstance(it, dict) and _is_our_command(it.get("command"), "cursor"))
        ]
        if not remove:
            kept.append({"type": "command", "command": _format_command("cursor", event)})
        if kept:
            hooks[event] = kept
        else:
            hooks.pop(event, None)

    _atomic_write_json(path, cfg)
    return {"ok": True, "installed": not remove, "path": str(path)}


# ================= GEMINI CLI (~/.gemini/settings.json) =================

GEMINI_EVENTS = (
    "BeforeAgent",
    "BeforeTool",
    "AfterTool",
    "AfterAgent",
    "SessionEnd",
)


def _gemini_path() -> Path:
    return Path.home() / ".gemini" / "settings.json"


def _is_gemini_installed() -> bool:
    try:
        cfg = _read_json_obj(_gemini_path())
        hooks = cfg.get("hooks", {})
        if not isinstance(hooks, dict):
            return False
        for groups in hooks.values():
            if not isinstance(groups, list):
                continue
            for group in groups:
                for h in group.get("hooks", []) if isinstance(group, dict) else []:
                    if isinstance(h, dict) and (
                        h.get("name") == "mi-rear-pet" or _is_our_command(h.get("command"), "gemini")
                    ):
                        return True
    except Exception:
        pass
    return False


def _configure_gemini(remove: bool = False) -> Dict[str, Any]:
    path = _gemini_path()
    cfg = _read_json_obj(path)
    if not remove:
        _backup_once(path, "gemini-settings")
    hooks = cfg.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise ValueError("Invalid hooks object in ~/.gemini/settings.json")

    for event in GEMINI_EVENTS:
        groups = hooks.get(event, [])
        if not isinstance(groups, list):
            groups = []
        kept = []
        for group in groups:
            if not isinstance(group, dict):
                kept.append(group)
                continue
            sub = group.get("hooks")
            if not isinstance(sub, list):
                kept.append(group)
                continue
            remaining = [
                h
                for h in sub
                if not (
                    isinstance(h, dict)
                    and (h.get("name") == "mi-rear-pet" or _is_our_command(h.get("command"), "gemini"))
                )
            ]
            if remaining:
                kept.append(dict(group, hooks=remaining))
        if not remove:
            kept.append(
                {
                    "matcher": "*",
                    "hooks": [
                        {
                            "name": "mi-rear-pet",
                            "type": "command",
                            "command": _format_command("gemini", event),
                        }
                    ],
                }
            )
        if kept:
            hooks[event] = kept
        else:
            hooks.pop(event, None)

    _atomic_write_json(path, cfg)
    return {"ok": True, "installed": not remove, "path": str(path)}


# ================= WORKBUDDY / CODEBUDDY (~/.codebuddy/settings.json) =================

WORKBUDDY_EVENTS = (
    "UserPromptSubmit",
    "PreToolUse",
    "PostToolUse",
    "PostToolUseFailure",
    "PermissionRequest",
    "Stop",
    "SessionEnd",
)


def _workbuddy_path() -> Path:
    env_dir = os.environ.get("CODEBUDDY_CONFIG_DIR")
    if env_dir:
        return Path(env_dir) / "settings.json"
    wb_dir = Path.home() / ".workbuddy"
    if wb_dir.exists():
        return wb_dir / "settings.json"
    return Path.home() / ".codebuddy" / "settings.json"


def _is_workbuddy_installed() -> bool:
    for p in (_workbuddy_path(), Path.home() / ".codebuddy" / "settings.json", Path.home() / ".workbuddy" / "settings.json"):
        try:
            cfg = _read_json_obj(p)
            hooks = cfg.get("hooks", {})
            if not isinstance(hooks, dict):
                continue
            for groups in hooks.values():
                if not isinstance(groups, list):
                    continue
                for group in groups:
                    for h in group.get("hooks", []) if isinstance(group, dict) else []:
                        if isinstance(h, dict) and (
                            h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "workbuddy")
                        ):
                            return True
        except Exception:
            pass
    return False


def _configure_workbuddy(remove: bool = False) -> Dict[str, Any]:
    path = _workbuddy_path()
    cfg = _read_json_obj(path)
    if not remove:
        _backup_once(path, "workbuddy-settings")
    hooks = cfg.setdefault("hooks", {})
    if not isinstance(hooks, dict):
        raise ValueError(f"Invalid hooks object in {path}")

    for event in WORKBUDDY_EVENTS:
        groups = hooks.get(event, [])
        if not isinstance(groups, list):
            groups = []
        kept = []
        for group in groups:
            if not isinstance(group, dict):
                kept.append(group)
                continue
            sub = group.get("hooks")
            if not isinstance(sub, list):
                kept.append(group)
                continue
            remaining = [
                h
                for h in sub
                if not (
                    isinstance(h, dict)
                    and (h.get("statusMessage") == HOOK_MARKER or _is_our_command(h.get("command"), "workbuddy"))
                )
            ]
            if remaining:
                kept.append(dict(group, hooks=remaining))
        if not remove:
            kept.append(
                {
                    "hooks": [
                        {
                            "type": "command",
                            "command": _format_command("workbuddy", event),
                            "timeout": 2,
                            "statusMessage": HOOK_MARKER,
                        }
                    ]
                }
            )
        if kept:
            hooks[event] = kept
        else:
            hooks.pop(event, None)

    _atomic_write_json(path, cfg)
    return {"ok": True, "installed": not remove, "path": str(path)}


# ================= DEEPSEEK HARNESS (~/.dsh/profiles/desktop) =================

DSH_PLUGIN_PKG = "@local/mi-rear-pet-status"
DSH_PLUGIN_SRC = ROOT_DIR / "adapters" / "dsh-plugin"


def _dsh_profile_dirs() -> List[Path]:
    profiles_root = Path.home() / ".dsh" / "profiles"
    found: List[Path] = []
    for name in ("desktop", "web"):
        p = profiles_root / name
        if (p / "package.json").exists():
            found.append(p)
    return found


def _dsh_target_profile() -> Path:
    dirs = _dsh_profile_dirs()
    return dirs[0] if dirs else (Path.home() / ".dsh" / "profiles" / "desktop")


def _is_dsh_installed() -> bool:
    for pdir in _dsh_profile_dirs():
        try:
            pkg = _read_json_obj(pdir / "package.json")
            bundles = pkg.get("dsh", {}).get("profile", {}).get("bundles", [])
            deps = pkg.get("dependencies", {})
            mod_path = pdir / "node_modules" / "@local" / "mi-rear-pet-status"
            if (
                isinstance(bundles, list)
                and DSH_PLUGIN_PKG in bundles
                and isinstance(deps, dict)
                and DSH_PLUGIN_PKG in deps
                and (mod_path.exists() or mod_path.is_symlink())
            ):
                return True
        except Exception:
            pass
    return False


def _remove_path_any(target: Path) -> None:
    if target.is_symlink() or target.is_file():
        target.unlink()
    elif target.is_dir():
        shutil.rmtree(target)


def _configure_dsh(remove: bool = False) -> Dict[str, Any]:
    pdir = _dsh_target_profile()
    pkg_path = pdir / "package.json"
    if not pkg_path.exists():
        raise ValueError(f"未找到 DeepSeek Harness 配置文件：{pkg_path}（请先启动过一次 DeepSeek Harness）")

    pkg = _read_json_obj(pkg_path)
    if not remove:
        _backup_once(pkg_path, f"dsh-{pdir.name}-package")

    deps = pkg.setdefault("dependencies", {})
    if not isinstance(deps, dict):
        raise ValueError(f"Invalid dependencies in {pkg_path}")
    dsh_obj = pkg.setdefault("dsh", {})
    profile_obj = dsh_obj.setdefault("profile", {}) if isinstance(dsh_obj, dict) else {}
    bundles = profile_obj.setdefault("bundles", [])
    if not isinstance(bundles, list):
        raise ValueError(f"Invalid dsh.profile.bundles in {pkg_path}")

    mod_dir = pdir / "node_modules" / "@local"
    mod_link = mod_dir / "mi-rear-pet-status"

    if remove:
        deps.pop(DSH_PLUGIN_PKG, None)
        profile_obj["bundles"] = [b for b in bundles if b != DSH_PLUGIN_PKG]
        if mod_link.exists() or mod_link.is_symlink():
            _remove_path_any(mod_link)
    else:
        if not DSH_PLUGIN_SRC.is_dir():
            raise ValueError(f"插件目录缺失：{DSH_PLUGIN_SRC}")
        deps[DSH_PLUGIN_PKG] = f"link:{DSH_PLUGIN_SRC}"
        if DSH_PLUGIN_PKG not in bundles:
            bundles.append(DSH_PLUGIN_PKG)
        mod_dir.mkdir(parents=True, exist_ok=True)
        if mod_link.exists() or mod_link.is_symlink():
            _remove_path_any(mod_link)
        try:
            os.symlink(str(DSH_PLUGIN_SRC), str(mod_link), target_is_directory=True)
        except OSError:
            # Fallback on Windows without Developer Mode symlink permission: copy directory cleanly
            shutil.copytree(DSH_PLUGIN_SRC, mod_link)

    _atomic_write_json(pkg_path, pkg)
    return {"ok": True, "installed": not remove, "path": str(pkg_path)}


# ================= PUBLIC API =================

AGENTS = {
    "codex": {
        "name": "Codex",
        "label": "Codex",
        "detect": lambda: _codex_path().parent.exists(),
        "path": _codex_path,
        "is_installed": _is_codex_installed,
        "configure": _configure_codex,
        "auto": True,
        "note": "支持运行中、审批等待、完成与中断，自动保留已有配置",
    },
    "dsh": {
        "name": "DeepSeek Harness",
        "label": "DSH",
        "detect": lambda: (Path.home() / ".dsh").exists(),
        "path": _dsh_target_profile,
        "is_installed": _is_dsh_installed,
        "configure": _configure_dsh,
        "auto": True,
        "note": "一键安装并启用本地插件（操作后重启一次 DSH 生效），支持运行中、审批/提问等待、完成与报错",
    },
    "antigravity": {
        "name": "Antigravity",
        "label": "AGY",
        "detect": lambda: (Path.home() / ".gemini" / "antigravity").exists()
        or (Path.home() / ".gemini" / "config").exists(),
        "path": _antigravity_path,
        "is_installed": _is_antigravity_installed,
        "configure": _configure_antigravity,
        "auto": True,
        "note": "独立命名空间写入，支持运行中、提问等待与任务完成",
    },
    "claude": {
        "name": "Claude",
        "label": "Claude",
        "detect": lambda: (Path.home() / ".claude").exists(),
        "path": _claude_path,
        "is_installed": _is_claude_installed,
        "configure": _configure_claude,
        "auto": True,
        "note": "支持运行中、等待审批、完成与报错，自动保留已有配置",
    },
    "workbuddy": {
        "name": "WorkBuddy",
        "label": "WorkBuddy",
        "experimental": True,
        "detect": lambda: (Path.home() / ".workbuddy").exists()
        or (Path.home() / ".workbuddy-ai").exists()
        or (Path.home() / ".codebuddy").exists(),
        "path": _workbuddy_path,
        "is_installed": _is_workbuddy_installed,
        "configure": _configure_workbuddy,
        "auto": True,
        "note": "实验版（未实机验证）：按官方文档接入 Hooks，支持运行中、等待审批、完成与报错",
    },
    "cursor": {
        "name": "Cursor",
        "label": "Cursor",
        "experimental": True,
        "detect": lambda: (Path.home() / ".cursor").exists(),
        "path": _cursor_path,
        "is_installed": _is_cursor_installed,
        "configure": _configure_cursor,
        "auto": True,
        "note": "实验版（未实机验证）：按官方文档接入 Hooks，支持对话开始、工具执行、完成与报错",
    },
}


def get_agents_status() -> List[Dict[str, Any]]:
    out = []
    for agent_id, spec in AGENTS.items():
        try:
            detected = bool(spec["detect"]())
        except Exception:
            detected = False
        try:
            installed = bool(spec["is_installed"]())
        except Exception:
            installed = False
        needs_trust = False
        warning = ""
        if agent_id == "codex" and installed:
            trust_info = _get_codex_trust_status()
            untrusted = int(trust_info.get("untrusted", 0))
            if untrusted > 0:
                needs_trust = True
                warning = (
                    f"Codex 尚未信任 {untrusted} 条桌宠钩子（安全机制默认拦截外部新增 Hook）。"
                    "请先完全退出并重启 Codex，再进入「Codex 设置 → Hooks」（或在终端 Codex 输入 /hooks）"
                    "信任所有「Mi Rear AI Pet status」条目即可生效。"
                )
        out.append(
            {
                "id": agent_id,
                "name": spec["name"],
                "label": spec["label"],
                "experimental": bool(spec.get("experimental", False)),
                "detected": detected,
                "installed": installed,
                "needs_trust": needs_trust,
                "warning": warning,
                "auto": spec["auto"],
                "config_path": str(spec["path"]()),
                "note": spec["note"],
            }
        )
    return out


def install_agent(agent_id: str) -> Dict[str, Any]:
    spec = AGENTS.get(agent_id)
    if not spec or not spec["configure"]:
        return {"ok": False, "error": f"Agent '{agent_id}' does not support one-click hook installation"}
    try:
        if not bool(spec["detect"]()):
            return {"ok": False, "error": f"本机未检测到 {spec['name']}，请先安装并至少启动一次"}
        return spec["configure"](remove=False)
    except Exception as exc:
        return {"ok": False, "error": str(exc)}


def remove_agent(agent_id: str) -> Dict[str, Any]:
    spec = AGENTS.get(agent_id)
    if not spec or not spec["configure"]:
        return {"ok": False, "error": f"Agent '{agent_id}' does not support one-click hook removal"}
    try:
        return spec["configure"](remove=True)
    except Exception as exc:
        return {"ok": False, "error": str(exc)}


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] == "status":
        print(json.dumps(get_agents_status(), ensure_ascii=False, indent=2))
        return
    action = sys.argv[1]
    if action in ("install", "remove") and len(sys.argv) >= 3:
        target = sys.argv[2]
        res = install_agent(target) if action == "install" else remove_agent(target)
        print(json.dumps(res, ensure_ascii=False, indent=2))
        sys.exit(0 if res.get("ok") else 1)
    print("Usage: hook_manager.py [status | install <agent> | remove <agent>]")
    sys.exit(1)


if __name__ == "__main__":
    main()
