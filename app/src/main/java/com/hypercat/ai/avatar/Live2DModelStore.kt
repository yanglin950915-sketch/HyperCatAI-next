package com.hypercat.ai.avatar

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Live2DModel(
    val directory: String,
    val entry: String,
    val displayName: String
)

/**
 * The default Live2D character is bundled into the APK at build time.
 * No model picker, ZIP import, or first-run network download is required.
 */
object Live2DModelStore {
    private const val BUILTIN_DIRECTORY = "file:///android_asset/avatar/default/"
    private const val BUILTIN_ENTRY = "Haru.model3.json"

    private fun builtInModel() = Live2DModel(
        directory = BUILTIN_DIRECTORY,
        entry = BUILTIN_ENTRY,
        displayName = "Haru · Live2D 官方样例"
    )

    private val mutableModel = MutableStateFlow(builtInModel())
    val state = mutableModel.asStateFlow()

    fun load(context: Context) {
        // Keep the public load signature for existing callers. The model is an APK asset.
        mutableModel.value = builtInModel()
    }

    fun current(context: Context): Live2DModel = builtInModel()
}
