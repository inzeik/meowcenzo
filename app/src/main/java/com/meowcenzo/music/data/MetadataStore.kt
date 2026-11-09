package com.meowcenzo.music.data

import android.content.Context
import org.json.JSONObject

/** User-edited tags for one song. Stored inside Meowcenzo only; the audio file is never modified. */
data class MetaOverride(
    val title: String,
    val artist: String,
    val album: String,
    val track: Int,
)

/** Tiny SharedPreferences+JSON store (same style as LibraryStore). Keyed by MediaStore song id. */
class MetadataStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("meowcenzo_meta", Context.MODE_PRIVATE)

    fun all(): Map<Long, MetaOverride> {
        val raw = prefs.getString("items", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val out = HashMap<Long, MetaOverride>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                val j = root.optJSONObject(k) ?: continue
                out[id] = MetaOverride(
                    title = j.optString("title"),
                    artist = j.optString("artist"),
                    album = j.optString("album"),
                    track = j.optInt("track", 0),
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(map: Map<Long, MetaOverride>) {
        val root = JSONObject()
        for ((id, o) in map) {
            root.put(
                id.toString(),
                JSONObject()
                    .put("title", o.title)
                    .put("artist", o.artist)
                    .put("album", o.album)
                    .put("track", o.track)
            )
        }
        prefs.edit().putString("items", root.toString()).apply()
    }
}