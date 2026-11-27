# 第三方组件与许可声明 / Third-Party Notices

本项目自身代码以 [MIT](LICENSE) 许可发布。以下第三方组件随仓库分发或在构建时下载，
各自遵循其原始许可。**本项目的 MIT 许可不覆盖这些组件。**

This project's own code is licensed under the [MIT license](LICENSE). The
third-party components below are either bundled in this repository or fetched at
build time, and remain under their own licenses. **The MIT license does not
cover them.**

---

## 1. 随仓库分发（bundled）

| 组件 | 版本 | 许可 | 位置 | 许可全文 |
| --- | --- | --- | --- | --- |
| [Live2D Cubism Core](https://www.live2d.com/en/sdk/download/web/) | 4.x（`live2dcubismcore.min.js`，207,155 B） | **Live2D Proprietary Software License（非开源）** | `app/src/main/assets/l2d/live2dcubismcore.min.js` | [LICENSES/live2dcubismcore-Live2D-Proprietary.txt](LICENSES/live2dcubismcore-Live2D-Proprietary.txt) |
| [pixi.js](https://github.com/pixijs/pixijs) | 6.5.10 | MIT | `app/src/main/assets/l2d/pixi.min.js` | [LICENSES/MIT.txt](LICENSES/MIT.txt) |
| [pixi-live2d-display](https://github.com/guansss/pixi-live2d-display) | 0.4.0（Cubism 4 build） | MIT | `app/src/main/assets/l2d/cubism4.min.js` | [LICENSES/MIT.txt](LICENSES/MIT.txt) |
| [acp-kernel](https://github.com/ranxianglei/acp-kernel) | 0.0.101 | MIT + 署名附加条款 | `app/src/main/assets/kernel/`（`index.js`、`chunk-*.js`） | [LICENSES/MIT.txt](LICENSES/MIT.txt) / [LICENSES/billion-context-LICENSE.txt](LICENSES/billion-context-LICENSE.txt) |
| [billion-context](https://github.com/ranxianglei/billion-context) | 0.0.101（记忆层） | MIT + 署名附加条款 | `app/src/main/assets/kernel/` | [LICENSES/billion-context-LICENSE.txt](LICENSES/billion-context-LICENSE.txt) |

### Live2D Cubism Core 特别说明

`live2dcubismcore.min.js` 是 Live2D Inc. 的**专有软件**，以 “Redistributable Code”
形式允许作为应用的一部分原样分发，但**不是开源软件**，也不受本项目 MIT 许可覆盖。
其使用受 [Live2D Proprietary Software License Agreement](https://www.live2d.com/eula/live2d-proprietary-software-license-agreement_en.html)
约束。再分发时请保留该文件原样及文件内 banner。

### billion-context / acp-kernel 署名附加条款

上游许可在 MIT 之外要求：任何终端用户可见或可交互的产品，须在「产品首页 / 文档 /
关于·致谢页」至少一处显著标明使用了 billion-context（涉及时包括 acp-kernel），并附上游链接。
本项目在 [README.md](README.md) 的「致谢」一节、[README.en.md](README.en.md) 的
“Acknowledgements” 一节，以及应用内「关于」入口满足该要求：

- 使用了 [billion-context](https://github.com/ranxianglei/billion-context)
- 使用了 [acp-kernel](https://github.com/ranxianglei/acp-kernel)

---

## 2. 构建时下载，不随仓库分发（fetched by `tools/fetch_models.sh`）

这些二进制体积较大，且由上游维护，因此不提交到 git；`tools/fetch_models.sh` 会按
`tools/models.sha256` 校验后放入对应目录。**未执行该脚本无法构建。**

| 组件 | 版本 | 许可 | 落盘位置 |
| --- | --- | --- | --- |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) Android AAR | v2.0.1（JitPack） | Apache-2.0 | `app/libs/sherpa-onnx.aar` |
| sherpa-onnx 流式中文 ASR 模型 `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`（int8） | 2023-02-23 | Apache-2.0 | `app/src/main/assets/asr/` |
| sherpa-onnx 关键词唤醒模型 `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20`（chunk-16 int8） | 2025-12-20 | Apache-2.0 | `app/src/main/assets/kws/` |
| [Silero VAD](https://github.com/snakers4/silero-vad) ONNX | `silero_vad.onnx` | MIT | `app/src/main/assets/vad/silero_vad.onnx` |

- Apache-2.0 全文：[LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)
- MIT 全文（Silero VAD 为 Copyright (c) 2021 Silero Team）：[LICENSES/MIT.txt](LICENSES/MIT.txt)

---

## 3. 通过 Gradle 依赖引入（Apache-2.0 / 见各自仓库）

以下依赖由 Gradle 在构建时解析，不随本仓库分发：

| 组件 | 版本 |
| --- | --- |
| Android Gradle Plugin、Kotlin Gradle Plugin、KSP | 9.5.1 / 2.2.10 / 2.2.10-2.0.2 |
| Kotlin standard library、kotlinx-coroutines | 随 Kotlin 2.2.10 |
| androidx.core:core-ktx | 1.10.1 |
| androidx.activity:activity-compose | 1.8.0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.6.1 |
| androidx.compose (BOM) : ui / ui-graphics / material3 / tooling | 2026.02.01 |
| androidx.room : room-runtime / room-ktx / room-compiler | 2.8.4 |
| androidx.documentfile | 1.0.1 |
| [OkHttp](https://github.com/square/okhttp) | 4.12.0 |
| JUnit 4（仅测试） | 4.13.2（EPL-1.0） |
| androidx.test.ext:junit、androidx.test.espresso:espresso-core、compose ui-test（仅测试） | 1.1.5 / 3.5.1 / BOM |

androidx、Kotlin、OkHttp 均以 Apache-2.0 发布，全文见
[LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)。JUnit 4 采用 Eclipse Public
License 1.0，仅用于单元测试、不进入发布产物。

---

## 4. 不属于本仓库的内容

- **Live2D 角色模型**（`.moc3` / `.model3.json` / 贴图 / `.vtube.json`）：文档与截图中的
  角色版权归其作者，其授权文件标注 `WorkshopSharingForbidden: true`，**禁止再分发**。
  本仓库不包含任何模型文件，使用者需自备并自行确认授权。
- **API 密钥与云服务**（腾讯云 ASR、火山引擎豆包 TTS、任意 OpenAI 兼容 LLM）：
  本仓库不包含任何密钥，使用者需自备账号并遵守各服务商条款。
