package com.godseyeview.core.globe

import android.content.Context

/** Persists the last camera view (plan Q18). Plain preferences are fine here:
 *  a camera position is not a secret. Tokens use the Keystore, never this. */
class LastViewStore(context: Context) {
    private val prefs = context.getSharedPreferences("globe_state", Context.MODE_PRIVATE)

    fun load(): CameraViewState? =
        prefs.getString(KEY_VIEW, null)?.let { CameraViewState.fromJson(it) }

    fun save(view: CameraViewState) {
        prefs.edit().putString(KEY_VIEW, view.toJson()).apply()
    }

    fun loadBasemap(): Basemap = Basemap.fromId(prefs.getString(KEY_BASEMAP, null))

    fun saveBasemap(basemap: Basemap) {
        prefs.edit().putString(KEY_BASEMAP, basemap.id).apply()
    }

    private companion object {
        const val KEY_VIEW = "last_view"
        const val KEY_BASEMAP = "basemap"
    }
}
