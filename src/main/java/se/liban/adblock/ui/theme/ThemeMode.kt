package se.liban.adblock.ui.theme

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    fun resolveDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDarkTheme
    }

    companion object {
        fun fromName(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
