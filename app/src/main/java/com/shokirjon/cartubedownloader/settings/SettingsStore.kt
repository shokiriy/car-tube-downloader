package com.shokirjon.cartubedownloader.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore("cartube_settings")

data class AppSettings(
    val wifiOnly: Boolean = false,
    val audioQuality: String = "K192",
    val videoQuality: String = "P480",
    val folderUri: String? = null,
    val reserveBytes: Long = 1_073_741_824L,
    val autoAdd: Boolean = false
)

class SettingsStore(private val context: Context) {
    private val wifi = booleanPreferencesKey("wifi_only")
    private val audio = stringPreferencesKey("audio_quality")
    private val video = stringPreferencesKey("video_quality")
    private val folder = stringPreferencesKey("folder_uri")
    private val reserveMb = intPreferencesKey("reserve_mb")
    private val autoAdd = booleanPreferencesKey("auto_add")
    val values: Flow<AppSettings> = context.prefs.data.map { p ->
        AppSettings(p[wifi] ?: false, p[audio] ?: "K192", p[video] ?: "P480", p[folder], (p[reserveMb] ?: 1024).coerceAtLeast(1024).toLong() * 1024 * 1024, p[autoAdd] ?: false)
    }
    suspend fun current() = values.first()
    suspend fun setWifiOnly(value: Boolean) { context.prefs.edit { it[wifi] = value } }
    suspend fun setAudio(value: String) { context.prefs.edit { it[audio] = value } }
    suspend fun setVideo(value: String) { context.prefs.edit { it[video] = value } }
    suspend fun setFolder(value: String?) { context.prefs.edit { if (value == null) it.remove(folder) else it[folder] = value } }
    suspend fun setReserveMb(value: Int) { context.prefs.edit { it[reserveMb] = value.coerceAtLeast(1024) } }
    suspend fun setAutoAdd(value: Boolean) { context.prefs.edit { it[autoAdd] = value } }
}
