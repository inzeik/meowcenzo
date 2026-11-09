package com.meowcenzo.music

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meowcenzo.music.data.Album
import com.meowcenzo.music.data.Artist
import com.meowcenzo.music.data.Folder
import com.meowcenzo.music.data.audioChanges
import com.meowcenzo.music.data.LibraryStore
import com.meowcenzo.music.data.MediaStoreScanner
import com.meowcenzo.music.data.MetaOverride
import com.meowcenzo.music.data.MetadataStore
import com.meowcenzo.music.data.Playlist
import com.meowcenzo.music.data.Song
import com.meowcenzo.music.data.SortOrder
import com.meowcenzo.music.playback.EqualizerManager
import com.meowcenzo.music.playback.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LibraryStore(app)
    private val metaStore = MetadataStore(app)
    private var overrides: Map<Long, MetaOverride> = metaStore.all()
    private val _edited = MutableStateFlow(overrides.keys.toSet())
    /** Ids of songs whose title/artist/album were edited inside Meowcenzo. */
    val editedIds: StateFlow<Set<Long>> = _edited.asStateFlow()
    val player = PlayerController(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.TITLE)

    private val _favorites = MutableStateFlow(store.favorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(store.playlists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _recents = MutableStateFlow(store.recents())
    private val _counts = MutableStateFlow(store.playCounts())

    private fun <T> Flow<T>.hot(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val songMap: StateFlow<Map<Long, Song>> =
        _songs.map { l -> l.associateBy { it.id } }.hot(emptyMap())

    val visibleSongs: StateFlow<List<Song>> = combine(_songs, query, sort) { list, q, order ->
        val filtered = if (q.isBlank()) list else list.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        when (order) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedWith(
                compareBy<Song>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.track })
            )
            SortOrder.RECENT -> filtered.sortedByDescending { it.dateAddedSec }
            SortOrder.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }.hot(emptyList())

    val albums: StateFlow<List<Album>> = combine(_songs, query) { list, q ->
        list.groupBy { it.albumId to it.album }
            .map { (key, ss) ->
                Album(key.first, key.second, ss.first().artist, ss.sortedBy { it.track })
            }
            .filter { q.isBlank() || it.name.contains(q, true) || it.artist.contains(q, true) }
            .sortedBy { it.name.lowercase() }
    }.hot(emptyList())

    val artists: StateFlow<List<Artist>> = combine(_songs, query) { list, q ->
        list.groupBy { it.artist }
            .map { (name, ss) -> Artist(name, ss.sortedBy { it.album.lowercase() }) }
            .filter { q.isBlank() || it.name.contains(q, true) }
            .sortedBy { it.name.lowercase() }
    }.hot(emptyList())

    val folders: StateFlow<List<Folder>> = _songs.map { list ->
        list.groupBy { it.folderPath }
            .map { (path, ss) -> Folder(path, ss.first().folderName, ss.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.name.lowercase() }
    }.hot(emptyList())

    val favoriteSongs: StateFlow<List<Song>> = combine(_songs, _favorites) { list, favs ->
        list.filter { it.id in favs }
    }.hot(emptyList())

    val recentSongs: StateFlow<List<Song>> = combine(songMap, _recents) { map, ids ->
        ids.mapNotNull { map[it] }
    }.hot(emptyList())

    val mostPlayed: StateFlow<List<Song>> = combine(songMap, _counts) { map, counts ->
        counts.entries.sortedByDescending { it.value }.take(50).mapNotNull { map[it.key] }
    }.hot(emptyList())

    init {
        EqualizerManager.init(app)
        player.onTrackStarted = ::markPlayed
        player.connect()

        // Auto-rescan when audio files are added/removed on the device.
        viewModelScope.launch {
            app.contentResolver.audioChanges().collectLatest {
                delay(2000) // wait for bulk copies to finish
                if (hasAudioPermission()) scan(silent = true)
            }
        }
    }

    fun scan(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _loading.value = true
            _songs.value = MediaStoreScanner.scan(getApplication()).withOverrides()
            _loading.value = false
        }
    }

    private fun List<Song>.withOverrides(): List<Song> =
        if (overrides.isEmpty()) this else map { s ->
            overrides[s.id]?.let { o ->
                s.copy(title = o.title, artist = o.artist, album = o.album, track = o.track)
            } ?: s
        }

    // ---- Metadata editing (kept in the app's own library; file tags are untouched) -----------

    fun updateMetadata(id: Long, title: String, artist: String, album: String, track: Int) {
        val o = MetaOverride(
            title = title.trim(),
            artist = artist.trim().ifBlank { "Unknown artist" },
            album = album.trim().ifBlank { "Unknown album" },
            track = track.coerceAtLeast(0),
        )
        overrides = overrides + (id to o)
        metaStore.save(overrides)
        _edited.value = overrides.keys.toSet()
        _songs.value = _songs.value.map {
            if (it.id == id) it.copy(title = o.title, artist = o.artist, album = o.album, track = o.track) else it
        }
    }

    fun resetMetadata(id: Long) {
        overrides = overrides - id
        metaStore.save(overrides)
        _edited.value = overrides.keys.toSet()
        scan(silent = true)   // reload the original file tags
    }

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(getApplication(), p) == PackageManager.PERMISSION_GRANTED
    }

    // ---- Playback shortcuts ------------------------------------------------------------------

    fun play(list: List<Song>, index: Int) = player.playQueue(list, index)
    fun shuffle(list: List<Song>) = player.playQueue(list, 0, shuffle = true)

    private fun markPlayed(id: Long) {
        val recents = (listOf(id) + _recents.value.filter { it != id }).take(30)
        _recents.value = recents
        store.saveRecents(recents)
        val counts = _counts.value.toMutableMap().also { it[id] = (it[id] ?: 0) + 1 }
        _counts.value = counts
        store.savePlayCounts(counts)
    }

    // ---- Favourites --------------------------------------------------------------------------

    fun toggleFavorite(id: Long) {
        val next = _favorites.value.let { if (id in it) it - id else it + id }
        _favorites.value = next
        store.saveFavorites(next)
    }

    // ---- Playlists ---------------------------------------------------------------------------

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        store.savePlaylists(list)
    }

    fun createPlaylist(name: String, firstSongId: Long? = null) {
        val clean = name.trim().ifBlank { return }
        savePlaylists(_playlists.value + Playlist(UUID.randomUUID().toString(), clean, listOfNotNull(firstSongId)))
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        savePlaylists(_playlists.value.map {
            if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        })
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        savePlaylists(_playlists.value.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        })
    }

    fun deletePlaylist(playlistId: String) {
        savePlaylists(_playlists.value.filterNot { it.id == playlistId })
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}