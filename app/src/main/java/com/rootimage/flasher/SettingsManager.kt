package com.rootimage.flasher

import android.content.Context

object SettingsManager {

    private const val PREFS = "flasher_settings"
    private const val UNLOCKED = "protected_unlocked"

    fun isUnlocked(context: Context): Boolean {
        return context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getBoolean(UNLOCKED, false)
    }

    fun setUnlocked(context: Context, value: Boolean) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(UNLOCKED, value)
            .apply()
    }
}
