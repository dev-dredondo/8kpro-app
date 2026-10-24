package tv.own.owntv.core.i18n

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtlKeysTest {
    @Test
    fun `left is start in LTR`() {
        assertEquals(HorizontalDirection.START, Key.DirectionLeft.horizontalDirection(LayoutDirection.Ltr))
    }

    @Test
    fun `right is end in LTR`() {
        assertEquals(HorizontalDirection.END, Key.DirectionRight.horizontalDirection(LayoutDirection.Ltr))
    }

    @Test
    fun `left is end in RTL`() {
        assertEquals(HorizontalDirection.END, Key.DirectionLeft.horizontalDirection(LayoutDirection.Rtl))
    }

    @Test
    fun `right is start in RTL`() {
        assertEquals(HorizontalDirection.START, Key.DirectionRight.horizontalDirection(LayoutDirection.Rtl))
    }

    @Test
    fun `non-horizontal key has no logical direction`() {
        assertNull(Key.DirectionUp.horizontalDirection(LayoutDirection.Ltr))
    }
}
