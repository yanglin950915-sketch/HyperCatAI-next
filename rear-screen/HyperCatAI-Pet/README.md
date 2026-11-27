# HyperCat AI 背屏模块（REAREye）

## 组件文件
`rearscreen/` 是可导入的 REAREye 组件目录：
```
rearscreen/
├── manifest.xml
└── web/
    └── index.html
```
Release 会额外提供 `HyperCatAI-rearscreen-v0.9.21.zip`，ZIP 根目录直接包含 `manifest.xml` 和 `web/index.html`。

## 安装
1. 在 REAREye「背屏中心 → 背屏个性化 → 组件模板管理器」导入 ZIP。
2. 到「卡片管理器」添加该组件对应的卡片并启用。
3. 安装 HyperCat AI v0.9.21；首次使用需确认 REAREye 能读取 `content://com.hypercat.ai.rear.provider/pet-state`。

## Agent 同步
- HyperCat AI 的 ContentProvider 暴露 `pet_name`、`mood`、`reply` 和更新时间。
- MAML 每秒刷新数据；WebView 通过 `window.maml.getStringByName()` 读取已声明的 MAML 字符串变量。
- Agent 思考时显示思考状态，完成回答后同步最终答复和情绪。
- 背屏触摸通过 WebView 内部 `Triggers` 发送显式包名广播，主应用统一写回萌宠状态。

## 外观与适配
- 976×596 横向设计基准，左侧约三分之一留作摄像头避让区；背景铺满，文字和触摸目标仅放在可用区域。
- Canvas 使用 CSS 视口与 DPR 分离的 backing store，自动适配实际背屏 WebView 视口。
- 单文件离线 HTML；无在线脚本、图片、字体或 CDN。
- 支持 init/resume/pause/enterAod/exitAod 生命周期，AOD 或暂停时停止动画循环。

## 验证边界
代码接入完成不等于目标手机实测通过。请重点验证：卡片是否成功加载、左侧摄像头是否完全避让、Provider 是否可读、摸摸/思考/休息是否回传、息屏唤醒是否恢复，以及主题缓存更新后是否需要重新导入组件。
