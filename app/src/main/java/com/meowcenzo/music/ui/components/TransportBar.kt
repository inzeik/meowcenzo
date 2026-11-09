package com.meowcenzo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.meowcenzo.music.ui.theme.Meow

/**
 * One joined control strip instead of five separate boxes:
 *
 *   [ SHF ][ PREV ][   PLAY   ][ NEXT ][ RPT ]
 *
 * Same height, shared 1dp dividers, one outer border. PLAY is the only filled (white)
 * cell. Shuffle / repeat get a lit square + brighter icon when active.
 * Pressed cells invert, exactly like EXECUTE_ALL on the home screen.
 */
@Composable
fun TransportBar(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repeatOn = repeatMode != Player.REPEAT_MODE_OFF
    val repeatLabel = when (repeatMode) {
        Player.REPEAT_MODE_ALL -> "RPT:ALL"
        Player.REPEAT_MODE_ONE -> "RPT:ONE"
        else -> "RPT:OFF"
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, Meow.Line, RectangleShape)
    ) {
        TransportCell(
            icon = Icons.Rounded.Shuffle,
            label = if (shuffle) "SHF:ON" else "SHF:OFF",
            description = "Shuffle",
            active = shuffle,
            modifier = Modifier.weight(1f),
            onClick = onShuffle,
        )
        CellDivider()
        TransportCell(
            icon = Icons.Rounded.SkipPrevious,
            label = "PREV",
            description = "Previous",
            modifier = Modifier.weight(1.15f),
            onClick = onPrevious,
        )
        CellDivider()
        TransportCell(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            label = if (isPlaying) "PAUSE" else "PLAY",
            description = "Play/Pause",
            primary = true,
            modifier = Modifier.weight(1.7f),
            onClick = onPlayPause,
        )
        CellDivider()
        TransportCell(
            icon = Icons.Rounded.SkipNext,
            label = "NEXT",
            description = "Next",
            modifier = Modifier.weight(1.15f),
            onClick = onNext,
        )
        CellDivider()
        TransportCell(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = repeatLabel,
            description = "Repeat",
            active = repeatOn,
            modifier = Modifier.weight(1f),
            onClick = onRepeat,
        )
    }
}

@Composable
private fun CellDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Meow.Line))
}

@Composable
private fun TransportCell(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    // PLAY is white with black icon; pressed it inverts to dark with white icon.
    val bg: Color = when {
        primary && !pressed -> Meow.TextHi
        pressed -> Meow.PanelHigh
        else -> Meow.Ink
    }
    val fg: Color = when {
        primary && !pressed -> Meow.Ink
        primary || pressed -> Meow.TextHi
        active -> Meow.TextHi
        else -> Meow.TextLo
    }

    Column(
        modifier
            .fillMaxHeight()
            .background(bg, RectangleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = fg,
            modifier = Modifier.size(if (primary) 36.dp else 26.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                // lit square = feature is ON
                Box(Modifier.size(4.dp).background(fg, RectangleShape))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                ),
                color = fg,
                maxLines = 1,
            )
        }
    }
}