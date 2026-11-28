# AGENTS.md — AI 编程助手自动部署与引导手册

> **给正在阅读本文件的 AI Agent（Codex / DeepSeek Harness / Claude Code / Antigravity / WorkBuddy / Cursor / OpenClaw 等）：**
> 如果用户把本仓库交给你，请严格按下方 **Step 1 → Step 5** 自动完成所有电脑端操作，再用简洁清晰的中文一步一步引导用户完成手机端操作。不要让用户手动敲命令或翻文档。

---

## 环境要求
- 操作系统：macOS / Windows / Linux
- 运行环境：Python 3.9+（**零第三方依赖**，只使用 Python 标准库）
- 网络要求：电脑与手机连接**同一个 Wi-Fi 局域网**（手机请先关闭 VPN/梯子）

---

## Step 1：获取仓库并检查 Python 环境
1. 若当前工作目录尚未包含 `server.py`，先克隆仓库到用户常用目录（例如 `~/xiaomi-rear-ai-pet`）：
   ```bash
   git clone https://github.com/nzz0991999-ai/xiaomi-rear-ai-pet.git
   cd xiaomi-rear-ai-pet
   ```
2. 检查 Python 3：
   ```bash
   python3 --version   # Windows 若无 python3，尝试 python --version 或 py -3 --version
   ```
   若用户电脑未安装 Python 3，提醒用户从 Microsoft Store / python.org 安装后再继续。

---

## Step 2：自动检测并一键接入用户本机的 AI Agent
不要手工编辑用户的 `~/.codex/hooks.json`、`~/.claude/settings.json` 等配置文件！直接调用仓库自带的安全管理器 `hook_manager.py`（写入前会自动备份到 `~/.local/state/mi-rear-pet/backups/`，且完全保留用户已有 hooks）：

1. 检测本机已安装的 AI 软件：
   ```bash
   python3 -c 'import hook_manager, json; print(json.dumps(hook_manager.get_agents_status(), ensure_ascii=False, indent=2))'
   ```
   支持的 Agent ID：`codex`、`dsh`（DeepSeek Harness）、`antigravity`、`claude`、`workbuddy`（实验版）、`cursor`（实验版）。
2. 对检测到（`detected: true`）且尚未接入（`installed: false`）的 Agent，征求用户确认（或若用户已明确授权“全自动接入”，直接执行）：
   ```bash
   python3 -c 'import hook_manager; print(hook_manager.install_agent("<agent_id>"))'
   ```
   - 如需卸载还原：`python3 -c 'import hook_manager; print(hook_manager.remove_agent("<agent_id>"))'`
3. **重要提醒**：
   - 接入完成后，明确告诉用户**「请稍后重启一次刚才接入的 AI 软件（如 Codex / DeepSeek Harness / Claude / Antigravity），新钩子才会生效」**。
   - **Codex 特别注意**：新版 Codex 有 Hook 信任校验机制，通过外部脚本写入 `~/.codex/hooks.json` 后默认处于 `untrusted / modified` 状态会被静默跳过。若 `get_agents_status()` 中 `codex.needs_trust` 为 `true`，请明确引导用户**先完全退出并重启 Codex**，再进入 **Codex 设置 → Hooks**（或在终端 Codex 输入 `/hooks`），逐条信任所有 `Mi Rear AI Pet status` 条目后即可生效。

---

## Step 3：启动本地状态服务并打开控制台网页
1. 检查 `8765` 端口是否已在运行：
   ```bash
   curl -s http://127.0.0.1:8765/api/info
   ```
2. 若未运行，在后台启动 `server.py`（不要阻塞当前对话）：
   - **macOS / Linux**：
     ```bash
     nohup python3 server.py --port 8765 > /tmp/mi-rear-pet.log 2>&1 &
     ```
     并打开浏览器控制台：`open "http://127.0.0.1:8765/?preview=1"`
   - **Windows (PowerShell)**：
     ```powershell
     Start-Process python -ArgumentList "server.py --port 8765" -WindowStyle Minimized
     Start-Process "http://127.0.0.1:8765/?preview=1"
     ```
     同时提醒 Windows 用户：若手机扫码连不上，右键以管理员身份运行一次目录里的 `Windows放行防火墙8765端口.bat`。
