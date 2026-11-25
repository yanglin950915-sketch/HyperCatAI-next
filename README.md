# HyperCat AI

A fresh Android project for a rear-screen AI companion and agent.

## Initial technical baseline
- Kotlin + Jetpack Compose
- Android min SDK 26, compile/target SDK 35
- Local model integration planned through llama.cpp / GGUF
- Online model integration planned through OpenAI-compatible APIs
- Pet animation layer and agent tool layer developed in parallel
- Shizuku remains optional; no root or bootloader unlock required

## Important device constraint
Native Xiaomi rear-screen integration depends on supported system APIs, SDK access, and device firmware policy. A regular Android activity or overlay does not guarantee inclusion in Xiaomi's native rear-screen app picker.

## Roadmap
1. Establish buildable Android foundation
2. Implement pet state machine, selectable pets, and animation assets
3. Implement provider abstraction for local and online LLMs
4. Add permission-gated agent tools and user confirmations
5. Validate rear-screen behavior on supported Xiaomi hardware
