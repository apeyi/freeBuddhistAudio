package com.fba.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User settings shown on My FBA. Backed by SharedPreferences, exposed as flows. */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _englishOnly = MutableStateFlow(prefs.getBoolean(KEY_ENGLISH_ONLY, true))
    /** Hide talks and lists in languages other than English (default on). */
    val englishOnly: StateFlow<Boolean> = _englishOnly

    private val _preferRemastered = MutableStateFlow(prefs.getBoolean(KEY_PREFER_REMASTERED, true))
    /** Play the remastered version when a talk has one (default on). */
    val preferRemastered: StateFlow<Boolean> = _preferRemastered

    fun setEnglishOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENGLISH_ONLY, value).apply()
        _englishOnly.value = value
    }

    fun setPreferRemastered(value: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_REMASTERED, value).apply()
        _preferRemastered.value = value
    }

    /** Per-talk override of the remastered/original choice; null = follow the setting. */
    fun remasterChoice(catNum: String): Boolean? =
        if (prefs.contains("remaster_$catNum")) prefs.getBoolean("remaster_$catNum", true) else null

    fun setRemasterChoice(catNum: String, useRemaster: Boolean) {
        prefs.edit().putBoolean("remaster_$catNum", useRemaster).apply()
    }

    /** Resolved choice for a talk: explicit per-talk choice, else the global default. */
    fun useRemaster(catNum: String): Boolean = remasterChoice(catNum) ?: preferRemastered.value

    companion object {
        private const val KEY_ENGLISH_ONLY = "english_only"
        private const val KEY_PREFER_REMASTERED = "prefer_remastered"
    }
}
