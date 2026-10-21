package com.albaconecta.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CorpalivPurple,               // Botones, Switch/Checkbox activos, títulos
    onPrimary = Color.White,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = CorpalivPurpleDeep,
    secondary = CorpalivMagenta,            // Chips, filtros, píldoras de acento
    onSecondary = Color.White,
    secondaryContainer = MagentaContainerLight,
    onSecondaryContainer = Color(0xFF5C0745),
    tertiary = CorpalivAmber,              // Acento "Colaborar/Donar"
    onTertiary = CorpalivInk,               // Texto negro sobre ámbar, igual que el sitio
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF3B2D00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = CorpalivLilac,             // Fondo lila claro del sitio
    onBackground = CorpalivInk,
    surface = CorpalivLilac,
    onSurface = CorpalivInk,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleDarkTheme,
    onPrimary = OnPurpleDark,
    primaryContainer = CorpalivPurpleHover, // El hover del sitio funciona como contenedor oscuro
    onPrimaryContainer = PurpleContainerLight,
    secondary = MagentaDarkTheme,
    onSecondary = OnMagentaDark,
    secondaryContainer = MagentaContainerDark,
    onSecondaryContainer = MagentaContainerLight,
    tertiary = CorpalivAmber,
    onTertiary = OnAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberContainerLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = Color(0xFF484454),
    onSurfaceVariant = OutlineVariantLight,
    outline = OutlineDark,
    outlineVariant = Color(0xFF484454)
)

@Composable
fun AlbaConectaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamicColor apagado: Material You (Android 12+) pisaría la paleta de marca
    // con los colores del wallpaper del usuario.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}