3. 确认服务启动成功并读取本机局域网地址：
   ```bash
   curl -s http://127.0.0.1:8765/api/info
   ```
   向用户报告电脑已就绪，并告知其局域网地址（`lan_url`）以及网页控制台已自动打开。

---

## Step 4：分步引导用户完成手机端操作（一次只说 1–2 步，别刷屏）

### A. 若用户使用「小米妙享背屏」（小米 17 / 17 Pro / 17 Pro Max / 18 Pro / 18 Pro Max 等）
1. **安装手机端 App**：
   - 让用户确保手机和电脑连**同一 Wi-Fi**（手机关梯子/VPN），用**小米自带浏览器或 Via 浏览器**（不要用微信）扫描电脑控制台卡片 2 的「背屏桌宠 App」二维码，安装 **背屏 AI 桌宠 (`MiRearPet.apk`)**。
   - 若用户手机用 Edge/Chrome 报 `ERR_TOO_MANY_RETRIES`，请立刻让他换小米自带浏览器或 Via。
2. **安装并激活 Shizuku（免 Root，安装包已内置在仓库里！）**：
   - 打开「背屏 AI 桌宠」确认自动搜到电脑变绿（`✓ 已连接`）；
   - 如果手机尚未装 Shizuku，直接点 App 里的 **「下载安装 Shizuku」**（会自动从用户电脑局域网秒传仓库内置的官方 `Shizuku.apk`，免翻墙）；
   - 引导用户在手机开启开发者选项与 3 个开关：「设置 → 我的设备 → 全部参数」连点 **OS 版本** → 进「更多设置 → 开发者选项」打开 **USB 调试**、**无线调试**、**USB 调试（安全设置）**；
   - 打开 Shizuku 点 **「配对」** → 去无线调试获取 6 位配对码填入通知栏（若通知栏看不到输入框，去「设置 → 通知与控制中心 → 通知显示设置」临时切一次「原生样式」）→ 返回 Shizuku 点 **「启动」**，确认顶部显示 **「Shizuku 正在运行」**。
3. **授权并一键投到妙享背屏**：
   - 回到「背屏 AI 桌宠」→ 点 **「一键授权 Shizuku」**（选始终允许）→ 点 **「一键投到妙享背屏」** → 把手机翻到背面即可看到大眼睛 AI 桌宠！
   - 若希望正面锁屏时背屏也能持续亮着，打开 App 里的 **「息屏 / 切走后自动拉回背屏（守护模式）」** 开关，并将 Shizuku 与本 App 省电策略设为「无限制」。

### B. 若用户没有小米背屏（普通安卓手机 / 平板 / iPhone / iPad）
- **普通安卓手机 / 平板**：无需安装 Shizuku，装好「背屏 AI 桌宠」后直接点底部的 **「当前屏幕全屏运行 AI 桌宠」** 即可纯黑常亮运行。
- **iPhone / iPad**：在电脑控制台卡片 2 切到「纯黑网页版」标签，用 Safari 扫码打开横屏使用。

---

## Step 5：帮用户做一次实时联动实测
当用户说手机已经看到大眼睛后，帮用户触发一次实测推送验证端到端同步：
```bash
# 推送「运行中」（电光青）
curl -s -X POST http://127.0.0.1:8765/api/test -H "Content-Type: application/json" -d '{"provider":"codex","state":"running"}'
# 3 秒后推送「任务完成」（翡翠绿），再恢复空闲
curl -s -X POST http://127.0.0.1:8765/api/test -H "Content-Type: application/json" -d '{"provider":"codex","state":"completed_unread"}'
curl -s -X POST http://127.0.0.1:8765/api/test -H "Content-Type: application/json" -d '{"provider":"codex","state":"idle"}'
```
最后告知用户两个小技巧：
- 背屏上 **单指长按 2 秒** 可调出「背屏布局校准」（调节左侧双摄挖孔避让、眼睛大小、亮度、一键重置默认）；
- 后续如果本仓库发布了新版 APK，手机 App 打开后会自动提示更新（或在右上角点 **「检查更新」** 一键升级）。

---

## 进阶：通用 Webhook 接口（接入任意自定义 Agent / 脚本）
`POST http://127.0.0.1:8765/api/event`
```json
{"provider": "custom", "state": "running", "session": "my-task"}
```
`state` 支持：`idle` | `running` | `waiting_user` | `completed_unread` | `failed`。
