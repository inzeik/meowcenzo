package com.meowcenzo.music.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.meowcenzo.music.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerUi(
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueIds: List<Long> = emptyList(),
    val index: Int = -1,
    val speed: Float = 1f,
    val connected: Boolean = false,
)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build()
    )
    .build()

/** Thin UI-friendly wrapper around a Media3 [MediaController] connected to [PlaybackService]. */
class PlayerController(context: Context) {

    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    var onTrackStarted: ((Long) -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            controller?.let { c ->
                if (c.isPlaying) {
                    val dur = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    _ui.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0L), durationMs = dur) }
                }
            }
            handler.postDelayed(this, 250L)
        }
    }

    private var lastMarkedId: Long? = null

    private fun mark(id: Long) {
        lastMarkedId = id
        onTrackStarted?.invoke(id)
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toLongOrNull()?.let { mark(it) }
        }
        // Also count a play when playback actually starts on a track we have not counted yet
        // (e.g. the queue restored after restart, then you press play).
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) return
            val id = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
            if (id != lastMarkedId) mark(id)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                sync(c)
                handler.post(ticker)
            } catch (_: Exception) {
                future = null
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun release() {
        handler.removeCallbacks(ticker)
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val ids = List(p.mediaItemCount) { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        _ui.value = PlayerUi(
            songId = item?.mediaId?.toLongOrNull(),
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = p.playWhenReady &&
                    p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE,
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queueIds = ids,
            index = p.currentMediaItemIndex,
            speed = p.playbackParameters.speed,
            connected = true,
        )
    }

    // ---- Transport ---------------------------------------------------------------------------

    fun playQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        val start = if (shuffle == true) songs.indices.random() else startIndex.coerceIn(songs.indices)
        c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0L)
        if (c.playWhenReady) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }   // restarts the track if > 3 s in

    fun seekToFraction(f: Float) {
        val c = controller ?: return
        val dur = c.duration.takeIf { it != C.TIME_UNSET } ?: return
        val target = (dur * f.coerceIn(0f, 1f)).toLong()
        c.seekTo(target)
        _ui.update { it.copy(positionMs = target, durationMs = dur) }   // UI reacts instantly
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }

    // ---- Queue -------------------------------------------------------------------------------

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playQueue(listOf(song)) else
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playQueue(listOf(song)) else c.addMediaItem(song.toMediaItem())
    }

    fun removeAt(index: Int) { controller?.removeMediaItem(index) }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }
}