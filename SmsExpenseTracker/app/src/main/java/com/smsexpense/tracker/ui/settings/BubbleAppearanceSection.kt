package com.smsexpense.tracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.repository.BubbleSettings
import com.smsexpense.tracker.domain.repository.BubbleShape
import com.smsexpense.tracker.ui.components.BubbleVisual
import com.smsexpense.tracker.ui.components.toComposeShape
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

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
    onBackgroundPicked: (android.net.Uri) -> Unit = {},
    onBackgroundCleared: () -> Unit = {},
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
        Text(stringResource(R.string.appearance_size, size), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = size.toFloat(),
            onValueChange = { size = it.toInt() },
            onValueChangeFinished = { onSizeChange(size) },
            valueRange = BubbleSettings.MIN_SIZE_DP.toFloat()..BubbleSettings.MAX_SIZE_DP.toFloat(),
        )

        // --- Shape ---
        Text(stringResource(R.string.appearance_shape), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleShape.entries.forEach { shape ->
                FilterChip(
                    selected = settings.shape == shape,
                    onClick = { onShapeChange(shape) },
                    label = {
                        Text(
                            stringResource(
                                when (shape) {
                                    BubbleShape.CIRCLE -> R.string.appearance_shape_circle
                                    BubbleShape.ROUNDED -> R.string.appearance_shape_rounded
                                    BubbleShape.SQUARE -> R.string.appearance_shape_square
                                }
                            )
                        )
                    },
                )
            }
        }

        // --- Colour ---
        Text(stringResource(R.string.appearance_colour), style = MaterialTheme.typography.bodyMedium)
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
            stringResource(R.string.appearance_theme_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Background image ---
        Text(stringResource(R.string.appearance_background), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.appearance_background_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The photo picker needs no storage permission on any supported version:
        // androidx falls back to OPEN_DOCUMENT below Android 13.
        val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let(onBackgroundPicked) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    pickImage.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly,
                        )
                    )
                },
            ) {
                Text(
                    stringResource(
                        if (settings.backgroundPath == null) {
                            R.string.appearance_background_pick
                        } else {
                            R.string.appearance_background_change
                        }
                    )
                )
            }
            if (settings.backgroundPath != null) {
                androidx.compose.material3.TextButton(onClick = onBackgroundCleared) {
                    Text(stringResource(R.string.appearance_background_remove))
                }
            }
        }

        // --- Opacity ---
        Text(
            stringResource(R.string.appearance_opacity, (opacity * 100).toInt()),
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
                stringResource(R.string.appearance_show_amount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = settings.showAmount, onCheckedChange = onShowAmountChange)
        }
    }
}

/**
 * Drag a miniature bubble around a phone-shaped canvas to choose where it first
 * appears. Stored as screen percentages, so the spot holds on any screen size.
 */
@Composable
fun BubblePositionPicker(
    settings: BubbleSettings,
    onPositionChange: (Float, Float) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onSnapChange: (Boolean) -> Unit,
) {
    var xPct by remember(settings.startXPercent) { mutableFloatStateOf(settings.startXPercent) }
    var yPct by remember(settings.startYPercent) { mutableFloatStateOf(settings.startYPercent) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.position_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(9f / 19.5f)
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.large,
                )
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.large,
                ),
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()
            val dot = 22.dp
            val dotPx = with(LocalDensity.current) { dot.toPx() }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (xPct * (canvasWidth - dotPx)).toInt(),
                            (yPct * (canvasHeight - dotPx)).toInt(),
                        )
                    }
                    .size(dot)
                    .background(
                        settings.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                        settings.shape.toComposeShape(),
                    )
                    .pointerInput(canvasWidth, canvasHeight) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                xPct = (xPct + amount.x / (canvasWidth - dotPx)).coerceIn(0f, 1f)
                                yPct = (yPct + amount.y / (canvasHeight - dotPx)).coerceIn(0f, 1f)
                            },
                            onDragEnd = { onPositionChange(xPct, yPct) },
                            onDragCancel = { onPositionChange(xPct, yPct) },
                        )
                    },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = false,
                onClick = {
                    xPct = BubbleSettings.DEFAULT_X_PERCENT
                    yPct = BubbleSettings.DEFAULT_Y_PERCENT
                    onPositionChange(xPct, yPct)
                },
                label = { Text(stringResource(R.string.position_reset)) },
            )
            FilterChip(
                selected = false,
                onClick = { xPct = 0f; onPositionChange(xPct, yPct) },
                label = { Text(stringResource(R.string.position_left)) },
            )
            FilterChip(
                selected = false,
                onClick = { xPct = 1f; onPositionChange(xPct, yPct) },
                label = { Text(stringResource(R.string.position_right)) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.position_remember),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = settings.rememberPosition, onCheckedChange = onRememberChange)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.position_snap), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.position_snap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.snapToEdge, onCheckedChange = onSnapChange)
        }
    }
}
