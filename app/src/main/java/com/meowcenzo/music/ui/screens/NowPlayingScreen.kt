package com.meowcenzo.music.ui.screens

import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.meowcenzo.music.MusicViewModel
import com.meowcenzo.music.playback.SleepTimer
import com.meowcenzo.music.ui.components.ArtworkImage
import com.meowcenzo.music.ui.components.SleepTimerDialog
import com.meowcenzo.music.ui.components.TerminalVisualizer
import com.meowcenzo.music.ui.components.TrackInfoDialog
import com.meowcenzo.music.ui.components.TransportBar
import com.meowcenzo.music.ui.components.tilt3D
import com.meowcenzo.music.ui.components.formatTime
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit, onOpenEqualizer: () -> Unit) {
    val ui by vm.player.ui.collectAsState()
    val songMap by vm.songMap.collectAsState()
    val favs by vm.favorites.collectAsState()
    val editedIds by vm.editedIds.collectAsState()
    val remaining by SleepTimer.remainingMs.collectAsState()
    val context = LocalContext.current
    val song = ui.songId?.let { songMap[it] }

    var scrub by remember { mutableStateOf<Float?>(null) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }
    var showSleep by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var metaFormat by remember { mutableStateOf("AUDIO") }
    var metaBitrate by remember { mutableStateOf("---") }
    var metaSampleRate by remember { mutableStateOf("---") }
    var metaBitDepth by remember { mutableStateOf("---") }

    LaunchedEffect(song?.uri) {
        if (song?.uri != null) {
            metaFormat = "SCANNING..."
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, song.uri)
                    val mime = context.contentResolver.getType(song.uri)
                    metaFormat = when {
                        mime?.contains("mpeg") == true -> "MP3"
                        mime?.contains("mp4") == true || mime?.contains("m4a") == true -> "M4A"
                        mime?.contains("flac") == true -> "FLAC"
                        mime?.contains("wav") == true -> "WAV"
                        mime?.contains("ogg") == true -> "OGG"
                        else -> "AUDIO"
                    }
                    val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    metaBitrate = br?.toLongOrNull()?.div(1000)?.let { "${it}KBPS" } ?: "VBR"
                    val sr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) else null
                    metaSampleRate = sr?.toLongOrNull()?.div(1000)?.let { "${it}KHZ" } ?: "---"
                    val bd = if (Build.VERSION.SDK_INT >= 33) retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE) else null
                    metaBitDepth = bd?.let { "${it}BIT" } ?: "---"
                } catch (e: Exception) { metaFormat = "AUDIO" } finally { try { retriever.release() } catch (e: Exception) {} }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dotAlpha"
    )

    val progress = scrub ?: if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    val artScale by animateFloatAsState(
        if (ui.isPlaying) 1f else 0.95f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Meow.Ink).statusBarsPadding().navigationBarsPadding()
    ) {
        // --- TOP HEADER ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).border(1.dp, Meow.Line, RectangleShape).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).height(6.dp).background(Meow.TextHi, RectangleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = (if (pagerState.currentPage == 0) "NOW_DIFFUSING" else "UP_NEXT_QUEUE").uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Meow.TextHi
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(6.dp).height(6.dp).background(Meow.TextHi, RectangleShape))
            }
            Box(
                Modifier.size(36.dp).border(1.dp, Meow.Line, RectangleShape)
                    .clickable(enabled = song != null) { showInfo = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Info, "Track info", tint = Meow.TextHi, modifier = Modifier.size(18.dp))
            }
        }

        // --- SLIDING PAGER ---
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) {
                // ==========================================
                // PLAYER PAGE
                // ==========================================
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.fillMaxWidth(0.95f).aspectRatio(1f)
                                .tilt3D()
                                .graphicsLayer { scaleX = artScale; scaleY = artScale }
                                .border(1.dp, Meow.Line, RectangleShape)
                        ) {
                            ArtworkImage(song, Modifier.fillMaxSize(), shape = RectangleShape, px = 1024)

                            Row(
                                Modifier.align(Alignment.TopStart).padding(6.dp)
                                    .background(Meow.Ink.copy(alpha = 0.85f), RectangleShape)
                                    .border(1.dp, Meow.Line, RectangleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(4.dp).alpha(dotAlpha).background(Color(0xFFFF2A2A), CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "$metaFormat // $metaBitrate // $metaSampleRate // $metaBitDepth".uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = Meow.TextHi
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = (song?.title ?: ui.title ?: "UNKNOWN TRACK").uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                                color = Meow.TextHi,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = (song?.artist ?: ui.artist ?: "UNKNOWN ARTIST").uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                color = Meow.TextLo,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            TerminalVisualizer(
                                isPlaying = ui.isPlaying,
                                modifier = Modifier.fillMaxWidth().height(36.dp).padding(bottom = 8.dp)
                            )
                            TerminalSeekBar(
                                progress = progress,
                                onSeek = { fraction ->
                                    scrubJob?.cancel()
                                    scrub = fraction
                                    // Live seek while dragging, but at most every ~180 ms. Seeking on every
                                    // touch event floods the player (very slow on 24-bit FLAC / ALAC).
                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (now - lastSeekAt >= 180L) {
                                        lastSeekAt = now
                                        vm.player.seekToFraction(fraction)
                                    }
                                },
                                onSeekEnd = {
                                    scrub?.let { vm.player.seekToFraction(it) }   // exact final position
                                    scrubJob = coroutineScope.launch { delay(350); scrub = null }
                                }
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = formatTime((progress * ui.durationMs).toLong()),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = Meow.TextHi
                                )
                                Text(
                                    text = formatTime(ui.durationMs),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = Meow.TextLo
                                )
                            }
                        }

                        TransportBar(
                            isPlaying = ui.isPlaying,
                            shuffle = ui.shuffle,
                            repeatMode = ui.repeatMode,
                            onShuffle = { vm.player.toggleShuffle() },
                            onPrevious = { vm.player.previous() },
                            onPlayPause = { vm.player.togglePlay() },
                            onNext = { vm.player.next() },
                            onRepeat = { vm.player.cycleRepeat() },
                        )

                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

                            MonoTile(Icons.Rounded.Speed, "${ui.speed}x", Modifier.weight(1f)) {
                                val next = speeds[(speeds.indexOfFirst { it == ui.speed } + 1) % speeds.size]
                                vm.player.setSpeed(next)
                            }
                            MonoTile(
                                Icons.Rounded.Bedtime, if (remaining > 0) formatTime(remaining) else "SLEEP",
                                Modifier.weight(1f), active = remaining > 0,
                            ) { showSleep = true }
                            MonoTile(Icons.Rounded.Equalizer, "EQ", Modifier.weight(1f), onClick = onOpenEqualizer)
                            MonoTile(Icons.AutoMirrored.Rounded.QueueMusic, "QUEUE", Modifier.weight(1f)) {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }

                            val isFav = ui.songId?.let { it in favs } == true
                            MonoTile(
                                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                "", Modifier.size(48.dp), active = isFav, showLabel = false
                            ) { ui.songId?.let(vm::toggleFavorite) }
                        }
                    }
                }
            } else {
                // ==========================================
                // QUEUE PAGE (FIXED ALIGNMENT)
                // ==========================================
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UP_NEXT // ${ui.queueIds.size} TRACKS".uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                            color = Meow.TextHi
                        )
                        Text(
                            text = "RETURN >",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Meow.TextHi,
                            modifier = Modifier
                                .clickable { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                                .padding(4.dp)
                        )
                    }

                    HorizontalDivider(color = Meow.Line, thickness = 1.dp)

                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(ui.queueIds) { i, id ->
                            val s = songMap[id]
                            val current = i == ui.index

                            // FLOATING BLOCK STYLING
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp) // Space between rows
                                    .background(if (current) Meow.PanelHigh else Color.Transparent, RectangleShape)
                                    .border(1.dp, if (current) Meow.Line else Color.Transparent, RectangleShape)
                                    .clickable { vm.player.skipTo(i) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp), // Inner padding
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = String.format("%02d", i + 1),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = if (current) Meow.TextHi else Meow.TextLo,
                                    modifier = Modifier.width(36.dp)
                                )

                                ArtworkImage(s, Modifier.size(48.dp).border(1.dp, Meow.Line, RectangleShape), RectangleShape, px = 128)

                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        text = (s?.title ?: "UNKNOWN").uppercase(Locale.getDefault()),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = if (current) Meow.TextHi else Meow.TextLo,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = s?.artist?.uppercase(Locale.getDefault()) ?: "",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = Meow.TextLo,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!current) {
                                    IconButton(onClick = { vm.player.removeAt(i) }) {
                                        Icon(Icons.Rounded.Close, "Remove", tint = Meow.TextLo, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Meow.TextHi, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showInfo && song != null) {
        TrackInfoDialog(
            song = song,
            edited = song.id in editedIds,
            onDismiss = { showInfo = false },
            onSave = { t, a, al, tr -> vm.updateMetadata(song.id, t, a, al, tr) },
            onReset = { vm.resetMetadata(song.id) },
        )
    }

    if (showSleep) {
        SleepTimerDialog(
            activeMinutesLabel = if (remaining > 0) formatTime(remaining) else null,
            onPick = { SleepTimer.start(it) },
            onCancelTimer = { SleepTimer.cancel() },
            onDismiss = { showSleep = false },
        )
    }
}

// --- SEGMENTED TERMINAL SEEK BAR ---
@Composable
fun TerminalSeekBar(progress: Float, onSeek: (Float) -> Unit, onSeekEnd: () -> Unit = {}) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val width = size.width.toFloat()
                if (width > 0) {
                    var fraction = (down.position.x / width).coerceIn(0f, 1f)
                    onSeek(fraction)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        fraction = (change.position.x / width).coerceIn(0f, 1f)
                        onSeek(fraction)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
                onSeekEnd()
            }
        }
    ) {
        val totalBlocks = 40
        val blockSpacing = 2.dp
        val blockWidth = (maxWidth - (blockSpacing * (totalBlocks - 1))) / totalBlocks

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = blockWidth.toPx()
            val gap = blockSpacing.toPx()
            val h = size.height

            for (i in 0 until totalBlocks) {
                val x = i * (w + gap)
                val blockProgress = i.toFloat() / totalBlocks
                val isPlayed = blockProgress <= progress

                drawRect(
                    color = if (isPlayed) Meow.TextHi else Meow.PanelHigh,
                    topLeft = Offset(x, 0f),
                    size = Size(w, h)
                )
            }
        }
    }
}

// --- MONOCHROME UTILITY TILE ---
@Composable
private fun MonoTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(48.dp).clip(RectangleShape).background(Meow.PanelHigh)
            .border(1.dp, if (active) Meow.TextHi else Meow.Line, RectangleShape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) Meow.TextHi else Meow.TextLo, modifier = Modifier.size(16.dp))
        if (showLabel && label.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (active) Meow.TextHi else Meow.TextLo,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}