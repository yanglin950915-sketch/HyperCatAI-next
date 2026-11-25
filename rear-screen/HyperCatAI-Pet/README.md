# HyperCat AI 背屏模块

## 完整文件夹
仓库根目录的 `rearscreen/` 就是要替换的完整文件夹：
```
rearscreen/
└── manifest.xml
```
不需要手工制作 ZIP。也可从 GitHub 打开 `rearscreen/manifest.xml` 下载原始文件，然后放入名为 `rearscreen` 的文件夹中。

## 数据同步
主程序点击“确认更换萌宠”后写入本地状态。背屏从 HyperCat AI 自己的 Provider 读取：
`content://com.hypercat.ai.rear.provider/pet-state`
MAML 每秒轮询一次，右上角按钮触发即时刷新。

## 外观与交互
纯黑背景；机器人面部位于背屏下半区，上方预留摄像头安全区域。点击机器人显示爱心眼和开心反馈。

最终布局、Provider 跨应用读取和刷新行为仍需在目标手机上验证。
