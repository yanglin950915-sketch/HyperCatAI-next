package com.hypercat.ai.rear

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import org.json.JSONObject

/** Read-only virtual-avatar state bridge for Xiaomi's native MAML host. */
class RearAvatarProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val appContext = requireNotNull(context)
        if (!RearCallerVerifier.isTrustedUid(appContext, Binder.getCallingUid())) {
            throw SecurityException("Only Xiaomi Theme Manager may read HyperCat AI avatar state")
        }
        if (uri.authority != AUTHORITY || uri.path != "/avatar-state" && uri.path != "/pet-state") {
            throw IllegalArgumentException("Unknown HyperCat avatar-state URI")
        }
        return cursor(readAvatarJson())
    }

    private fun readAvatarJson(): String {
        val appContext = requireNotNull(context)
        val state = AvatarStateStore.read(appContext)
        val device = com.hypercat.ai.avatar.AvatarDeviceStateStore.state.value
        return JSONObject()
            .put("avatar_name", state.avatarName)
            .put("avatar_id", state.avatarId)
            // Compatibility aliases for MAML cards deployed by older app versions.
            .put("pet_name", state.avatarName)
            .put("pet_emoji", state.avatarId)
            .put("mood", state.mood)
            .put("reply", state.reply)
            .put("updated_at", state.updatedAt.toString())
            .put("enabled", state.enabled)
            .put("user_text", state.userText)
            .put("background_color", state.backgroundColor)
            .put("battery", device.battery)
            .put("charging", device.charging)
            .put("steps", device.steps)
            .put("weather", device.weather)
            .put("temperature", device.temperature)
            .put("music", device.music)
            .put("track", device.track)
            .put("artist", device.artist)
            .put("lyrics", device.lyrics)
            .put("current_lyric", device.currentLyric)
            .put("music_beat", device.musicBeat.toDouble())
            .toString()
    }

    private fun cursor(json: String): Cursor = MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(json)) }
    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.hypercat.avatar-state"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val AUTHORITY = "com.hypercat.ai.rear.provider"
    }
}
