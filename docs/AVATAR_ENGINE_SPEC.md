# HyperCat AI 虚拟形象运行时与 Agent 交互规范

## 目标

虚拟形象是 HyperCat AI Agent 的可视化人格，不是独立的形象应用。主屏、设置预览与小米背屏共用角色身份、情绪和交互事件；Agent 是状态与对话的唯一业务来源。

## 当前实现

- Android 主屏使用 Jetpack Compose 承载虚拟形象界面。
- Live2D 由 `Live2DView` 在 WebView 中加载 APK 内置的 PixiJS、Cubism 4 渲染脚本和用户模型。
- 默认 Live2D 模型资源随 APK 内置；主屏和背屏读取同一份内置模型状态。
- 默认直接显示完整 Live2D 二次元角色，不再以简笔画或轻量 Canvas 形象作为默认回退。
- 背屏 MAML 模板位于仓库 `rearscreen/`，由 Gradle 作为 Android assets 打入 APK。

## 内置模型资源约定

- 默认角色采用 Live2D 官方原创角色 Haru 样例。
- 构建任务从固定 commit 的 `Live2D/CubismWebSamples` 获取 `Samples/Resources/Haru`，包括模型入口、MOC3、高清纹理、物理、表情、动作和音频资源。
- 构建产物将模型写入 APK assets 的 `avatar/default/`；首次安装无需网络下载或导入模型 ZIP。
- `Live2DModelStore` 始终返回 APK 内置模型；主屏 WebGL Host 和背屏 MAML 部署共用该入口。
- 背屏部署器从 APK assets 读取模型目录并打入本机生成的 MAML 部署包。
- 模型素材使用必须遵守 Live2D Free Material License Agreement 与 Cubism Sample Data Terms of Use，并保留版权声明。素材来源和许可说明见 `THIRD_PARTY_NOTICES.md` 与 `LICENSES/live2d-haru-sample-NOTICE.txt`。


## Agent 与形象的交互协议

Agent 不直接操作 WebGL、模型骨骼或贴图。Android 层把业务状态转换为统一语义：

- 情绪：`neutral`、`happy`、`sad`、`angry`、`shy`、`thinking`。
- 表现状态：待机、思考、回复、朗读、聆听、错误。
- 触摸事件：轻触、长按、拖动；事件先回传 Android，再进入当前 Agent 会话。
- 口型：优先使用可用的 TTS 播放状态/波形驱动；无法获取波形时使用轻量说话动画。
- 视线：触摸位置与宿主提供的 gaze 坐标只影响渲染层，不直接改写 Agent 会话。

当前背屏旧卡片仍使用历史 `pet_*` Intent/Provider 字段作为兼容协议。后续统一命名时必须保留旧字段读取或迁移映射，不能直接删除，否则用户已部署的卡片会失去触摸和状态同步。

## 背屏资源打包

- `app/build.gradle.kts` 将 `../rearscreen` 加入 APK assets；用户无需另行下载或手动导入背屏资源 ZIP。
- 用户开启背屏投放并选择目标卡片后，`RearCardPackageBuilder` 从 APK assets 组装 MAML 部署文件，并将 APK 内置的 Live2D Haru 模型一并打包。
- `RearProjectionController` 通过 Shizuku 将生成的卡片部署到用户明确选择的主题卡片目录。
- 运行时生成的 MAML ZIP 是系统主题卡片格式，不是应用安装依赖；构建流水线只发布 APK。

## 互动闭环

1. 用户在主屏或背屏轻触、长按或拖动形象。
2. 渲染层发出语义事件，不直接调用模型 API。
3. Android 事件分发层校验来源、去重并将事件写入当前 Agent 会话。
4. Agent 回复后更新统一状态仓库。
5. 主屏 Live2D 与背屏 MAML 同步更新情绪、回复文本和说话状态。

## 验收标准

- 无模型时首次安装即可显示内置虚拟形象，不出现空白黑屏或要求导入背屏 ZIP。
- 主屏预览和背屏默认使用同一内置 Live2D 角色。
- 轻触、长按、拖动均能回传 Agent，并显示对应反馈。
- Agent 思考、回复、朗读和错误状态能同步到形象。
- 资源部署失败时明确报告失败，不将“ZIP 已生成”误报为“背屏显示成功”。
- 目标小米机型需实机验证 MAML、触摸事件、权限和功耗。
