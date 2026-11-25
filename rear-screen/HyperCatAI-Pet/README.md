# HyperCat AI Pet — MAML rear-screen module

- Template: `manifest.xml`
- Data source: `content://com.hypercat.ai.rear/pet`
- Provider columns: `code` (integer), `result` (JSON string)
- JSON fields: `pet_name`, `pet_emoji`, `mood`, `reply`, `updated_at`

## Requirements

1. Install and launch HyperCat AI 0.2.0+ at least once.
2. Use a MAML-capable rear-screen module manager on a compatible Xiaomi HyperOS build (for example, REAREye or Janus where supported).
3. Import this module and enable its card in the rear-screen center.

The module is a data-driven MAML template, not a standalone APK. HyperOS versions differ in whether third-party MAML modules can be imported. No Root is required by this module itself, but some import tools may have their own requirements. If the target manager cannot read this app's exported ContentProvider, the data bridge needs a supported UriRoute adapter or a manager-specific provider permission.

The left 32% is intentionally left clear for the camera cutout. Layout is designed for the 904×572 rear panel and scales through MAML view dimensions.
