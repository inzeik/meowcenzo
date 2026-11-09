package com.meowcenzo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowcenzo.music.ui.theme.Meow
import kotlinx.coroutines.delay

// ---- EDIT THESE ------------------------------------------------------------------------
private const val AUTHOR = "INZEIK"
private const val REPO = "github.com/inzeik/meowcenzo"
private const val COPYRIGHT_YEAR = "2026"
// The repo currently has no LICENSE file, so this shows the legal default.
// Pick a license (MIT, GPL-3.0, Apache-2.0...), add a LICENSE file, then change this text.
private const val LICENSE_NAME = "ALL RIGHTS RESERVED"
// -----------------------------------------------------------------------------------------

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }
        catch (_: Exception) { "?" }
    }

    val rows = remember(versionName) {
        listOf(
            "VERSION" to versionName,
            "PACKAGE" to "COM.MEOWCENZO.MUSIC",
            "ENGINE" to "ANDROIDX MEDIA3 1.8.0",
            "INTERFACE" to "JETPACK COMPOSE",
            "TARGET" to "ANDROID 8.0+",
            "MODE" to "OFFLINE // NO INTERNET PERMISSION",
            "CORNERS" to "0DP // 100% RECTANGLES",
        )
    }
    // "boot sequence": rows appear one by one
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(rows.size) {
        while (shown < rows.size) { delay(110); shown++ }
    }

    Column(Modifier.fillMaxSize().background(Meow.Ink)) {
        TerminalScreenHeader("ABOUT", onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            AsciiCat()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "MEOWCENZO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 4.sp
                ),
                color = Meow.TextHi,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "[ OFFLINE LOCAL AUDIO PLAYER ]",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                ),
                color = Meow.TextLo,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Panel("SYSTEM_MANIFEST") {
                rows.take(shown).forEach { (k, v) -> KeyValue(k, v) }
                if (shown < rows.size) BlinkCursor(mono(14))
            }
            Spacer(Modifier.height(16.dp))

            Panel("AUTHOR") {
                KeyValue("BUILT_BY", AUTHOR)
                KeyValue("SOURCE", REPO.uppercase())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "> NO CLOUD. NO ACCOUNT. NO TRACKING.\n> JUST YOUR FILES AND A LOUD CAT.",
                    style = mono(12),
                    color = Meow.TextLo,
                )
            }
            Spacer(Modifier.height(16.dp))

            Panel("LICENSE") {
                KeyValue("TYPE", LICENSE_NAME)
                KeyValue("COPYRIGHT", "(C) $COPYRIGHT_YEAR $AUTHOR")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "THIRD-PARTY // APACHE-2.0",
                    style = mono(11).copy(fontWeight = FontWeight.Bold),
                    color = Meow.TextHi,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ANDROIDX MEDIA3\nJETPACK COMPOSE\nMATERIAL ICONS",
                    style = mono(11),
                    color = Meow.TextLo,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("> END_OF_FILE", style = mono(12), color = Meow.TextLo)
                BlinkCursor(mono(12), Meow.TextLo)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun mono(size: Int) = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = FontFamily.Monospace, fontSize = size.sp
)

/** Cat that blinks every couple of seconds. Own composable, so only it recomposes. */
@Composable
private fun AsciiCat() {
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { delay(2600); blink = true; delay(140); blink = false }
    }
    val eyes = if (blink) "-.-" else "o.o"
    Box(
        Modifier
            .border(1.dp, Meow.Line, RectangleShape)
            .background(Meow.PanelHigh, RectangleShape)
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Text(
            text = " /\\_/\\\n( $eyes )\n > ^ <",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp,
            ),
            color = Meow.TextHi,
        )
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Meow.Line, RectangleShape)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Meow.PanelHigh).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 6.dp, height = 6.dp).background(Meow.TextHi, RectangleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "// $title",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black
                ),
                color = Meow.TextHi,
            )
        }
        HorizontalDivider(color = Meow.Line, thickness = 1.dp)
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = key,
            style = mono(11),
            color = Meow.TextLo,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = mono(12).copy(fontWeight = FontWeight.Bold),
            color = Meow.TextHi,
            modifier = Modifier.weight(1f),
        )
    }
}