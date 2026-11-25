# HyperCat AI 背屏模块

## 直接替换方式
本模块由 CI 打包为 `rearscreen.zip`，压缩包内固定结构为：

```
rearscreen/
└── manifest.xml
```

解压后，用整个新版 `rearscreen` 文件夹替换背屏 AI 壁纸目录中的旧 `rearscreen` 文件夹。替换前请备份旧文件。

## 数据同步
主程序选择萌宠后，需要点击“确认更换萌宠”。确认操作会将名称、Emoji、心情和回复写入本地状态，并通知 ContentProvider。模板每秒刷新一次作为兜底。

数据 URI：`content://uriroute/data?group=hypercat&name=pet-state`。

## 背屏触摸
点击萌宠区域会切换为开心表情并显示摸摸反馈；右上角刷新按钮会重新读取主程序状态。这些是 MAML 模板内的本地交互，不会直接执行 Agent 或手机系统操作。

## 兼容性说明
模板由 HyperOS 背屏 MAML 宿主加载，Provider 由 HyperCat AI APK 提供。不同 HyperOS 版本对 MAML 控件事件、ContentProvider 跨进程访问和刷新时机的支持可能不同，最终以真机测试为准。
