# HyperCat AI 多虚拟形象引擎规范（v1）

## 目标

让不同来源、不同骨架命名、不同动画结构的 GLB 角色共用一套背屏运行时。东海帝皇与案例曼波是首批适配样本，不是系统支持范围的上限。

## 分层

1. **Renderer**：Three.js、GLTFLoader、相机适配、toon 材质、描边。
2. **Rig Adapter**：骨骼发现、别名映射、能力检测、骨骼 rest pose 缓存。
3. **Physics**：耳朵、尾巴、头发、衣物等可选物理链；缺少骨骼时跳过。
4. **Behavior**：呼吸、张望、眨眼、表情、走路、跑步、舞蹈、触摸反馈。
5. **Agent Bridge**：只接收统一的语义状态/动作，不直接操作 Three.js 骨骼。
6. **Android Sync**：主程序确认 petId 后写入持久状态，背屏按 petId 加载同一角色和状态。

## 模型目录约定

每个模型一个独立目录：

```text
rearscreen/web/model/<petId>/
  character.glb
  pet.json
```

模型可以携带贴图和动画。若贴图已嵌入 GLB，不要求额外贴图文件。禁止假定所有模型都有相同动画名、表情图集或骨骼。

## pet.json 示例

```json
{
  "schemaVersion": 1,
  "petId": "sample_pet",
  "displayName": "示例虚拟形象",
  "model": "character.glb",
  "rig": {
    "head": ["Head", "head"],
    "neck": ["Neck"],
    "spine": ["spine1", "spine2"],
    "arms": ["Upperarm L", "Upperarm R"],
    "ears": ["Ear_01_L", "Ear_01_R"],
    "tail": ["Tail", "しっぽ"],
    "hair": [],
    "cloth": []
  },
  "expression": {
    "enabled": false,
    "blink": false,
    "atlas": null
  },
  "animationAliases": {
    "idle": ["idle", "breathing"],
    "walk": ["walking", "walk"],
    "run": ["running", "run"],
    "dance": ["dancing", "dance"]
  }
}
```

rig 字段允许缺省；适配器先按显式映射找骨骼，再按通用命名规则探测。正式模型应优先提供显式映射，避免误识别同名配饰。

## 能力检测规则

| 能力 | 启用条件 |
|---|---|
| headLook | 找到 Head 或 Neck |
| breathing | 找到至少一根 spine |
| armPose | 找到至少一根手臂骨骼 |
| earPhysics | 找到耳朵骨骼并成功构建物理链 |
| tailPhysics | 找到尾巴骨骼并成功构建物理链 |
| hairPhysics | 找到头发骨骼并成功构建物理链 |
| clothPhysics | 找到衣物/裙摆骨骼并成功构建物理链 |
| touchInteraction | 模型包含可命中的 Mesh |
| expression / blink | 必须由 pet.json 明确声明并完成表情目标校验；不能仅凭 mesh 名称推断贴图图集 |

适配器的“找到骨骼”只代表候选存在；物理能力还必须在链构建成功后才对外报告为可用。

## 统一 Agent 指令

Agent 不发送骨骼名或欧拉角，只发送语义命令：

- `setMood(mood, durationMs?)`
- `playAction(action, options?)`
- `setLocomotion(state)`
- `onTouch(target)`

初始 mood：`idle`、`happy`、`sad`、`angry`、`sleepy`、`thinking`、`excited`。
初始 action：`wave`、`nod`、`dance`、`stretch`、`stumble`、`petReaction`。

每只虚拟形象由自己的动作映射表把语义指令转成可用动画/程序化动作；不支持的动作安全降级到 idle 或可用表情。

## 首批迁移约束

- 保留旧版已验证的 GLTFLoader、toon 材质、outline、拖拽弹簧、物理链和主循环算法。
- 把原先写死的 `POSE_BONE_NAMES` 改为 rig adapter 返回的骨骼列表。
- `updatePose` 不得无条件访问 `pose['Upperarm L']` 等键；所有动作必须按能力检测执行。
- `setupEarTwitch`、`setupChains` 在每次换模型前清理旧状态，避免跨宠物残留引用。
- 表情图集与动画名必须逐模型声明，不允许对未知模型套用旧图集索引。
- 先加载并校验新模型，成功后再切换当前 petId；失败时保留上一只已显示的虚拟形象。

## 当前实现状态

- 已新增通用骨骼发现器 `pet-rig-adapter.js`。
- 尚未把它接入背屏运行时：当前仓库没有旧版 `app_personal.js` 和完整 Web 运行时源码。接入需在取得完整旧版脚本后进行，避免用空壳覆盖已验证的引擎。
