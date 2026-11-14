// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "droidutools_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_OLED_BLACK = booleanPreferencesKey("is_oled_black_enabled")
        val KEY_ACCENT_COLOR = intPreferencesKey("accent_color_index")
        val KEY_PAIRING_PREFERENCE = stringPreferencesKey("pairing_preference") // "AUTO", "RP_PAIRING", "LOCKDOWND"
        val KEY_USBMUXD_PORT = intPreferencesKey("usbmuxd_port")
        val KEY_AUTO_MOUNT_DDI = booleanPreferencesKey("auto_mount_ddi")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val isOledBlackFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_OLED_BLACK] ?: true // Default to true for A54 Super AMOLED
    }

    val accentColorIndexFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR] ?: 0 // 0: Electric Cyan, 1: Apple Blue, 2: Emerald Green, 3: Neon Purple
    }

    val pairingPreferenceFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAIRING_PREFERENCE] ?: "AUTO"
    }

    val autoMountDdiFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_MOUNT_DDI] ?: true
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOledBlack(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OLED_BLACK] = enabled }
    }

    suspend fun setAccentColorIndex(index: Int) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = index }
    }

    suspend fun setPairingPreference(preference: String) {
        context.dataStore.edit { it[KEY_PAIRING_PREFERENCE] = preference }
    }

    suspend fun setAutoMountDdi(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_MOUNT_DDI] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun wipeAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
