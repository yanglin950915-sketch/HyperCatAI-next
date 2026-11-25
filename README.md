# HyperCat AI

面向小米 Pro 系列背屏的轻量萌宠应用。

## 当前版本
- Android APK：0.4.0
- 主程序：萌宠选择 + 明确确认同步
- 背屏：MAML + 内置 ContentProvider 数据桥
- 无 Root / 无 Shizuku 依赖

## 背屏模块
GitHub Actions 会生成两个构建产物：
- `HyperCatAI-debug-apk`：安装到手机的 APK
- `rearscreen`：可直接替换的背屏文件夹压缩包，内部为 `rearscreen/manifest.xml`

解压 `rearscreen` 产物后，用里面的 `rearscreen` 文件夹替换背屏 AI 壁纸目录中的旧文件夹。

## 更换萌宠
在主程序中点选萌宠，再点击“确认更换萌宠”。只有确认后才写入背屏数据。MAML 模板每秒读取一次状态，右上角刷新按钮也可手动刷新。

数据 URI：`content://uriroute/data?group=hypercat&name=pet-state`。

## 当前范围
当前版本专注于萌宠选择、同步和背屏内触摸反馈。AI Agent、本地模型和在线模型功能尚未接入，不在主界面展示未完成的占位入口。
