package com.ucd.player.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat

private fun Typography.withFont(f: FontFamily, w: FontWeight?) = copy(
    displayLarge = displayLarge.copy(fontFamily = f, fontWeight = w ?: displayLarge.fontWeight),
    displayMedium = displayMedium.copy(fontFamily = f, fontWeight = w ?: displayMedium.fontWeight),
    displaySmall = displaySmall.copy(fontFamily = f, fontWeight = w ?: displaySmall.fontWeight),
    headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = w ?: headlineLarge.fontWeight),
    headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = w ?: headlineMedium.fontWeight),
    headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = w ?: headlineSmall.fontWeight),
    titleLarge = titleLarge.copy(fontFamily = f, fontWeight = w ?: titleLarge.fontWeight),
    titleMedium = titleMedium.copy(fontFamily = f, fontWeight = w ?: titleMedium.fontWeight),
    titleSmall = titleSmall.copy(fontFamily = f, fontWeight = w ?: titleSmall.fontWeight),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

private fun schemeFor(st: UcdStyle): ColorScheme {
    val base = if (st.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = st.accent,
        onPrimary = st.onAccent,
        primaryContainer = st.surface,
        onPrimaryContainer = st.fg,
        secondary = st.muted,
        onSecondary = st.onAccent,
        secondaryContainer = st.surface,
        onSecondaryContainer = st.fg,
        tertiary = st.accent,
        onTertiary = st.onAccent,
        background = st.bg[st.bg.size / 2],
        onBackground = st.fg,
        // Las superficies son transparentes: el fondo lo dibuja GlassBackdrop
        surface = Color.Transparent,
        onSurface = st.fg,
        surfaceVariant = st.surface,
        onSurfaceVariant = st.muted,
        outline = st.outline,
        outlineVariant = st.outline.copy(alpha = st.outline.alpha * 0.5f),
        surfaceContainerLowest = st.panel,
        surfaceContainerLow = st.panel,
        surfaceContainer = st.panel,
        surfaceContainerHigh = st.panel,
        surfaceContainerHighest = st.panel,
    )
}

/**
 * Aplica el tema elegido a toda la app: colores, formas, letra y estilo de superficies.
 * `applySystemBars` ajusta también el color de los iconos de las barras del sistema.
 */
@Composable
fun UcdTheme(
    themeId: String = Themes.Midnight.id,
    applySystemBars: Boolean = false,
    content: @Composable () -> Unit,
) {
    val st = Themes.byId(themeId)
    UcdRuntime.corner = st.corner

    val view = LocalView.current
    if (applySystemBars && !view.isInEditMode) {
        SideEffect {
            var c: Context = view.context
            while (c is ContextWrapper && c !is Activity) c = c.baseContext
            (c as? Activity)?.window?.let { w ->
                val ctl = WindowInsetsControllerCompat(w, view)
                ctl.isAppearanceLightStatusBars = !st.dark
                ctl.isAppearanceLightNavigationBars = !st.dark
            }
        }
    }

    val shapes = Shapes(
        small = RoundedCornerShape((20f * st.corner).dp),
        medium = RoundedCornerShape((28f * st.corner).dp),
        large = RoundedCornerShape((36f * st.corner).dp),
        extraLarge = RoundedCornerShape((44f * st.corner).dp),
    )
    val typography = remember(st.font, st.titleWeight) { Typography().withFont(st.font, st.titleWeight) }

    CompositionLocalProvider(LocalUcdStyle provides st) {
        MaterialTheme(
            colorScheme = schemeFor(st),
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
