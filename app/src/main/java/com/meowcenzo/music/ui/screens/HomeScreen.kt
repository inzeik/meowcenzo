package com.meowcenzo.music.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowcenzo.music.MusicViewModel
import com.meowcenzo.music.data.Song
import com.meowcenzo.music.data.SortOrder
import com.meowcenzo.music.ui.Route
import com.meowcenzo.music.ui.components.ArtworkImage
import com.meowcenzo.music.ui.components.formatTime
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private fun Song.matches(q: String) =
    q.isEmpty() || title.contains(q, true) || artist.contains(q, true) || album.contains(q, true)

private fun SortOrder.short() = when (this) {
    SortOrder.TITLE -> "TITLE"
    SortOrder.ARTIST -> "ARTIST"
    SortOrder.RECENT -> "NEWEST"
    SortOrder.DURATION -> "LONGEST"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MusicViewModel,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    val songs by vm.songs.collectAsState()
    val shownSongs by vm.visibleSongs.collectAsState()
    val recents by vm.recentSongs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favs by vm.favorites.collectAsState()
    val loading by vm.loading.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()

    // Observe only the two player fields the list needs (not the 4x/second position ticks).
    val playerFlow = vm.player.ui
    val playingId by remember(playerFlow) { playerFlow.map { it.songId }.distinctUntilChanged() }
        .collectAsState(initial = null)
    val isPlayingNow by remember(playerFlow) { playerFlow.map { it.isPlaying }.distinctUntilChanged() }
        .collectAsState(initial = false)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("SONGS", "ALBUMS", "ARTISTS", "PLAYLISTS", "FAVS")

    val q = query.trim()
    val searching = q.isNotEmpty()

    val albums = remember(songs, q) {
        songs.groupBy { it.albumId }.values.map { it.first() }
            .filter { q.isEmpty() || it.album.contains(q, true) || it.artist.contains(q, true) }
            .sortedBy { it.album.lowercase() }
    }
    val artists = remember(songs, q) {
        songs.groupBy { it.artist }.toList()
            .filter { q.isEmpty() || it.first.contains(q, true) }
            .sortedBy { it.first.lowercase() }
    }
    val shownPlaylists = remember(playlists, q) {
        playlists.filter { q.isEmpty() || it.name.contains(q, true) }
    }
    val favSongs = remember(songs, favs, q) { songs.filter { it.id in favs && it.matches(q) } }
    val playList = if (selectedTab == 4) favSongs else shownSongs

    // Scan feedback: keep the spinner visible at least ~1s, even if the scan is instant.
    var scanTick by remember { mutableIntStateOf(0) }
    var holdScan by remember { mutableStateOf(false) }
    LaunchedEffect(scanTick) {
        if (scanTick > 0) { holdScan = true; delay(1000); holdScan = false }
    }
    val scanning = loading || holdScan

    // RANDOMIZE feedback: stay on the home screen, flash a confirmation, mini player shows the track.
    var randomTick by remember { mutableIntStateOf(0) }
    var randomFlash by remember { mutableStateOf(false) }
    LaunchedEffect(randomTick) {
        if (randomTick > 0) { randomFlash = true; delay(1200); randomFlash = false }
    }

    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    val executeInteraction = remember { MutableInteractionSource() }
    val isExecutePressed by executeInteraction.collectIsPressedAsState()

    // Everything scrolls (header, search, buttons) so the list gets the whole screen.
    // Only the tab row stays pinned.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Meow.Ink),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "header") {
            HomeHeader(
                scanning = scanning,
                trackCount = songs.size,
                onOpenSettings = onOpenSettings,
                onScan = { scanTick++; vm.scan() }
            )
        }

        item(key = "search") {
            TerminalSearchBar(
                query = query,
                onQueryChange = { vm.query.value = it },
                onSearch = { focusManager.clearFocus() }
            )
            Spacer(Modifier.height(12.dp))
        }

        stickyHeader(key = "tabs") {
            Box(Modifier.fillMaxWidth().background(Meow.Ink).padding(vertical = 8.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs.size) { index ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .background(if (isSelected) Meow.TextHi else Meow.Ink, RectangleShape)
                                .border(1.dp, if (isSelected) Meow.TextHi else Meow.Line, RectangleShape)
                                .clickable { selectedTab = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSelected) "▸ ${tabs[index]}" else tabs[index],
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isSelected) Meow.Ink else Meow.TextLo
                            )
                        }
                    }
                }
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (isExecutePressed) Meow.PanelHigh else Meow.TextHi, RectangleShape)
                        .border(1.dp, Meow.TextHi, RectangleShape)
                        .clickable(interactionSource = executeInteraction, indication = null) {
                            if (playList.isNotEmpty()) {
                                vm.player.playQueue(playList, startIndex = 0)
                                onOpenPlayer()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExecutePressed) "[ EXECUTING... ]" else "EXECUTE_ALL",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                        ),
                        color = if (isExecutePressed) Meow.TextHi else Meow.Ink
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (randomFlash) Meow.TextHi else Meow.Ink, RectangleShape)
                        .border(1.dp, if (randomFlash) Meow.TextHi else Meow.Line, RectangleShape)
                        .clickable {
                            if (playList.isNotEmpty()) {
                                vm.player.playQueue(playList, shuffle = true)
                                randomTick++            // no onOpenPlayer(): stay on home
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (randomFlash) "[ SHUFFLED ]" else "RANDOMIZE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                        ),
                        color = if (randomFlash) Meow.Ink else Meow.TextHi
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                if (!searching && recents.isNotEmpty()) {
                    item(key = "recents") {
                        SectionHeader("// RECENT_EXECUTIONS")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(recents.take(10), key = { _, s -> s.id }) { index, song ->
                                RecentlyPlayedCard(index, song) {
                                    vm.player.playQueue(recents, startIndex = index)
                                    onOpenPlayer()
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                item(key = "dbheader") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "// DATABASE [${shownSongs.size}]",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            color = Meow.TextLo
                        )
                        SortChip(sort.short()) {
                            val all = SortOrder.values()
                            vm.sort.value = all[(sort.ordinal + 1) % all.size]
                        }
                    }
                }

                if (shownSongs.isEmpty()) {
                    item(key = "empty-songs") {
                        EmptyState(if (songs.isEmpty()) "// NO_AUDIO_FILES_FOUND" else "// NO_MATCH: \"$q\"")
                    }
                } else {
                    itemsIndexed(shownSongs, key = { _, s -> s.id }) { index, song ->
                        SongListItem(
                            index = index,
                            song = song,
                            isPlaying = playingId == song.id && isPlayingNow,
                            isActive = playingId == song.id
                        ) {
                            vm.player.playQueue(shownSongs, startIndex = index)
                            onOpenPlayer()
                        }
                    }
                }
            }
            1 -> {
                if (albums.isEmpty()) item(key = "empty-albums") { EmptyState("// NO_ALBUMS") }
                items(albums, key = { it.albumId }) { album ->
                    AlbumListItem(album) { onNavigate(Route.Album(album.albumId, album.album)) }
                }
            }
            2 -> {
                if (artists.isEmpty()) item(key = "empty-artists") { EmptyState("// NO_ARTISTS") }
                items(artists, key = { it.first }) { (artistName, artistSongs) ->
                    ArtistListItem(
                        artistName = artistName,
                        songCount = artistSongs.size,
                        representativeSong = artistSongs.first()
                    ) { onNavigate(Route.Artist(artistName)) }
                }
            }
            3 -> {
                if (shownPlaylists.isEmpty()) item(key = "empty-playlists") { EmptyState("// NO_PLAYLISTS") }
                items(shownPlaylists, key = { it.id }) { playlist ->
                    PlaylistListItem(playlist.name, playlist.songIds.size) {
                        onNavigate(Route.PlaylistRoute(playlist.id))
                    }
                }
            }
            4 -> {
                if (favSongs.isEmpty()) item(key = "empty-favs") { EmptyState("// NO_FAVOURITES") }
                itemsIndexed(favSongs, key = { _, s -> s.id }) { index, song ->
                    SongListItem(
                        index = index,
                        song = song,
                        isPlaying = playingId == song.id && isPlayingNow,
                        isActive = playingId == song.id
                    ) {
                        vm.player.playQueue(favSongs, startIndex = index)
                        onOpenPlayer()
                    }
                }
            }
        }
    }
}

