package com.hypercat.ai.rear

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import org.json.JSONObject

/**
 * Read-only state bridge for the REAREye host. Only the installed REAREye package
 * may query it; arbitrary third-party apps cannot read Agent reply text.
 */
class RearPetProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val appContext = requireNotNull(context)
        if (!RearCallerVerifier.isTrustedUid(appContext, Binder.getCallingUid())) {
            throw SecurityException("Only REAREye may read HyperCat AI rear-pet state")
        }
        if (uri.authority != AUTHORITY || uri.path != "/pet-state") {
            throw IllegalArgumentException("Unknown HyperCat rear-state URI")
        }
        return cursor(readPetJson())
    }

    private fun readPetJson(): String {
        val prefs = requireNotNull(context).getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return JSONObject()
            .put("pet_name", prefs.getString("pet_name", "小橘") ?: "小橘")
            .put("pet_emoji", prefs.getString("pet_emoji", "🤖") ?: "🤖")
            .put("mood", prefs.getString("mood", "IDLE") ?: "IDLE")
            .put("reply", prefs.getString("reply", "你好！我是你的机器人萌宠。") ?: "")
            .put("updated_at", prefs.getLong("updated_at", 0L).toString())
            .toString()
    }

    private fun cursor(json: String): Cursor = MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(json)) }
    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.hypercat.pet-state"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val AUTHORITY = "com.hypercat.ai.rear.provider"
        private const val PREFS = "hypercat_rear_pet"
    }
}
