package com.example.pricelist.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.pricelist.ui.theme.Purple40
import com.example.pricelist.ui.theme.Teal40

// Define your color scheme using Material 3's lightColorScheme
private val LightColors = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    secondary = Teal40,
    onSecondary = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun PriceListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
