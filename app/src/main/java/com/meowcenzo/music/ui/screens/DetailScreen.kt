package com.meowcenzo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meowcenzo.music.MusicViewModel
import com.meowcenzo.music.data.Song
import com.meowcenzo.music.ui.components.ArtworkImage
import com.meowcenzo.music.ui.components.TerminalButton
import com.meowcenzo.music.ui.components.formatTime
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private fun formatTotal(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** One screen reused for album, artist, folder, playlist and "most played" detail pages. */
@Composable
fun DetailScreen(
    vm: MusicViewModel,
    title: String,
    subtitle: String,
    songs: List<Song>,
    bottomPad: Dp,
    onBack: () -> Unit,
    onSongMore: (Song) -> Unit,
    onDeleteList: (() -> Unit)? = null,
) {
    // Only watch the two fields the list needs (not the 4x/second position ticks).
    val playerFlow = vm.player.ui
    val playingId by remember(playerFlow) { playerFlow.map { it.songId }.distinctUntilChanged() }
        .collectAsState(initial = null)
    val isPlayingNow by remember(playerFlow) { playerFlow.map { it.isPlaying }.distinctUntilChanged() }
        .collectAsState(initial = false)

    val totalMs = remember(songs) { songs.sumOf { it.durationMs } }

    var shuffleTick by remember { mutableIntStateOf(0) }
    var shuffleFlash by remember { mutableStateOf(false) }
    LaunchedEffect(shuffleTick) {
        if (shuffleTick > 0) { shuffleFlash = true; delay(1200); shuffleFlash = false }
    }

    Column(Modifier.fillMaxSize().background(Meow.Ink)) {
        // --- TOP BAR ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).border(1.dp, Meow.Line, RectangleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(6.dp).background(Meow.TextHi, RectangleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "DETAIL_VIEW",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextHi
            )
            Spacer(Modifier.weight(1f))
            if (onDeleteList != null) {
                Box(
                    Modifier.size(36.dp).border(1.dp, Meow.Line, RectangleShape).clickable(onClick = onDeleteList),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Delete, "Delete playlist", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
            // --- HEADER ---
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(220.dp).border(1.dp, Meow.Line, RectangleShape)) {
                        ArtworkImage(songs.firstOrNull(), Modifier.fillMaxSize(), RectangleShape, px = 600)
                    }
                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = title.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black
                        ),
                        color = Meow.TextHi,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Meow.TextLo,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "// ${songs.size} TRACKS // ${formatTotal(totalMs)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Meow.TextLo
                    )
                    Spacer(Modifier.height(20.dp))

                    // Same two buttons as the home screen: white primary + outlined secondary.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TerminalButton(
                            label = "EXECUTE_ALL",
                            modifier = Modifier.weight(1f),
                            filled = true,
                            enabled = songs.isNotEmpty()
                        ) { vm.play(songs, 0) }
                        TerminalButton(
                            label = if (shuffleFlash) "[ SHUFFLED ]" else "RANDOMIZE",
                            modifier = Modifier.weight(1f),
                            enabled = songs.isNotEmpty()
                        ) { vm.shuffle(songs); shuffleTick++ }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            item(key = "listheader") {
                Column {
                    Text(
                        text = "// TRACKLIST [${songs.size}]",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Meow.TextLo,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(color = Meow.Line, thickness = 1.dp)
                }
            }

            if (songs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "// EMPTY",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Meow.TextLo,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }

            // --- SONG LIST (same look as the home list) ---
            itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, s ->
                val isActive = playingId == s.id
                val isPlaying = isActive && isPlayingNow

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isActive) Meow.PanelHigh.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { vm.play(songs, i) }
                            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d", i + 1),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = if (isActive) Meow.TextHi else Meow.TextLo,
                            modifier = Modifier.width(28.dp)
                        )
                        Box(Modifier.border(1.dp, if (isActive) Meow.TextHi else Meow.Line, RectangleShape)) {
                            ArtworkImage(
                                song = s,
                                modifier = Modifier.size(48.dp),
                                shape = RectangleShape,
                                px = 128
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = s.title.uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (isActive) Meow.TextHi else Meow.TextHi.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isActive) {
                                    BlinkCursor(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                                }
                            }
                            Text(
                                text = s.artist.uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Meow.TextLo,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (isPlaying) {
                            Icon(Icons.Rounded.GraphicEq, "Playing", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = formatTime(s.durationMs),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Meow.TextLo
                            )
                        }
                        // 44dp tap target for the options menu
                        Box(
                            Modifier.size(44.dp).clickable { onSongMore(s) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MoreVert, "More", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Meow.Line.copy(alpha = 0.15f), thickness = 1.dp)
                }
            }
        }
    }
}