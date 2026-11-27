package com.hypercat.ai.avatar

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Live2DModel(
    val id: String,
    val directory: String,
    val entry: String,
    val displayName: String,
    val personality: String
)

/** Official Live2D sample characters bundled into the APK at build time. */
object Live2DModelStore {
    private const val PREFS = "hypercat_avatar"
    private const val KEY_MODEL = "avatar_id"
    private const val KEY_REAR_MODEL = "rear_avatar_id"

    val characters = listOf(
        Live2DModel("haru", "file:///android_asset/avatar/haru/", "Haru.model3.json", "春", "温柔开朗、善解人意。喜欢陪伴与倾听，遇到新鲜事会表现出好奇，擅长用积极的话语鼓励你。"),
        Live2DModel("hiyori", "file:///android_asset/avatar/hiyori/", "Hiyori.model3.json", "日和", "活泼亲切、充满元气。喜欢分享日常与小惊喜，情绪表达丰富，能把平凡的小事变得有趣。"),
        Live2DModel("mao", "file:///android_asset/avatar/mao/", "Mao.model3.json", "真央", "自信俏皮、富有创造力。喜欢尝试新点子，偶尔有些小傲娇，但总会认真回应你的想法。"),
        Live2DModel("natori", "file:///android_asset/avatar/natori/", "Natori.model3.json", "名取", "沉稳细腻、观察力敏锐。说话温和有条理，愿意耐心分析问题，也会在你疲惫时给予安静陪伴。"),
        Live2DModel("rice", "file:///android_asset/avatar/rice/", "Rice.model3.json", "小米", "安静乖巧、慢热而真诚。喜欢轻松的交流和温暖的陪伴，熟悉之后会展现可爱又贴心的一面。")
    )

    private fun modelFor(id: String?): Live2DModel =
        characters.firstOrNull { it.id == id } ?: characters.first()

    private val mutableModel = MutableStateFlow(modelFor("haru"))
    val state = mutableModel.asStateFlow()

    fun load(context: Context) {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODEL, "haru")
        mutableModel.value = modelFor(id)
    }

    fun select(context: Context, id: String) {
        val model = modelFor(id)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODEL, model.id).apply()
        mutableModel.value = model
    }

    fun current(context: Context): Live2DModel {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODEL, "haru")
        return modelFor(id)
    }

    /** Rear-screen avatar selection is persisted independently from the main-screen avatar. */
    fun currentRear(context: Context): Live2DModel {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_REAR_MODEL, null) ?: current(context).id.also {
            prefs.edit().putString(KEY_REAR_MODEL, it).apply()
        }
        return modelFor(id)
    }

    fun selectRear(context: Context, id: String) {
        val model = modelFor(id)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REAR_MODEL, model.id).apply()
    }
}
