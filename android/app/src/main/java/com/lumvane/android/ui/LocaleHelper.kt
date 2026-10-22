package com.lumvane.android.ui

import android.content.Context
import android.content.res.Configuration
import com.lumvane.android.data.ThemeMode
import java.util.Locale

object LocaleHelper {
    private const val PREFS = "ui_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME = "theme"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun language(c: Context): String = prefs(c).getString(KEY_LANGUAGE, "").orEmpty()

    fun saveLanguage(c: Context, tag: String) = prefs(c).edit().putString(KEY_LANGUAGE, tag).apply()

    fun theme(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs(c).getString(KEY_THEME, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)

    fun saveTheme(c: Context, mode: ThemeMode) = prefs(c).edit().putString(KEY_THEME, mode.name).apply()

    fun wrap(base: Context): Context {
        val tag = language(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
