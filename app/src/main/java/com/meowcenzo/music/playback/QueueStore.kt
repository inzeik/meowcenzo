package com.meowcenzo.music.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the queue, current track, position, shuffle and repeat across app restarts,
 * so the MiniPlayer shows your last session (paused) when you reopen the app.
 * Pure offline, SharedPreferences + JSON, same style as LibraryStore.
 */
class QueueStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("meowcenzo_queue", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var listener: Player.Listener? = null

    /** Saves immediately. Only a window around the current track is stored for huge queues. */
    fun saveNow(player: Player?) {
        if (player == null) return
        pending?.let { handler.removeCallbacks(it) }
        pending = null

        val count = player.mediaItemCount
        if (count == 0) {
            prefs.edit().clear().apply()
            return
        }
        val current = player.currentMediaItemIndex.coerceAtLeast(0)
        val from = (current - KEEP_BEHIND).coerceAtLeast(0)
        val to = minOf(count, from + MAX_ITEMS)

        val arr = JSONArray()
        for (i in from until to) {
            val item = player.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: continue
            val md = item.mediaMetadata
            arr.put(
                JSONObject()
                    .put("id", item.mediaId)
                    .put("uri", uri.toString())
                    .put("title", md.title?.toString().orEmpty())
                    .put("artist", md.artist?.toString().orEmpty())
                    .put("album", md.albumTitle?.toString().orEmpty())
                    .put("art", md.artworkUri?.toString().orEmpty())
            )
        }
        prefs.edit()
            .putString("items", arr.toString())
            .putInt("index", current - from)
            .putLong("pos", player.currentPosition.coerceAtLeast(0L))
            .putInt("repeat", player.repeatMode)
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .apply()
    }

    /** Call once in PlaybackService.onCreate. Does nothing if the player already has a queue. */
    fun restore(player: Player) {
        if (player.mediaItemCount > 0) return
        val raw = prefs.getString("items", null) ?: return
        val arr = try { JSONArray(raw) } catch (_: Exception) { return }

        val items = ArrayList<MediaItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val uriStr = o.optString("uri")
            if (uriStr.isEmpty()) continue
            val uri = Uri.parse(uriStr)
            val meta = MediaMetadata.Builder()
                .setTitle(o.optString("title"))
                .setArtist(o.optString("artist"))
                .setAlbumTitle(o.optString("album"))
                .apply {
                    o.optString("art").takeIf { it.isNotEmpty() }?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()
            items += MediaItem.Builder()
                .setMediaId(o.optString("id"))
                .setUri(uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .setMediaMetadata(meta)
                .build()
        }
        if (items.isEmpty()) return

        val index = prefs.getInt("index", 0).coerceIn(0, items.lastIndex)
        player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
        player.setMediaItems(items, index, prefs.getLong("pos", 0L))
        player.prepare()
        player.playWhenReady = false          // never auto-play on app launch
    }

    /** Call once after [restore]. Saves on pause immediately, on queue edits after 0.5 s. */
    fun attach(player: Player) {
        detach(player)
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !p.isPlaying) {
                    saveNow(p)
                    return
                }
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    )
                ) schedule(p)
            }
        }
        listener = l
        player.addListener(l)
    }

    fun detach(player: Player?) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        listener?.let { player?.removeListener(it) }
        listener = null
    }

    private fun schedule(player: Player) {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { saveNow(player) }
        pending = r
        handler.postDelayed(r, 500L)
    }

    private companion object {
        const val KEEP_BEHIND = 50
        const val MAX_ITEMS = 400
    }
}