package com.smsexpense.tracker.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.BubbleShape
import com.smsexpense.tracker.ui.components.BubbleVisual
import com.smsexpense.tracker.ui.components.toComposeShape

/**
 * Appearance controls with a live preview: the preview uses the very same
 * composable the overlay draws, so nothing can drift between them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleAppearanceControls(
    settings: BubbleSettings,
    onSizeChange: (Int) -> Unit,
    onShapeChange: (BubbleShape) -> Unit,
    onColorChange: (Long?) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onShowAmountChange: (Boolean) -> Unit,
) {
    // Sliders track locally while dragging and commit on release.
    var size by remember(settings.sizeDp) { mutableIntStateOf(settings.sizeDp) }
    var opacity by remember(settings.opacity) { mutableFloatStateOf(settings.opacity) }
    val previewSettings = settings.copy(sizeDp = size, opacity = opacity)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- Live preview ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((BubbleSettings.MAX_SIZE_DP + 32).dp),
                contentAlignment = Alignment.Center,
            ) {
                BubbleVisual(settings = previewSettings, label = "12.50 JD")
            }
        }

        // --- Size ---
        Text("Size — ${size}dp", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = size.toFloat(),
            onValueChange = { size = it.toInt() },
            onValueChangeFinished = { onSizeChange(size) },
            valueRange = BubbleSettings.MIN_SIZE_DP.toFloat()..BubbleSettings.MAX_SIZE_DP.toFloat(),
        )

        // --- Shape ---
        Text("Shape", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleShape.entries.forEach { shape ->
                FilterChip(
                    selected = settings.shape == shape,
                    onClick = { onShapeChange(shape) },
                    label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        // --- Colour ---
        Text("Colour", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleSettings.PRESET_COLORS.forEach { argb ->
                val selected = settings.colorArgb == argb
                val swatch = argb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .size(36.dp)
                        .clickable { onColorChange(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = swatch,
                        shape = settings.shape.toComposeShape(),
                        border = if (selected) {
                            androidx.compose.foundation.BorderStroke(
                                3.dp, MaterialTheme.colorScheme.onSurface,
                            )
                        } else null,
                        modifier = Modifier.size(if (selected) 34.dp else 30.dp),
                    ) {}
                    if (argb == null) {
                        Text(
                            "A",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        Text(
            "\"A\" follows the app theme.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Opacity ---
        Text(
            "Opacity — ${(opacity * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            onValueChangeFinished = { onOpacityChange(opacity) },
            valueRange = BubbleSettings.MIN_OPACITY..1f,
        )

        // --- Content ---
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Show the amount inside the bubble",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = settings.showAmount, onCheckedChange = onShowAmountChange)
        }
    }
}
