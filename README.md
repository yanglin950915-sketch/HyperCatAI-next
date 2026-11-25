# HyperCat AI

面向小米 17 Pro / 17 Pro Max 背屏的 AI 萌宠 Agent。

## 0.2.0：MAML 背屏组件路线

本版本保留 Android 主应用，并新增独立的 MAML 背屏模板：

- 主应用通过 `PetStateStore` 保存当前萌宠、表情状态和回复。
- 导出的 `RearPetProvider` 以 `content://com.hypercat.ai.rear/pet` 提供只读 JSON 数据。
- `rear-screen/HyperCatAI-Pet/manifest.xml` 是独立背屏模板，使用 MAML `ContentProviderBinder` 读取数据并绘制界面。
- Shizuku Activity 定向启动保留为备用实验，不再作为主背屏架构。

## 安装与使用

1. 安装 HyperCat AI APK，打开一次并选择萌宠。
2. 在 GitHub Actions 的本次构建产物中下载 `HyperCatAI-RearScreen-MAML.zip`。
3. 按设备上可用的背屏组件管理器（例如 REAREye / Janus）导入模板目录；若当前固件没有组件管理器，则需验证主题管理器 MAML 资源导入路径。
4. 在小米背屏中心启用 HyperCat AI Pet 卡片/模块。
5. 回到主应用切换萌宠或表情，模板通过 Provider 读取最新状态。

**注意：** 普通第三方 APK 不能自行注册成小米原生背屏卡片。MAML 模板导入仍依赖设备固件支持的管理器/资源加载方式；本仓库不承诺所有 HyperOS 版本都能直接导入。模板需在真机验证。

## 技术基线

- Kotlin + Jetpack Compose，minSdk 26 / compileSdk 35
- Shizuku 为可选能力，不要求 Root 或解锁 Bootloader
- 本地模型和在线模型接口仍在开发中；当前版本未宣称已完成模型推理接入
- MAML 模板采用 904×572 背屏布局基准，避开左侧摄像头区域
