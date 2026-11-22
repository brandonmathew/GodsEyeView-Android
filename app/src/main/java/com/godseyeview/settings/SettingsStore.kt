package com.godseyeview.settings

import android.content.Context

/** Quality modes (plan Q20). AUTO resolves from device specs at runtime;
 *  the others are the user's manual override. */
enum class QualityMode(val id: String) {
    AUTO("auto"),
    HIGH("high"),
    BALANCED("balanced"),
    BATTERY_SAVER("battery_saver"),
    ;

    companion object {
        fun fromId(id: String?): QualityMode =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Stage 2 preferences (plan Q14/Q19/Q20). Plain SharedPreferences for
 *  non-secret values; the Cesium ion token is NOT stored here - it lives
 *  in TokenStore, Keystore-encrypted (plan Q35). The basemap choice stays
 *  in LastViewStore with the rest of the view state. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadQualityMode(): QualityMode = QualityMode.fromId(prefs.getString(KEY_QUALITY, null))

    fun saveQualityMode(mode: QualityMode) {
        prefs.edit().putString(KEY_QUALITY, mode.id).apply()
    }

    fun loadCacheLimitMb(): Int = prefs.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)

    fun saveCacheLimitMb(limitMb: Int) {
        prefs.edit().putInt(KEY_CACHE_LIMIT_MB, limitMb).apply()
    }

    /** The user's update channel (plan Q23); null = not chosen, the
     *  updater's default for this build applies. */
    fun loadUpdateChannel(): com.godseyeview.update.UpdateChannel? =
        when (prefs.getString(KEY_UPDATE_CHANNEL, null)) {
            "STABLE" -> com.godseyeview.update.UpdateChannel.STABLE
            "BETA" -> com.godseyeview.update.UpdateChannel.BETA
            else -> null
        }

    fun saveUpdateChannel(channel: com.godseyeview.update.UpdateChannel) {
        prefs.edit().putString(KEY_UPDATE_CHANNEL, channel.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_QUALITY = "quality_mode"
        const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
        const val KEY_UPDATE_CHANNEL = "update_channel"
        const val DEFAULT_CACHE_LIMIT_MB = 250
    }
}
