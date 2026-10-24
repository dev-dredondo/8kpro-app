package tv.own.owntv.features.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.GuideWidthLimits
import tv.own.owntv.core.settings.GuideWidthShares
import tv.own.owntv.core.settings.normalizeGuideWidths

class GuideWidthsTest {
    @Test
    fun `valid split must total one hundred and keep both columns usable`() {
        assertTrue(GuideWidthShares(25, 75).isValid)
        assertFalse(GuideWidthShares(25, 70).isValid)
        assertFalse(GuideWidthShares(5, 95).isValid)
    }

    @Test
    fun `normalization snaps channel share and balances epg remainder`() {
        assertEquals(GuideWidthShares(35, 65), normalizeGuideWidths(GuideWidthShares(33, 80)))
        assertEquals(GuideWidthShares(90, 10), normalizeGuideWidths(GuideWidthShares(100, 0)))
    }

    @Test
    fun `defaults approximate the stock pinned guide column`() {
        assertEquals(GuideWidthShares(10, 90), GuideWidthLimits.defaults)
        assertTrue(GuideWidthLimits.defaults.isValid)
    }
}
