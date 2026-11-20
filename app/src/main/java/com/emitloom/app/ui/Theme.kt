// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF101514)
internal val Panel = Color(0xFF1A2220)
internal val PanelRaised = Color(0xFF23302C)
internal val Mint = Color(0xFF9DE6C3)
internal val TextPrimary = Color(0xFFF1F5F2)
internal val TextMuted = Color(0xFFABBDB4)
internal val Divider = Color(0xFF34443D)

private val colors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF0A2419),
    primaryContainer = Color(0xFF233E33),
    onPrimaryContainer = Mint,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF75887E),
    outlineVariant = Divider,
    error = Color(0xFFFFB4AA)
)

@Composable
fun EmitLoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
