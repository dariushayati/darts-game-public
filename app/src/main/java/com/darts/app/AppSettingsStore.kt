package com.darts.app

import android.content.Context
import androidx.core.content.edit

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("darts_settings", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("darts_rules", Context.MODE_PRIVATE)

    fun loadSoundEnabled(): Boolean {
        if (prefs.contains(KEY_SOUND)) return prefs.getBoolean(KEY_SOUND, true)
        val migrated = legacy.getBoolean(KEY_SOUND, true)
        prefs.edit { putBoolean(KEY_SOUND, migrated) }
        return migrated
    }

    fun saveSoundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOUND, enabled) }
    }

    fun loadVoiceCalloutsEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_CALLOUTS, true)

    fun saveVoiceCalloutsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VOICE_CALLOUTS, enabled) }
    }

    fun loadAutodartsEnabled(): Boolean = prefs.getBoolean(KEY_AUTODARTS_ENABLED, false)

    fun saveAutodartsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTODARTS_ENABLED, enabled) }
    }

    fun loadAutodartsUrl(): String =
        prefs.getString(KEY_AUTODARTS_URL, DEFAULT_AUTODARTS_URL)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_AUTODARTS_URL

    fun saveAutodartsUrl(url: String) {
        prefs.edit { putString(KEY_AUTODARTS_URL, url) }
    }

    fun loadScoringMode(): ScoringMode =
        prefs.getString(KEY_SCORING_MODE, null)
            ?.let { runCatching { ScoringMode.valueOf(it) }.getOrNull() }
            ?: if (loadAutodartsEnabled()) ScoringMode.AUTODARTS else ScoringMode.MANUAL

    fun saveScoringMode(mode: ScoringMode) {
        prefs.edit {
            putString(KEY_SCORING_MODE, mode.name)
            // Keep the old flag in step for older code paths and migration.
            putBoolean(KEY_AUTODARTS_ENABLED, mode == ScoringMode.AUTODARTS)
        }
    }

    fun loadThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
    }

    fun loadShareEnabled(): Boolean = prefs.getBoolean(KEY_SHARE_ENABLED, false)

    fun saveShareEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHARE_ENABLED, enabled) }
    }

    fun loadMirrorAddress(): String = prefs.getString(KEY_MIRROR_ADDRESS, "") ?: ""

    fun saveMirrorAddress(value: String) {
        prefs.edit { putString(KEY_MIRROR_ADDRESS, value) }
    }

    fun loadMirrorCode(): String = prefs.getString(KEY_MIRROR_CODE, "") ?: ""

    fun saveMirrorCode(value: String) {
        prefs.edit { putString(KEY_MIRROR_CODE, value) }
    }

    fun loadAutoScoreSoftBoard(): Boolean = prefs.getBoolean(KEY_AUTO_SCORE_SOFT_BOARD, false)

    fun saveAutoScoreSoftBoard(softBoard: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SCORE_SOFT_BOARD, softBoard) }
    }

    fun loadAutoScoreForceCpu(): Boolean = prefs.getBoolean(KEY_AUTO_SCORE_FORCE_CPU, false)

    fun saveAutoScoreForceCpu(forceCpu: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SCORE_FORCE_CPU, forceCpu) }
    }

    fun loadAutoScoreDebugImages(): Boolean =
        prefs.getBoolean(KEY_AUTO_SCORE_DEBUG_IMAGES, false)

    fun saveAutoScoreDebugImages(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SCORE_DEBUG_IMAGES, enabled) }
    }

    fun loadCameraZoom(): Float =
        prefs.getFloat(KEY_CAMERA_ZOOM, 1f).takeIf { it > 0f } ?: 1f

    fun saveCameraZoom(ratio: Float) {
        if (ratio > 0f) prefs.edit { putFloat(KEY_CAMERA_ZOOM, ratio) }
    }

    fun loadCameraTrackingRate(): CameraTrackingRate =
        prefs.getString(KEY_CAMERA_TRACKING_RATE, null)
            ?.let { runCatching { CameraTrackingRate.valueOf(it) }.getOrNull() }
            ?: CameraTrackingRate.BALANCED

    fun saveCameraTrackingRate(rate: CameraTrackingRate) {
        prefs.edit { putString(KEY_CAMERA_TRACKING_RATE, rate.name) }
    }

    private companion object {
        const val KEY_SOUND = "soundEnabled"
        const val KEY_VOICE_CALLOUTS = "voiceCalloutsEnabled"
        const val KEY_AUTODARTS_ENABLED = "autodartsEnabled"
        const val KEY_AUTODARTS_URL = "autodartsUrl"
        const val KEY_SCORING_MODE = "scoringMode"
        const val KEY_THEME = "themeMode"
        const val KEY_SHARE_ENABLED = "shareEnabled"
        const val KEY_MIRROR_ADDRESS = "mirrorAddress"
        const val KEY_MIRROR_CODE = "mirrorCode"
        const val KEY_AUTO_SCORE_SOFT_BOARD = "autoScoreSoftBoard"
        const val KEY_AUTO_SCORE_FORCE_CPU = "autoScoreForceCpu"
        const val KEY_AUTO_SCORE_DEBUG_IMAGES = "autoScoreDebugImages"
        const val KEY_CAMERA_ZOOM = "cameraZoom"
        const val KEY_CAMERA_TRACKING_RATE = "cameraTrackingRate"
    }
}
