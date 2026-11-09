package com.meowcenzo.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meowcenzo.music.MusicViewModel
import com.meowcenzo.music.data.Song
import com.meowcenzo.music.ui.components.BottomDock
import com.meowcenzo.music.ui.components.NamePlaylistDialog
import com.meowcenzo.music.ui.components.PlaylistPickerDialog
import com.meowcenzo.music.ui.components.SongOptionsSheet
import com.meowcenzo.music.ui.screens.DetailScreen
import com.meowcenzo.music.ui.screens.EqualizerScreen
import com.meowcenzo.music.ui.screens.AboutScreen
import com.meowcenzo.music.ui.screens.HomeScreen
import com.meowcenzo.music.ui.screens.SettingsScreen
import com.meowcenzo.music.ui.screens.MiniPlayer
import com.meowcenzo.music.ui.screens.NowPlayingScreen
import com.meowcenzo.music.ui.theme.Meow
import java.util.Locale

@Composable
fun MeowcenzoApp(vm: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val toAsk = remember {
        buildList {
            add(audioPerm)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[audioPerm] == true
    }
    LaunchedEffect(granted) { if (granted) vm.scan() }

    Surface(Modifier.fillMaxSize(), color = Meow.Ink, contentColor = Meow.TextHi) {
        if (granted) Shell(vm) else PermissionScreen(onAllow = { launcher.launch(toAsk) })
    }
}

@Composable
private fun Shell(vm: MusicViewModel) {
    val songs by vm.songs.collectAsState()
    val songMap by vm.songMap.collectAsState()
    val favs by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val mostPlayed by vm.mostPlayed.collectAsState()

    var stack by remember { mutableStateOf(listOf<Route>()) }
    var showNow by remember { mutableStateOf(false) }
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    var optionsPlaylistId by remember { mutableStateOf<String?>(null) }
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    var pendingSong by remember { mutableStateOf<Song?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }

    // The BottomDock now sits under the content in a Column, so screens only need a small gap.
    val bottomPad = 16.dp

    BackHandler(enabled = showNow) { showNow = false }
    BackHandler(enabled = !showNow && stack.isNotEmpty()) { stack = stack.dropLast(1) }

    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { stack = stack.dropLast(1) }
    val openOptions: (Song) -> Unit = {
        optionsSong = it
        optionsPlaylistId = (stack.lastOrNull() as? Route.PlaylistRoute)?.id
    }

    Box(Modifier.fillMaxSize().background(Meow.Ink)) {
        Column(Modifier.fillMaxSize().imePadding()) {   // keyboard pushes the dock up instead of covering it
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                when (val route = stack.lastOrNull()) {
                    null -> HomeScreen(
                        vm = vm,
                        onOpenPlayer = { showNow = true },
                        onOpenSettings = { push(Route.Settings) },
                        onOpenEqualizer = { push(Route.Equalizer) },
                        onNavigate = push
                    )
                    is Route.Album -> {
                        val list = songs.filter { it.albumId == route.id && it.album == route.name }.sortedBy { it.track }
                        DetailScreen(vm, route.name, list.firstOrNull()?.artist.orEmpty(), list, bottomPad, pop, openOptions)
                    }
                    is Route.Artist -> {
                        val list = songs.filter { it.artist == route.name }.sortedBy { it.album.lowercase() }
                        DetailScreen(vm, route.name, "${list.size} songs", list, bottomPad, pop, openOptions)
                    }
                    is Route.FolderRoute -> {
                        val list = songs.filter { it.folderPath == route.path }.sortedBy { it.title.lowercase() }
                        DetailScreen(vm, list.firstOrNull()?.folderName ?: "Folder", route.path, list, bottomPad, pop, openOptions)
                    }
                    is Route.PlaylistRoute -> {
                        val p = playlists.find { it.id == route.id }
                        if (p == null) {
                            LaunchedEffect(Unit) { pop() }
                        } else {
                            val list = p.songIds.mapNotNull { songMap[it] }
                            DetailScreen(
                                vm, p.name, "${list.size} songs", list, bottomPad, pop, openOptions,
                                onDeleteList = { vm.deletePlaylist(p.id); pop() },
                            )
                        }
                    }
                    Route.MostPlayed ->
                        DetailScreen(vm, "Most played", "${mostPlayed.size} songs", mostPlayed, bottomPad, pop, openOptions)
                    Route.Equalizer -> EqualizerScreen(onBack = pop, bottomPad = bottomPad)
                    Route.Settings -> SettingsScreen(
                        onBack = pop,
                        onOpenEqualizer = { push(Route.Equalizer) },
                        onOpenAbout = { push(Route.About) },
                    )
                    Route.About -> AboutScreen(onBack = pop)
                }
            }


            BottomDock(
                vm = vm,
                showMiniPlayer = !showNow,
                showTaskbar = stack.isEmpty(),   // the SYS taskbar belongs to the home screen only
                onOpenPlayer = { showNow = true },
            )
        }

        AnimatedVisibility(
            visible = showNow,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            NowPlayingScreen(
                vm = vm,
                onClose = { showNow = false },
                onOpenEqualizer = { showNow = false; push(Route.Equalizer) },
            )
        }
    }

    optionsSong?.let { s ->
        SongOptionsSheet(
            song = s,
            isFavorite = s.id in favs,
            onDismiss = { optionsSong = null },
            onPlayNext = { vm.player.playNext(s) },
            onAddToQueue = { vm.player.addToQueue(s) },
            onToggleFavorite = { vm.toggleFavorite(s.id) },
            onAddToPlaylist = { pickerSong = s },
            onRemoveFromPlaylist = optionsPlaylistId?.let { pid -> { vm.removeFromPlaylist(pid, s.id) } },
        )
    }

    pickerSong?.let { s ->
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { vm.addToPlaylist(it.id, s.id) },
            onNew = { pendingSong = s; showNewPlaylist = true },
            onDismiss = { pickerSong = null },
        )
    }

    if (showNewPlaylist) {
        NamePlaylistDialog(
            onDismiss = { showNewPlaylist = false; pendingSong = null },
            onCreate = { vm.createPlaylist(it, pendingSong?.id) },
        )
    }
}

