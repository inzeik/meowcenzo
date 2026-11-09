package com.meowcenzo.music.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Read-only technical details shown in the track info dialog. "---" means unknown. */
data class TrackTech(
    val format: String = "---",
    val mime: String = "---",
    val bitrate: String = "---",
    val sampleRate: String = "---",
    val bitDepth: String = "---",
    val size: String = "---",
    val year: String = "---",
    val genre: String = "---",
    val albumArtist: String = "---",
    val composer: String = "---",
    val disc: String = "---",
    val fileName: String = "---",
)

suspend fun loadTrackTech(context: Context, song: Song): TrackTech = withContext(Dispatchers.IO) {
    var mime: String? = null
    var sizeBytes = 0L
    var year = 0
    var displayName: String? = null

    try {
        context.contentResolver.query(
            song.uri,
            arrayOf(
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DISPLAY_NAME,
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                mime = c.getString(0)
                sizeBytes = c.getLong(1)
                year = c.getInt(2)
                displayName = c.getString(3)
            }
        }
    } catch (_: Exception) { }

    val ext = song.path.substringAfterLast('.', "").uppercase(Locale.getDefault())
    var bitrate = "---"
    var sampleRate = "---"
    var bitDepth = "---"
    var genre = "---"
    var albumArtist = "---"
    var composer = "---"
    var disc = "---"
    var yearText = if (year > 0) year.toString() else "---"

    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(context, song.uri)
        fun meta(key: Int): String? = r.extractMetadata(key)?.takeIf { it.isNotBlank() }

        meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { bitrate = "${it / 1000} KBPS" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toLongOrNull()?.let {
                val khz = String.format(Locale.US, "%.1f", it / 1000.0).removeSuffix(".0")
                sampleRate = "$khz KHZ"
            }
            meta(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.let { bitDepth = "$it BIT" }
        }
        meta(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { genre = it }
        meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { albumArtist = it }
        meta(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { composer = it }
        meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { disc = it }
        if (yearText == "---") meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { yearText = it }
    } catch (_: Exception) {
    } finally {
        try { r.release() } catch (_: Exception) { }
    }

    TrackTech(
        format = ext.ifBlank { "AUDIO" },
        mime = mime ?: "---",
        bitrate = bitrate,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        size = if (sizeBytes > 0) Formatter.formatShortFileSize(context, sizeBytes).uppercase(Locale.getDefault()) else "---",
        year = yearText,
        genre = genre,
        albumArtist = albumArtist,
        composer = composer,
        disc = disc,
        fileName = displayName ?: song.path.substringAfterLast('/'),
    )
}