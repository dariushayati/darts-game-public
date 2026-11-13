package com.darts.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameScreenArchitectureTest {

    private val sourceDir = listOf(
        File("src/main/java/com/darts/app"),
        File("app/src/main/java/com/darts/app"),
    ).firstOrNull { it.isDirectory } ?: File("src/main/java/com/darts/app")

    private fun gameScreens(): List<File> = sourceDir
        .listFiles { file ->
            file.isFile &&
                file.name.endsWith("GameScreen.kt") &&
                file.name != "MirrorGameScreen.kt" &&
                file.name != "JoinGameScreen.kt"
        }
        .orEmpty()
        .sortedBy { it.name }

    @Test
    fun everyGameScreenUsesTheSharedFrame() {
        val screens = gameScreens()
        assertTrue("expected the six game screens, found ${screens.map { it.name }}", screens.size >= 6)
        screens.forEach { file ->
            val source = file.readText()
            assertTrue("${file.name} must use GameScreenFrame", source.contains("GameScreenFrame("))
            assertTrue("${file.name} must use the shared autodarts loop", source.contains("LaunchedAutodartsEffect("))
            assertTrue("${file.name} must use the shared match recorder", source.contains("RecordMatchWhenFinished("))
            assertTrue("${file.name} must use the shared sound helper", source.contains("rememberSoundEffects()"))
            assertFalse("${file.name} must not own KeepScreenOn", source.contains("KeepScreenOn()"))
            assertFalse("${file.name} must not own the mirror host", source.contains("rememberMirrorHost("))
        }
    }

    @Test
    fun sharedFrameOwnsCrossCuttingBehavior() {
        val source = File(sourceDir, "GameScreenFrame.kt").readText()
        assertTrue(source.contains("KeepScreenOn()"))
        assertTrue(source.contains("rememberMirrorHost("))
        assertTrue(source.contains("MirrorHostOverlays("))
        assertTrue(source.contains("BackHandler("))
    }

    @Test
    fun autoSharePreferenceIsWiredToSetupAndGameStart() {
        val setupBar = File(sourceDir, "SetupBottomBar.kt").readText()
        assertTrue(setupBar.contains("loadShareEnabled()"))
        assertTrue(setupBar.contains("saveShareEnabled("))
        assertTrue(setupBar.contains("SECOND SCREEN"))

        val frame = File(sourceDir, "GameScreenFrame.kt").readText()
        assertTrue(frame.contains("loadShareEnabled()"))
        assertTrue(frame.contains("mirror.start(context)"))
    }

    @Test
    fun inGameCameraKeepsTheHeadlessAnalysisBinding() {
        val source = File(sourceDir, "CameraScoringOverlay.kt").readText()
        assertTrue(
            "only create a Preview when the dialog supplies a PreviewView",
            source.contains("val preview = previewView?.let { view ->"),
        )
        assertTrue(
            "headless camera must bind ImageAnalysis without a Preview",
            Regex(
                """else\s*\{\s*provider\.bindToLifecycle\(\s*lifecycleOwner,\s*""" +
                    """CameraSelector\.DEFAULT_BACK_CAMERA,\s*analysis,\s*\)""",
            ).containsMatchIn(source),
        )
        assertFalse(
            "must not keep a Preview bound when the preview dialog is closed",
            source.contains("remember { Preview.Builder().build() }"),
        )
    }

}
