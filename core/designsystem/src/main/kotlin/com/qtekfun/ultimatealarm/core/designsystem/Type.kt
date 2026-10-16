package com.qtekfun.ultimatealarm.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Bricolage Grotesque for times and headings, DM Sans for text (both SIL OFL 1.1, bundled: no font downloads). */
private fun variable(res: Int, weight: FontWeight) = Font(res, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Display = FontFamily(
    variable(R.font.bricolage_grotesque, FontWeight.Normal),
    variable(R.font.bricolage_grotesque, FontWeight.Medium),
    variable(R.font.bricolage_grotesque, FontWeight.SemiBold),
    variable(R.font.bricolage_grotesque, FontWeight.Bold)
)

val Body = FontFamily(
    variable(R.font.dm_sans, FontWeight.Normal),
    variable(R.font.dm_sans, FontWeight.Medium),
    variable(R.font.dm_sans, FontWeight.SemiBold),
    variable(R.font.dm_sans, FontWeight.Bold)
)

/** A time on its own ("7:00"): the display face, tight, with tabular figures so it does not jump as it changes. */
fun timeStyle(size: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = Display,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = size.sp,
    letterSpacing = (-size / TIME_TRACKING).sp,
    fontFeatureSettings = "tnum"
)

internal fun amanecerTypography(): Typography {
    val base = Typography()
    fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(fontFamily = Display, fontWeight = weight)
    fun TextStyle.body(weight: FontWeight? = null) = copy(fontFamily = Body, fontWeight = weight ?: fontWeight)
    return base.copy(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(FontWeight.Bold),
        headlineMedium = base.headlineMedium.display(FontWeight.Bold),
        headlineSmall = base.headlineSmall.display(FontWeight.Bold),
        titleLarge = base.titleLarge.display(),
        titleMedium = base.titleMedium.body(FontWeight.SemiBold),
        titleSmall = base.titleSmall.body(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(FontWeight.SemiBold),
        labelMedium = base.labelMedium.body(FontWeight.SemiBold),
        labelSmall = base.labelSmall.body(FontWeight.Medium)
    )
}

private const val TIME_TRACKING = 30f
