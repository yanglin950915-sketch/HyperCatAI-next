# HyperCat AI

面向小米 Pro 系列背屏的 AI 萌宠 Agent Android 应用。

## 当前版本
- Android APK：0.3.0
- 背屏：MAML + 内置 UriRoute 兼容数据 Provider
- 无 Root：不依赖 REAREye、Janus 或 LSPosed
- Shizuku：仅作为可选实验能力

## 背屏数据桥
HyperCat AI 内置只读 ContentProvider，兼容 UriRoute 的固定 `/data` 查询形态：

```
content://uriroute/data?group=hypercat&name=pet-state
```

返回萌宠名称、Emoji、心情、Agent 回复和更新时间。MAML 模板每秒轮询一次，读取应用内同一份萌宠状态。

这是协议兼容层，不是将 UriRoute 整个应用或 Rhino JavaScript 引擎嵌入 APK。它只提供 HyperCat AI 固定的萌宠数据。MAML 是否能被指定 HyperOS 版本加载、系统宿主能否访问 Provider，仍需真机验证。

模块文件位于 `rear-screen/HyperCatAI-Pet/`，安装说明见该目录 README。