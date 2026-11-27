package com.jwwviewer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists drawing memo pins next to the library / app files. */
object MemoStore {
    private const val DIR = "memos"

    fun keyFor(uri: String, displayName: String): String {
        val base = displayName.ifBlank { uri.substringAfterLast('/') }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val hash = uri.hashCode().toUInt().toString(16)
        return "${base}_$hash.json"
    }

    fun load(context: Context, key: String): List<DrawingMemo> {
        val f = file(context, key)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        DrawingMemo(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            x = o.getDouble("x").toFloat(),
                            y = o.getDouble("y").toFloat(),
                            text = o.optString("text", ""),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, key: String, memos: List<DrawingMemo>) {
        val f = file(context, key)
        if (memos.isEmpty()) {
            if (f.exists()) f.delete()
            return
        }
        val arr = JSONArray()
        memos.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("x", m.x.toDouble())
                    .put("y", m.y.toDouble())
                    .put("text", m.text),
            )
        }
        f.writeText(arr.toString())
    }

    private fun file(context: Context, key: String): File {
        val dir = File(context.filesDir, DIR).also { it.mkdirs() }
        return File(dir, key)
    }
}
