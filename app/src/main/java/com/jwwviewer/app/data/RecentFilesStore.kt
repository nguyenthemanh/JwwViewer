package com.jwwviewer.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentDataStore by preferencesDataStore("jww_recent")

data class RecentFile(
    val uri: String,
    val name: String,
    val kind: String, // jww | pdf
    val openedAt: Long,
)

class RecentFilesStore(private val context: Context) {
    private val key = stringPreferencesKey("items")

    val recent: Flow<List<RecentFile>> = context.recentDataStore.data.map { prefs ->
        decode(prefs[key].orEmpty())
    }

    suspend fun add(uri: Uri, name: String, kind: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // some providers disallow persistable grants
        }
        context.recentDataStore.edit { prefs ->
            val list = decode(prefs[key].orEmpty()).toMutableList()
            list.removeAll { it.uri == uri.toString() }
            list.add(0, RecentFile(uri.toString(), name, kind, System.currentTimeMillis()))
            prefs[key] = encode(list.take(20))
        }
    }

    suspend fun remove(uri: String) {
        context.recentDataStore.edit { prefs ->
            val list = decode(prefs[key].orEmpty()).filterNot { it.uri == uri }
            prefs[key] = encode(list)
        }
    }

    private fun encode(items: List<RecentFile>): String {
        val arr = JSONArray()
        for (i in items) {
            arr.put(
                JSONObject()
                    .put("uri", i.uri)
                    .put("name", i.name)
                    .put("kind", i.kind)
                    .put("openedAt", i.openedAt),
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<RecentFile> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        RecentFile(
                            uri = o.getString("uri"),
                            name = o.getString("name"),
                            kind = o.optString("kind", "jww"),
                            openedAt = o.optLong("openedAt", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
