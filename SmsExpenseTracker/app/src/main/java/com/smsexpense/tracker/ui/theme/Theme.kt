package com.smsexpense.tracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2CF),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4D6357),
    surfaceVariant = Color(0xFFDCE5DD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5B0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFA8F2CF),
    secondary = Color(0xFFB3CCBD),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    // Overlay windows are built from a service context, which does not inherit the
    // activity's layout direction, so it is forced from the selected language.
    val layoutDirection = if (com.smsexpense.tracker.util.AppLocale.isRtl()) {
        androidx.compose.ui.unit.LayoutDirection.Rtl
    } else {
        androidx.compose.ui.unit.LayoutDirection.Ltr
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
