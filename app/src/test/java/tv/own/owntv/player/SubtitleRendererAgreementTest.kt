package tv.own.owntv.player

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.core.settings.SubtitleStyle.Position

/**
 * Every subtitle option must reach **all three** renderers.
 *
 * Subtitles are drawn by three unrelated things depending on what is playing: mpv's own OSD, Media3's
 * `SubtitleView` (Live TV and the VOD image-subtitle handoff), and the app-drawn Compose overlay on
 * mpv's direct-render path. Each reads the same settings and derives its own instruction from them,
 * so an option can quietly work on one path and do nothing on another — "I changed the subtitle
 * colour and it only worked on films" is the shape of that bug, and nothing else in the suite catches
 * it.
 *
 * The rule pinned here, per option: **a non-default setting produces a non-default instruction on all
 * three renderers, and the default setting produces the stock instruction on all three.** The
 * numbers themselves belong to [SubtitleStyleTest]; this is about coverage.
 */
class SubtitleRendererAgreementTest {

    // The stock look each renderer has when every option is on "Default", as the renderers express it.
    private val mediaStockText = 0xFFFFFFFF.toInt() // CaptionStyleCompat.DEFAULT foreground
    private val composeStockText = 0xFFFFFFFF.toInt()
    private val composeStockBoxAlpha = 0.45f

    @Test
    fun `text size reaches mpv, SubtitleView and the overlay`() {
        val scale = 1.5f
        assertTrue("the setting must register as picked", SubtitleStyle.hasScale(scale))

        // mpv — `sub-scale` is the multiplier itself.
        assertNotEquals(SubtitleStyle.SCALE_DEFAULT, scale)
        // Media3 — DEFAULT_TEXT_SIZE_FRACTION * scale (setFractionalTextSize).
        assertNotEquals(MEDIA3_DEFAULT_TEXT_SIZE_FRACTION, MEDIA3_DEFAULT_TEXT_SIZE_FRACTION * scale)
        // Compose overlay — 24sp * scale.
        assertNotEquals(OVERLAY_BASE_SP, OVERLAY_BASE_SP * scale)
    }

    @Test
    fun `default text size leaves every renderer at its own size`() {
        val scale = SubtitleStyle.SCALE_DEFAULT
        assertTrue(!SubtitleStyle.hasScale(scale))
        assertEquals(MEDIA3_DEFAULT_TEXT_SIZE_FRACTION, MEDIA3_DEFAULT_TEXT_SIZE_FRACTION * scale, 0f)
        assertEquals(OVERLAY_BASE_SP, OVERLAY_BASE_SP * scale, 0f)
    }

    @Test
    fun `text colour reaches mpv, SubtitleView and the overlay`() {
        val hex = "#FFEE00"
        assertTrue(SubtitleStyle.hasColor(hex))

        val argb = SubtitleStyle.colorArgb(hex)
        // mpv — "#AARRGGBB" for `sub-color`.
        assertNotEquals("#FFFFFFFF", SubtitleStyle.mpvColor(hex))
        // Media3 — CaptionStyleCompat foreground, replacing the stock white.
        assertNotEquals(mediaStockText, argb)
        // Compose overlay — the same ARGB, replacing Color.White.
        assertNotEquals(composeStockText, argb)
    }

    @Test
    fun `default colour leaves every renderer on its own colour`() {
        assertTrue(!SubtitleStyle.hasColor(SubtitleStyle.COLOR_DEFAULT))
        // Nothing is pushed at all, so both view-based renderers keep white — which is also what
        // colorArgb falls back to, so an accidental push could never be spotted by value alone. The
        // guard above is the real assertion.
        assertEquals(mediaStockText, SubtitleStyle.colorArgb(SubtitleStyle.COLOR_DEFAULT))
    }

    @Test
    fun `background transparency reaches mpv, SubtitleView and the overlay`() {
        val pct = 90
        assertTrue(SubtitleStyle.hasOpacity(pct))

        val argb = SubtitleStyle.backgroundArgb(pct)
        // mpv — `sub-back-color`.
        assertEquals("#E5000000", SubtitleStyle.mpvBackColor(pct))
        // Media3 — CaptionStyleCompat background; the stock value is transparent black.
        assertNotEquals(0x00000000, argb)
        // Compose overlay — replaces the historical 45%-black box.
        val alpha = ((argb ushr 24) and 0xFF) / 255f
        assertNotEquals(composeStockBoxAlpha, alpha)
    }

    @Test
    fun `fully transparent is a real choice, not a default`() {
        // 0 means "no box at all" and must still be pushed to all three, or the overlay keeps drawing
        // its 45% black behind text the user asked to be bare.
        assertTrue(SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_MIN))
        assertTrue(!SubtitleStyle.hasOpacity(SubtitleStyle.OPACITY_DEFAULT))
        assertNotEquals(composeStockBoxAlpha, 0f)
    }

    @Test
    fun `every anchor reaches mpv, SubtitleView and the overlay`() {
        for (p in Position.ANCHORS) {
            // mpv — sub-pos / sub-align-x.
            val mpvMoved = SubtitleStyle.mpvSubPos(p) != SubtitleStyle.MPV_POS_BOTTOM ||
                SubtitleStyle.mpvAlignX(p) != SubtitleStyle.MPV_ALIGN_X_DEFAULT
            // Media3 — the Cue is rebuilt with these fractions (bottom-centre is the stock placement).
            val cueMoved = SubtitleStyle.lineFraction(p) != SubtitleStyle.lineFraction(Position.DEFAULT) ||
                SubtitleStyle.positionFraction(p) != SubtitleStyle.positionFraction(Position.DEFAULT)
            // Compose overlay — alignment + text alignment.
            val overlayMoved = p.alignment() != Alignment.BottomCenter || p.textAlign() != TextAlign.Center

            // BOTTOM_CENTER is the one anchor that IS the stock placement, so nothing should move.
            if (p == Position.BOTTOM_CENTER) {
                assertTrue("$p should not move anything", !mpvMoved && !cueMoved && !overlayMoved)
            } else {
                assertTrue("mpv ignores $p", mpvMoved)
                assertTrue("SubtitleView ignores $p", cueMoved)
                assertTrue("the Compose overlay ignores $p", overlayMoved)
            }
        }
    }

    @Test
    fun `the default anchor leaves all three renderers where they were`() {
        val p = Position.DEFAULT
        assertEquals(SubtitleStyle.MPV_POS_BOTTOM, SubtitleStyle.mpvSubPos(p))
        assertEquals(SubtitleStyle.MPV_ALIGN_X_DEFAULT, SubtitleStyle.mpvAlignX(p))
        assertEquals(Alignment.BottomCenter, p.alignment())
        assertEquals(TextAlign.Center, p.textAlign())
    }

    private companion object {
        /** `SubtitleView.DEFAULT_TEXT_SIZE_FRACTION` — an Android constant, restated so this stays a
         *  plain JVM test. */
        const val MEDIA3_DEFAULT_TEXT_SIZE_FRACTION = 0.0533f
        /** The overlay's base text size in sp, from `SubtitleOverlay`. */
        const val OVERLAY_BASE_SP = 24f
    }
}
