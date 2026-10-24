package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.core.settings.SubtitleStyle.Position

/**
 * The subtitle-anchor mapping the three renderers agree on — mpv's own OSD, Media3's `SubtitleView`
 * and the Compose overlay drawn over mpv's direct-render path.
 *
 * Pinned before any work on subtitle geometry: the mapping is currently applied in several places, and
 * a consolidation is only safe if "the anchor a position resolves to" is something a test states rather
 * than something each call site re-derives. The "Default" values matter as much as the anchors — every
 * option's default means *leave this renderer alone*, which is what keeps the master toggle from
 * changing anything on its own, including broadcaster-styled Live TV cues.
 */
class SubtitleStyleTest {

    @Test
    fun `every option has a default that means leave this alone`() {
        assertFalse(SubtitleStyle.hasScale(SubtitleStyle.SCALE_DEFAULT))
        assertFalse(SubtitleStyle.hasColor(SubtitleStyle.COLOR_DEFAULT))
        assertFalse(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_DEFAULT))
        assertEquals(Position.DEFAULT, Position.fromKey(null))
        assertEquals(Position.DEFAULT, Position.fromKey("nonsense"))
    }

    @Test
    fun `a picked option is recognised as picked`() {
        assertTrue(SubtitleStyle.hasScale(1.5f))
        assertTrue(SubtitleStyle.hasColor("#FFEE00"))
        assertTrue(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_MIN)) // 0 = "no box", a real choice
        assertTrue(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_START))
    }

    @Test
    fun `the picker offers six anchors in reading order and every key round-trips`() {
        assertEquals(
            listOf(
                Position.TOP_LEFT, Position.TOP_CENTER, Position.TOP_RIGHT,
                Position.BOTTOM_LEFT, Position.BOTTOM_CENTER, Position.BOTTOM_RIGHT,
            ),
            Position.ANCHORS,
        )
        for (p in Position.entries) assertEquals(p, Position.fromKey(p.key))
    }

    @Test
    fun `top and bottom anchors are classified consistently`() {
        assertTrue(Position.TOP_LEFT.isTop && Position.TOP_CENTER.isTop && Position.TOP_RIGHT.isTop)
        assertFalse(Position.BOTTOM_LEFT.isTop || Position.BOTTOM_CENTER.isTop || Position.BOTTOM_RIGHT.isTop)
        // "Default" is not top, so it falls to the renderer's own placement, which is the bottom.
        assertFalse(Position.DEFAULT.isTop)
    }

    @Test
    fun `left and right are the outer columns only — centre is neither`() {
        assertTrue(Position.TOP_LEFT.isLeft && Position.BOTTOM_LEFT.isLeft)
        assertTrue(Position.TOP_RIGHT.isRight && Position.BOTTOM_RIGHT.isRight)
        assertFalse(Position.TOP_CENTER.isLeft || Position.TOP_CENTER.isRight)
        assertFalse(Position.BOTTOM_CENTER.isLeft || Position.BOTTOM_CENTER.isRight)
        assertFalse(Position.DEFAULT.isLeft || Position.DEFAULT.isRight)
    }

    @Test
    fun `mpv sub-pos is a percentage of height, bottom by default`() {
        assertEquals(SubtitleStyle.MPV_POS_BOTTOM, SubtitleStyle.mpvSubPos(Position.DEFAULT))
        assertEquals(SubtitleStyle.MPV_POS_BOTTOM, SubtitleStyle.mpvSubPos(Position.BOTTOM_CENTER))
        assertEquals(SubtitleStyle.MPV_POS_TOP, SubtitleStyle.mpvSubPos(Position.TOP_CENTER))
        assertEquals(SubtitleStyle.MPV_POS_TOP, SubtitleStyle.mpvSubPos(Position.TOP_RIGHT))
    }

    @Test
    fun `mpv horizontal alignment follows the anchor column`() {
        assertEquals("left", SubtitleStyle.mpvAlignX(Position.BOTTOM_LEFT))
        assertEquals("right", SubtitleStyle.mpvAlignX(Position.TOP_RIGHT))
        assertEquals(SubtitleStyle.MPV_ALIGN_X_DEFAULT, SubtitleStyle.mpvAlignX(Position.BOTTOM_CENTER))
        assertEquals(SubtitleStyle.MPV_ALIGN_X_DEFAULT, SubtitleStyle.mpvAlignX(Position.DEFAULT))
    }

    @Test
    fun `the fraction mapping agrees with the mpv mapping for every anchor`() {
        // The Compose overlay and mpv's OSD must place the same line in the same place, or the same
        // subtitle jumps when playback changes engine.
        for (p in Position.entries) {
            val nearTop = SubtitleStyle.lineFraction(p) < 0.5f
            assertEquals("vertical agreement for $p", p.isTop, nearTop)
            assertEquals("vertical agreement for $p", SubtitleStyle.mpvSubPos(p) == SubtitleStyle.MPV_POS_TOP, nearTop)

            val x = SubtitleStyle.positionFraction(p)
            when (SubtitleStyle.mpvAlignX(p)) {
                "left" -> assertTrue("left anchor for $p", x < 0.5f)
                "right" -> assertTrue("right anchor for $p", x > 0.5f)
                else -> assertEquals("centre anchor for $p", 0.5f, x, 0.0001f)
            }
        }
    }

    @Test
    fun `the anchors sit inside the frame, never flush against an edge`() {
        // A line placed at exactly 0 or 1 is clipped by overscan on a TV; the 5% inset is the safe area.
        for (p in Position.entries) {
            assertTrue("line inside for $p", SubtitleStyle.lineFraction(p) in 0.02f..0.98f)
            assertTrue("column inside for $p", SubtitleStyle.positionFraction(p) in 0.02f..0.98f)
        }
    }

    @Test
    fun `colors parse in both accepted forms and never crash on junk`() {
        assertEquals(0xFFFFEE00.toInt(), SubtitleStyle.colorArgb("#FFEE00"))
        assertEquals(0xFFFFEE00.toInt(), SubtitleStyle.colorArgb("FFEE00"))
        assertEquals(0x80FF0000.toInt(), SubtitleStyle.colorArgb("#80FF0000"))
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle.colorArgb("")) // falls back to opaque white
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle.colorArgb("#zzzzzz"))
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle.colorArgb("#12345"))
    }

    @Test
    fun `the background box is black at the requested transparency`() {
        assertEquals(0x00000000, SubtitleStyle.backgroundArgb(0))
        assertEquals(0xFF000000.toInt(), SubtitleStyle.backgroundArgb(100))
        assertEquals(0x7F000000, SubtitleStyle.backgroundArgb(50))
        assertEquals(0xFF000000.toInt(), SubtitleStyle.backgroundArgb(500)) // clamped
        assertEquals(0x00000000, SubtitleStyle.backgroundArgb(-20)) // clamped
    }

    @Test
    fun `mpv gets its colors in the AARRGGBB form it expects`() {
        assertEquals("#FFFFEE00", SubtitleStyle.mpvColor("#FFEE00"))
        assertEquals("#7F000000", SubtitleStyle.mpvBackColor(50))
    }
}
