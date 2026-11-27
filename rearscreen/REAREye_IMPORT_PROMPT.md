# REAREye v2.0.0 导入提示

请按 REAREye 自身支持的工作流操作，不要把 ZIP 当作 APK 安装。

1. 先调用 `get_widget_spec`，确认当前版本完整的单文件 HTML 规范。
2. 使用随附的 `HyperCatAI-reareye-widget.html` 完整内容，按 904×572 横向尺寸和标题「HyperCat AI」调用 `create_html_widget`。
3. 创建成功后，使用返回的 `business` 调用 `create_card`，创建背屏显示卡片；在要求确认时先向我确认。
4. 请检查随附 MAML 参考包中的 `manifest.xml`：它包含 ContentProviderBinder（`content://com.hypercat.ai.rear.provider/pet-state`）以及三个触摸动作的 WebView Trigger。若当前 HTML-only 组件工作流不能保留或配置这些绑定，请明确说明，不要声称 Agent 状态同步和触摸回传已经完成。
5. 不要调用 `register_app_card` 来代替 `create_card`；它创建的是应用启动入口，不是显示卡片。
