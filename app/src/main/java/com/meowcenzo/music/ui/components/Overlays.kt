package com.meowcenzo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meowcenzo.music.data.Playlist
import com.meowcenzo.music.data.Song
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.delay
import java.util.Locale

// =====================================================================================
// Shared terminal building blocks (sharp rectangles, monospace, 1dp Meow.Line borders)
// =====================================================================================

/** Dialog window: header bar with title + close box, hairline divider, then your content. */
@Composable
fun TerminalDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(Meow.Ink, Meow.Tile)
                .border(1.dp, Meow.Line, Meow.Tile)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Meow.PanelHigh)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(Meow.TextHi, Meow.Tile))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextHi,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .border(1.dp, Meow.Line, Meow.Tile)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Close", tint = Meow.TextHi, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = Meow.Line, thickness = 1.dp)
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

/** Outlined button; [filled] makes it the white primary one. Pressed state inverts, like EXECUTE_ALL. */
@Composable
fun TerminalButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val invert = if (filled) !pressed else pressed

    val bg = when {
        !enabled -> Meow.Ink
        invert -> Meow.TextHi
        else -> Meow.Ink
    }
    val fg = when {
        !enabled -> Meow.TextLo
        invert -> Meow.Ink
        else -> Meow.TextHi
    }
    Box(
        modifier
            .height(48.dp)
            .background(bg, Meow.Tile)
            .border(1.dp, if (!enabled) Meow.Line else if (filled || pressed) Meow.TextHi else Meow.Line, Meow.Tile)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}

// =====================================================================================
// Song options (bottom sheet)
// =====================================================================================

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(Meow.PanelHigh, Meow.Tile).border(1.dp, Meow.Line, Meow.Tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Meow.TextHi, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
            color = Meow.TextHi,
            modifier = Modifier.weight(1f),
        )
        Text(">", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = Meow.TextLo)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Meow.Ink,
        contentColor = Meow.TextHi,
        shape = Meow.Tile,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 3.dp).background(Meow.Line))
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(song, Modifier.size(56.dp).border(1.dp, Meow.Line, Meow.Tile), Meow.Tile, px = 160)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = song.title.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Meow.Line, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))
        SheetAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "PLAY_NEXT") { onPlayNext(); onDismiss() }
        SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "ADD_TO_QUEUE") { onAddToQueue(); onDismiss() }
        SheetAction(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (isFavorite) "REMOVE_FROM_FAVS" else "ADD_TO_FAVS",
        ) { onToggleFavorite(); onDismiss() }
        SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "ADD_TO_PLAYLIST") { onAddToPlaylist(); onDismiss() }
        if (onRemoveFromPlaylist != null) {
            SheetAction(Icons.Rounded.Delete, "REMOVE_FROM_PLAYLIST") { onRemoveFromPlaylist(); onDismiss() }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

// =====================================================================================
// New playlist
// =====================================================================================

@Composable
fun NamePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val ok = name.isNotBlank()

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    fun submit() {
        if (ok) { onCreate(name); onDismiss() }
    }

    TerminalDialog("NEW_PLAYLIST", onDismiss) {
        Text(
            "// ENTER NAME AND CONFIRM",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Meow.TextLo,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Meow.PanelHigh, Meow.Tile)
                .border(1.dp, Meow.TextHi, Meow.Tile)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "> ",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
                color = Meow.TextHi,
            )
            Box(Modifier.weight(1f)) {
                if (name.isEmpty()) {
                    Text(
                        "PLAYLIST_NAME",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = Meow.TextLo,
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Meow.TextHi,
                    ),
                    cursorBrush = SolidColor(Meow.TextHi),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton("CANCEL", Modifier.weight(1f), onClick = onDismiss)
            TerminalButton("CREATE", Modifier.weight(1f), filled = true, enabled = ok) { submit() }
        }
    }
}

// =====================================================================================
// Add to playlist
// =====================================================================================

@Composable
private fun PickerRow(label: String, trailing: String?, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "> ",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo,
            )
            Text(
                label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextLo,
                )
            }
        }
        HorizontalDivider(color = Meow.Line.copy(alpha = 0.4f), thickness = 1.dp)
    }
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    TerminalDialog("ADD_TO_PLAYLIST", onDismiss) {
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            item(key = "new") { PickerRow("+ NEW_PLAYLIST", null) { onNew(); onDismiss() } }
            items(playlists, key = { it.id }) { p ->
                PickerRow(p.name, "[${p.songIds.size}]") { onPick(p); onDismiss() }
            }
        }
        Spacer(Modifier.height(16.dp))
        TerminalButton("CLOSE", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

// =====================================================================================
// Sleep timer
// =====================================================================================

@Composable
fun SleepTimerDialog(
    activeMinutesLabel: String?,
    onPick: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    TerminalDialog("SLEEP_TIMER", onDismiss) {
        Text(
            "// PAUSE PLAYBACK AFTER",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Meow.TextLo,
        )
        Spacer(Modifier.height(12.dp))
        listOf(listOf(5, 10, 15), listOf(30, 45, 60)).forEach { rowMinutes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMinutes.forEach { m ->
                    TerminalButton("%02d MIN".format(m), Modifier.weight(1f)) { onPick(m); onDismiss() }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (activeMinutesLabel != null) {
            Spacer(Modifier.height(8.dp))
            TerminalButton(
                label = "TURN_OFF // $activeMinutesLabel LEFT",
                modifier = Modifier.fillMaxWidth(),
                filled = true,
            ) { onCancelTimer(); onDismiss() }
        } else {
            Text(
                "// TIMER_STATUS: OFF",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                color = Meow.TextLo,
            )
        }
    }
}