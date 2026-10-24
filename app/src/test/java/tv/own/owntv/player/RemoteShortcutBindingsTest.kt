package tv.own.owntv.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutBinding
import tv.own.owntv.core.settings.RemoteShortcutBindings
import tv.own.owntv.core.settings.RemoteShortcutPress

class RemoteShortcutBindingsTest {
    @Test
    fun defaultsPreserveCurrentBrowsePaging() {
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_UP, RemoteShortcutPress.SHORT, RemoteShortcutAction.PAGE_TOWARD_FIRST) in RemoteShortcutBindings.defaults)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_DOWN, RemoteShortcutPress.LONG, RemoteShortcutAction.JUMP_TO_LAST) in RemoteShortcutBindings.defaults)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_MEDIA_REWIND, RemoteShortcutPress.LONG, RemoteShortcutAction.JUMP_TO_FIRST) in RemoteShortcutBindings.defaults)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, RemoteShortcutPress.SHORT, RemoteShortcutAction.PAGE_TOWARD_LAST) in RemoteShortcutBindings.defaults)
    }

    @Test
    fun bindingsRoundTripAndIgnoreMalformedValues() {
        val expected = listOf(
            RemoteShortcutBinding(KeyEvent.KEYCODE_PROG_RED, RemoteShortcutPress.SHORT, RemoteShortcutAction.OPEN_GUIDE),
            RemoteShortcutBinding(KeyEvent.KEYCODE_INFO, RemoteShortcutPress.LONG, RemoteShortcutAction.TOGGLE_PLAYBACK_INFO),
        )

        assertEquals(expected.toSet(), RemoteShortcutBindings.decode(RemoteShortcutBindings.encode(expected)).toSet())
        assertTrue(RemoteShortcutBindings.decode(setOf("bad", "1|NOPE|OPEN_HOME")).isEmpty())
    }

    @Test
    fun replacementChangesOnlyMatchingButtonAndPress() {
        val replacement = RemoteShortcutBinding(
            KeyEvent.KEYCODE_CHANNEL_UP,
            RemoteShortcutPress.SHORT,
            RemoteShortcutAction.OPEN_GUIDE,
        )
        val result = RemoteShortcutBindings.replace(RemoteShortcutBindings.defaults, replacement)

        assertTrue(replacement in result)
        assertFalse(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_UP, RemoteShortcutPress.SHORT, RemoteShortcutAction.PAGE_TOWARD_FIRST) in result)
        assertTrue(RemoteShortcutBinding(KeyEvent.KEYCODE_CHANNEL_UP, RemoteShortcutPress.LONG, RemoteShortcutAction.JUMP_TO_FIRST) in result)
    }

    @Test
    fun essentialNavigationIsProtectedButNumberAndSpareKeysAreAssignable() {
        assertTrue(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_BACK))
        assertTrue(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_7))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_NUMPAD_7))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_PROG_BLUE))
        assertFalse(RemoteShortcutBindings.isProtectedKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
