# Third-party component notices

HyperCat AI includes or adapts the following components for its optional Live2D renderer. Their licenses remain separate from HyperCat AI's own source license.

## PixiJS 6.5.10

- File: `rearscreen/web/live2d/pixi.min.js`
- License: MIT
- Copyright: Mat Groves and PixiJS contributors
- License text: [LICENSES/MIT.txt](LICENSES/MIT.txt)

## pixi-live2d-display 0.4.0 (Cubism 4 build)

- File: `rearscreen/web/live2d/cubism4.min.js`
- License: MIT
- Copyright: Guan (guansss)
- License text: [LICENSES/MIT.txt](LICENSES/MIT.txt)

## Live2D Cubism Core (4.x)

- File: `rearscreen/web/live2d/live2dcubismcore.min.js`
- Copyright: Live2D Inc.
- License: Live2D Proprietary Software License Agreement; this is **not open-source software** and is not covered by the MIT license.
- The Core file is redistributed unchanged as Live2D-designated Redistributable Code. Retain its original banner and comply with the current [Live2D license agreement](https://www.live2d.com/eula/live2d-proprietary-software-license-agreement_en.html).
- License notice: [LICENSES/live2dcubismcore-Live2D-Proprietary.txt](LICENSES/live2dcubismcore-Live2D-Proprietary.txt)

## Adapted renderer source

- The Live2D WebView renderer in `rearscreen/web/live2d/render.html` is adapted from [SZTMBABY/virtual-companion](https://github.com/SZTMBABY/virtual-companion).
- Copyright (c) 2026 SZTMBABY; MIT license.
- Full notice: [LICENSES/virtual-companion-MIT.txt](LICENSES/virtual-companion-MIT.txt)

## User-provided models

HyperCat AI does not bundle any character model. Imported `.model3.json`, `.moc3`, texture, motion, and expression files remain user-supplied content. Import only models that you created or are authorized to use and distribute to the target device.

## Publication reminder

Live2D's SDK publication licensing may apply when distributing an application that embeds Cubism Core. The appropriate plan depends on the application and distribution model; user-importable model functionality may require additional review. Check the current official [SDK publication license guidance](https://www.live2d.com/zh-CHS/sdk/license/) before public release.
