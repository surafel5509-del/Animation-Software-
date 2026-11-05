package com.animationstudio.coreui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StudioBackground = Color(0xff111318)
val StudioSurface = Color(0xff191c22)
val StudioPanel = Color(0xff20242c)
val StudioAccent = Color(0xff8ab4ff)
val StudioMint = Color(0xff75d5c3)

private val ProfessionalDark = darkColorScheme(
    primary = StudioAccent,
    onPrimary = Color(0xff0b254d),
    primaryContainer = Color(0xff263b5c),
    onPrimaryContainer = Color(0xffd7e5ff),
    secondary = StudioMint,
    background = StudioBackground,
    onBackground = Color(0xffe4e7ed),
    surface = StudioSurface,
    onSurface = Color(0xffe4e7ed),
    surfaceVariant = StudioPanel,
    onSurfaceVariant = Color(0xffbdc3cf),
    outline = Color(0xff3a404c),
    error = Color(0xffffb4ab),
)

@Composable
fun AnimationStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ProfessionalDark, typography = Typography(), content = content)
}
