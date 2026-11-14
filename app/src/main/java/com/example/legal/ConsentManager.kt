// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.legal

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.dataStore
import com.example.data.DeviceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Manages user legal consent state, document version tracking,
 * and data wiping upon consent withdrawal.
 */
class ConsentManager(
    private val context: Context,
    private val database: DeviceDatabase,
    private val scope: CoroutineScope
) {
    companion object {
        const val CURRENT_LEGAL_DOC_VERSION = "1.1.0"
        val KEY_CONSENT_ACCEPTED = booleanPreferencesKey("legal_consent_accepted")
        val KEY_CONSENT_DOC_VERSION = stringPreferencesKey("legal_consent_doc_version")
        val KEY_CONSENT_TIMESTAMP_UTC = stringPreferencesKey("legal_consent_timestamp_utc")
        val KEY_CONSENT_APP_VERSION = stringPreferencesKey("legal_consent_app_version")
        val KEY_NOTICE_APPLE_ID_SHOWN = booleanPreferencesKey("legal_notice_apple_id_shown")
        val KEY_NOTICE_ANISETTE_SHOWN = booleanPreferencesKey("legal_notice_anisette_shown")
    }

    val isConsentAccepted: StateFlow<Boolean> = context.dataStore.data
        .map { prefs ->
            val accepted = prefs[KEY_CONSENT_ACCEPTED] ?: false
            val docVersion = prefs[KEY_CONSENT_DOC_VERSION]
            // Must be accepted AND match the current legal document version
            accepted && (docVersion == CURRENT_LEGAL_DOC_VERSION)
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val needsReconsent: StateFlow<Boolean> = context.dataStore.data
        .map { prefs ->
            val accepted = prefs[KEY_CONSENT_ACCEPTED] ?: false
            val docVersion = prefs[KEY_CONSENT_DOC_VERSION]
            accepted && (docVersion != CURRENT_LEGAL_DOC_VERSION)
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val acceptedDocVersion: StateFlow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_CONSENT_DOC_VERSION] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val acceptedTimestampUtc: StateFlow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_CONSENT_TIMESTAMP_UTC] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val acceptedAppVersion: StateFlow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_CONSENT_APP_VERSION] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val isAppleIdNoticeShown: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_NOTICE_APPLE_ID_SHOWN] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val isAnisetteNoticeShown: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_NOTICE_ANISETTE_SHOWN] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun acceptConsent(appVersion: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentUtcTimestamp = dateFormat.format(Date())

        context.dataStore.edit { prefs ->
            prefs[KEY_CONSENT_ACCEPTED] = true
            prefs[KEY_CONSENT_DOC_VERSION] = CURRENT_LEGAL_DOC_VERSION
            prefs[KEY_CONSENT_TIMESTAMP_UTC] = currentUtcTimestamp
            prefs[KEY_CONSENT_APP_VERSION] = appVersion
        }
    }

    suspend fun markAppleIdNoticeShown() {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTICE_APPLE_ID_SHOWN] = true
        }
    }

    suspend fun markAnisetteNoticeShown() {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTICE_ANISETTE_SHOWN] = true
        }
    }

    /**
     * Withdraws consent, wipes all local databases, clears datastore preferences,
     * deletes pairing files, and halts background services.
     */
    suspend fun withdrawConsentAndWipeData() {
        withContext(Dispatchers.IO) {
            // 1. Clear database
            database.clearAllTables()

            // 2. Clear datastore
            context.dataStore.edit { prefs ->
                prefs.clear()
            }

            // 3. Clear pairing records, encrypted cert vault, backups & private files
            try {
                listOf("pairing_records", "certificates", "cert_vault", "app_backups").forEach { dirName ->
                    val dir = java.io.File(context.filesDir, dirName)
                    if (dir.exists()) dir.deleteRecursively()
                }
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("signing_workspace_") || file.name.startsWith("signed_") || file.name.startsWith("tweaked_")) {
                        file.deleteRecursively()
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
