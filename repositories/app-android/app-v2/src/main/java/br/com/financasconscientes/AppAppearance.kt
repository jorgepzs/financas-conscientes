package br.com.financasconscientes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

internal object AppAppearance {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREFERENCES = "app_appearance"
    private const val KEY_MODE = "theme_mode"

    fun selectedMode(context: Context): String = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(KEY_MODE, MODE_LIGHT)
        .orEmpty()
        .takeIf { it in setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK) }
        ?: MODE_LIGHT

    fun saveMode(context: Context, mode: String) {
        require(mode in setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK))
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .apply()
    }

    fun shouldUseNight(mode: String?, systemNight: Boolean): Boolean = when (mode) {
        MODE_DARK -> true
        MODE_LIGHT -> false
        else -> systemNight
    }

    fun contextForMode(base: Context): Context {
        val source = base.resources.configuration
        val systemNight = (source.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val requestedNight = shouldUseNight(selectedMode(base), systemNight)
        val configuration = Configuration(source)
        val nightBits = if (requestedNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        return base.createConfigurationContext(configuration)
    }
}

abstract class ThemeAwareActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppAppearance.contextForMode(newBase))
    }
}
