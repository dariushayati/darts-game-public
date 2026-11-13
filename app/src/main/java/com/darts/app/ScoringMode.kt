package com.darts.app

/** How darts enter a game. CAMERA is currently offered in X01 only. */
enum class ScoringMode {
    MANUAL,
    AUTODARTS,
    CAMERA,
}

/** How often the in-game camera is sampled for detection. Denser is slower/hotter. */
enum class CameraTrackingRate(val intervalMs: Long, val label: String) {
    BATTERY_SAVER(300L, "BATTERY"),
    BALANCED(200L, "BALANCED"),
    HIGH(140L, "HIGH"),
}

fun scoringConfigFor(mode: ScoringMode, autodartsUrl: String): AutodartsConfig? = when (mode) {
    ScoringMode.MANUAL -> null
    ScoringMode.AUTODARTS -> AutodartsConfig(url = autodartsUrl, mode = ScoringMode.AUTODARTS)
    ScoringMode.CAMERA -> AutodartsConfig(mode = ScoringMode.CAMERA)
}

/** Restarts camera calibration after a reset or undo; no-op for manual/Autodarts. */
fun recalibrateCameraIfNeeded(autodarts: AutodartsConfig?) {
    if (autodarts?.mode == ScoringMode.CAMERA) {
        CameraScoringHost.controller?.recalibrate()
    }
}
