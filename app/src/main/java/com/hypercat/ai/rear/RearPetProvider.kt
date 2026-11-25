package com.hypercat.ai.rear

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.hypercat.ai.BuildConfig
import org.json.JSONObject

/**
 * Fixed, read-only UriRoute-compatible endpoint for HyperCat pet state.
 * URI: content://uriroute/data?group=hypercat&name=pet-state
 * Implements the /data result-column contract only; no Rhino/script execution.
 */
class RearPetProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        if (uri.authority != AUTHORITY) return cursor(error("Invalid authority"))
        val group = uri.getQueryParameter("group")
        val name = uri.getQueryParameter("name")
        if (group != GROUP || name != SCRIPT_NAME) return cursor(error("Unknown HyperCat data route"))
        return when (uri.pathSegments.firstOrNull()?.lowercase()) {
            "data" -> cursor(readPetJson())
            "update" -> cursor(JSONObject().put("success", "cache cleared").toString())
            else -> cursor(error("Unsupported UriRoute path"))
        }
    }

    private fun readPetJson(): String {
        val prefs = requireNotNull(context).getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return JSONObject()
            .put("pet_name", prefs.getString("pet_name", "小橘") ?: "小橘")
            .put("pet_emoji", prefs.getString("pet_emoji", "🐈") ?: "🐈")
            .put("mood", prefs.getString("mood", "IDLE") ?: "IDLE")
            .put("reply", prefs.getString("reply", "你好！我是你的 AI 萌宠伙伴。") ?: "")
            .put("updated_at", prefs.getLong("updated_at", 0L).toString())
            .put("app_version", BuildConfig.VERSION_NAME)
            .toString()
    }

    private fun error(message: String) = JSONObject().put("error", message).toString()
    private fun cursor(json: String): Cursor = MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(json)) }
    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.uriroute.data"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val AUTHORITY = "uriroute"
        private const val GROUP = "hypercat"
        private const val SCRIPT_NAME = "pet-state"
        private const val PREFS = "hypercat_rear_pet"
    }
}