// =====================================================================================
// Header: title + live status on the left, boxed EQ / SCAN buttons on the right
// =====================================================================================

@Composable
private fun HomeHeader(
    scanning: Boolean,
    trackCount: Int,
    onOpenSettings: () -> Unit,
    onScan: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlinkDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "MEOWCENZO",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Meow.TextHi,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (scanning) "// SCANNING_LIBRARY..." else "// $trackCount TRACKS INDEXED",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (scanning) Meow.TextHi else Meow.TextLo,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderButton(Icons.Rounded.Settings, "SETTINGS", active = false, onClick = onOpenSettings)
            ScanButton(scanning, onScan)
        }
    }
}

@Composable
private fun BlinkDot() {
    val a by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    // alpha is read inside graphicsLayer (draw phase): no recomposition per frame.
    Box(Modifier.size(6.dp).graphicsLayer { alpha = a }.background(Meow.TextHi, CircleShape))
}

/** Blinking "_" that owns its own animation, so rows never recompose per frame. */
@Composable
fun BlinkCursor(style: TextStyle, color: Color = Meow.TextHi) {
    val a by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Text("_", modifier = Modifier.graphicsLayer { alpha = a }, style = style, color = color)
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = if (pressed) Meow.Ink else Meow.TextHi
    Column(
        Modifier
            .size(width = 52.dp, height = 48.dp)
            .background(if (pressed) Meow.TextHi else Meow.PanelHigh, RectangleShape)
            .border(1.dp, if (active || pressed) Meow.TextHi else Meow.Line, RectangleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = fg, modifier = iconModifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 8.sp
            ),
            color = if (pressed) Meow.Ink else if (active) Meow.TextHi else Meow.TextLo,
            maxLines = 1
        )
    }
}

