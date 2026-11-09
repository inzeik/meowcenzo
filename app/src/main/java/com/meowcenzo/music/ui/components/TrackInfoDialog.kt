package com.meowcenzo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowcenzo.music.data.Song
import com.meowcenzo.music.data.TrackTech
import com.meowcenzo.music.data.loadTrackTech
import com.meowcenzo.music.ui.theme.Meow
import java.util.Locale

/**
 * Track metadata viewer + editor.
 * Edits are saved inside Meowcenzo's library; the audio file's own tags are NOT rewritten.
 */
@Composable
fun TrackInfoDialog(
    song: Song,
    edited: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, track: Int) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val tech by produceState(TrackTech(), song.id) { value = loadTrackTech(context, song) }

    var editing by remember { mutableStateOf(false) }
    var title by remember(song.id, editing) { mutableStateOf(song.title) }
    var artist by remember(song.id, editing) { mutableStateOf(song.artist) }
    var album by remember(song.id, editing) { mutableStateOf(song.album) }
    var track by remember(song.id, editing) { mutableStateOf(if (song.track > 0) song.track.toString() else "") }

    TerminalDialog(if (editing) "EDIT_METADATA" else "TRACK_INFO", onDismiss) {
        Column(
            Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!editing) {
                if (edited) Note("// EDITED IN MEOWCENZO LIBRARY")
                Section("// METADATA")
                InfoRow("TITLE", song.title)
                InfoRow("ARTIST", song.artist)
                InfoRow("ALBUM", song.album)
                InfoRow("ALBUM_ARTIST", tech.albumArtist)
                InfoRow("TRACK", if (song.track > 0) song.track.toString() else "---")
                InfoRow("DISC", tech.disc)
                InfoRow("YEAR", tech.year)
                InfoRow("GENRE", tech.genre)
                InfoRow("COMPOSER", tech.composer)

                Section("// TECHNICAL")
                InfoRow("FORMAT", tech.format)
                InfoRow("MIME", tech.mime)
                InfoRow("BITRATE", tech.bitrate)
                InfoRow("SAMPLE_RATE", tech.sampleRate)
                InfoRow("BIT_DEPTH", tech.bitDepth)
                InfoRow("DURATION", formatTime(song.durationMs))
                InfoRow("SIZE", tech.size)

                Section("// FILE")
                InfoRow("NAME", tech.fileName)
                InfoRow("PATH", song.path)
            } else {
                Note("// SAVED INSIDE MEOWCENZO. FILE TAGS ARE NOT MODIFIED.")
                Spacer(Modifier.height(12.dp))
                EditField("TITLE", title, { title = it })
                EditField("ARTIST", artist, { artist = it })
                EditField("ALBUM", album, { album = it })
                EditField("TRACK_NO", track, { track = it.filter(Char::isDigit).take(3) }, numeric = true)
            }
        }

        Spacer(Modifier.height(16.dp))
        if (!editing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalButton("EDIT", Modifier.weight(1f)) { editing = true }
                TerminalButton("CLOSE", Modifier.weight(1f), filled = true, onClick = onDismiss)
            }
        } else {
            if (edited) {
                TerminalButton("RESET_TO_FILE_TAGS", Modifier.fillMaxWidth()) { onReset(); editing = false }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalButton("CANCEL", Modifier.weight(1f)) { editing = false }
                TerminalButton("SAVE", Modifier.weight(1f), filled = true, enabled = title.isNotBlank()) {
                    onSave(title, artist, album, track.toIntOrNull() ?: 0)
                    editing = false
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
        color = Meow.TextHi,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
    HorizontalDivider(color = Meow.Line, thickness = 1.dp)
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
        color = Meow.TextLo,
    )
}

@Composable
private fun InfoRow(key: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo,
                modifier = Modifier.width(104.dp),
            )
            Text(
                text = value.ifBlank { "---" }.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextHi,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = Meow.Line.copy(alpha = 0.2f), thickness = 1.dp)
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = Meow.TextLo,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .background(Meow.PanelHigh, Meow.Tile)
            .border(1.dp, Meow.Line, Meow.Tile)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "> ",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
            color = Meow.TextHi,
        )
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(120)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = Meow.TextHi),
            cursorBrush = SolidColor(Meow.TextHi),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
}