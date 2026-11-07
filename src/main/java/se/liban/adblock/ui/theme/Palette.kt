package se.liban.adblock.ui.theme

/**
 * Fixed light and dark colors for the app.
 * Text pairs are checked in unit tests so both themes stay readable.
 */
object AdBlockPalette {
    const val GREEN = 0xFF146C43
    const val GREEN_DARK = 0xFF0C3B26
    const val MINT = 0xFF8FD9B0
    const val MINT_CONTAINER = 0xFFD7F5E6
    const val WHITE = 0xFFFFFFFF

    const val LIGHT_BACKGROUND = 0xFFF4F7F5
    const val LIGHT_ON_BACKGROUND = 0xFF1A1C1B
    const val LIGHT_SURFACE_CONTAINER = WHITE
    const val LIGHT_ON_SURFACE_VARIANT = 0xFF3E5148
    const val LIGHT_OUTLINE = 0xFF5E6E66
    const val LIGHT_OUTLINE_VARIANT = 0xFFB7C4BD
    const val LIGHT_SURFACE_VARIANT = 0xFFE4EEE8

    const val DARK_BACKGROUND = 0xFF0E1512
    const val DARK_ON_BACKGROUND = 0xFFE6F0EB
    const val DARK_SURFACE_CONTAINER = 0xFF18211D
    const val DARK_ON_SURFACE_VARIANT = 0xFFB7C7BE
    const val DARK_OUTLINE = 0xFF8A9A92
    const val DARK_OUTLINE_VARIANT = 0xFF3E5148
    const val DARK_SURFACE_VARIANT = 0xFF24302A

    val textPairs: List<Pair<Long, Long>> = listOf(
        LIGHT_ON_BACKGROUND to LIGHT_BACKGROUND,
        LIGHT_ON_BACKGROUND to LIGHT_SURFACE_CONTAINER,
        LIGHT_ON_SURFACE_VARIANT to LIGHT_BACKGROUND,
        LIGHT_ON_SURFACE_VARIANT to LIGHT_SURFACE_CONTAINER,
        WHITE to GREEN,
        GREEN_DARK to MINT_CONTAINER,
        DARK_ON_BACKGROUND to DARK_BACKGROUND,
        DARK_ON_BACKGROUND to DARK_SURFACE_CONTAINER,
        DARK_ON_SURFACE_VARIANT to DARK_BACKGROUND,
        DARK_ON_SURFACE_VARIANT to DARK_SURFACE_CONTAINER,
        GREEN_DARK to MINT,
        MINT_CONTAINER to GREEN_DARK
    )

    val borderPairs: List<Pair<Long, Long>> = listOf(
        LIGHT_OUTLINE to LIGHT_BACKGROUND,
        LIGHT_OUTLINE to LIGHT_SURFACE_CONTAINER,
        DARK_OUTLINE to DARK_BACKGROUND,
        DARK_OUTLINE to DARK_SURFACE_CONTAINER
    )
}

fun contrastRatio(foreground: Long, background: Long): Double {
    val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
    val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    fun channel(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    val rgb = argb.toInt()
    val red = channel((rgb shr 16) and 0xFF)
    val green = channel((rgb shr 8) and 0xFF)
    val blue = channel(rgb and 0xFF)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}
