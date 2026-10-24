package tv.own.owntv.player

import androidx.annotation.StringRes
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import tv.own.owntv.R
import tv.own.owntv.core.player.MiniPlayerPosition

/** Where on screen each docking spot puts the mini-player window. */
val MiniPlayerPosition.alignment: Alignment
    get() = when (this) {
        MiniPlayerPosition.TOP_LEFT -> AbsoluteAlignment.TopLeft
        MiniPlayerPosition.TOP_CENTER -> Alignment.TopCenter
        MiniPlayerPosition.TOP_RIGHT -> AbsoluteAlignment.TopRight
        MiniPlayerPosition.BOTTOM_LEFT -> AbsoluteAlignment.BottomLeft
        MiniPlayerPosition.BOTTOM_CENTER -> Alignment.BottomCenter
        MiniPlayerPosition.BOTTOM_RIGHT -> AbsoluteAlignment.BottomRight
    }

/** The user-facing label for each docking spot. */
val MiniPlayerPosition.labelRes: Int
    @StringRes get() = when (this) {
        MiniPlayerPosition.TOP_LEFT -> R.string.player_mini_top_left
        MiniPlayerPosition.TOP_CENTER -> R.string.player_mini_top_center
        MiniPlayerPosition.TOP_RIGHT -> R.string.player_mini_top_right
        MiniPlayerPosition.BOTTOM_LEFT -> R.string.player_mini_bottom_left
        MiniPlayerPosition.BOTTOM_CENTER -> R.string.player_mini_bottom_center
        MiniPlayerPosition.BOTTOM_RIGHT -> R.string.player_mini_bottom_right
    }
