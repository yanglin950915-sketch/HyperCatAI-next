# HyperCat Face Engine 规格（v0.1）

## 视觉基准
HyperCat 不显示猫头、猫耳、身体或四肢。画面仅由一双醒目的猫咪风格大眼睛、小鼻子和简洁嘴巴组成，默认不画胡须。背景纯黑，眼睛采用 Vector Robot 式高对比发光显示效果，并以猫咪情绪曲线塑造亲和力。不复制 Vector 的具体素材，只借鉴“眼睛是角色主体、情绪通过眼形和眼神连续变化”的交互语言。

## 当前实现
- rearscreen/web/js/face-engine.js：Canvas 2D 表情渲染器，使用 ES5 语法，不依赖 Three.js 或 GLB。
- rearscreen/web/face-preview.html：独立预览页，可切换十种情绪，并通过指针移动控制视线。
- 黑底、双大眼、虹膜/瞳孔/高光、鼻部、嘴型、眉形、眨眼、轻微呼吸和情绪切换。
- API：HyperCatFace.create(canvas, options)、setMood(mood, durationMs)、setGaze(x, y)、start()、stop()、resize()、destroy()。
- 情绪：idle、happy、curious、thinking、sleepy、sad、angry、surprised、love、sleep。

## 集成原则
1. 先在独立预览页验证眼睛比例、颜色、嘴型与背屏裁切，再接入 MAML/WebView。
2. Android 主程序只发送语义 mood，不直接控制 Canvas 坐标。
3. 背屏读取已确认的 pet state 后调用 setMood；触摸可映射到 happy 或 curious。
4. 主屏与背屏共用 mood 枚举和默认动画时长。
5. 省电或减少动态效果模式下关闭呼吸和自动眨眼。
6. 这是首个可运行的渲染原型，尚未接入 Android、MAML Provider，也未完成 APK/真机测试。

## 后续验收
- 360×640、背屏实际分辨率和不同 DPI 下比例正常。
- 眨眼不闪烁；情绪切换自然。
- 背景纯黑，无滚动条和白边。
- 页面暂停时停止动画；恢复时重新 start 并 resize。
