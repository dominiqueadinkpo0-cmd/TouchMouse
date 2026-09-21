package com.touchmouse

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "touchmouse")

object PrefsKeys {
    val SENSITIVITY = floatPreferencesKey("sensitivity") // 0.5 .. 3.0
    val PAD_SIZE = intPreferencesKey("pad_size") // 120..280 dp
    val PAD_ALPHA = floatPreferencesKey("pad_alpha") // 0.2 .. 1.0
    val PAD_POSITION = stringPreferencesKey("pad_position") // bottom_left, bottom_right, top_left, top_right, custom
    val CURSOR_SIZE = intPreferencesKey("cursor_size")
    val DWELL_CLICK = booleanPreferencesKey("dwell_click")
    val DWELL_TIME = intPreferencesKey("dwell_time") // ms
    val VOLUME_CONTROL = booleanPreferencesKey("volume_control")
    val GYRO_CONTROL = booleanPreferencesKey("gyro_control")
    val WHISPER_ENABLED = booleanPreferencesKey("whisper_enabled")
    val WHISPER_MODE = stringPreferencesKey("whisper_mode") // offline_google, whisper_api
    val WHISPER_API_KEY = stringPreferencesKey("whisper_api_key")
    val WHISPER_LANGUAGE = stringPreferencesKey("whisper_language") // fr, en, auto
    val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
    val PAD_X = intPreferencesKey("pad_x")
    val PAD_Y = intPreferencesKey("pad_y")
}

data class AppPrefs(
    val sensitivity: Float = 2.0f,
    val padSize: Int = 160,
    val padAlpha: Float = 0.65f,
    val padPosition: String = "middle_left",
    val cursorSize: Int = 28,
    val dwellClick: Boolean = false,
    val dwellTime: Int = 800,
    val volumeControl: Boolean = true,
    val gyroControl: Boolean = false,
    val whisperEnabled: Boolean = true,
    val whisperMode: String = "offline_google",
    val whisperApiKey: String = "",
    val whisperLanguage: String = "fr",
    val overlayEnabled: Boolean = false,
    val padX: Int = -1,
    val padY: Int = -1,
)

fun Context.prefsFlow() = dataStore.data.map { p ->
    AppPrefs(
        sensitivity = p[PrefsKeys.SENSITIVITY] ?: 2.0f,
        padSize = p[PrefsKeys.PAD_SIZE] ?: 160,
        padAlpha = p[PrefsKeys.PAD_ALPHA] ?: 0.65f,
        padPosition = p[PrefsKeys.PAD_POSITION] ?: "middle_left",
        cursorSize = p[PrefsKeys.CURSOR_SIZE] ?: 28,
        dwellClick = p[PrefsKeys.DWELL_CLICK] ?: false,
        dwellTime = p[PrefsKeys.DWELL_TIME] ?: 800,
        volumeControl = p[PrefsKeys.VOLUME_CONTROL] ?: true,
        gyroControl = p[PrefsKeys.GYRO_CONTROL] ?: false,
        whisperEnabled = p[PrefsKeys.WHISPER_ENABLED] ?: true,
        whisperMode = p[PrefsKeys.WHISPER_MODE] ?: "offline_google",
        whisperApiKey = p[PrefsKeys.WHISPER_API_KEY] ?: "",
        whisperLanguage = p[PrefsKeys.WHISPER_LANGUAGE] ?: "fr",
        overlayEnabled = p[PrefsKeys.OVERLAY_ENABLED] ?: false,
        padX = p[PrefsKeys.PAD_X] ?: -1,
        padY = p[PrefsKeys.PAD_Y] ?: -1,
    )
}
