package app.yeschef.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yeschef.R

// YesChef palette: crisp white, light "sky" blue, soft gold accents.
object YcColors {
    val Sky = Color(0xFF2F6FB0)
    val SkyLight = Color(0xFFD9E9FB)
    val SkyMist = Color(0xFFEEF5FD)
    val Ink = Color(0xFF0E2238)
    val Gold = Color(0xFFB08A3A)
    val GoldLight = Color(0xFFF6E8C6)
    val GoldDeep = Color(0xFF3D2B06)
    val Night = Color(0xFF0E1622)
}

private val LightColors = lightColorScheme(
    primary = YcColors.Sky,
    onPrimary = Color.White,
    primaryContainer = YcColors.SkyLight,
    onPrimaryContainer = Color(0xFF0B2A4A),
    secondary = Color(0xFF5A7898),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEFA),
    onSecondaryContainer = Color(0xFF13304D),
    tertiary = YcColors.Gold,
    onTertiary = Color.White,
    tertiaryContainer = YcColors.GoldLight,
    onTertiaryContainer = YcColors.GoldDeep,
    background = Color.White,
    onBackground = YcColors.Ink,
    surface = Color.White,
    onSurface = YcColors.Ink,
    surfaceVariant = Color(0xFFE8F0F9),
    onSurfaceVariant = Color(0xFF4A5D73),
    surfaceTint = YcColors.Sky,
    outline = Color(0xFF8FA4BC),
    outlineVariant = Color(0xFFD5E1EE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFE),
    surfaceContainer = YcColors.SkyMist,
    surfaceContainerHigh = Color(0xFFE8F1FB),
    surfaceContainerHighest = Color(0xFFE1ECF8),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFCE4E1),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC8F5),
    onPrimary = Color(0xFF07304F),
    primaryContainer = Color(0xFF1D4A73),
    onPrimaryContainer = YcColors.SkyLight,
    secondary = Color(0xFFB2C8E0),
    onSecondary = Color(0xFF1C3248),
    secondaryContainer = Color(0xFF2A3F57),
    onSecondaryContainer = Color(0xFFDCE8F5),
    tertiary = Color(0xFFE6C77E),
    onTertiary = Color(0xFF3D2B06),
    tertiaryContainer = Color(0xFF594316),
    onTertiaryContainer = YcColors.GoldLight,
    background = YcColors.Night,
    onBackground = Color(0xFFE3EAF3),
    surface = YcColors.Night,
    onSurface = Color(0xFFE3EAF3),
    surfaceVariant = Color(0xFF283446),
    onSurfaceVariant = Color(0xFFB9C6D6),
    surfaceTint = Color(0xFF9CC8F5),
    outline = Color(0xFF7D8DA2),
    outlineVariant = Color(0xFF314053),
    surfaceContainerLowest = Color(0xFF0A111B),
    surfaceContainerLow = Color(0xFF131D2B),
    surfaceContainer = Color(0xFF172232),
    surfaceContainerHigh = Color(0xFF1E2A3C),
    surfaceContainerHighest = Color(0xFF263347),
)

/** DM Serif Display for headings gives recipes a cookbook feel; body text stays Roboto. */
val SerifDisplay = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))

private val base = Typography()
private val YcTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SerifDisplay),
    displayMedium = base.displayMedium.copy(fontFamily = SerifDisplay),
    displaySmall = base.displaySmall.copy(fontFamily = SerifDisplay),
    headlineLarge = base.headlineLarge.copy(fontFamily = SerifDisplay),
    headlineMedium = base.headlineMedium.copy(fontFamily = SerifDisplay),
    headlineSmall = base.headlineSmall.copy(fontFamily = SerifDisplay),
    titleLarge = base.titleLarge.copy(fontFamily = SerifDisplay, fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp),
)

private val YcShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Colours used to tag shopping items by recipe: soft, distinct, and readable on white and night blue. */
val RecipeTagColors = listOf(
    Color(0xFF4A90D9), // sky
    Color(0xFFD4A53C), // gold
    Color(0xFF3FA796), // sea green
    Color(0xFFD9736A), // coral
    Color(0xFF8C7AD6), // lavender
    Color(0xFF6FAE4F), // herb
    Color(0xFFE08E3C), // apricot
    Color(0xFFC468A6), // berry
)

/** Small caps-style overline used for section labels. */
val OverlineStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)

@Composable
fun YesChefTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = YcTypography,
        shapes = YcShapes,
        content = content,
    )
}
