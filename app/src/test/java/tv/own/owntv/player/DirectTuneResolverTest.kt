package tv.own.owntv.player

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectTuneResolverTest {

    // --- resolveDirectTuneCandidate ---

    @Test
    fun singleCandidate_returnsIt() {
        assertEquals(10L, resolveDirectTuneCandidate(listOf(10L), emptySet()))
    }

    @Test
    fun singleCandidate_evenIfNotInZap_returnsIt() {
        assertEquals(10L, resolveDirectTuneCandidate(listOf(10L), setOf(99L)))
    }

    @Test
    fun twoCandidates_oneInZap_returnsThat() {
        assertEquals(20L, resolveDirectTuneCandidate(listOf(10L, 20L), setOf(20L)))
    }

    @Test
    fun twoCandidates_noneInZap_returnsNull() {
        assertNull(resolveDirectTuneCandidate(listOf(10L, 20L), emptySet()))
    }

    @Test
    fun twoCandidates_bothInZap_returnsNull() {
        assertNull(resolveDirectTuneCandidate(listOf(10L, 20L), setOf(10L, 20L)))
    }

    @Test
    fun threeCandidates_oneInZap_returnsThat() {
        assertEquals(30L, resolveDirectTuneCandidate(listOf(10L, 20L, 30L), setOf(30L)))
    }

    @Test
    fun threeCandidates_twoInZap_returnsNull() {
        assertNull(resolveDirectTuneCandidate(listOf(10L, 20L, 30L), setOf(10L, 20L)))
    }

    @Test
    fun emptyCandidates_returnsNull() {
        assertNull(resolveDirectTuneCandidate(emptyList(), emptySet()))
    }

    @Test
    fun zapContextWithNoOverlap_returnsNull() {
        assertNull(resolveDirectTuneCandidate(listOf(10L, 20L), setOf(99L, 100L)))
    }

    // --- keyToDigit ---

    @Test
    fun standardDigits_0through9() {
        assertEquals('0', keyToDigit(Key.Zero))
        assertEquals('1', keyToDigit(Key.One))
        assertEquals('2', keyToDigit(Key.Two))
        assertEquals('3', keyToDigit(Key.Three))
        assertEquals('4', keyToDigit(Key.Four))
        assertEquals('5', keyToDigit(Key.Five))
        assertEquals('6', keyToDigit(Key.Six))
        assertEquals('7', keyToDigit(Key.Seven))
        assertEquals('8', keyToDigit(Key.Eight))
        assertEquals('9', keyToDigit(Key.Nine))
    }

    @Test
    fun numpadDigits_0through9() {
        assertEquals('0', keyToDigit(Key.NumPad0))
        assertEquals('1', keyToDigit(Key.NumPad1))
        assertEquals('2', keyToDigit(Key.NumPad2))
        assertEquals('3', keyToDigit(Key.NumPad3))
        assertEquals('4', keyToDigit(Key.NumPad4))
        assertEquals('5', keyToDigit(Key.NumPad5))
        assertEquals('6', keyToDigit(Key.NumPad6))
        assertEquals('7', keyToDigit(Key.NumPad7))
        assertEquals('8', keyToDigit(Key.NumPad8))
        assertEquals('9', keyToDigit(Key.NumPad9))
    }

    @Test
    fun nonDigitKeys_returnNull() {
        assertNull(keyToDigit(Key.Enter))
        assertNull(keyToDigit(Key.DirectionCenter))
        assertNull(keyToDigit(Key.Back))
        assertNull(keyToDigit(Key.DirectionUp))
        assertNull(keyToDigit(Key.ChannelUp))
        assertNull(keyToDigit(Key.A))
    }

    @Test
    fun standardAndNumpadProduceSameDigit() {
        // Each standard key and its numpad counterpart must produce the same char.
        val pairs = listOf(
            Key.Zero to Key.NumPad0, Key.One to Key.NumPad1, Key.Two to Key.NumPad2,
            Key.Three to Key.NumPad3, Key.Four to Key.NumPad4, Key.Five to Key.NumPad5,
            Key.Six to Key.NumPad6, Key.Seven to Key.NumPad7, Key.Eight to Key.NumPad8,
            Key.Nine to Key.NumPad9,
        )
        for ((std, numpad) in pairs) {
            assertEquals(keyToDigit(std), keyToDigit(numpad))
        }
    }

    // --- wrappedZapIndex ---
    // The wraparound behaviour that lets CH+/- never dead-end (last → first, first → last) and
    // handles both the OwnTV convention (CH+ = -1, CH- = +1) and any other delta. Used by both
    // the live-list path and the saved-anchor fallback path in LiveViewModel.zap().

    @Test
    fun wrappedZapIndex_chDown_fromMiddle() {
        // delta=+1 (CH-), size=5, current=2 → index 3.
        assertEquals(3, wrappedZapIndex(currentIndex = 2, delta = 1, listSize = 5))
    }

    @Test
    fun wrappedZapIndex_chUp_fromMiddle() {
        // delta=-1 (CH+), size=5, current=2 → index 1.
        assertEquals(1, wrappedZapIndex(currentIndex = 2, delta = -1, listSize = 5))
    }

    @Test
    fun wrappedZapIndex_chDown_atLast_wrapsToZero() {
        assertEquals(0, wrappedZapIndex(currentIndex = 4, delta = 1, listSize = 5))
    }

    @Test
    fun wrappedZapIndex_chUp_atZero_wrapsToLast() {
        assertEquals(4, wrappedZapIndex(currentIndex = 0, delta = -1, listSize = 5))
    }

    @Test
    fun wrappedZapIndex_handlesLargeNegativeDelta() {
        // Effectively a CH- by 2 from the second-to-last position.
        assertEquals(0, wrappedZapIndex(currentIndex = 3, delta = -3, listSize = 5))
    }

    @Test
    fun wrappedZapIndex_handlesLargePositiveDelta() {
        assertEquals(2, wrappedZapIndex(currentIndex = 3, delta = 4, listSize = 5))
    }

    @Test
    fun wrappedZapIndex_singleElement_returnsNull() {
        // No neighbour possible.
        assertEquals(null, wrappedZapIndex(currentIndex = 0, delta = 1, listSize = 1))
        assertEquals(null, wrappedZapIndex(currentIndex = 0, delta = -1, listSize = 1))
    }

    @Test
    fun wrappedZapIndex_emptyList_returnsNull() {
        assertEquals(null, wrappedZapIndex(currentIndex = 0, delta = 1, listSize = 0))
    }

    @Test
    fun wrappedZapIndex_outOfRangeCurrentIndex_returnsNull() {
        // Defensive: caller passed a stale snapshot. Don't pretend we have a neighbour.
        assertEquals(null, wrappedZapIndex(currentIndex = 10, delta = 1, listSize = 5))
        assertEquals(null, wrappedZapIndex(currentIndex = -1, delta = 1, listSize = 5))
    }
}
