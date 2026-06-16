package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("comfypad_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_DEFAULT_STEPS = "default_steps"
        const val KEY_DEFAULT_CFG = "default_cfg"
        const val KEY_DEFAULT_WIDTH = "default_width"
        const val KEY_DEFAULT_HEIGHT = "default_height"
        const val KEY_THEME = "app_theme" // "dark", "light", "system"
        const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
    }

    var serverIp: String
        get() = prefs.getString(KEY_SERVER_IP, "192.168.1.100") ?: "192.168.1.100"
        set(value) = prefs.edit().putString(KEY_SERVER_IP, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, 8188)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var defaultSteps: Int
        get() = prefs.getInt(KEY_DEFAULT_STEPS, 20)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_STEPS, value).apply()

    var defaultCfg: Float
        get() = prefs.getFloat(KEY_DEFAULT_CFG, 7.0f)
        set(value) = prefs.edit().putFloat(KEY_DEFAULT_CFG, value).apply()

    var defaultWidth: Int
        get() = prefs.getInt(KEY_DEFAULT_WIDTH, 512)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_WIDTH, value).apply()

    var defaultHeight: Int
        get() = prefs.getInt(KEY_DEFAULT_HEIGHT, 512)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_HEIGHT, value).apply()

    var appTheme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var saveToGallery: Boolean
        get() = prefs.getBoolean(KEY_SAVE_TO_GALLERY, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_TO_GALLERY, value).apply()
}
