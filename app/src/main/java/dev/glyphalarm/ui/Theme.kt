package dev.glyphalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.glyphalarm.R

/**
 * Classic Nothing look: pure black, flat dark-grey cards, white dot-matrix type for headlines, a plain sans for text,
 * monoline icons and a single red "signal" accent. No gradients, no shadows, no blur.
 */
@Immutable
class NColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderVisible: Color,
    val display: Color,
    val primary: Color,
    val secondary: Color,
    val disabled: Color,
    val accent: Color = Color(0xFFD71921),
    val success: Color = Color(0xFF4A9E5C),
    val dark: Boolean,
)

val NDark = NColors(
    bg = Color(0xFF000000), surface = Color(0xFF1C1C1E), surfaceRaised = Color(0xFF2A2A2D),
    border = Color(0xFF2C2C2E), borderVisible = Color(0xFF3F3F43),
    display = Color(0xFFFFFFFF), primary = Color(0xFFE8E8E8), secondary = Color(0xFF9A9AA0), disabled = Color(0xFF66666B), dark = true,
)

val NLight = NColors(
    bg = Color(0xFFF2F2F2), surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFE9E9EA),
    border = Color(0xFFE2E2E3), borderVisible = Color(0xFFCCCCCE),
    display = Color(0xFF000000), primary = Color(0xFF1A1A1A), secondary = Color(0xFF6A6A70), disabled = Color(0xFF9E9EA4), dark = false,
)

val LocalN = staticCompositionLocalOf { NDark }

/** Dot-matrix face with Cyrillic (MatrixSans Print, SIL OFL) used where Nothing uses Ndot. */
val DotFont = FontFamily(Font(R.font.matrix_print, FontWeight.Normal))

object NType {
    val dotTitle = TextStyle(fontFamily = DotFont, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal)
    val dotHeading = TextStyle(fontFamily = DotFont, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal)
    val dotNumber = TextStyle(fontFamily = DotFont, fontSize = 34.sp, fontWeight = FontWeight.Normal)
    val dotDisplay = TextStyle(fontFamily = DotFont, fontSize = 64.sp, fontWeight = FontWeight.Normal)

    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val meta = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)
    val bodyMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val heading = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
    val caps = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
}

@Composable
fun GlyphTheme(content: @Composable () -> Unit) {
    val n = if (isSystemInDarkTheme()) NDark else NLight
    val scheme = if (n.dark) darkColorScheme(background = n.bg, surface = n.bg, onSurface = n.primary, primary = n.display)
    else lightColorScheme(background = n.bg, surface = n.bg, onSurface = n.primary, primary = n.display)
    CompositionLocalProvider(LocalN provides n) { MaterialTheme(colorScheme = scheme, content = content) }
}

/** Plain screen background. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    Box(modifier.background(LocalN.current.bg)) { content() }
}

/** Flat dark-grey card with big round corners. */
@Composable
fun Modifier.nCard(radius: Dp = 24.dp, raised: Boolean = false, outlined: Boolean = false): Modifier {
    val n = LocalN.current
    val shape = RoundedCornerShape(radius)
    return this.clip(shape)
        .background(if (raised) n.surfaceRaised else n.surface, shape)
        .then(if (outlined) Modifier.border(1.dp, n.borderVisible, shape) else Modifier)
}
