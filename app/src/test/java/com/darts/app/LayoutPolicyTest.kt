package com.darts.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPolicyTest {
    @Test
    fun `phones never use two pane`() {
        assertFalse(LayoutPolicy.isTablet(400))
        assertFalse(LayoutPolicy.useTwoPane(400, widthDp = 500f, heightDp = 800f))
    }

    @Test
    fun `tablet portrait stays stacked`() {
        assertTrue(LayoutPolicy.isTablet(800))
        assertFalse(LayoutPolicy.useTwoPane(800, widthDp = 800f, heightDp = 1280f))
    }

    @Test
    fun `tablet landscape uses two pane`() {
        assertTrue(LayoutPolicy.useTwoPane(1280, widthDp = 1280f, heightDp = 800f))
        assertTrue(LayoutPolicy.useTwoPane(400, widthDp = 1000f, heightDp = 600f))
    }

    @Test
    fun `small tablet landscape uses two pane`() {
        assertTrue(LayoutPolicy.useTwoPane(600, widthDp = 800f, heightDp = 600f))
        assertTrue(LayoutPolicy.useTwoPane(600, widthDp = 600f, heightDp = 400f))
    }

    @Test
    fun `panel width clamps between 320 and 420`() {
        assertEquals(320f, LayoutPolicy.panelWidthDp(800f), 0.01f)
        assertEquals(389.12f, LayoutPolicy.panelWidthDp(1024f), 0.01f)
        assertEquals(420f, LayoutPolicy.panelWidthDp(1280f), 0.01f)
    }

    @Test
    fun `content max widths`() {
        assertEquals(720f, LayoutPolicy.MENU_MAX_WIDTH_DP, 0f)
        assertEquals(640f, LayoutPolicy.CONTENT_MAX_WIDTH_DP, 0f)
    }
}
