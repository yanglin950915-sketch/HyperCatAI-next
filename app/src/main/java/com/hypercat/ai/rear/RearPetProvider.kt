package com.hypercat.ai.rear

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject

/**
 * Read-only data endpoint consumed by the Xiaomi MAML rear-screen template.
 * URI: content://com.hypercat.ai.rear/pet
 */
class RearPetProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val context = requireNotNull(context)
        val state = PetStateStore.read(context)
        val json = JSONObject()
            .put("pet_name", state.petName)
            .put("pet_emoji", state.petEmoji)
            .put("mood", state.mood)
            .put("reply", state.reply)
            .put("updated_at", state.updatedAt)
            .toString()
        return MatrixCursor(arrayOf("code", "result")).apply {
            addRow(arrayOf(0, json))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.hypercat.pet"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
