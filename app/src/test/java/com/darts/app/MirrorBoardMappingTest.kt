package com.darts.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorBoardMappingTest {

    @Test
    fun everyGameTypeMapsToItself() {
        GameType.entries.forEach { game ->
            assertEquals(game, mirrorGameType(game.name))
        }
    }

    @Test
    fun unknownGameTypeFallsBackToTheGenericBoard() {
        assertNull(mirrorGameType("UNKNOWN_GAME"))
    }

    @Test
    fun shieldsUsesTheRichMirrorWhenStateIsPresent() {
        assertTrue(prefersShieldsMirror(GameType.SHIELDS.name, hasState = true))
        assertFalse(prefersShieldsMirror(GameType.SHIELDS.name, hasState = false))
        assertFalse(prefersShieldsMirror(GameType.LAVA.name, hasState = true))
    }
}
