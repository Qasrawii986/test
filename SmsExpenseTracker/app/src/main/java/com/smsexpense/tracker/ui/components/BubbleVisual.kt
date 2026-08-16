package com.smsexpense.tracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsexpense.tracker.domain.repository.BubbleShape
import com.smsexpense.tracker.domain.repository.BubbleSettings

/** Corner rounding for each configurable bubble shape. */
fun BubbleShape.toComposeShape(): Shape = when (this) {
    BubbleShape.CIRCLE -> RoundedCornerShape(percent = 50)
    BubbleShape.ROUNDED -> RoundedCornerShape(percent = 30)
    BubbleShape.SQUARE -> RoundedCornerShape(percent = 12)
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
    // Pick readable text for whichever colour the user chose.
    val contentColor = if (base.luminance() > 0.5f) Color.Black else Color.White

    Surface(
        shape = settings.shape.toComposeShape(),
        color = background,
        shadowElevation = 6.dp,
        modifier = modifier.size(settings.sizeDp.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content(contentColor)
        }
    }
}
