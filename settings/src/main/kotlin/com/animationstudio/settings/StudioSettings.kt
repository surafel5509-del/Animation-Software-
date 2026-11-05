package com.animationstudio.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("studio_settings")

data class WorkspacePreferences(
    val gridEnabled: Boolean = true,
    val checkerboardEnabled: Boolean = true,
    val onionSkinEnabled: Boolean = true,
    val onionBefore: Int = 2,
    val onionAfter: Int = 1,
    val onionOpacity: Float = .22f,
    val autosaveIntervalSeconds: Int = 30,
    val previewQuality: Float = 1f,
)

class StudioSettings(private val context: Context) {
    val preferences: Flow<WorkspacePreferences> = context.dataStore.data.map { values ->
        WorkspacePreferences(
            gridEnabled = values[GRID] ?: true,
            checkerboardEnabled = values[CHECKERBOARD] ?: true,
            onionSkinEnabled = values[ONION] ?: true,
            onionBefore = values[ONION_BEFORE] ?: 2,
            onionAfter = values[ONION_AFTER] ?: 1,
            onionOpacity = values[ONION_OPACITY] ?: .22f,
            autosaveIntervalSeconds = values[AUTOSAVE] ?: 30,
            previewQuality = values[QUALITY] ?: 1f,
        )
    }

    suspend fun update(transform: (WorkspacePreferences) -> WorkspacePreferences) {
        // Preference writes are individually atomic; the editor reads the latest flow value before updating.
        context.dataStore.edit { values ->
            val current = WorkspacePreferences(
                values[GRID] ?: true, values[CHECKERBOARD] ?: true, values[ONION] ?: true,
                values[ONION_BEFORE] ?: 2, values[ONION_AFTER] ?: 1, values[ONION_OPACITY] ?: .22f,
                values[AUTOSAVE] ?: 30, values[QUALITY] ?: 1f,
            )
            val updated = transform(current)
            values[GRID] = updated.gridEnabled; values[CHECKERBOARD] = updated.checkerboardEnabled
            values[ONION] = updated.onionSkinEnabled; values[ONION_BEFORE] = updated.onionBefore.coerceIn(0, 12)
            values[ONION_AFTER] = updated.onionAfter.coerceIn(0, 12); values[ONION_OPACITY] = updated.onionOpacity.coerceIn(0f, 1f)
            values[AUTOSAVE] = updated.autosaveIntervalSeconds.coerceIn(10, 300); values[QUALITY] = updated.previewQuality.coerceIn(.25f, 1f)
        }
    }

    private companion object {
        val GRID = booleanPreferencesKey("grid")
        val CHECKERBOARD = booleanPreferencesKey("checkerboard")
        val ONION = booleanPreferencesKey("onion")
        val ONION_BEFORE = intPreferencesKey("onion_before")
        val ONION_AFTER = intPreferencesKey("onion_after")
        val ONION_OPACITY = floatPreferencesKey("onion_opacity")
        val AUTOSAVE = intPreferencesKey("autosave_seconds")
        val QUALITY = floatPreferencesKey("preview_quality")
    }
}
