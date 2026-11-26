# HyperCat AI

面向小米 Pro 系列背屏的轻量 AI 萌宠应用。

## 当前项目结构

- Android 主程序：萌宠选择、确认后同步、状态存储。
- `rearscreen/`：小米背屏 MAML 模板及 Web 表情运行时。
- `rearscreen/web/js/face-engine.js`：Vector Robot 风格灵感的极简猫咪表情 Canvas 引擎。
- `rearscreen/web/face-preview.html`：十种情绪的交互式独立预览。
- `rearscreen/web/js/pet-rig-adapter.js`：通用 GLB 骨骼发现与能力检测器（保留作可选扩展）。
- `rearscreen/web/js/pet-behavior.js`：统一情绪/动作语义桥。
- `docs/FACE_ENGINE_SPEC.md`：新猫咪表情引擎设计与集成验收规范。
- `docs/PET_ENGINE_SPEC.md`：多萌宠 GLB 引擎规范。

## 核心视觉方向

不显示猫头轮廓、猫耳、身体或四肢。只显示一双醒目的猫咪风格大眼睛、小鼻子和简洁嘴巴，默认不画胡须；背景纯黑。眼睛采用 Vector Robot 式高对比发光显示语言，并通过眼形、瞳孔、高光、眨眼和视线变化表达情绪。

首版表情引擎使用 Canvas 2D，不依赖 Three.js 或 GLB，便于在小米背屏 WebView 中运行并控制资源开销。后续再按实际设备效果调整眼睛比例、颜色、动画速度与五官是否显示。

## 当前可预览情绪

idle、happy、curious、thinking、sleepy、sad、angry、surprised、love、sleep。

## 背屏同步

主程序确认萌宠后写入 SharedPreferences；背屏通过本应用导出的 ContentProvider 读取：
`content://com.hypercat.ai.rear.provider/pet-state`。

## 开发状态

当前分支已加入独立表情渲染器和预览页，但还没有把预览页挂入现有 MAML manifest / Android 页面，也没有完成 Android 构建或真机验证。后续需先在目标 WebView 验证渲染与生命周期，再接入主程序的情绪状态和背屏 Provider。旧版 GLB/Three.js 适配器暂时保留，不作为新表情引擎的依赖。


## Android 表情预览接入（开发中）
- `app/src/main/assets/face-engine.js` 与 `face-preview.html`：将共享表情引擎打包进 APK assets。
- `PetFacePreview.kt`：Compose WebView 容器，通过受限 JavaScript bridge 回传情绪。
- `MainActivity.kt`：主屏预览点击情绪后写入 `PetStateStore`，Provider 通知与 MAML 轮询机制继续沿用。
- `rearscreen/manifest.xml`：旧机器人几何图形已替换为无头部轮廓的极简猫咪大眼表情，触摸时显示开心反馈。
- 当前 MAML 图形是静态降级表现；HTML Canvas 动画已接入 Android 主程序源码，但还没有在 Android 构建和设备上验证。MAML 背屏的连续动画接入仍需确认当前小米主题宿主可用的 WebView/JS 桥接语法。
