# 小米妙享背屏 · AI 桌宠状态屏（免 Root 独立版 v2.1）

免 Root、免第三方投屏工具（无需 MRSS / Webview Kiosk），把 **小米妙享背屏**（适配小米 17 / 17 Pro / 17 Pro Max / 18 Pro / 18 Pro Max 等所有妙享背屏机型，也支持任意闲置安卓手机 / 平板 / iPad 全屏副屏）改造成一块实时同步电脑 AI 编程助手运行状态的「拟人赛博大眼睛桌宠」。

- **支持主流 AI 编程助手**：**Codex**、**DeepSeek Harness (DSH)**、**Antigravity**、**Claude Code**，以及实验版 **WorkBuddy**、**Cursor** 和通用 Webhook。
- **零依赖跨平台**：电脑端仅需 Python 3 标准库（macOS / Windows 通用）；手机端内置 `60 KB` 专属客户端 + 官方 `Shizuku v13.6.0` 安装包（同 Wi-Fi 秒传免翻墙）。
- **支持云端自动更新**：后续发新版 Release 后，手机 App 打开会自动提示「✨ 发现新版本 · 点击立即更新」。

---

## 最爽玩法：把下面这句话直接发给你的 AI，让它全自动帮你装好！

无论你用的是 **Codex、DeepSeek Harness、Claude Code、Antigravity、WorkBuddy 还是 Cursor**，直接把下面这句话发给你的 AI（仓库内置了专门给 AI 读的 [`AGENTS.md`](./AGENTS.md) 自动化操作手册）：

```text
帮我阅读 https://github.com/nzz0991999-ai/xiaomi-rear-ai-pet 里的 AGENTS.md，
自动帮我在电脑上部署小米妙享背屏 AI 桌宠并一键接入我当前使用的 AI 软件，
启动本地服务后，再一步一步教我在手机上完成连接和投屏！
```

---

## 3 分钟快速上手一图流

![3分钟快速上手一图流](./3%E5%88%86%E9%92%9F%E5%BF%AB%E9%80%9F%E4%B8%8A%E6%89%8B%E4%B8%80%E5%9B%BE%E6%B5%81.png)

---

## 手动上手 4 步（不想用 AI 自动装也只要 3 分钟）

> **下载方式**：直接前往本仓库右侧 **[Releases](https://github.com/nzz0991999-ai/xiaomi-rear-ai-pet/releases)** 下载打包好的 ZIP 解压，或 `git clone` 本仓库。

### 1. 启动电脑端（macOS / Windows 通用）
确保电脑装有 Python 3（macOS 自带，Windows 可在微软商店免费安装 Python 3）：
- **macOS**：双击 `启动背屏桌宠.command`
- **Windows**：双击 `启动背屏桌宠_Windows.bat`
  - *(Windows 首次运行若手机扫不到电脑，右键以管理员身份运行一次 `Windows放行防火墙8765端口.bat`)*

启动后浏览器会自动弹出本地控制台，照着页面操作即可：

### 2. 一键接入电脑上的 AI 软件
在控制台 **卡片 1** 找到你正在使用的 AI 工具点 **「一键接入」**（原配置会自动备份，随时可一键卸载还原），然后**完全退出并重启一次该 AI 软件**生效。
- **Codex 用户额外一步**：新版 Codex 默认会拦截未信任的外部 Hook。完全重启 Codex 后，进入 **Codex 设置 → Hooks**（或在终端 Codex 输入 `/hooks`），把所有 **`Mi Rear AI Pet status`** 钩子点一遍信任即可开始实时同步（控制台卡片 1 也会自动检测并显示「待信任」提醒）。

### 3. 手机扫码安装 App
确保手机和电脑连在**同一个 Wi-Fi**（手机先关闭梯子/VPN）：
- 用**小米自带浏览器**或 **Via 浏览器**（不要用微信扫码）扫控制台 **卡片 2** 的二维码，安装 **背屏 AI 桌宠 (`MiRearPet.apk`)**；
- 打开 App 会自动搜到你的电脑并连上（显示绿色 `✓ 已连接`）。

### 4. 一键投到小米妙享背屏（或任意手机全屏）
- **小米妙享背屏用户**：
  1. 仓库已内置官方 **Shizuku v13.6.0** 安装包：首次使用在手机 App 点 **「下载安装 Shizuku」** 直接从电脑局域网秒传安装（也可切到网页卡片 2 的「Shizuku 直装」二维码扫码装）；
  2. 展开 App 内自带的 3 步指引开启无线调试并启动 Shizuku（顶部显示「Shizuku 正在运行」）；
  3. 回 App 点 **「一键授权 Shizuku」** → 点 **「一键投到妙享背屏」**，翻到背面即可看到大眼睛！
  4. 如希望正面锁屏时背屏也常亮不退出，打开 **「守护模式」** 开关（并把 Shizuku 与本 App 省电策略设为无限制）。
- **普通安卓手机 / 平板用户**：不用装 Shizuku，直接点 App 底部的 **「当前屏幕全屏运行 AI 桌宠」** 即可纯黑无边框常亮运行。
- **iPhone / iPad 用户**：在电脑控制台卡片 2 切到「纯黑网页版」标签，用 Safari 扫码打开即可。

---

## 背屏校准、互动与排查
- **单指长按背屏 2 秒**：呼出「背屏布局校准」菜单，可微调左侧双摄挖孔避让、眼睛大小、OLED 亮度、空闲时钟，并支持一键 **「重置默认」**。
- **单击互动**：点左/右眼 Wink，点眉心雷达审视，左右轻滑触发超频/无语彩蛋。
- **扫码打不开 / 报 `ERR_TOO_MANY_RETRIES`**：改用小米自带浏览器或 Via，或在浏览器设置里关掉「安全 DNS / 始终使用安全连接」；macOS 请在「系统设置 → 隐私与安全性 → 本地网络」允许「终端」。
- **遇到问题反馈**：展开手机 App 最底部的 **「诊断日志」** 点 **「复制日志」**，在 GitHub Issues 贴出即可快速排查。

## 开源许可
本项目基于 [MIT License](./LICENSE) 开源；内置的 [Shizuku](https://github.com/RikkaApps/Shizuku) 官方安装包遵循 Apache-2.0 协议。