// ==========================================
// ANIMATED TERMINAL PERMISSION SCREEN
// ==========================================
@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    val context = LocalContext.current

    // --- ANIMATIONS ---
    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    val bracketScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bracketScale"
    )
    val scanLineY by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f, targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanLineY"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Meow.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // --- TOP STATUS BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .alpha(dotAlpha)
                        .background(Color(0xFFFF2A2A), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "STATUS: WAITING".uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Meow.TextLo
                )
            }
            Text(
                text = "MEM: 64% // CPU: 1.2GHZ".uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = Meow.TextLo.copy(alpha = 0.5f)
            )
        }

        // --- CENTER SECTION ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Targeting Bracket Icon Box
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = bracketScale
                        scaleY = bracketScale
                    }
                    .drawBehind {
                        val stroke = 4.dp.toPx()
                        val length = 24.dp.toPx()

                        // Draw Brackets
                        drawLine(Meow.TextHi, Offset(0f, 0f), Offset(length, 0f), stroke)
                        drawLine(Meow.TextHi, Offset(0f, 0f), Offset(0f, length), stroke)
                        drawLine(Meow.TextHi, Offset(size.width, 0f), Offset(size.width - length, 0f), stroke)
                        drawLine(Meow.TextHi, Offset(size.width, 0f), Offset(size.width, length), stroke)
                        drawLine(Meow.TextHi, Offset(0f, size.height), Offset(length, size.height), stroke)
                        drawLine(Meow.TextHi, Offset(0f, size.height), Offset(0f, size.height - length), stroke)
                        drawLine(Meow.TextHi, Offset(size.width, size.height), Offset(size.width - length, size.height), stroke)
                        drawLine(Meow.TextHi, Offset(size.width, size.height), Offset(size.width, size.height - length), stroke)

                        // Draw Scanning Radar Line
                        val y = scanLineY.dp.toPx()
                        drawLine(
                            color = Meow.TextHi.copy(alpha = 0.3f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AudioFile,
                    contentDescription = "Audio Access",
                    tint = Meow.TextHi,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "MEOWCENZO".uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = Meow.TextHi,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "[ LOCAL AUDIO PLAYER ]".uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = Meow.TextLo,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // --- TERMINAL LOG BOX ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Meow.Line.copy(alpha = 0.5f), RectangleShape)
                    .background(Meow.PanelHigh.copy(alpha = 0.2f), RectangleShape)
                    .padding(16.dp)
            ) {
                Text(
                    text = "SYS.LOG: PERMISSION_CHECK".uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Meow.TextLo
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "> TO BUILD YOUR LIBRARY AND PLAY YOUR MUSIC, MEOWCENZO NEEDS ACCESS TO THE AUDIO FILES ALREADY ON THIS DEVICE.".uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 22.sp
                    ),
                    color = Meow.TextHi,
                    textAlign = TextAlign.Start,
                )
            }

            Spacer(Modifier.height(32.dp))

            // --- HIGH-CONTRAST ACTION BUTTON ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RectangleShape)
                    .background(Meow.TextHi, RectangleShape)
                    .clickable { onAllow() }, // Standard clickable fixes the error
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "> ALLOW ACCESS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    ),
                    color = Meow.Ink
                )
                Text(
                    text = "_",
                    modifier = Modifier.alpha(cursorAlpha),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    ),
                    color = Meow.Ink
                )
            }

            Spacer(Modifier.height(16.dp))

            // --- SECONDARY BUTTON ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, Meow.Line, RectangleShape)
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OPEN SETTINGS".uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Meow.TextHi.copy(alpha = 0.7f)
                )
            }
        }

        // --- FOOTER ---
        Text(
            text = "V1.0 // BUILT BY INZEIK".uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = Meow.TextLo,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}