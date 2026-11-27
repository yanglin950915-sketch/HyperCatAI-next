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

## REAREye v2.0.0 创建流程

APK 内置工具描述明确了流程：
1. `get_html_widget_spec` 获取 HTML widget 规范。
2. `create_html_widget` 用完整 HTML 和标题创建组件；默认尺寸 904×572。
3. 使用返回的 `business` 调用 `create_card`，创建实际显示卡片。
4. 组件创建/激活需要在 REAREye 内按提示确认。

Release 提供两个文件：
- `HyperCatAI-reareye-widget.html`：可交给 REAREye 的 `create_html_widget` 使用的单文件 HTML。
- `HyperCatAI-rearscreen.zip`：包含 MAML WebView 包装与 HyperCat Provider/触摸绑定的参考包；不要假定 REAREye 的 HTML-only 创建流程会自动导入 ZIP 中的 manifest。

`register_app_card` 创建的是应用启动入口，不是显示卡片。只有需要把 HyperCat AI 加入背屏应用启动列表时才使用它。

## Agent 同步
- HyperCat AI 的 ContentProvider 暴露 `pet_name`、`mood`、`reply` 和更新时间。
- MAML 每秒刷新数据；WebView 通过 `window.maml.getStringByName()` 读取已声明的 MAML 字符串变量。
- Agent 思考时显示思考状态，完成回答后同步最终答复和情绪。
- 背屏触摸通过 WebView 内部 `Triggers` 发送显式包名广播，主应用统一写回萌宠状态。

## 外观与适配
- 904×572 横向设计基准，左侧约三分之一留作摄像头避让区；背景铺满，文字和触摸目标仅放在可用区域。
- Canvas 使用 CSS 视口与 DPR 分离的 backing store，自动适配实际背屏 WebView 视口。
- 单文件离线 HTML；无在线脚本、图片、字体或 CDN。
- 支持 init/resume/pause/enterAod/exitAod 生命周期，AOD 或暂停时停止动画循环。

## 验证边界
代码接入完成不等于目标手机实测通过。请重点验证：卡片是否成功加载、左侧摄像头是否完全避让、Provider 是否可读、摸摸/思考/休息是否回传、息屏唤醒是否恢复，以及主题缓存更新后是否需要重新导入组件。
