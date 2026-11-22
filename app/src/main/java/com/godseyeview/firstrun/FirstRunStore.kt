package com.godseyeview.firstrun

import android.content.Context

/** First-run missions, from upstream v0.3.0's firstRunExperience.js
 *  FIRST_RUN_MISSIONS plus the Live Contacts start named in the v2 plan
 *  (Q20). Each mission is a layer preset: the layer ids it switches on.
 *  The layers themselves arrive in Stage 2+; the preset is stored now
 *  and applied to the registry as layers register. */
enum class Mission(val id: String, val layerIds: List<String>) {
    LIVE_CONTACTS("live-contacts", listOf("flights", "ais-live-vessels")),
    SPACE_MISSIONS("space-missions", listOf("rocket-launches")),
    ENVIRONMENTAL("environmental", listOf("earthquakes", "local-firms")),
    EXPLORE("explore", emptyList()),
    ;

    companion object {
        fun fromId(id: String?): Mission =
            entries.firstOrNull { it.id == id } ?: EXPLORE
    }
}

/** First-run state (Q20). Upstream's show policy (firstRunExperience.js):
 *  the launcher returns every session until "Don't show this again" is
 *  ticked. Choosing a mission always persists the choice + its preset;
 *  only the suppress flag stops the picker coming back. Plain
 *  SharedPreferences — nothing here is secret. */
class FirstRunStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShow(): Boolean = !prefs.getBoolean(KEY_SUPPRESSED, false)

    fun chosenMission(): Mission = Mission.fromId(prefs.getString(KEY_MISSION, null))

    fun presetLayerIds(): List<String> = chosenMission().layerIds

    /** ENTER GOD MODE: persist the mission + preset; suppress only when
     *  the "Don't show this again" box was ticked. */
    fun choose(mission: Mission, suppress: Boolean) {
        prefs.edit().apply {
            putString(KEY_MISSION, mission.id)
            if (suppress) putBoolean(KEY_SUPPRESSED, true)
        }.apply()
    }

    /** SKIP: dismisses for this session; suppresses only when ticked. */
    fun dismiss(suppress: Boolean) {
        if (suppress) {
            prefs.edit().putBoolean(KEY_SUPPRESSED, true).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "first_run"
        const val KEY_MISSION = "mission"
        const val KEY_SUPPRESSED = "suppressed"
    }
}
