package com.jetsetter.pro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jetsetter.pro.R

// Brand display face: Plus Jakarta Sans (OFL), a geometric humanist sans that echoes the iOS
// `.rounded` display look. Bundled as a single variable TTF; each weight is one named Font with
// a matching wght variation (minSdk 26 supports font variation settings). Body text stays on the
// system face (Roboto) for maximum legibility at small sizes.
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Rounded = FontFamily(jakarta(600), jakarta(700), jakarta(800))
private val Default = FontFamily.Default

/**
 * Named text styles ported from `JetsetterTheme.Typography`, tuned for a premium hierarchy:
 * tight negative tracking + generous leading on big display/metric type, slightly positive
 * tracking on small/label text, and the Plus Jakarta Sans brand face on all headings.
 */
@Immutable
data class JetTypography(
    val heroTitle: TextStyle = TextStyle(fontFamily = Rounded, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp),
    val displayTitle: TextStyle = TextStyle(fontFamily = Rounded, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight(700), letterSpacing = (-0.4).sp),
    val pageTitle: TextStyle = TextStyle(fontFamily = Rounded, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight(700), letterSpacing = (-0.2).sp),
    val cardTitle: TextStyle = TextStyle(fontFamily = Rounded, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight(600), letterSpacing = (-0.1).sp),
    val bodyMedium: TextStyle = TextStyle(fontFamily = Default, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    val metric: TextStyle = TextStyle(fontFamily = Rounded, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp),
    val label: TextStyle = TextStyle(fontFamily = Rounded, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(600), letterSpacing = 0.6.sp),
    val caption: TextStyle = TextStyle(fontFamily = Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

val LocalJetTypography = staticCompositionLocalOf { JetTypography() }

/** Material 3 type scale mapped onto the JetSetter sizes (used by stock M3 components). */
val JetMaterialTypography = Typography(
    titleLarge = TextStyle(fontFamily = Rounded, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight(700), letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight(600), letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Default, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = Default, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(600), letterSpacing = 0.6.sp),
)
