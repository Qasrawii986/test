package com.smsexpense.tracker.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsexpense.tracker.domain.repository.BubbleShape
import com.smsexpense.tracker.domain.repository.BubbleSettings
import java.io.File

/** Corner rounding for each configurable bubble shape. */
fun BubbleShape.toComposeShape(): Shape = when (this) {
    BubbleShape.CIRCLE -> RoundedCornerShape(percent = 50)
    BubbleShape.ROUNDED -> RoundedCornerShape(percent = 30)
    BubbleShape.SQUARE -> RoundedCornerShape(percent = 12)
}

/**
 * Loads the picked background. Keyed on the path, and the path carries a
 * timestamp, so choosing a new image always decodes afresh. Files are stored
 * pre-scaled to ~512px, so this is cheap enough to do in composition.
 */
@Composable
private fun rememberBackground(path: String?): ImageBitmap? = remember(path) {
    if (path.isNullOrBlank()) return@remember null
    runCatching {
        val file = File(path)
        if (!file.exists()) null else BitmapFactory.decodeFile(path)?.asImageBitmap()
    }.getOrNull()
}

/**
 * The collapsed bubble, rendered from user settings. Shared by the real overlay
 * and the settings preview so what you configure is exactly what appears.
 */
@Composable
fun BubbleVisual(
    settings: BubbleSettings,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit = { contentColor ->
        Text(
            text = label,
            color = contentColor,
            fontSize = (settings.sizeDp / 4.6f).sp,
            lineHeight = (settings.sizeDp / 4.0f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    },
) {
    val base = settings.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val background = base.copy(alpha = settings.opacity)
    val image = rememberBackground(settings.backgroundPath)
    // Over a photo the colour says nothing about readability, so pin the text to
    // white and put a scrim behind it. Otherwise follow the chosen colour.
    val contentColor = when {
        image != null -> Color.White
        base.luminance() > 0.5f -> Color.Black
        else -> Color.White
    }

    Surface(
        shape = settings.shape.toComposeShape(),
        color = background,
        shadowElevation = 6.dp,
        modifier = modifier.size(settings.sizeDp.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = settings.opacity,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA * settings.opacity)),
                )
            }
            content(contentColor)
        }
    }
}

/** Enough to keep white text legible on a bright photo without hiding it. */
private const val SCRIM_ALPHA = 0.35f
