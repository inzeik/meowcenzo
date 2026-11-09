package com.meowcenzo.music.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meowcenzo.music.ui.theme.Meow

/** Shared header for secondary screens: [<] ■ TITLE */
@Composable
fun TerminalScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).border(1.dp, Meow.Line, RectangleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Meow.TextHi, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(6.dp).background(Meow.TextHi, RectangleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black
            ),
            color = Meow.TextHi,
        )
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Meow.Ink)) {
        TerminalScreenHeader("SETTINGS", onBack)
        Text(
            text = "// SYSTEM_CONFIGURATION",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = Meow.TextLo,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsRow(Icons.Rounded.Equalizer, "01", "EQUALIZER", "// BANDS, PRESETS, BASS BOOST", onOpenEqualizer)
            SettingsRow(Icons.Rounded.Info, "02", "ABOUT MEOWCENZO", "// BUILD, AUTHOR, LICENSE", onOpenAbout)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    number: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = if (pressed) Meow.Ink else Meow.TextHi
    val sub = if (pressed) Meow.Ink else Meow.TextLo

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (pressed) Meow.TextHi else Meow.Ink, RectangleShape)
            .border(1.dp, if (pressed) Meow.TextHi else Meow.Line, RectangleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (pressed) Meow.Ink else Meow.PanelHigh, RectangleShape)
                .border(1.dp, if (pressed) Meow.Ink else Meow.Line, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (pressed) Meow.TextHi else Meow.TextHi, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "$number // $title",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black
                ),
                color = fg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = sub,
            )
        }
        Text(
            ">",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            color = sub,
        )
    }
}