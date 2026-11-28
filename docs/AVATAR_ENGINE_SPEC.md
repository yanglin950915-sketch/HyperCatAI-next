# HyperCat AI 虚拟形象运行时与 Agent 交互规范

## 当前实现
- 主屏、设置预览和小米背屏统一使用原作者 Mi Rear AI Pet 的原始动画运行时。
- Android 通过本地 WebView 加载 APK 内的 HTML，不依赖外部 LAN 服务即可呈现桌宠动画。
- Agent 是唯一业务状态来源；Android 将思考、聆听、回复、朗读和错误状态映射为桌宠状态。
- 桌宠轻触、长按和滑动事件通过 Android Bridge 回传当前 Agent 会话。
- 背屏 MAML 包从 APK assets 中读取同一份桌宠页面，沿用 HyperCat AI 的 Shizuku 卡片部署和恢复流程。
- HyperCat AI 原有模型配置、对话历史、语音识别、TTS 和 Agent 工具链保持独立，不由桌宠项目替代。

## 上游来源与许可
桌宠视觉、动画、画布、触摸反馈和设置交互直接采用 `third_party/mi-rear-ai-pet/` 中的原作者实现。上游项目许可证为 MIT；保留原始 LICENSE 和版权归属，不将其改称为 HyperCat AI 原创作品。

## 交互协议
- 主屏桌宠使用 `HyperCatBridge.onPetInteraction`。
- 背屏 MAML 使用 `maml.doAction`，Android 侧由 `RearAgentInteractionDispatcher` 进入 Agent。
- 状态通过 `window.__setAgentState` / `window.__setAgentJson` 同步。
- 触摸动作包括轻触、长按、滑动；语音输入仍由 HyperCat AI 原有语音识别和 TTS 管理。

## 验收
- 主屏与背屏均显示原作者桌宠，不再加载旧模型引擎、渲染器或模型资源。
- 桌宠可离线显示；无需启动上游 LAN companion server。
- Agent 状态变化同步至桌宠；桌宠触摸动作进入当前 Agent 会话。
- 小米目标机型的 MAML 部署、触摸和功耗仍需实机验证。
