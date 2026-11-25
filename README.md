# HyperCat AI

面向小米 Pro 系列背屏的轻量机器人萌宠应用。

## 当前版本
- Android APK：下一构建版本（机器人萌宠同步修复）
- 主程序：选择萌宠后点击确认同步
- 背屏：黑色背景、机器人表情、摄像头避让布局
- 无 Root / 无 Shizuku 依赖

## rearscreen 文件夹
仓库中的 `rearscreen/` 就是完整可替换文件夹，当前包含：
```
rearscreen/
└── manifest.xml
```
不再手工制作 rearscreen.zip。可直接打开 [rearscreen/manifest.xml](rearscreen/manifest.xml) 下载原始文件，并放入名为 `rearscreen` 的文件夹中；用整个文件夹替换旧目录。GitHub Actions 的目录构建产物由 GitHub 平台自动封装下载，不是项目额外生成的 ZIP。

## 数据同步
主程序确认后写入 SharedPreferences；背屏通过本应用独立导出的 ContentProvider 读取：
`content://com.hypercat.ai.rear.provider/pet-state`
不再使用通用 `uriroute` authority，避免与系统/第三方 Provider 冲突。MAML 每秒轮询，并支持右上角手动刷新。

## 当前范围
当前版本专注于萌宠选择、状态同步和背屏触摸反馈。AI Agent、本地模型和在线模型尚未接入。
