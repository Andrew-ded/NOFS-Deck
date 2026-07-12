package com.nofs.desk.data

import android.content.Context
import android.content.SharedPreferences

/** Настройки подключения и панели. Демо-режим по умолчанию — панель живая из коробки. */
data class DeskSettings(
    val demoMode: Boolean = true,
    val host: String = "192.168.1.100",
    val port: Int = 48484,
    /** Минут бездействия до скринсейвера (чёрный экран с часами); 0 — выключен. */
    val screensaverMinutes: Int = 10
)

object SettingsStore {
    private const val PREFS = "nofs_settings"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): DeskSettings {
        val p = prefs(context)
        return DeskSettings(
            demoMode = p.getBoolean("demoMode", true),
            host = p.getString("host", "192.168.1.100") ?: "192.168.1.100",
            port = p.getInt("port", 48484),
            screensaverMinutes = p.getInt("saverMin", 10)
        )
    }

    fun save(context: Context, settings: DeskSettings) {
        prefs(context).edit()
            .putBoolean("demoMode", settings.demoMode)
            .putString("host", settings.host)
            .putInt("port", settings.port)
            .putInt("saverMin", settings.screensaverMinutes)
            .apply()
    }
}
