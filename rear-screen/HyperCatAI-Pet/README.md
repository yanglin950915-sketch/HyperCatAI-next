# HyperCat AI 背屏 MAML 模块（UriRoute 兼容协议）

本模块从 HyperCat AI APK 内置的 ContentProvider 读取萌宠数据。不需要另装 UriRoute、REAREye、Janus，也不需要 Root。

## 数据接口

```
content://uriroute/data?group=hypercat&name=pet-state
```

Provider 按 UriRoute `/data` 读取形态返回单列 `result`，内容为 JSON：`pet_name`、`pet_emoji`、`mood`、`reply`、`updated_at`、`app_version`。

这是固定协议兼容层，不是把 UriRoute 的 Rhino JavaScript 引擎、脚本安装器或全部功能移植进 APK。只实现 HyperCat 自己的 `hypercat/pet-state` 路由。

MAML 模板每秒轮询一次 Provider。萌宠变化通常会在下一次轮询显示；这是轮询，不是推送。背屏休眠或系统暂停刷新时，更新会延迟到恢复后。

## 安装

1. 安装并至少打开一次 HyperCat AI。
2. 在应用内选择萌宠并产生状态/回复。
3. 用文件管理器备份目标 AI 壁纸原始资源，再将本目录中的 `manifest.xml` 放入该壁纸的 `rearscreen` 目录（以实际壁纸目录结构为准）。
4. 回到妙享背屏，选择该 AI 壁纸。

Android/HyperOS 可能限制文件管理器访问 `Android/data`。如果目录不可写，不要删除原始资源或覆盖系统分区。

## 兼容性限制

- Provider authority 使用 `uriroute`，因此不要同时安装独立 UriRoute，否则可能发生 authority 冲突。
- Provider exported=true，供系统 MAML 宿主跨进程读取；HyperOS 是否允许目标宿主访问仍需真机验证。
- MAML XML 是否被当前固件接受、是否正确渲染，也必须真机验证。构建成功不等于背屏已工作。