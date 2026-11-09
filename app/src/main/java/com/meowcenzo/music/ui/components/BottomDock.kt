package com.meowcenzo.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.meowcenzo.music.MusicViewModel
import com.meowcenzo.music.ui.screens.MiniPlayer
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One solid block pinned to the bottom of the app: [MiniPlayer] on top, optional system
 * taskbar under it, and the system navigation-bar area filled with Meow.Ink.
 * Nothing is transparent, so list rows can never show through underneath.
 *
 * It sits in a Column UNDER the screen content (not as an overlay), so lists simply
 * end where the dock begins and need no extra bottom padding.
 */
@Composable
fun BottomDock(
    vm: MusicViewModel,
    showMiniPlayer: Boolean,
    showTaskbar: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only observe the two fields we need, so the dock does not recompose on every
    // playback-position tick (the full PlayerUi changes 4x per second).
    val flow = vm.player.ui
    val hasSong by remember(flow) { flow.map { it.songId != null }.distinctUntilChanged() }
        .collectAsState(initial = false)
    val isPlaying by remember(flow) { flow.map { it.isPlaying }.distinctUntilChanged() }
        .collectAsState(initial = false)
    val songs by vm.songs.collectAsState()

    val miniVisible = showMiniPlayer && hasSong

    Column(
        modifier
            .fillMaxWidth()
            .background(Meow.Ink)          // background BEFORE padding, so it also fills the nav-bar area
            .navigationBarsPadding()
    ) {
        if (miniVisible) {
            MiniPlayer(vm = vm, onOpen = onOpenPlayer)
        } else if (showTaskbar) {
            HorizontalDivider(color = Meow.Line, thickness = 1.dp)
        }
        if (showTaskbar) {
            SystemTaskbar(trackCount = songs.size, active = isPlaying)
        }
    }
}

/** The "SYS: [bar]  TRACKS: n | MODE: LOCAL" strip. The bar only animates while music plays. */
@Composable
fun SystemTaskbar(trackCount: Int, active: Boolean, modifier: Modifier = Modifier) {
    val fill: State<Float> = if (active) {
        rememberInfiniteTransition(label = "sys").animateFloat(
            initialValue = 0.1f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sysFill",
        )
    } else {
        remember { mutableFloatStateOf(0.12f) }
    }

    Row(
        modifier
            .fillMaxWidth()
            .background(Meow.PanelHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SYS:",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Meow.TextLo,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .width(60.dp)
                    .height(6.dp)
                    .background(Meow.Ink, RectangleShape)
                    .drawBehind {
                        // state is read here (draw phase), so only a redraw happens, no recomposition
                        drawRect(Meow.TextHi, size = Size(size.width * fill.value, size.height))
                    }
                    .border(1.dp, Meow.Line, RectangleShape)
            )
        }
        Text(
            text = "TRACKS: $trackCount | MODE: LOCAL",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Meow.TextLo,
        )
    }
}