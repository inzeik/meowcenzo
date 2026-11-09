package com.meowcenzo.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.meowcenzo.music.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Foreground playback service. Owns the ExoPlayer, the system media notification,
 * lock-screen controls, Bluetooth / headset buttons and Android Auto-style sessions.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var queueStore: QueueStore? = null

    override fun onCreate() {
        super.onCreate()
        EqualizerManager.init(this)

        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // The default LoadControl waits for ~2.5 s of audio (5 s after a rebuffer) before playing again
        // after every seek. With hi-res 24-bit FLAC / ALAC that is a long pause. These values resume
        // almost instantly; local files read fast, so the small start threshold is safe.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 150,
                /* bufferForPlaybackAfterRebufferMs = */ 300,
            )
            .setPrioritizeTimeOverSizeThresholds(true)   // hi-res files are big per second
            .build()

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(attrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)   // pause when headphones unplug
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            // 1. Trigger when the player creates a new audio track
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    EqualizerManager.attach(audioSessionId)
                    broadcastAudioSession(audioSessionId)
                }
            }

            // 2. Fallback: Trigger when playback starts (in case the ID was missed)
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val currentSessionId = player.audioSessionId
                    if (currentSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        EqualizerManager.attach(currentSessionId)
                        broadcastAudioSession(currentSessionId)
                    }
                }
            }
        })

        // Initial attach attempt
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            EqualizerManager.attach(player.audioSessionId)
        }

        queueStore = QueueStore(this).also {
            it.restore(player)
            it.attach(player)
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Controllers send items with only a mediaUri; resolve it to a playable item.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                        if (uri != null) item.buildUpon().setUri(uri).build() else item
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        SleepTimer.onFinish = { player.pause() }
    }

    // Helper to broadcast the session open action
    private fun broadcastAudioSession(sessionId: Int) {
        try {
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        queueStore?.saveNow(session?.player)
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        SleepTimer.onFinish = null
        SleepTimer.cancel()
        EqualizerManager.release()

        // FIXED: Cast session.player to ExoPlayer to get the audioSessionId
        val exoPlayer = session?.player as? ExoPlayer
        exoPlayer?.audioSessionId?.let { sessionId ->
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                sendBroadcast(intent)
            }
        }

        queueStore?.saveNow(session?.player)
        queueStore?.detach(session?.player)

        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}