@Composable
private fun ScanButton(scanning: Boolean, onClick: () -> Unit) {
    val rot: State<Float> = if (scanning) {
        rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "scanRot"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    HeaderButton(
        icon = Icons.Rounded.Refresh,
        label = if (scanning) "WAIT" else "SCAN",
        active = scanning,
        onClick = onClick,
        iconModifier = Modifier.graphicsLayer { rotationZ = rot.value }
    )
}

// =====================================================================================
// Search field (real text input, same terminal look as before)
// =====================================================================================

@Composable
private fun TerminalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Meow.PanelHigh, RectangleShape)
            .border(1.dp, if (focused) Meow.TextHi else Meow.Line, RectangleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("> ", style = mono.copy(fontWeight = FontWeight.Black), color = Meow.TextHi)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Row {
                    Text("SEARCH_LIBRARY", style = mono, color = Meow.TextLo)
                    if (!focused) BlinkCursor(mono)   // fake cursor only while idle
                }
            }
            BasicTextField(
                value = query,
                onValueChange = { onQueryChange(it.take(60)) },
                singleLine = true,
                textStyle = mono.copy(color = Meow.TextHi),
                cursorBrush = SolidColor(Meow.TextHi),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, "Clear", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(Icons.Rounded.Search, "Search", tint = Meow.TextLo, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SortChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .border(1.dp, Meow.Line, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Sort, "Sort", tint = Meow.TextHi, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "SORT:$label",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
            ),
            color = Meow.TextHi
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        color = Meow.TextLo,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        ),
        color = Meow.TextLo,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun RecentlyPlayedCard(index: Int, song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Meow.PanelHigh).border(1.dp, Meow.Line, RectangleShape).padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ID:${String.format("%02d", index + 1)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo
            )
        }

        Box(Modifier.border(1.dp, Meow.Line, RectangleShape)) {
            ArtworkImage(
                song = song,
                modifier = Modifier.size(140.dp),
                shape = RectangleShape,
                px = 256
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = song.title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = Meow.TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Meow.TextLo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SongListItem(
    index: Int,
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isActive) Meow.PanelHigh.copy(alpha = 0.3f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("%02d", index + 1),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (isActive) Meow.TextHi else Meow.TextLo,
                modifier = Modifier.width(28.dp)
            )

            Box(Modifier.border(1.dp, if (isActive) Meow.TextHi else Meow.Line, RectangleShape)) {
                ArtworkImage(
                    song = song,
                    modifier = Modifier.size(48.dp),
                    shape = RectangleShape,
                    px = 128
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isActive) Meow.TextHi else Meow.TextHi.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        BlinkCursor(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                    }
                }
                Text(
                    text = song.artist.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPlaying) {
                Icon(Icons.Rounded.GraphicEq, "Playing", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = formatTime(song.durationMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextLo
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = ">",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo
            )
        }
        HorizontalDivider(color = Meow.Line.copy(alpha = 0.15f), thickness = 1.dp)
    }
}

@Composable
fun AlbumListItem(album: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.border(1.dp, Meow.Line, RectangleShape)) {
            ArtworkImage(
                song = album,
                modifier = Modifier.size(56.dp),
                shape = RectangleShape,
                px = 256
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = album.album.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = Meow.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ARTIST: ${album.artist.uppercase(Locale.getDefault())}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(">", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = Meow.TextLo)
    }
    HorizontalDivider(color = Meow.Line.copy(alpha = 0.15f), thickness = 1.dp)
}

@Composable
fun ArtistListItem(artistName: String, songCount: Int, representativeSong: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CHANGED: Now using ArtworkImage instead of the Person Icon
        Box(Modifier.border(1.dp, Meow.Line, RectangleShape)) {
            ArtworkImage(
                song = representativeSong,
                modifier = Modifier.size(56.dp),
                shape = RectangleShape,
                px = 256
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = artistName.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = Meow.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ENTRIES: $songCount",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo
            )
        }
        Text(">", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = Meow.TextLo)
    }
    HorizontalDivider(color = Meow.Line.copy(alpha = 0.15f), thickness = 1.dp)
}

@Composable
fun PlaylistListItem(playlistName: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(Meow.PanelHigh, RectangleShape).border(1.dp, Meow.Line, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.QueueMusic, null, tint = Meow.TextHi)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlistName.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = Meow.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ENTRIES: $songCount",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo
            )
        }
        Text(">", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = Meow.TextLo)
    }
    HorizontalDivider(color = Meow.Line.copy(alpha = 0.15f), thickness = 1.dp)
}