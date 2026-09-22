package com.example.data.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val titleBn: String, val titleEn: String) {
    LIGHT("লাইট মোড (Light)", "Light"),
    DARK("ডার্ক মোড (Dark)", "Dark"),
    SYSTEM("সিস্টেম ডিফল্ট (System)", "System")
}

object ThemeManager {
    private const val PREFS_NAME = "mukul_theme_preferences"
    private const val KEY_THEME_MODE = "saved_theme_mode"

    private val _themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
        _themeMode.value = try {
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.LIGHT
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setTheme(context: Context, mode: ThemeMode) = setThemeMode(context, mode)

    fun toggleTheme(context: Context) {
        val next = if (_themeMode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(context, next)
    }
}
