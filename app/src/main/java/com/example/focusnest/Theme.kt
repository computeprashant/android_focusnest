package com.example.focusnest

import android.content.Context

/** What is drawn behind the screen. All of it is generated in code. */
enum class Backdrop { STARS, MOUNTAINS, NONE }

/** Colour schemes. Everything is built into the app, so nothing is downloaded. */
enum class AppTheme(
    val label: String,
    val bgTop: Int,
    val bgBottom: Int,
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    val onAccent: Int,
    val surface: Int,
    val track: Int,
    val focusRing: Int,
    val breakRing: Int,
    val backdrop: Backdrop,
    val decor: Int,
    val decorFar: Int,
) {
    NIGHT_SKY(
        label = "Night sky",
        bgTop = 0xFF0B1026.toInt(),
        bgBottom = 0xFF1B2A4A.toInt(),
        text = 0xFFF5F7FF.toInt(),
        secondaryText = 0xFFAAB4D6.toInt(),
        accent = 0xFFFFD27F.toInt(),
        onAccent = 0xFF1B1B1B.toInt(),
        surface = 0xE61F2A48.toInt(),
        track = 0xFF33406A.toInt(),
        focusRing = 0xFFFFD27F.toInt(),
        breakRing = 0xFF7FD1C7.toInt(),
        backdrop = Backdrop.STARS,
        decor = 0xFFFFFFFF.toInt(),
        decorFar = 0x00000000,
    ),
    MOUNTAINS(
        label = "Lovely mountains",
        bgTop = 0xFF7FB7BE.toInt(),
        bgBottom = 0xFFF6E7CB.toInt(),
        text = 0xFF2D3A3A.toInt(),
        secondaryText = 0xFF5C6B6B.toInt(),
        accent = 0xFFE07A5F.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        track = 0xFFD9E4E4.toInt(),
        focusRing = 0xFFE07A5F.toInt(),
        breakRing = 0xFF3D8B7A.toInt(),
        backdrop = Backdrop.MOUNTAINS,
        decor = 0xFF6FA59F.toInt(),
        decorFar = 0xFFA9CFCB.toInt(),
    ),
    CLASSIC(
        label = "Classic light",
        bgTop = 0xFFF7F8FA.toInt(),
        bgBottom = 0xFFF7F8FA.toInt(),
        text = 0xFF1C1F26.toInt(),
        secondaryText = 0xFF6B7280.toInt(),
        accent = 0xFF3B6FD8.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        track = 0xFFE5E7EB.toInt(),
        focusRing = 0xFF3B6FD8.toInt(),
        breakRing = 0xFF2BA784.toInt(),
        backdrop = Backdrop.NONE,
        decor = 0x00000000,
        decorFar = 0x00000000,
    );
}

/** Remembers the chosen theme on the device. */
object ThemeStore {
    private const val PREFS = "app_theme"
    private const val KEY = "theme"

    fun current(ctx: Context): AppTheme {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppTheme.values().firstOrNull { it.name == saved } ?: AppTheme.NIGHT_SKY
    }

    fun save(ctx: Context, theme: AppTheme) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.name).apply()
    }
}
