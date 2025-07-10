package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * colors for Yomiko theme
 * soft pinks with white and pastel accents
 */
internal object YomikoColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFB7C5), // Pastel pink
        onPrimary = Color(0xFF3A1A21),
        primaryContainer = Color(0xFFB86D82), // Muted pink
        onPrimaryContainer = Color(0xFFFFF0F3),
        inversePrimary = Color(0xFFFFB7C5),
        secondary = Color(0xFFFFD6E0), // Lighter pastel pink
        onSecondary = Color(0xFF3A1A21),
        secondaryContainer = Color(0xFFD48E9F), // Softer pink
        onSecondaryContainer = Color(0xFFFFF0F3),
        tertiary = Color(0xFFFFCCD5), // Very light pink
        onTertiary = Color(0xFF3A1A21),
        tertiaryContainer = Color(0xFFE8A8B8), // Soft pink
        onTertiaryContainer = Color(0xFFFFF0F3),
        background = Color(0xFF2A1A1E), // Dark brownish pink
        onBackground = Color(0xFFFFF0F3), // White with pink tint
        surface = Color(0xFF2A1A1E), // Dark brownish pink
        onSurface = Color(0xFFFFF0F3), // White with pink tint
        surfaceVariant = Color(0xFF3D2A2F), // Slightly lighter brownish pink
        onSurfaceVariant = Color(0xFFFFF0F3), // White with pink tint
        surfaceTint = Color(0xFFFFB7C5), // Pastel pink tint
        inverseSurface = Color(0xFFFFF0F3),
        inverseOnSurface = Color(0xFF3D2A2F),
        outline = Color(0xFFE8A8B8), // Soft pink outline
        surfaceContainerLowest = Color(0xFF241519),
        surfaceContainerLow = Color(0xFF2F1F23),
        surfaceContainer = Color(0xFF3D2A2F), // Navigation bar background
        surfaceContainerHigh = Color(0xFF4A353A),
        surfaceContainerHighest = Color(0xFF574045),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFB86D82), // Muted pink
        onPrimary = Color(0xFFFFF0F3),
        primaryContainer = Color(0xFFFFB7C5), // Pastel pink
        onPrimaryContainer = Color(0xFF3A1A21),
        inversePrimary = Color(0xFFFFB7C5),
        secondary = Color(0xFFD48E9F), // Softer pink
        onSecondary = Color(0xFFFFF0F3),
        secondaryContainer = Color(0xFFFFD6E0), // Lighter pastel pink
        onSecondaryContainer = Color(0xFF3A1A21),
        tertiary = Color(0xFFE8A8B8), // Soft pink
        onTertiary = Color(0xFFFFF0F3),
        tertiaryContainer = Color(0xFFFFCCD5), // Very light pink
        onTertiaryContainer = Color(0xFF3A1A21),
        background = Color(0xFFFFF0F3), // White with pink tint
        onBackground = Color(0xFF3A1A21), // Dark brownish pink text
        surface = Color(0xFFFFFBFF), // White surface with pink tint
        onSurface = Color(0xFF3A1A21), // Dark brownish pink text
        surfaceVariant = Color(0xFFFFE4E9), // Very light pink
        onSurfaceVariant = Color(0xFF3A1A21), // Dark brownish pink text
        surfaceTint = Color(0xFFB86D82), // Muted pink tint
        inverseSurface = Color(0xFF3A1A21),
        inverseOnSurface = Color(0xFFFFF0F3),
        outline = Color(0xFFB86D82), // Muted pink outline
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF8F9),
        surfaceContainer = Color(0xFFFFF0F3), // White with pink tint container
        surfaceContainerHigh = Color(0xFFFFE6EB),
        surfaceContainerHighest = Color(0xFFFFE0E6),
    )
}
