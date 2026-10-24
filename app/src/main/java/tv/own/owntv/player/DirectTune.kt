package tv.own.owntv.player
import androidx.compose.ui.input.key.Key
/** Outcome of a direct-tune channel-number lookup, communicated from [tv.own.owntv.features.live.LiveViewModel]
 *  back to [PlayerHud] without requiring the HUD to query the database or infer success from player state. */
sealed interface DirectTuneResult {
    /** Exactly one candidate resolved; the channel is now playing. */
    data class Found(val channel: DirectTuneChannelInfo) : DirectTuneResult
    /** No visible channel carries this provider number in the searched sources. */
    data class NotFound(val number: Int) : DirectTuneResult
    /** Multiple visible candidates and no zap-context tiebreaker resolved a single one. */
    data class Ambiguous(val number: Int, val matchCount: Int) : DirectTuneResult
    /** An unexpected error during lookup (logged, playback unchanged). */
    data class Failed(val number: Int) : DirectTuneResult
    /** The playing channel or source set changed during lookup; stale result discarded. */
    data object Cancelled : DirectTuneResult
}

/** Minimal presentation data for a successfully tuned channel, so the HUD can render feedback
 *  immediately without reading the player metadata (which may still describe the previous channel). */
data class DirectTuneChannelInfo(
    val number: Int?,
    val name: String,
    val logoUrl: String? = null,
    /** False when the tuned channel was already playing and no stream was restarted — the HUD then has no
     *  playback start to wait for before it retires the OSD. */
    val restarted: Boolean = true,
)

/** Resolve a list of candidate channel IDs (same provider number, same source) to a single one.
 *  If exactly one candidate exists, return it. If multiple exist, return the one in [zapContext]
 *  (if exactly one duplicate is there). Returns null when resolution is ambiguous. */
internal fun resolveDirectTuneCandidate(
    candidateIds: List<Long>,
    zapContextIds: Set<Long>,
): Long? {
    if (candidateIds.size == 1) return candidateIds[0]
    val inZap = candidateIds.filter { it in zapContextIds }
    return if (inZap.size == 1) inZap[0] else null
}

/** Maps standard and numpad digit keys to '0'..'9', or null for non-digit keys.
 *  Internal for testing. */
internal fun keyToDigit(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** Compute the next index for a CH+/- delta within a bounded zap list. Handles negative deltas
 *  (CH+ = -1, CH- = +1 in the OwnTV convention) and wraps around both ends so the user never
 *  dead-ends (last → first, first → last).
 *
 *  Returns null when there is no valid navigation target:
 *    - [listSize] < 2: a single-element list has no neighbour.
 *    - [currentIndex] out of range: caller passed an inconsistent snapshot.
 *    - The wrapped index lands on [currentIndex] itself (can only happen with a single-element list,
 *      already guarded by the first rule, but kept defensively).
 */
internal fun wrappedZapIndex(currentIndex: Int, delta: Int, listSize: Int): Int? {
    if (listSize < 2) return null
    if (currentIndex !in 0 until listSize) return null
    val raw = ((currentIndex + delta) % listSize + listSize) % listSize
    return if (raw == currentIndex) null else raw
}
