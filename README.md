# HyperCat AI

面向小米 Pro 系列背屏的轻量 AI 萌宠应用。

## 当前项目结构

- Android 主程序：萌宠选择、确认后同步、状态存储。
- `rearscreen/`：小米背屏 MAML 模板。
- `rearscreen/web/js/pet-rig-adapter.js`：通用 GLB 骨骼发现与能力检测器。
- `docs/PET_ENGINE_SPEC.md`：多萌宠引擎分层、模型目录、pet.json 与 Agent 指令规范。

## 多萌宠设计

项目不限定角色形象或骨骼命名。每只萌宠可提供独立的 `pet.json` 骨骼别名与能力声明；通用适配器探测 Head、Neck、Spine、Arms、Ears、Tail、Hair、Cloth 等骨骼组，并输出能力清单。缺失能力安全关闭，不阻断其他动作。

东海帝皇与案例曼波作为首批适配样本。旧版已验证的 Three.js 物理、表情、材质和触摸算法应保留并逐步迁移，不用新的空壳引擎覆盖。

## 背屏同步

主程序确认萌宠后写入 SharedPreferences；背屏通过本应用导出的 ContentProvider 读取：
`content://com.hypercat.ai.rear.provider/pet-state`。

## 开发状态说明

当前仓库尚未包含完整旧版 `app_personal.js` / WebGL 运行时，也没有可直接加载的角色 GLB 文件。因此本次先提交可独立使用的骨骼适配器和规范；Three.js 运行时集成与真机验证仍待旧版源码及模型资产到位。
